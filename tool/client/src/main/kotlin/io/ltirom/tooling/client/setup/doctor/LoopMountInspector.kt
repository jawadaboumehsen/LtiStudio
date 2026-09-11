package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying Linux loop device control (/dev/loop-control) and passwordless sudo mount elevation.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating loop mount permissions.
 */
public class LoopMountInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasLoopControl = if (context.availableCommands.isNotEmpty()) {
            "loop-control" in context.availableCommands
        } else {
            cli.execute(distro, listOf("test", "-e", "/dev/loop-control")).exitCode == 0
        }
        val hasPasswordlessSudo = cli.execute(distro, listOf("sudo", "-n", "true")).exitCode == 0 ||
            cli.execute(distro, listOf("sudo", "-n", "/bin/mount", "-V")).exitCode == 0 ||
            cli.execute(distro, listOf("sudo", "-n", "mount", "-V")).exitCode == 0
        val sudoMountCmd = "echo \"\$USER ALL=(ALL) NOPASSWD: /bin/mount, /bin/umount\" | sudo tee /etc/sudoers.d/ltirom-mount"

        return DiagnosticCheckItem(
            id = "loop_mount",
            title = "Linux Loop Devices & Mount Elevation",
            category = DiagnosticCategory.KERNEL_AND_MOUNT,
            status = when {
                !hasLoopControl -> StepStatus.WARNING
                !hasPasswordlessSudo -> StepStatus.WARNING
                else -> StepStatus.SUCCESS
            },
            detail = when {
                !hasLoopControl -> "/dev/loop-control not found; kernel loop device support may be restricted."
                !hasPasswordlessSudo -> "Loop control available; sudo requires password for ext4/f2fs loop mounts."
                else -> "Loop control ready; non-interactive elevation active for partition loop mounts."
            },
            remediation = if (!hasPasswordlessSudo) {
                "Notice: For automated loop mounts, configure passwordless mount: $sudoMountCmd"
            } else null,
            copyableCommand = if (!hasPasswordlessSudo) sudoMountCmd else null,
            isOptionalForRuntime = true
        )
    }
}
