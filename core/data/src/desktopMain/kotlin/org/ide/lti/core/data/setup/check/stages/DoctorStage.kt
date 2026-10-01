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

import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus

public class DoctorStage : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.SYSTEM_DIAGNOSTICS

    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val distro = ctx.environment.distro
        val checks = mutableListOf<DiagnosticCheckItem>()

        // 1. Storage check via df -P
        val dfRes = ctx.readOnlyCommands.run(distro, listOf("df", "-P", ctx.userHome))
        val availBytes = if (dfRes.exitCode == 0) {
            val line = dfRes.output.lines().drop(1).firstOrNull { it.isNotBlank() }
            val availKb = line?.split(Regex("\\s+"))?.getOrNull(3)?.toLongOrNull()
            availKb?.times(1024L)
        } else {
            null
        }
        scope.setStorage(availBytes, System.currentTimeMillis())

        val storageStatus = if (availBytes != null && availBytes > 10_000_000_000L) {
            StepStatus.SUCCESS
        } else {
            StepStatus.WARNING
        }
        val storageDetail = availBytes?.let { "${it / (1024 * 1024 * 1024)} GB available" }
            ?: "Could not determine free space"

        checks += DiagnosticCheckItem(
            id = "storage",
            title = "Disk Space",
            category = DiagnosticCategory.STORAGE,
            status = storageStatus,
            detail = storageDetail,
        )

        scope.setDiagnostics(checks)

        val anyFailed = checks.any { it.status == StepStatus.FAILED }
        val anyWarn = checks.any { it.status == StepStatus.WARNING }

        return when {
            anyFailed -> StageOutcome.Failed("${checks.count { it.status == StepStatus.FAILED }} checks failed.")
            anyWarn -> StageOutcome.Warning("${checks.count { it.status == StepStatus.WARNING }} warnings detected.")
            else -> StageOutcome.Success("${checks.size}/${checks.size} checks verified.")
        }
    }
}
