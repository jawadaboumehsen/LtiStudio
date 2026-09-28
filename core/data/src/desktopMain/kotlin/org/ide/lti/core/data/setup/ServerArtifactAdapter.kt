package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import kotlinx.coroutines.CancellationException
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment

public class ServerArtifactAdapter(
    private val installer: ServerArtifactInstaller = ServerArtifactInstaller(),
    private val daemonManager: WslDaemonManager = WslDaemonManager(),
) : ServerArtifactPort {

    override suspend fun status(environment: SetupEnvironment): ServerArtifact {
        val artifact = installer.status(environment)
        val serverInfo = daemonManager.checkExistingDaemon(environment.distro)?.let {
            daemonManager.getServerInfo(environment)
        }
        return artifact.copy(
            runningVersion = serverInfo?.serverVersion,
            runningActiveRuns = serverInfo?.activeRuns ?: 0,
        )
    }

    override suspend fun ensureInstalled(environment: SetupEnvironment): ServerArtifact =
        installer.ensureInstalled(environment)

    override suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String): ServerArtifact {
        val installed = installer.status(environment)
        val needsInstall = installed.installState is InstallState.Failed ||
            installed.installState is InstallState.NotInstalled
        val installFailure = if (needsInstall) {
            installer.ensureInstalled(environment).takeIf { it.installState is InstallState.Failed }
        } else {
            null
        }
        val activeVer = installed.activeVersion ?: installer.getActiveVersion(environment)
        val failure = installFailure ?: startFailure(environment, activeVer, javaHome)?.let { detail ->
            installed.copy(activeVersion = activeVer, installState = InstallState.Failed("start", detail))
        }
        return failure?.copy(javaHome = javaHome) ?: runningArtifact(environment, installed, activeVer, javaHome)
    }

    /** Starts (or adopts) the service; returns why it failed, or null when it is running. */
    private suspend fun startFailure(environment: SetupEnvironment, activeVer: String?, javaHome: String): String? =
        try {
            daemonManager.ensureRunningVersion(
                distro = environment.distro,
                activeVersion = activeVer,
                home = environment.home,
                javaHome = javaHome,
            )
            null
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // The launch log tail says why the JVM died; the exception alone usually does not.
            daemonManager.lastLaunchErrorLogTail ?: e.message ?: e::class.simpleName.orEmpty()
        }

    private suspend fun runningArtifact(
        environment: SetupEnvironment,
        installed: ServerArtifact,
        activeVer: String?,
        javaHome: String,
    ): ServerArtifact {
        val serverInfo = daemonManager.getServerInfo(environment)
        return ServerArtifact(
            bundledVersion = installed.bundledVersion,
            activeVersion = activeVer,
            runningVersion = serverInfo?.serverVersion ?: activeVer,
            runningActiveRuns = serverInfo?.activeRuns ?: 0,
            installState = if (daemonManager.isUpdateWaiting) InstallState.Outdated else InstallState.Current,
            javaHome = javaHome,
        )
    }
}
