package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying host C/C++ compilers and build engine (cmake, make, clang/gcc, clang++/g++).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating build toolchains.
 */
public class CompilersInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasCmake = cli.execute(distro, listOf("which", "cmake")).exitCode == 0
        val hasMake = cli.execute(distro, listOf("which", "make")).exitCode == 0
        val hasClang = cli.execute(distro, listOf("which", "clang")).exitCode == 0
        val hasGcc = cli.execute(distro, listOf("which", "gcc")).exitCode == 0
        val hasClangPlus = cli.execute(distro, listOf("which", "clang++")).exitCode == 0
        val hasGplus = cli.execute(distro, listOf("which", "g++")).exitCode == 0

        val missingCompilers = buildList {
            if (!hasCmake) add("cmake")
            if (!hasMake) add("make")
            if (!hasClang && !hasGcc) add("clang")
            if (!hasClangPlus && !hasGplus) add("clang++")
        }
        val compilersOk = missingCompilers.isEmpty()
        val compilerCmd = if (compilersOk) null else "sudo apt-get install -y build-essential ${missingCompilers.joinToString(" ")}"

        return DiagnosticCheckItem(
            id = "host_compilers",
            title = "Host Compilers & Build Engine",
            category = DiagnosticCategory.COMPILERS,
            status = when {
                compilersOk -> StepStatus.SUCCESS
                context.binariesPresent -> StepStatus.WARNING
                else -> StepStatus.FAILED
            },
            detail = if (compilersOk) {
                "CMake, Make, and native C/C++ compilers verified."
            } else {
                "Missing: ${missingCompilers.joinToString(", ")}."
            },
            remediation = if (compilersOk) null else "Run '$compilerCmd' in WSL.",
            copyableCommand = compilerCmd,
            isOptionalForRuntime = context.binariesPresent
        )
    }
}
