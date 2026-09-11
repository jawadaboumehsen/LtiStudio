package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying Dual OpenJDK runtimes (17 and 21) and the javac compiler.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to Java development environments.
 */
public class DualJdkInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasJava = if (context.availableCommands.isNotEmpty()) {
            "java" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "java")).exitCode == 0
        }
        val hasJavac = if (context.availableCommands.isNotEmpty()) {
            "javac" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "javac")).exitCode == 0
        }
        val javaVerOutput = if (hasJava) {
            cli.execute(distro, listOf("java", "-version")).let { it.output + "\n" + it.error }.trim()
        } else ""
        val detectedJavaVer = Regex("version\\s+\"([^\"]+)").find(javaVerOutput)?.groupValues?.get(1) ?: if (hasJava) "Active" else "None"
        val hasJvm17 = if (context.availableCommands.isNotEmpty()) {
            "java-17-openjdk-amd64" in context.availableCommands || "java-17-openjdk" in context.availableCommands
        } else {
            cli.execute(distro, listOf("test", "-d", "/usr/lib/jvm/java-17-openjdk-amd64")).exitCode == 0 ||
                cli.execute(distro, listOf("test", "-d", "/usr/lib/jvm/java-17-openjdk")).exitCode == 0
        }
        val hasJvm21 = if (context.availableCommands.isNotEmpty()) {
            "java-21-openjdk-amd64" in context.availableCommands || "java-21-openjdk" in context.availableCommands
        } else {
            cli.execute(distro, listOf("test", "-d", "/usr/lib/jvm/java-21-openjdk-amd64")).exitCode == 0 ||
                cli.execute(distro, listOf("test", "-d", "/usr/lib/jvm/java-21-openjdk")).exitCode == 0
        }
        val jdkCmd = "sudo apt-get install -y openjdk-17-jdk openjdk-21-jdk"

        val jdkStatus: StepStatus
        val jdkDetail: String
        val jdkRemediation: String?
        val jdkCopyCmd: String?

        when {
            !hasJava -> {
                jdkStatus = StepStatus.FAILED
                jdkDetail = "No Java runtime detected in WSL."
                jdkRemediation = "Run '$jdkCmd' in WSL."
                jdkCopyCmd = jdkCmd
            }
            !hasJavac -> {
                jdkStatus = if (context.binariesPresent) StepStatus.WARNING else StepStatus.FAILED
                jdkDetail = "JRE ($detectedJavaVer) detected, but javac compiler is missing for Gradle builds."
                jdkRemediation = "Run '$jdkCmd' in WSL to install the full development JDK."
                jdkCopyCmd = jdkCmd
            }
            !hasJvm17 || !hasJvm21 -> {
                jdkStatus = StepStatus.WARNING
                jdkDetail = "Java ($detectedJavaVer) with javac verified. OpenJDK 17 (apktool) & OpenJDK 21 (LtiRomKt) recommended."
                jdkRemediation = "Run '$jdkCmd' in WSL."
                jdkCopyCmd = jdkCmd
            }
            else -> {
                jdkStatus = StepStatus.SUCCESS
                jdkDetail = "Java ($detectedJavaVer) with javac compiler and dual OpenJDK runtimes verified."
                jdkRemediation = null
                jdkCopyCmd = null
            }
        }

        return DiagnosticCheckItem(
            id = "jdk_dual",
            title = "Dual JDK Runtimes & Compiler",
            category = DiagnosticCategory.RUNTIMES,
            status = jdkStatus,
            detail = jdkDetail,
            remediation = jdkRemediation,
            copyableCommand = jdkCopyCmd,
            isOptionalForRuntime = context.binariesPresent && hasJava
        )
    }
}
