package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslPathTranslator
import java.io.File

/**
 * Single Responsibility: Bootstrapping and synchronizing the LtiRomServer distribution
 * to the Linux native ext4 filesystem ($HOME/.ltirom/server/) to bypass Plan9 (9P) I/O bottleneck.
 */
public class WslExt4Deployer(
    private val cli: WslCliExecutor = WslCliExecutor()
) {
    public fun ensureInstalled(distro: String, userHome: String, overrideBinaryPath: String?): String {
        if (overrideBinaryPath != null) return overrideBinaryPath

        val ext4Binary = "$userHome/.ltirom/server/bin/ltirom-server"
        val ext4Jar = "$userHome/.ltirom/server/lib/ltirom-server.jar"
        val projectInstallDir = resolveProjectServerDir()

        if (projectInstallDir != null) {
            val projectJar = "$projectInstallDir/lib/ltirom-server.jar"
            val binaryMissing = cli.execute(distro, listOf("test", "-x", ext4Binary), timeoutSeconds = 3).exitCode != 0
            val needsSync = binaryMissing || isNewer(distro, projectJar, ext4Jar)

            if (needsSync) {
                val serverDir = "$userHome/.ltirom/server"
                cli.execute(distro, listOf("mkdir", "-p", serverDir), timeoutSeconds = 5)
                cli.execute(distro, listOf("cp", "-r", "$projectInstallDir/.", serverDir), timeoutSeconds = 15)
                cli.execute(distro, listOf("chmod", "-R", "+x", "$serverDir/bin"), timeoutSeconds = 5)
            }
            return ext4Binary
        }

        val exists = cli.execute(distro, listOf("test", "-x", ext4Binary), timeoutSeconds = 3).exitCode == 0
        if (exists) return ext4Binary

        return resolveProjectServerPath() ?: "/usr/local/bin/ltirom-server"
    }

    /** True if [sourcePath] has a newer mtime than [targetPath] (or [targetPath] doesn't exist). */
    private fun isNewer(distro: String, sourcePath: String, targetPath: String): Boolean {
        val targetMtime = cli.execute(distro, listOf("stat", "-c", "%Y", targetPath), timeoutSeconds = 3)
        if (targetMtime.exitCode != 0) return true
        val sourceMtime = cli.execute(distro, listOf("stat", "-c", "%Y", sourcePath), timeoutSeconds = 3)
        val source = sourceMtime.output.trim().toLongOrNull() ?: return false
        val target = targetMtime.output.trim().toLongOrNull() ?: return true
        return source > target
    }

    private fun resolveProjectServerDir(): String? {
        val userHome = System.getProperty("user.home") ?: return null
        val winDir = File(userHome, "Documents/LtiRomProject/LtiRomServer/build/install/ltirom-server")
        if (!winDir.isDirectory) return null
        return WslPathTranslator().toWslPath(winDir.absolutePath)
    }

    private fun resolveProjectServerPath(): String? {
        val userHome = System.getProperty("user.home") ?: return null
        val winPath = File(userHome, "Documents/LtiRomProject/LtiRomServer/build/install/ltirom-server/bin/ltirom-server")
        return WslPathTranslator().toWslPath(winPath.absolutePath)
    }
}
