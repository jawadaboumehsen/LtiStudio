package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying host transfer and synchronization utilities (curl, git, rsync, tar).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating transfer tools.
 */
public class TransferToolsInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasCurl = cli.execute(distro, listOf("which", "curl")).exitCode == 0
        val hasGit = cli.execute(distro, listOf("which", "git")).exitCode == 0
        val hasRsync = cli.execute(distro, listOf("which", "rsync")).exitCode == 0
        val hasTar = cli.execute(distro, listOf("which", "tar")).exitCode == 0

        val missingTransfer = buildList {
            if (!hasCurl) add("curl")
            if (!hasGit) add("git")
            if (!hasRsync) add("rsync")
            if (!hasTar) add("tar")
        }
        val transferOk = missingTransfer.isEmpty()
        val transferCmd = if (transferOk) null else "sudo apt-get install -y ${missingTransfer.joinToString(" ")}"

        return DiagnosticCheckItem(
            id = "transfer_tools",
            title = "Host Transfer & Sync Utilities",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (transferOk) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (transferOk) {
                "curl, git, rsync, and tar utilities verified for repository sync and downloads."
            } else {
                "Missing transfer utilities: ${missingTransfer.joinToString(", ")}."
            },
            remediation = if (transferOk) null else "Run '$transferCmd' in WSL.",
            copyableCommand = transferCmd
        )
    }
}
