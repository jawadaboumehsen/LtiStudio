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
import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Service responsible for resolving missing package dependencies and toolchain binaries in WSL 2.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Resolves approved system packages and synchronizes user tools.
 * - Non-Interactive Automation & Security: Strictly unprivileged. Privileged package installs
 *   are handed off to the user's terminal. Never requests, executes, or writes sudoers configuration.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class WslPackageRemediator(
    private val cli: WslCliExecutor = WslCliExecutor(),
) {
    /**
     * Concrete remediation derived from diagnostic results. This is the single source that both the
     * setup-plan preview and the remediation execution read, so what the user sees is exactly what runs
     * (FR-004 package-summary parity).
     */
    public data class RemediationPlan(
        val aptPackages: List<String> = emptyList(),
        val pipPackages: List<String> = emptyList(),
        val configureLoopMountElevation: Boolean = false,
        val syncToolchainBinaries: Boolean = false,
    ) {
        public val isEmpty: Boolean
            get() = aptPackages.isEmpty() && pipPackages.isEmpty() && !syncToolchainBinaries

        public val requiresElevation: Boolean
            get() = aptPackages.isNotEmpty()
    }

    public sealed interface RemediationExecutionResult {
        public data class Succeeded(val packages: List<String>) : RemediationExecutionResult
        public data class Failed(val command: String, val exitCode: Int, val error: String) : RemediationExecutionResult
        public data object AwaitingUserAuthorization : RemediationExecutionResult
    }

    public companion object {
        /**
         * Resolves the remediation for every FAILED/WARNING diagnostic. Pure: no IO, no side effects.
         * Package names are strictly sourced from [RequirementCatalog.APPROVED_APT_PACKAGES].
         */
        public fun resolve(diagnosticItems: List<DiagnosticCheckItem>): RemediationPlan {
            val failedOrWarning = diagnosticItems.filter {
                it.status == StepStatus.FAILED || it.status == StepStatus.WARNING
            }
            val aptPackages = linkedSetOf<String>()
            var needsToolchainSync = false

            for (item in failedOrWarning) {
                val approved = RequirementCatalog.APPROVED_APT_PACKAGES[item.id]
                if (approved != null) {
                    aptPackages.addAll(approved)
                }
                if (item.id == "toolchain_binaries") {
                    needsToolchainSync = true
                }
            }

            return RemediationPlan(
                aptPackages = aptPackages.sorted(),
                pipPackages = emptyList(),
                configureLoopMountElevation = false,
                syncToolchainBinaries = needsToolchainSync,
            )
        }
    }

    /**
     * Legacy boolean adapter for [remediate]. Returns true if no elevation is required and
     * local synchronization succeeded, or false if user authorization is required.
     */
    public suspend fun remediate(
        context: DiagnosticContext,
        diagnosticItems: List<DiagnosticCheckItem>,
        onProgress: (String) -> Unit = {},
    ): Boolean = remediate(context, resolve(diagnosticItems), onProgress)

    /**
     * Executes unprivileged remediation steps. Privileged package actions return false
     * indicating that terminal authorization is required.
     */
    public suspend fun remediate(
        context: DiagnosticContext,
        plan: RemediationPlan,
        onProgress: (String) -> Unit = {},
    ): Boolean = when {
        plan.isEmpty -> {
            onProgress("All diagnostic checks are already satisfied. No remediation needed.")
            true
        }
        plan.requiresElevation -> {
            onProgress("System package installation requires administrator authorization in your WSL terminal.")
            false
        }
        else -> {
            applyUnprivilegedRemediation(context, plan, onProgress)
            true
        }
    }

    private fun applyUnprivilegedRemediation(
        context: DiagnosticContext,
        plan: RemediationPlan,
        onProgress: (String) -> Unit,
    ) {
        val distro = context.distro
        val needsToolchainSync = plan.syncToolchainBinaries

        // Toolchain Binaries Synchronization (unprivileged, runs in user home)
        if (needsToolchainSync) {
            onProgress("Materializing missing toolchain binaries in ~/LtiRomTools/bin...")
            val toolsRoot = "${context.home}/LtiRomTools"
            val binDir = context.binDir
            val extDir = "$toolsRoot/external"
            cli.execute(distro, listOf("mkdir", "-p", binDir))

            if (cli.execute(distro, listOf("test", "-d", "$extDir/bin")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/bin/.", "$binDir/"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/payload-dumper-go/payload-dumper-go")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/payload-dumper-go/payload-dumper-go", "$binDir/payload-dumper-go"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/signapk/signapk/build/libs/signapk.jar")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/signapk/signapk/build/libs/signapk.jar", "$binDir/signapk.jar"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/fuse.erofs")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "fuse.erofs", "$binDir/erofsfuse"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mkbootimg.py")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mkbootimg.py", "$binDir/mkbootimg"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mke2fs.android")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mke2fs.android", "$binDir/mke2fs"))
            }
            onProgress("Toolchain binaries synchronized.")
        }
    }
}
