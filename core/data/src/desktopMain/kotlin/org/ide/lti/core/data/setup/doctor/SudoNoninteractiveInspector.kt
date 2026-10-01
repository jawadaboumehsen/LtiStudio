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
 * Inspector verifying whether non-interactive, passwordless sudo elevation is available.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Exclusively probes `sudo -n true`.
 * - Security & Non-Interactive Automation: Guarantees that automated package remediation
 *   or kernel operations will not block waiting for password input on a missing TTY.
 */
public class SudoNoninteractiveInspector(private val cli: WslCliExecutor = WslCliExecutor()) :
    SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val res = cli.execute(
            distro = distro,
            command = listOf("sudo", "-n", "true"),
            timeoutSeconds = 5,
        )

        val isSudoNoninteractiveOk = res.exitCode == 0

        return DiagnosticCheckItem(
            id = "sudo_noninteractive",
            title = "Elevation Authorization Mode",
            category = DiagnosticCategory.OS_UTILITIES,
            status = StepStatus.SUCCESS,
            detail = if (isSudoNoninteractiveOk) {
                "Headless non-interactive elevation active."
            } else {
                "Interactive terminal elevation required for privileged actions."
            },
            remediation = null,
            copyableCommand = null,
            isOptionalForRuntime = true,
        )
    }
}
