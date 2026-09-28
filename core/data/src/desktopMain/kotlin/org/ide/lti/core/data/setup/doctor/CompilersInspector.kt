package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.data.setup.AptCommandBuilder
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
        fun hasCommand(cmd: String): Boolean = if (context.availableCommands.isNotEmpty()) {
            cmd in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", cmd)).exitCode == 0
        }

        val hasCmake = hasCommand("cmake")
        val hasMake = hasCommand("make")
        val hasClang = hasCommand("clang")
        val hasGcc = hasCommand("gcc")
        val hasClangPlus = hasCommand("clang++")
        val hasGplus = hasCommand("g++")

        val missingCompilers = buildList {
            if (!hasCmake) add("cmake")
            if (!hasMake) add("make")
            if (!hasClang && !hasGcc) add("clang")
            if (!hasClangPlus && !hasGplus) add("clang++")
        }
        val compilersOk = missingCompilers.isEmpty()
        val missingPackages = buildList {
            add("build-essential")
            if (!hasCmake) add("cmake")
            if (!hasMake) add("make")
            if (!hasClang && !hasGcc) add("clang")
        }.distinct()
        val compilerCmd = if (compilersOk) null else AptCommandBuilder.install(missingPackages)

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
