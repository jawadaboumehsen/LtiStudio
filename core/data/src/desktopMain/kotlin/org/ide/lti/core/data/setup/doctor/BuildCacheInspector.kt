package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying optional ccache build accelerator for fast C/C++ submodule rebuilds.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to compiler caching acceleration.
 */
public class BuildCacheInspector(
    private val cli: WslCliExecutor = WslCliExecutor()
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasCcache = if (context.availableCommands.isNotEmpty()) {
            "ccache" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "ccache")).exitCode == 0
        }
        val ccacheCmd = if (hasCcache) null else "sudo apt-get install -y ccache"

        return DiagnosticCheckItem(
            id = "build_cache",
            title = "Build Cache Accelerator (ccache)",
            category = DiagnosticCategory.PERFORMANCE,
            status = if (hasCcache) StepStatus.SUCCESS else StepStatus.WARNING,
            detail = if (hasCcache) {
                "ccache compiler accelerator active for fast submodule rebuilds."
            } else {
                "Optional: ccache not installed. Install to accelerate C/C++ compilation."
            },
            remediation = if (hasCcache) null else "Run '$ccacheCmd' in WSL.",
            copyableCommand = ccacheCmd,
            isOptionalForRuntime = true
        )
    }
}
