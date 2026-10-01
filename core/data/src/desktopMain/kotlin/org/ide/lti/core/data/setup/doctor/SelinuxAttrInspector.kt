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
 * Inspector verifying SELinux extended attribute utilities (attr, getfattr).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating SELinux tooling.
 */
public class SelinuxAttrInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasAttr = if (context.availableCommands.isNotEmpty()) {
            "attr" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "attr")).exitCode == 0
        }
        val hasGetfattr = if (context.availableCommands.isNotEmpty()) {
            "getfattr" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "getfattr")).exitCode == 0
        }
        val attrOk = hasAttr && hasGetfattr
        val attrCmd = if (attrOk) null else AptCommandBuilder.install(listOf("attr"))

        return DiagnosticCheckItem(
            id = "selinux_attr",
            title = "SELinux Extended Attributes",
            category = DiagnosticCategory.OS_UTILITIES,
            status = if (attrOk) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (attrOk) {
                "attr and getfattr utilities available for security.selinux context extraction."
            } else {
                "Missing: attr/getfattr (required by FirmwareImagePort to preserve SELinux file contexts)."
            },
            remediation = if (attrOk) null else "Run '$attrCmd' in WSL.",
            copyableCommand = attrCmd,
        )
    }
}
