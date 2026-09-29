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
 * Inspector verifying host C/C++ compilers and build engine (cmake, make, clang/gcc, clang++/g++).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating build toolchains.
 */
public class CompilersInspector(private val cli: WslCliExecutor = WslCliExecutor()) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        fun hasCommand(cmd: String): Boolean = if (context.availableCommands.isNotEmpty()) {
            cmd in context.availableCommands
        } else {
            cli.execute(distro, listOf("which", cmd)).exitCode == 0
        }

        // Each entry is satisfied by any of its alternatives; the first one names what is missing.
        val missingCompilers = listOf(listOf("cmake"), listOf("make"), listOf("clang", "gcc"), listOf("clang++", "g++"))
            .filter { alternatives -> alternatives.none(::hasCommand) }
            .map { it.first() }
        val compilersOk = missingCompilers.isEmpty()
        // clang++ ships with the clang / build-essential packages, so it is never an apt package of its own.
        val missingPackages = (listOf("build-essential") + missingCompilers - "clang++").distinct()
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
            isOptionalForRuntime = context.binariesPresent,
        )
    }
}
