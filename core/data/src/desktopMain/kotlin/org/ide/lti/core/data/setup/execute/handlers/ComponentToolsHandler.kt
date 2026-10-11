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

import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolManager

/** Executes setup and retry plans through the same component store used by the Tools editor. */
public class ComponentToolsHandler(private val manager: ToolManager) :
    PlanActionHandler<SetupPlanAction.SwitchToolchain> {
    override suspend fun execute(action: SetupPlanAction.SwitchToolchain, context: ExecutionContext): SetupOutcome? =
        if (context.plan.kind == SetupPlanKind.FULL_SETUP && action.forceRebuildGroups.isEmpty()) {
            executePrepareMissing(action, context)
        } else {
            executeGroups(action, context)
        }

    @Suppress("ReturnCount")
    private suspend fun executePrepareMissing(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
    ): SetupOutcome? {
        try {
            val summary =
                manager.prepareMissing(
                    targets = action.inputs.mapValues { (_, input) ->
                        input.toResolvedSource()
                    },
                ) { _, event ->
                    context.reportEvent(event)
                }
            if (summary.isComplete) {
                return null
            }
            if (summary.groupResults.values.any { it is GroupSetupResult.Cancelled }) {
                return SetupOutcome.Cancelled
            }
            val failures =
                summary.groupResults.entries
                    .filter { it.value is GroupSetupResult.Failed }
                    .map { (group, res) -> "${group.value}: ${(res as GroupSetupResult.Failed).error.message}" }

            val notAttempted =
                summary.groupResults.entries
                    .filter { it.value is GroupSetupResult.NotAttempted }
                    .map { (group, res) -> "${group.value}: ${(res as GroupSetupResult.NotAttempted).blocker.message}" }

            val allIssues = failures + notAttempted
            val summaryText =
                "${summary.readyCount} ready · ${summary.failedCount} failed · " +
                    "${summary.notAttemptedCount} not attempted"
            return SetupOutcome.Failed(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                reason = if (allIssues.isEmpty()) summaryText else "$summaryText: ${allIssues.joinToString("; ")}",
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            return SetupOutcome.Cancelled
        } catch (e: Exception) {
            return SetupOutcome.Failed(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                reason = e.message ?: "Failed to execute prepareMissing",
            )
        }
    }

    @Suppress("ReturnCount")
    private suspend fun executeGroups(
        action: SetupPlanAction.SwitchToolchain,
        context: ExecutionContext,
    ): SetupOutcome? {
        val failures = mutableListOf<String>()
        try {
            for ((group, input) in action.inputs) {
                if (context.plan.kind == SetupPlanKind.REPAIR_TOOL && group !in action.forceRebuildGroups) continue
                val result =
                    if (group in action.forceRebuildGroups) {
                        manager.repair(group, source = input.toResolvedSource()) { context.reportEvent(it) }
                    } else {
                        manager.installAndUse(group, input.toResolvedSource()) { context.reportEvent(it) }
                    }
                val outcome = result.toSetupOutcome()
                if (outcome is SetupOutcome.Cancelled) return SetupOutcome.Cancelled
                if (outcome is SetupOutcome.Interrupted) return outcome
                if (outcome is SetupOutcome.Failed) failures.add("${group.value}: ${outcome.reason}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            return SetupOutcome.Cancelled
        }
        return if (failures.isEmpty()) {
            null
        } else {
            SetupOutcome.Failed(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                reason = failures.joinToString("; "),
            )
        }
    }

    private fun ExecutionContext.reportEvent(event: InstallEvent) {
        when (event) {
            is InstallEvent.Started -> recordRunStarted(SetupStepStage.TOOLCHAIN_COMPILATION, event.runId)
            is InstallEvent.Output -> appendLog(event.text)
            is InstallEvent.Finished -> appendLog("[Tools] ${event.result}")
        }
    }

    private fun OperationResult.toSetupOutcome(): SetupOutcome? = when (this) {
        is OperationResult.Installed, is OperationResult.Reused, is OperationResult.Used -> null
        OperationResult.Cancelled -> SetupOutcome.Cancelled
        is OperationResult.Interrupted -> SetupOutcome.Interrupted(reason)
        is OperationResult.Failed -> SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, message)
        is OperationResult.TrustRequired ->
            SetupOutcome.Failed(
                SetupStepStage.TOOLCHAIN_COMPILATION.name,
                "Repository trust is required for $url",
            )
    }
}

/** What a confirmed plan froze, as the source the store builds: nothing of the identity may get lost on the way. */
internal fun ResolvedInput.toResolvedSource(): ResolvedSource = when (this) {
    is ResolvedInput.Git -> {
        val recommended = ToolGroupCatalog.group(group)?.source as? ToolSource.Git
        ResolvedSource.Git(
            repoUrl = repoUrl ?: requireNotNull(recommended).recommendedUrl,
            ref = ref,
            commit = commit,
            submodules = submoduleCommits,
        )
    }
    is ResolvedInput.Release -> ResolvedSource.Release(version, url, sha256, archiveMember)
}
