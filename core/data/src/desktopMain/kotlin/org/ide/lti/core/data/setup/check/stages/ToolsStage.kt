/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import io.ltirom.tooling.core.remote.ToolListResult
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.data.tools.KotlinStoreLayout
import org.ide.lti.core.data.tools.KotlinStoreLayout.toData
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.GroupStatus
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager

public class ToolsStage(private val toolManager: ToolManager? = null) : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.TOOLCHAIN_COMPILATION

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val tm = toolManager
        if (tm != null) {
            val statusRes = tm.refresh()
            if (statusRes.isFailure) {
                val ex = statusRes.exceptionOrNull()
                val toolError = (ex as? ToolErrorException)?.toolError
                val errorMsg = toolError?.message ?: ex?.message ?: "Component tool storage is unavailable."
                if (errorMsg.contains("settings.json can't be read")) {
                    return StageOutcome.Failed("~/LtiRomTools/settings.json can't be read", error = errorMsg)
                }

                val health = ctx.transport?.checkHealth()
                if (ctx.transport != null && health == null) {
                    scope.setStepOutcome(
                        SetupStepStage.SERVER_CONNECTIVITY,
                        StageOutcome.Failed(
                            "Connection could not be verified",
                            error = "Connection could not be verified",
                        ),
                    )
                    return StageOutcome.Pending("Pending daemon connectivity")
                }

                if (toolError is ToolError.AdmissionRequired || errorMsg.contains("AdmissionRequired") ||
                    errorMsg.contains("heartbeat")
                ) {
                    return StageOutcome.Pending("Awaiting command admission handshake", error = "admission closed")
                }
                return StageOutcome.Failed(errorMsg, error = errorMsg)
            }

            val inventory = statusRes.getOrThrow()
            updateToolsMatrix(ctx, scope, inventory)

            val unready = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for (group in ToolGroupCatalog.allGroups) {
                val state = inventory.groups[group.id]
                val (targetId, targetSource) = resolveTarget(group, state)
                val current = state?.current

                val matchesSource = current?.source?.toData() == targetSource.toData()
                when {
                    state == null || current == null -> {
                        unready += "${group.id.value} (NotInstalled)"
                    }
                    !current.intact -> {
                        failed += "${group.id.value} (NeedsRepair)"
                    }
                    state.status == GroupStatus.Failed -> {
                        failed += "${group.id.value} (Failed)"
                    }
                    current.id != targetId || !matchesSource -> {
                        unready += "${group.id.value} (TargetMismatch)"
                    }
                    else -> {
                        // Intact and current matches target
                    }
                }
            }

            val readyCount = ToolGroupCatalog.allGroups.size - failed.size - unready.size
            if (unready.isEmpty() && failed.isEmpty() && ToolGroupCatalog.allGroups.isNotEmpty()) {
                return StageOutcome.Success("${ToolGroupCatalog.allGroups.size} tool groups ready")
            }

            if (failed.isNotEmpty()) {
                val allIssues = failed + unready
                return StageOutcome.Failed(
                    "$readyCount ready · ${failed.size} failed · ${unready.size} not attempted",
                    error = allIssues.joinToString(),
                )
            }

            return StageOutcome.Pending(
                "$readyCount ready · 0 failed · ${unready.size} not attempted: ${unready.joinToString()}",
            )
        }

        val home = ctx.environment.home.ifBlank { ctx.userHome }
        val binDir = "$home/LtiRomTools/bin"
        val publishedTools =
            when (val res = ctx.transport?.listTools()) {
                is ToolListResult.Tools -> res.list.filter { it.installed }.map { it.tool }.toSet()
                else -> emptySet()
            }

        val distro = ctx.environment.distro
        val missing = mutableListOf<String>()
        val matrix =
            ToolchainSetupState.defaultToolsMatrix().map { tool ->
                val group = ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == tool.id } }
                val output = group?.outputs?.firstOrNull { it.toolId == tool.id }
                val isPublished = tool.id in publishedTools
                val legacyPath = "$binDir/${tool.id}"
                val currentPath = if (group != null &&
                    output != null
                ) {
                    "$binDir/${group.id.value}/current/${output.file}"
                } else {
                    legacyPath
                }
                val checkX = ctx.readOnlyCommands.execute(distro, listOf("test", "-x", legacyPath))
                val checkF = ctx.readOnlyCommands.execute(distro, listOf("test", "-f", legacyPath))
                val checkCurrX = ctx.readOnlyCommands.execute(distro, listOf("test", "-x", currentPath))
                val fileExists = checkX.exitCode == 0 || checkF.exitCode == 0 || checkCurrX.exitCode == 0
                val ready = isPublished || fileExists
                if (!ready) {
                    missing.add(tool.id)
                }
                tool.copy(
                    path = if (ready) (if (fileExists) legacyPath else currentPath) else null,
                    status = if (ready) StepStatus.SUCCESS else StepStatus.PENDING,
                )
            }
        scope.setToolsMatrix(matrix)

        // Check for outdated pinned release downloads (e.g. SetupScenarioTest)
        val releaseGroups = ToolGroupCatalog.allGroups.filter { it.recipe is RecipeConfig.Release }
        val outdated = mutableListOf<String>()
        val outdatedErrors = mutableListOf<String>()
        @Suppress("LoopWithTooManyJumpStatements")
        for (group in releaseGroups) {
            val rel = group.recipe as RecipeConfig.Release
            val pinnedVer = rel.versions.firstOrNull() ?: continue
            val output = group.outputs.firstOrNull() ?: continue
            val toolId = output.toolId
            val filePath = "$binDir/$toolId"
            val checkF = ctx.readOnlyCommands.execute(distro, listOf("test", "-f", filePath))
            val checkX = ctx.readOnlyCommands.execute(distro, listOf("test", "-x", filePath))
            if (checkF.exitCode == 0 || checkX.exitCode == 0) {
                val shaRes = ctx.readOnlyCommands.execute(distro, listOf("sha256sum", filePath))
                val stdout = shaRes.output.trim()
                val actualSha = stdout.substringBefore(' ').trim()
                if (actualSha.isNotBlank() && actualSha != pinnedVer.sha256) {
                    outdated.add(toolId)
                    outdatedErrors.add("$toolId (expected ${pinnedVer.version})")
                }
            }
        }

        if (outdated.isNotEmpty()) {
            val updatedMatrix =
                matrix.map {
                    if (it.id in outdated) it.copy(status = StepStatus.FAILED) else it
                }
            scope.setToolsMatrix(updatedMatrix)
            return StageOutcome.Failed(
                description = "${outdated.first()} outdated",
                error = "Not the pinned download: " + outdatedErrors.joinToString() +
                    ". Retry installs the pinned versions.",
            )
        }

        val requiredMissing =
            ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS.filter { req ->
                matrix.firstOrNull { it.id == req }?.status != StepStatus.SUCCESS
            }

        return if (requiredMissing.isEmpty()) {
            StageOutcome.Success("All tools verified in $binDir.")
        } else {
            StageOutcome.Pending(
                "${matrix.count {
                    it.status == StepStatus.SUCCESS
                }}/${matrix.size} tools present; missing: ${requiredMissing.joinToString()}",
            )
        }
    }

    private fun updateToolsMatrix(ctx: CheckContext, scope: CheckScope, inventory: ToolInventory) {
        val home = ctx.environment.home.ifBlank { ctx.userHome }
        val matrix =
            ToolchainSetupState.defaultToolsMatrix().map { tool ->
                val group = ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == tool.id } }
                val output = group?.outputs?.firstOrNull { it.toolId == tool.id }
                val current = group?.id?.let { inventory.groups[it]?.current }
                val exists = output != null && current?.outputs.orEmpty().any { it.file == output.file }
                val status =
                    when {
                        !exists -> StepStatus.PENDING
                        current?.intact != true -> StepStatus.FAILED
                        else -> StepStatus.SUCCESS
                    }
                tool.copy(
                    path =
                    if (exists && group != null && output != null) {
                        "$home/LtiRomTools/bin/${group.id.value}/current/${output.file}"
                    } else {
                        null
                    },
                    status = status,
                )
            }
        scope.setToolsMatrix(matrix)
    }

    private fun resolveTarget(group: ToolGroup, state: GroupState?): Pair<String, ResolvedSource> {
        val pick = state?.selection
        if (pick != null) {
            return pick.id to pick.source
        }
        return when (val s = group.source) {
            is ToolSource.Git -> {
                val pinned =
                    ResolvedSource.Git(
                        repoUrl = s.recommendedUrl,
                        ref = s.pinnedRef,
                        commit = s.pinnedCommit,
                    )
                KotlinStoreLayout.computeVersionId(pinned) to pinned
            }
            ToolSource.Release -> {
                val rel = group.recipe as? RecipeConfig.Release
                val firstVer = rel?.versions?.firstOrNull()
                val pinned =
                    ResolvedSource.Release(
                        version = firstVer?.version.orEmpty(),
                        url = firstVer?.url.orEmpty(),
                        sha256 = firstVer?.sha256.orEmpty(),
                        member = firstVer?.archiveMember.orEmpty(),
                    )
                KotlinStoreLayout.computeVersionId(pinned) to pinned
            }
        }
    }
}
