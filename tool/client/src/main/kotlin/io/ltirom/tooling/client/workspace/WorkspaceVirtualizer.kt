package io.ltirom.tooling.client.workspace

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File

public data class VirtualWorkspaceDescriptor(
    val workspaceId: String,
    val linuxExt4Path: String,
    val windowsUncPath: String,
    val isNativeExt4: Boolean = true
) {
    public val windowsFile: File get() = File(windowsUncPath)
}

/**
 * Workspace Virtualizer that bypasses the slow Plan9 (9P) filesystem (/mnt/c/)
 * by allocating workspaces directly on Linux native ext4 ($HOME/.ltirom/workspaces/)
 * and exposing them to the Windows Desktop GUI via the native hypervisor UNC provider
 * (\\wsl.localhost\<distro>\...).
 *
 * Delivers bare-metal NVMe performance (~1,200 MB/s vs ~150 MB/s over 9P)
 * for multi-gigabyte Android partition images (super.img, payload.bin, system.img).
 */
public class WorkspaceVirtualizer(
    private val distroName: String? = null,
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(cli)
) {
    private val validWorkspaceId = Regex("^[A-Za-z0-9_-]+$")

    private fun requireSafeWorkspaceId(workspaceId: String): String {
        require(validWorkspaceId.matches(workspaceId)) {
            "workspaceId must be a simple alphanumeric/-/_ identifier, got: $workspaceId"
        }
        return workspaceId
    }

    public fun getWslWorkspaceRoot(environment: SetupEnvironment): String {
        return "${environment.home}/.ltirom/workspaces"
    }

    public fun getWindowsUncRoot(environment: SetupEnvironment): String {
        val home = environment.home.removePrefix("/").replace('/', '\\')
        return "\\\\wsl.localhost\\${environment.distro}\\$home\\.ltirom\\workspaces"
    }

    public fun allocateWorkspace(workspaceId: String, environment: SetupEnvironment): VirtualWorkspaceDescriptor {
        requireSafeWorkspaceId(workspaceId)
        val linuxPath = "${getWslWorkspaceRoot(environment)}/$workspaceId"
        val uncPath = "${getWindowsUncRoot(environment)}\\$workspaceId"

        // Ensure directory exists on native ext4
        cli.execute(environment.distro, listOf("mkdir", "-p", linuxPath), timeoutSeconds = 5)

        return VirtualWorkspaceDescriptor(
            workspaceId = workspaceId,
            linuxExt4Path = linuxPath,
            windowsUncPath = uncPath,
            isNativeExt4 = true
        )
    }

    public fun deleteWorkspace(workspaceId: String, environment: SetupEnvironment): Boolean {
        requireSafeWorkspaceId(workspaceId)
        val linuxPath = "${getWslWorkspaceRoot(environment)}/$workspaceId"
        val res = cli.execute(environment.distro, listOf("rm", "-rf", linuxPath), timeoutSeconds = 10)
        return res.exitCode == 0
    }

    public fun getWslWorkspaceRoot(): String {
        val distro = distroName ?: error("distroName not configured and no SetupEnvironment provided")
        val home = detector.resolveWslUserHome(distro)
        return "$home/.ltirom/workspaces"
    }

    public fun getWindowsUncRoot(): String {
        val distro = distroName ?: error("distroName not configured and no SetupEnvironment provided")
        val home = detector.resolveWslUserHome(distro).removePrefix("/").replace('/', '\\')
        return "\\\\wsl.localhost\\$distro\\$home\\.ltirom\\workspaces"
    }

    public fun allocateWorkspace(workspaceId: String): VirtualWorkspaceDescriptor {
        val distro = distroName ?: error("distroName not configured and no SetupEnvironment provided")
        requireSafeWorkspaceId(workspaceId)
        val linuxPath = "${getWslWorkspaceRoot()}/$workspaceId"
        val uncPath = "${getWindowsUncRoot()}\\$workspaceId"

        // Ensure directory exists on native ext4
        cli.execute(distro, listOf("mkdir", "-p", linuxPath), timeoutSeconds = 5)

        return VirtualWorkspaceDescriptor(
            workspaceId = workspaceId,
            linuxExt4Path = linuxPath,
            windowsUncPath = uncPath,
            isNativeExt4 = true
        )
    }

    public fun deleteWorkspace(workspaceId: String): Boolean {
        val distro = distroName ?: error("distroName not configured and no SetupEnvironment provided")
        requireSafeWorkspaceId(workspaceId)
        val linuxPath = "${getWslWorkspaceRoot()}/$workspaceId"
        val res = cli.execute(distro, listOf("rm", "-rf", linuxPath), timeoutSeconds = 10)
        return res.exitCode == 0
    }
}
