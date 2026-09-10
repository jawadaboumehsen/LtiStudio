package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying archive and compression utilities (zip, unzip).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating archive tools.
 */
public class ArchiveToolsInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasZip = cli.execute(distro, listOf("which", "zip")).exitCode == 0
        val hasUnzip = cli.execute(distro, listOf("which", "unzip")).exitCode == 0
        val archiveOk = hasZip && hasUnzip
        val archiveCmd = if (archiveOk) null else "sudo apt-get install -y zip unzip"

        return DiagnosticCheckItem(
            id = "archive_tools",
            title = "Archive & Compression Tools",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (archiveOk) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (archiveOk) {
                "zip and unzip utilities verified for OTA unpacking and recovery packages."
            } else {
                "Missing: ${listOfNotNull(if (!hasZip) "zip" else null, if (!hasUnzip) "unzip" else null).joinToString(", ")}."
            },
            remediation = if (archiveOk) null else "Run '$archiveCmd' in WSL.",
            copyableCommand = archiveCmd
        )
    }
}
