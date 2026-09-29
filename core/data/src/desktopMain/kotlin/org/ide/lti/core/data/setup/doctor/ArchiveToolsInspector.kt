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
import org.ide.lti.core.data.setup.AptCommandBuilder
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
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasZip = if (context.availableCommands.isNotEmpty()) {
            "zip" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "zip")).exitCode == 0
        }
        val hasUnzip = if (context.availableCommands.isNotEmpty()) {
            "unzip" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "unzip")).exitCode == 0
        }
        val hasBrotli = if (context.availableCommands.isNotEmpty()) {
            "brotli" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "brotli")).exitCode == 0
        }
        val missing = buildList {
            if (!hasZip) add("zip")
            if (!hasUnzip) add("unzip")
            if (!hasBrotli) add("brotli")
        }
        val archiveOk = missing.isEmpty()
        val archiveCmd = if (archiveOk) null else AptCommandBuilder.install(missing)

        return DiagnosticCheckItem(
            id = "archive_tools",
            title = "Archive & Compression Tools",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (archiveOk) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (archiveOk) {
                "zip, unzip, and brotli utilities verified for OTA unpacking and recovery packages."
            } else {
                "Missing: ${missing.joinToString(", ")}."
            },
            remediation = if (archiveOk) null else "Run '$archiveCmd' in WSL.",
            copyableCommand = archiveCmd,
        )
    }
}
