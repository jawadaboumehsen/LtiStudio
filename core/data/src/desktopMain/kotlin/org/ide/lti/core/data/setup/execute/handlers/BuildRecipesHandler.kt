/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute.handlers

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.ToolchainBuildEngine
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities

public class BuildRecipesHandler : PlanActionHandler<SetupPlanAction.BuildRecipes> {
    override suspend fun execute(action: SetupPlanAction.BuildRecipes, context: ExecutionContext): SetupOutcome? {
        val serviceCli = context.serviceCli ?: return SetupOutcome.Failed(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
            reason = "No execution service CLI available.",
        )
        return runBuildRecipes(action, context, serviceCli)
    }

    private suspend fun runBuildRecipes(
        action: SetupPlanAction.BuildRecipes,
        context: ExecutionContext,
        serviceCli: WslCliExecutor,
    ): SetupOutcome? {
        val distro = context.distro
        val extDir = context.extDir
        val binDir = context.binDir
        val currentPlan = context.plan

        context.setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.RUNNING, null, null, null, null)
        context.appendLog("[Step 5/5] Preparing binary directory at $binDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", binDir))

        val buildEngine = ToolchainBuildEngine(
            cli = serviceCli,
            repository = context.repository,
            transport = context.transport,
        )
        val isRepair = currentPlan.kind == SetupPlanKind.REPAIR_TOOL
        val targetTool = currentPlan.targetStageOrToolId

        val buildRes = buildEngine.buildAndDistributeAll(
            distro = distro,
            extDir = extDir,
            binDir = binDir,
            journal = context.journal,
            targetRecipeGroups = if (isRepair) action.recipeGroups.toSet() else null,
            forceRebuild = isRepair,
            onRunStarted = context.recordRunStarted,
            onProgress = { toolOrGroup, idx, total ->
                context.setStepProgress(SetupStepStage.TOOLCHAIN_COMPILATION, toolOrGroup, idx, total)
            },
        ) { context.appendLog(it) }

        if (buildRes.isFailure) {
            val err = buildRes.exceptionOrNull()?.message
                ?: "Some core tool binaries are missing in $binDir after build."
            context.appendLog("[Step 5/5] ERROR: $err")
            context.setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.FAILED, null, err, null, null)
            return SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, err)
        }

        return finalizeBuildOutcome(isRepair, targetTool, context, serviceCli, binDir)
    }

    private suspend fun finalizeBuildOutcome(
        isRepair: Boolean,
        targetTool: String?,
        context: ExecutionContext,
        serviceCli: WslCliExecutor,
        binDir: String,
    ): SetupOutcome? {
        if (isRepair && targetTool != null) {
            val outcome = verifyRepairSiblings(targetTool, context, serviceCli)
            if (outcome != null) return outcome
        }

        context.setStepStatus(
            SetupStepStage.TOOLCHAIN_COMPILATION,
            StepStatus.SUCCESS,
            "Core tools ready in $binDir.",
            null,
            null,
            null,
        )
        return null
    }

    private suspend fun verifyRepairSiblings(
        targetTool: String,
        context: ExecutionContext,
        serviceCli: WslCliExecutor,
    ): SetupOutcome? {
        val group = ToolCapabilities.recipeGroupFor(targetTool)
        if (group == null) {
            return null
        }
        val siblingTools = ToolCapabilities.toolsForRecipeGroup(group)
        val verifier = ToolVerifier(serviceCli, context.repository)
        return verifySiblingList(targetTool, siblingTools, context, verifier)
    }

    private suspend fun verifySiblingList(
        targetTool: String,
        siblingTools: Set<String>,
        context: ExecutionContext,
        verifier: ToolVerifier,
    ): SetupOutcome? {
        val requiredTools = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS
        val distro = context.distro
        val binDir = context.binDir

        for (siblingId in siblingTools) {
            val binaryName = ToolchainBuildEngine.installedName(siblingId)
            val verifyResult = verifier.verify(distro, siblingId, binDir, binaryName)
            updateSiblingToolState(context, siblingId, verifyResult)

            if (!verifyResult.isSuccess && (siblingId in requiredTools || siblingId == targetTool)) {
                val reason = (verifyResult as? ToolVerifier.Result.Failure)?.reason
                    ?: "Tool $siblingId verification failed"
                context.appendLog("[Step 5/5] ERROR: $reason")
                context.setStepStatus(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    StepStatus.FAILED,
                    reason,
                    null,
                    null,
                    null,
                )
                return SetupOutcome.Failed(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                    reason = reason,
                )
            }
        }
        return null
    }

    private fun updateSiblingToolState(
        context: ExecutionContext,
        siblingId: String,
        verifyResult: ToolVerifier.Result,
    ) {
        val isOk = verifyResult.isSuccess
        val binaryPath = if (verifyResult is ToolVerifier.Result.Success) {
            verifyResult.binaryPath
        } else {
            (verifyResult as? ToolVerifier.Result.Failure)?.binaryPath
        }
        val isInstalled = verifyResult is ToolVerifier.Result.Success ||
            (verifyResult as? ToolVerifier.Result.Failure)?.isInstalled == true

        context.installedScope?.updateTool(siblingId) { item ->
            item.copy(
                status = if (isOk) StepStatus.SUCCESS else StepStatus.FAILED,
                path = if (isInstalled) binaryPath else null,
                lastTested = if (isOk) "Just now" else null,
            )
        }
    }
}
