package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying Python 3 runtime, pip package manager, and cryptography/pyasn1 libraries.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to Python cryptographic tooling.
 */
public class PythonRuntimeInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasPython3 = cli.execute(distro, listOf("which", "python3")).exitCode == 0
        val pyVersion = if (hasPython3) {
            val vOut = cli.execute(distro, listOf("python3", "--version")).output.trim()
            if (vOut.isNotBlank()) vOut else "Python 3"
        } else ""
        val hasPip = cli.execute(distro, listOf("which", "pip3")).exitCode == 0 ||
            cli.execute(distro, listOf("python3", "-m", "pip", "--version")).exitCode == 0
        val pyCryptoOk = hasPython3 && cli.execute(distro, listOf("python3", "-c", "import cryptography, pyasn1")).exitCode == 0

        val pyStatus: StepStatus
        val pyDetail: String
        val pyRemediation: String?
        val pyCopyCmd: String?

        when {
            !hasPython3 -> {
                pyStatus = StepStatus.FAILED
                pyDetail = "Python 3 interpreter not found in WSL (required for avbtool, mkbootimg, and img2sdat)."
                pyRemediation = "Run 'sudo apt-get install -y python3 python3-pip && pip3 install cryptography pyasn1' in WSL."
                pyCopyCmd = "sudo apt-get install -y python3 python3-pip && pip3 install cryptography pyasn1"
            }
            !hasPip -> {
                pyStatus = StepStatus.WARNING
                pyDetail = "$pyVersion detected, but pip3 package manager is missing."
                pyRemediation = "Run 'sudo apt-get install -y python3-pip && pip3 install cryptography pyasn1' in WSL."
                pyCopyCmd = "sudo apt-get install -y python3-pip && pip3 install cryptography pyasn1"
            }
            !pyCryptoOk -> {
                pyStatus = StepStatus.WARNING
                pyDetail = "$pyVersion verified, but cryptography/pyasn1 packages are missing."
                pyRemediation = "Run 'pip3 install --break-system-packages cryptography pyasn1' in WSL."
                pyCopyCmd = "pip3 install --break-system-packages cryptography pyasn1"
            }
            else -> {
                pyStatus = StepStatus.SUCCESS
                pyDetail = "$pyVersion and cryptography/pyasn1 verified for AVB 2.0 signing and boot image tools."
                pyRemediation = null
                pyCopyCmd = null
            }
        }

        return DiagnosticCheckItem(
            id = "python_crypto",
            title = "Python 3 Runtime & Cryptography",
            category = DiagnosticCategory.RUNTIMES,
            status = pyStatus,
            detail = pyDetail,
            remediation = pyRemediation,
            copyableCommand = pyCopyCmd
        )
    }
}
