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
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus

public class PublishToolsHandler(private val toolchainLayoutV2: Boolean = false) :
    PlanActionHandler<SetupPlanAction.PublishTools> {
    override suspend fun execute(action: SetupPlanAction.PublishTools, context: ExecutionContext): SetupOutcome? {
        if (toolchainLayoutV2) {
            context.appendLog(
                "[Step 5/5] toolchainLayoutV2 active: tool registrations are managed " +
                    "by the server activation transaction.",
            )
            return null
        }
        return executeLegacyPublication(action, context)
    }

    private suspend fun executeLegacyPublication(
        action: SetupPlanAction.PublishTools,
        context: ExecutionContext,
    ): SetupOutcome? {
        val binDir = context.binDir
        val toolPublicationPort = context.toolPublicationPort

        context.setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.RUNNING, null, null, null, null)
        context.appendLog(
            "[Step 5/5] Registering ${action.toolIds.size} tool(s) with the build service and " +
                "removing stale registrations...",
        )
        val pubRes = toolPublicationPort?.publish(binDir, action.toolIds.toSet())
        pubRes?.pruned?.takeIf { it.isNotEmpty() }?.let {
            context.appendLog("[Step 5/5] Removed ${it.size} stale registration(s): ${it.sorted().joinToString()}")
        }
        val publishedIds = pubRes?.registered.orEmpty()
        val unpublishedIds = pubRes?.failed?.keys.orEmpty()
        context.installedScope?.setPublishedToolIds(publishedIds, unpublishedIds)

        if (pubRes != null && !pubRes.isComplete) {
            val failureReasons = pubRes.failed.entries.joinToString(", ") { "${it.key}: ${it.value}" }
            context.setStepStatus(
                SetupStepStage.TOOLCHAIN_COMPILATION,
                StepStatus.FAILED,
                "Tool publication incomplete: $failureReasons",
                "Failed to register tools: $failureReasons",
                null,
                ToolchainFailureCategory.MISSING_PUBLISHED_TOOLS,
            )
            context.appendLog("[Step 5/5] ERROR: Failed to publish tools: $failureReasons")
            return SetupOutcome.Failed(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                reason = "Tool publication incomplete: $failureReasons",
            )
        }
        context.appendLog("[Step 5/5] Published ${publishedIds.size} tools to daemon.")
        return null
    }
}
