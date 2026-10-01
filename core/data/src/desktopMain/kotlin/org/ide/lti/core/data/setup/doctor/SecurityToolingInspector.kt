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
 * Inspector verifying OpenSSL CLI availability for AVB 2.0 RSA-4096 machine key generation.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to OpenSSL cryptographic CLI checks.
 */
public class SecurityToolingInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasOpenssl = if (context.availableCommands.isNotEmpty()) {
            "openssl" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "openssl")).exitCode == 0
        }
        val opensslCmd = if (hasOpenssl) null else AptCommandBuilder.install(listOf("openssl"))

        return DiagnosticCheckItem(
            id = "openssl_tools",
            title = "OpenSSL Cryptography Tooling",
            category = DiagnosticCategory.SECURITY,
            status = if (hasOpenssl) StepStatus.SUCCESS else StepStatus.FAILED,
            detail = if (hasOpenssl) {
                "OpenSSL cryptography CLI available for AVB 2.0 RSA-4096 signing."
            } else {
                "OpenSSL not found in WSL."
            },
            remediation = if (hasOpenssl) null else "Run '$opensslCmd' in WSL.",
            copyableCommand = opensslCmd,
        )
    }
}
