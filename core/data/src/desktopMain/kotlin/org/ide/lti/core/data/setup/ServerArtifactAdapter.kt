package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment

public class ServerArtifactAdapter(
    private val installer: ServerArtifactInstaller = ServerArtifactInstaller(),
    private val daemonManager: WslDaemonManager = WslDaemonManager()
) : ServerArtifactPort {

    override suspend fun status(environment: SetupEnvironment): ServerArtifact {
        val artifact = installer.status(environment)
        val serverInfo = daemonManager.checkExistingDaemon(environment.distro)?.let {
            daemonManager.getServerInfo(environment)
        }
        return artifact.copy(
            runningVersion = serverInfo?.serverVersion,
            runningActiveRuns = serverInfo?.activeRuns ?: 0
        )
    }

    override suspend fun ensureInstalled(environment: SetupEnvironment): ServerArtifact {
        return installer.ensureInstalled(environment)
    }

    override suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String): ServerArtifact {
        val installed = installer.status(environment)
        if (installed.installState is InstallState.Failed || installed.installState is InstallState.NotInstalled) {
            val res = installer.ensureInstalled(environment)
            if (res.installState is InstallState.Failed) {
                return res.copy(javaHome = javaHome)
            }
        }

        val activeVer = installed.activeVersion ?: installer.getActiveVersion(environment)
        val descriptor = daemonManager.ensureRunningVersion(
            distro = environment.distro,
            activeVersion = activeVer,
            home = environment.home,
            javaHome = javaHome
        )
        val serverInfo = daemonManager.getServerInfo(environment)
        return ServerArtifact(
            bundledVersion = installed.bundledVersion,
            activeVersion = activeVer,
            runningVersion = serverInfo?.serverVersion ?: activeVer,
            runningActiveRuns = serverInfo?.activeRuns ?: 0,
            installState = if (daemonManager.isUpdateWaiting) InstallState.Outdated else InstallState.Current,
            javaHome = javaHome
        )
    }
}
