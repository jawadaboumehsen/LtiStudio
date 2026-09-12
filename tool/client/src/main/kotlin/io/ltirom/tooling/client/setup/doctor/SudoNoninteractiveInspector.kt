package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying whether non-interactive, passwordless sudo elevation is available.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Exclusively probes `sudo -n true`.
 * - Security & Non-Interactive Automation: Guarantees that automated package remediation
 *   or kernel operations will not block waiting for password input on a missing TTY.
 */
public class SudoNoninteractiveInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val res = cli.execute(
            distro = distro,
            command = listOf("sudo", "-n", "true"),
            timeoutSeconds = 5
        )

        val isSudoNoninteractiveOk = res.exitCode == 0
        val sudoersInstruction = "echo \"\$USER ALL=(ALL) NOPASSWD: ALL\" | sudo tee /etc/sudoers.d/ltirom"

        return DiagnosticCheckItem(
            id = "sudo_noninteractive",
            title = "Non-Interactive Sudo Elevation",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (isSudoNoninteractiveOk) StepStatus.SUCCESS else StepStatus.WARNING,
            detail = if (isSudoNoninteractiveOk) {
                "Passwordless sudo configured for non-interactive commands."
            } else {
                "Non-interactive sudo is not configured for current user in $distro."
            },
            remediation = if (isSudoNoninteractiveOk) {
                null
            } else {
                "Configure passwordless sudo so automated package remediation can install dependencies non-interactively."
            },
            copyableCommand = if (isSudoNoninteractiveOk) null else sudoersInstruction,
            isOptionalForRuntime = true
        )
    }
}
