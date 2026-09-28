package io.ltirom.tooling.client.wsl

import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File

public class ServerArtifactInstaller(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val bundleDirProvider: () -> File? = {
        val resDir = System.getProperty("compose.application.resources.dir")
        if (resDir != null) File(resDir, "server") else null
    }
) {
    public fun getBundledVersion(): String? {
        val bundle = bundleDirProvider() ?: return null
        val verFile = File(bundle, "server-version.txt")
        return if (verFile.exists()) verFile.readText().trim().ifBlank { null } else null
    }

    public fun getActiveVersion(environment: SetupEnvironment): String? {
        val linkResult = cli.execute(environment.distro, listOf("readlink", "-f", "${environment.home}/.ltirom/server/current"))
        return if (linkResult.exitCode == 0 && linkResult.output.isNotBlank()) {
            linkResult.output.trim().substringAfterLast("/").ifBlank { null }
        } else {
            null
        }
    }

    public fun status(environment: SetupEnvironment): ServerArtifact {
        val bundled = getBundledVersion()
        val active = getActiveVersion(environment)
        val state = when {
            active == null -> InstallState.NotInstalled
            active == bundled -> InstallState.Current
            bundled != null -> InstallState.Outdated
            else -> InstallState.Current
        }
        return ServerArtifact(
            bundledVersion = bundled,
            activeVersion = active,
            runningVersion = null,
            runningActiveRuns = 0,
            installState = state,
            javaHome = null
        )
    }

    public fun ensureInstalled(environment: SetupEnvironment): ServerArtifact {
        val bundle = bundleDirProvider()
        val bundled = getBundledVersion()
        fun activeBefore(): String? = getActiveVersion(environment)

        if (bundle == null || bundled == null) {
            return ServerArtifact(
                bundledVersion = null,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("bundle", "Server bundle directory or server-version.txt not found"),
                javaHome = null
            )
        }

        val serverBase = "${environment.home}/.ltirom/server"
        val versionsDir = "$serverBase/versions"
        val partialDir = "$versionsDir/$bundled.partial"
        val finalDir = "$versionsDir/$bundled"
        val currentLink = "$serverBase/current"
        val currentTmpLink = "$serverBase/current.tmp"

        // 1. Cleanup existing partial
        val cleanPartial = cli.execute(environment.distro, listOf("rm", "-rf", partialDir))
        if (cleanPartial.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("cleanup", cleanPartial.error.ifBlank { cleanPartial.output }),
                javaHome = null
            )
        }

        // 2. Stage into partial
        val mkdirPartial = cli.execute(environment.distro, listOf("mkdir", "-p", partialDir))
        if (mkdirPartial.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("mkdir", mkdirPartial.error.ifBlank { mkdirPartial.output }),
                javaHome = null
            )
        }

        val wslSource = toWslPath(bundle.absolutePath)
        val copyResult = cli.execute(environment.distro, listOf("cp", "-r", "$wslSource/.", partialDir))
        if (copyResult.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("cp", copyResult.error.ifBlank { copyResult.output }),
                javaHome = null
            )
        }

        // Verify executable
        cli.execute(environment.distro, listOf("chmod", "+x", "$partialDir/bin/ltirom-server"))
        val verifyExecutable = cli.execute(environment.distro, listOf("test", "-x", "$partialDir/bin/ltirom-server"))
        if (verifyExecutable.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("verify", "Server binary $partialDir/bin/ltirom-server is not executable"),
                javaHome = null
            )
        }

        // 3. Rename partial to final
        cli.execute(environment.distro, listOf("rm", "-rf", finalDir))
        val renameResult = cli.execute(environment.distro, listOf("mv", partialDir, finalDir))
        if (renameResult.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("mv", renameResult.error.ifBlank { renameResult.output }),
                javaHome = null
            )
        }

        // 4. Atomic symlink swap
        val linkTmp = cli.execute(environment.distro, listOf("ln", "-sfn", finalDir, currentTmpLink))
        if (linkTmp.exitCode != 0) {
            return ServerArtifact(
                bundledVersion = bundled,
                activeVersion = activeBefore(),
                runningVersion = null,
                runningActiveRuns = 0,
                installState = InstallState.Failed("ln", linkTmp.error.ifBlank { linkTmp.output }),
                javaHome = null
            )
        }

        val swapResult = cli.execute(environment.distro, listOf("mv", "-T", currentTmpLink, currentLink))
        if (swapResult.exitCode != 0) {
            // Fallback direct symlink
            cli.execute(environment.distro, listOf("ln", "-sfn", finalDir, currentLink))
        }

        return ServerArtifact(
            bundledVersion = bundled,
            activeVersion = bundled,
            runningVersion = null,
            runningActiveRuns = 0,
            installState = InstallState.Current,
            javaHome = null
        )
    }

    private fun toWslPath(windowsPath: String): String {
        val normalized = windowsPath.replace('\\', '/')
        val drive = Regex("""^([a-zA-Z]):/(.*)""").find(normalized)
        return if (drive != null) {
            val letter = drive.groupValues[1].lowercase()
            val rest = drive.groupValues[2]
            "/mnt/$letter/$rest"
        } else {
            normalized
        }
    }
}
