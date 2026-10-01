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

import org.ide.lti.core.data.setup.SubmoduleSyncEngine
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus

public class SyncSourcesHandler : PlanActionHandler<SetupPlanAction.SyncSources> {
    override suspend fun execute(action: SetupPlanAction.SyncSources, context: ExecutionContext): SetupOutcome? {
        val distro = context.distro
        val workDir = context.workDir
        val extDir = context.extDir
        val serviceCli = context.serviceCli ?: return SetupOutcome.Failed(
            stage = SetupStepStage.REPO_SYNCHRONIZATION.name,
            reason = "No execution service CLI available.",
        )

        context.setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.RUNNING, null, null, null, null)
        context.appendLog("[Step 4/5] Ensuring workspace root directory exists at $workDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", workDir))
        context.appendLog("[Step 4/5] Ensuring tools directory exists at $extDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", extDir))

        val syncEngine = SubmoduleSyncEngine(
            cli = serviceCli,
            repository = context.repository,
            transport = context.transport,
        )
        val syncRes = syncEngine.syncAll(
            distro,
            extDir,
            journal = context.journal,
            onRunStarted = context.recordRunStarted,
            onProgress = { name, idx, total ->
                context.setStepProgress(SetupStepStage.REPO_SYNCHRONIZATION, name, idx, total)
            },
        ) { context.appendLog(it) }

        return if (syncRes.isFailure) {
            val err = syncRes.exceptionOrNull()?.message ?: "Failed to synchronize submodules"
            context.appendLog("[Step 4/5] ERROR: $err")
            context.setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.FAILED, null, err, null, null)
            SetupOutcome.Failed(SetupStepStage.REPO_SYNCHRONIZATION.name, err)
        } else {
            context.setStepStatus(
                SetupStepStage.REPO_SYNCHRONIZATION,
                StepStatus.SUCCESS,
                "All ${action.submodules.size} submodules and $workDir ready.",
                null,
                null,
                null,
            )
            null
        }
    }
}
