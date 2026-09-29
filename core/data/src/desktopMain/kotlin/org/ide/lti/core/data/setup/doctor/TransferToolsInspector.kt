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
import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying host transfer and synchronization utilities (curl, git, rsync, tar).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating transfer tools.
 */
public class TransferToolsInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        fun hasCommand(cmd: String): Boolean = if (context.availableCommands.isNotEmpty()) {
            cmd in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", cmd)).exitCode == 0
        }

        val hasCurl = hasCommand("curl")
        val hasGit = hasCommand("git")
        val hasRsync = hasCommand("rsync")
        val hasTar = hasCommand("tar")
        val hasFile = hasCommand("file")
        val hasXxd = hasCommand("xxd")
        val hasTruncate = hasCommand("truncate")

        val missingTransfer = buildList {
            if (!hasCurl) add("curl")
            if (!hasGit) add("git")
            if (!hasRsync) add("rsync")
            if (!hasTar) add("tar")
            if (!hasFile) add("file")
            if (!hasXxd) add("xxd")
            if (!hasTruncate) add("truncate")
        }
        val transferOk = missingTransfer.isEmpty()
        val missingApt = missingTransfer.filter { RequirementCatalog.isPackageApproved(it) }
        val transferCmd = if (transferOk || missingApt.isEmpty()) null else AptCommandBuilder.install(missingApt)

        return DiagnosticCheckItem(
            id = "transfer_tools",
            title = "Host Transfer & Sync Utilities",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (transferOk) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (transferOk) {
                "curl, git, rsync, tar, file, xxd, and truncate utilities verified for pipeline stages."
            } else {
                "Missing transfer utilities: ${missingTransfer.joinToString(", ")}."
            },
            remediation = if (transferOk) null else "Run '$transferCmd' in WSL.",
            copyableCommand = transferCmd,
        )
    }
}
