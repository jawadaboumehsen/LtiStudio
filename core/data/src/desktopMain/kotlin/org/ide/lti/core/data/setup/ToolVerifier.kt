/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolEvidence

/**
 * Single, unified probe execution and verification engine for toolchain binaries (T030).
 * Eliminates duplicate probe paths between testTool and repair verification.
 */
public class ToolVerifier(public val cli: WslCliExecutor, private val repository: ToolchainSetupRepository? = null) {
    public sealed interface Result {
        public val toolId: String
        public val isSuccess: Boolean

        public data class Success(
            override val toolId: String,
            val binaryPath: String,
            val exitCode: Int,
            val evidence: ToolEvidence,
        ) : Result {
            override val isSuccess: Boolean = true
        }

        public data class Failure(
            override val toolId: String,
            val binaryPath: String,
            val reason: String,
            val exitCode: Int?,
            val isInstalled: Boolean,
        ) : Result {
            override val isSuccess: Boolean = false
        }
    }

    public suspend fun verify(distro: String, toolId: String, binDir: String, binaryName: String): Result {
        val probe = ToolCatalog.probePlan(toolId, binDir, binaryName)
        val binaryPath = probe.path
        val exists = cli.execute(distro, probe.existsCheck).exitCode == 0
        val acceptedExitCodes = ToolCapabilities.acceptedExitCodesFor(toolId)

        if (!exists) {
            repository?.updateTool(toolId) { it.copy(isVerified = false) }
            return Result.Failure(
                toolId = toolId,
                binaryPath = binaryPath,
                reason = "Tool $toolId binary not found or not executable at $binaryPath",
                exitCode = null,
                isInstalled = false,
            )
        }

        val execRes = cli.execute(distro, probe.argv, timeoutSeconds = probe.timeoutSeconds)
        val exitCode = execRes.exitCode
        val isOk = exitCode in acceptedExitCodes
        val now = System.currentTimeMillis()

        return if (isOk) {
            repository?.updateTool(toolId) { tool ->
                tool.copy(
                    isVerified = true,
                    isCompiled = true,
                    binaryPath = binaryPath,
                    lastVerifiedTimestamp = now,
                )
            }
            Result.Success(
                toolId = toolId,
                binaryPath = binaryPath,
                exitCode = exitCode,
                evidence = ToolEvidence(
                    toolId = toolId,
                    recipeGroup = ToolCapabilities.recipeGroupFor(toolId),
                    probeArgv = probe.argv,
                    acceptedExitCodes = acceptedExitCodes,
                    isInstalled = true,
                    isVerified = true,
                    isFailedExecution = false,
                    lastVerifiedTimestamp = now,
                ),
            )
        } else {
            repository?.updateTool(toolId) { it.copy(isVerified = false) }
            val detail = execRes.error.ifBlank { execRes.output }.takeLast(200).trim()
            val detailSuffix = if (detail.isNotBlank()) " $detail" else ""
            val reason = "Tool $toolId verification probe failed: `${probe.argv.joinToString(" ")}` " +
                "exited $exitCode (accepted: ${acceptedExitCodes.sorted().joinToString()})$detailSuffix"
            Result.Failure(
                toolId = toolId,
                binaryPath = binaryPath,
                reason = reason,
                exitCode = exitCode,
                isInstalled = true,
            )
        }
    }
}
