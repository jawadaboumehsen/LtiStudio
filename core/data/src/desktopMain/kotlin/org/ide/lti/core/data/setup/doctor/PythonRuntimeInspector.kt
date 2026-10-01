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
 * Inspector verifying Python 3 runtime and cryptography/pyasn1 libraries.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to Python cryptographic tooling.
 * - Security: Uses strictly native apt packages (python3-cryptography, python3-pyasn1); never pip3.
 */
public class PythonRuntimeInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasPython3 = if (context.availableCommands.isNotEmpty()) {
            "python3" in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", "python3")).exitCode == 0
        }
        val pyVersion = if (hasPython3) {
            val vOut = cli.execute(distro, listOf("python3", "--version")).output.trim()
            if (vOut.isNotBlank()) vOut else "Python 3"
        } else {
            ""
        }

        val pyCryptoOk = hasPython3 && cli.execute(distro, listOf("python3", "-c", "import cryptography, pyasn1")).exitCode == 0

        val pyStatus: StepStatus
        val pyDetail: String
        val pyRemediation: String?
        val pyCopyCmd: String?

        when {
            !hasPython3 -> {
                pyStatus = StepStatus.FAILED
                pyDetail = "Python 3 interpreter not found in WSL (required for avbtool, mkbootimg, and img2sdat)."
                val cmd = AptCommandBuilder.install(listOf("python3", "python3-cryptography", "python3-pyasn1"))
                pyRemediation = "Run '$cmd' in WSL."
                pyCopyCmd = cmd
            }
            !pyCryptoOk -> {
                pyStatus = StepStatus.FAILED
                pyDetail = "$pyVersion verified, but cryptography/pyasn1 packages are missing."
                val cmd = AptCommandBuilder.install(listOf("python3-cryptography", "python3-pyasn1"))
                pyRemediation = "Run '$cmd' in WSL."
                pyCopyCmd = cmd
            }
            else -> {
                pyStatus = StepStatus.SUCCESS
                pyDetail = "$pyVersion and cryptography/pyasn1 verified for AVB 2.0 signing and boot image tools."
                pyRemediation = null
                pyCopyCmd = null
            }
        }

        return DiagnosticCheckItem(
            id = "python_crypto",
            title = "Python 3 Runtime & Cryptography",
            category = DiagnosticCategory.RUNTIMES,
            status = pyStatus,
            detail = pyDetail,
            remediation = pyRemediation,
            copyableCommand = pyCopyCmd,
        )
    }
}
