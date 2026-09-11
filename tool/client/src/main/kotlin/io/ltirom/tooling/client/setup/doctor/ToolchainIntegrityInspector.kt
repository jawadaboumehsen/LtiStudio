package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying the presence and integrity of all 14 core project tools in ~/LtiRomTools/bin.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to verifying compiled project binary integrity.
 */
public class ToolchainIntegrityInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val coreTools: List<String> = DEFAULT_CORE_TOOLS
) : SystemDiagnosticInspector {

    public companion object {
        public val DEFAULT_CORE_TOOLS: List<String> = listOf(
            "adb", "fastboot", "avbtool", "apktool.jar", "signapk.jar", "zipalign",
            "lpunpack", "lpmake", "lpdump", "mkfs.erofs", "dump.erofs", "erofsfuse",
            "img2sdat", "payload-dumper-go"
        )
    }

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val binDir = context.binDir
        val missingCoreTools = if (context.availableCommands.isNotEmpty()) {
            coreTools.filter { it !in context.availableCommands }
        } else {
            coreTools.filter {
                cli.execute(distro, listOf("test", "-e", "$binDir/$it")).exitCode != 0
            }
        }
        val allToolsOk = missingCoreTools.isEmpty()

        return DiagnosticCheckItem(
            id = "toolchain_binaries",
            title = "Compiled Toolchain Binaries Integrity",
            category = DiagnosticCategory.TOOLCHAIN,
            status = when {
                allToolsOk -> StepStatus.SUCCESS
                context.binariesPresent -> if (missingCoreTools.size < coreTools.size) StepStatus.WARNING else StepStatus.FAILED
                else -> StepStatus.WARNING
            },
            detail = if (allToolsOk) {
                "All ${coreTools.size} core tools active in $binDir."
            } else {
                "${coreTools.size - missingCoreTools.size}/${coreTools.size} tools active. Missing: ${missingCoreTools.joinToString(", ")}."
            },
            remediation = if (allToolsOk) null else "Run Toolchain Setup in app to compile/download missing binaries.",
            isOptionalForRuntime = !context.binariesPresent
        )
    }
}
