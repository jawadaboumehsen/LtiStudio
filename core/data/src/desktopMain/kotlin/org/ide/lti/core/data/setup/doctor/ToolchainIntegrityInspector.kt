/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.doctor

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
    private val coreTools: List<String> = DEFAULT_CORE_TOOLS,
) : SystemDiagnosticInspector {

    public companion object {
        public val DEFAULT_CORE_TOOLS: List<String> = listOf(
            "adb", "fastboot", "avbtool", "apktool.jar", "signapk.jar", "zipalign",
            "lpunpack", "lpmake", "lpdump", "mkfs.erofs", "dump.erofs", "erofsfuse",
            "img2sdat", "payload-dumper-go",
        )
    }

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val binDir = context.binDir
        val missingCoreTools = if (context.projectBinaries.isNotEmpty()) {
            coreTools.filter { it !in context.projectBinaries }
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
            isOptionalForRuntime = !context.binariesPresent,
        )
    }
}
