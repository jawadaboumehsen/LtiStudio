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

import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.DistroStatus

/**
 * WSL and Ubuntu environment check stage (T038).
 * Inspects installed WSL distributions, identifies default distro,
 * and ensures an approved Ubuntu LTS version is active.
 */
public class DistroStage(private val detector: WslEnvironmentDetector = WslEnvironmentDetector()) : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.WSL_DETECTION

    @Suppress("CyclomaticComplexMethod")
    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        // A verified WSL keeps its expensive facts (OS, user) but the cheap ones are read again: the default
        // distribution or WSL version may have been changed outside the app since.
        val pending = ctx.repository?.currentState?.activeAttempt
        val followsDefault = pending == null || (pending.environmentKey ?: pending.environment?.distro) == null
        if (ctx.alreadyVerifiedWsl && detector.stillCurrent(ctx.environment, requireDefault = followsDefault)) {
            scope.setDistroInfo(
                activeDistro = ctx.environment.distro,
                installedDistros = listOf(ctx.environment.distro),
                distroStatuses = listOf(DistroStatus.Usable(ctx.environment)),
            )
            return StageOutcome.Success("${ctx.environment.distro} · user ${ctx.environment.user}")
        }

        val statuses = detector.detectAll()
        val installed =
            statuses.mapNotNull {
                when (it) {
                    is DistroStatus.Usable -> it.environment.distro
                    is DistroStatus.Unsupported -> it.distro
                    is DistroStatus.StartupFailed -> it.distro
                    is DistroStatus.NoUsableUser -> it.distro
                    else -> null
                }
            }

        // Primary / default distro status (prefer pending attempt's target distro)
        val wanted =
            ctx.repository?.currentState?.activeAttempt?.let {
                it.environmentKey ?: it.environment?.distro
            }
        val primary =
            if (wanted != null) {
                statuses.filterIsInstance<DistroStatus.Usable>().firstOrNull { it.environment.distro == wanted }
                    ?: statuses.firstOrNull {
                        when (it) {
                            is DistroStatus.Unsupported -> it.distro == wanted
                            is DistroStatus.StartupFailed -> it.distro == wanted
                            is DistroStatus.NoUsableUser -> it.distro == wanted
                            else -> false
                        }
                    }
                    ?: statuses.firstOrNull()
                    ?: DistroStatus.NoDistro
            } else {
                statuses.firstOrNull() ?: DistroStatus.NoDistro
            }

        val activeDistroName =
            when (primary) {
                is DistroStatus.Usable -> primary.environment.distro
                is DistroStatus.Unsupported -> primary.distro
                is DistroStatus.StartupFailed -> primary.distro
                is DistroStatus.NoUsableUser -> primary.distro
                else -> null
            }

        scope.setDistroInfo(
            activeDistro = activeDistroName,
            installedDistros = installed,
            distroStatuses = statuses,
        )

        return when (primary) {
            is DistroStatus.Usable -> {
                StageOutcome.Success("${primary.environment.distro} · user ${primary.environment.user}")
            }
            is DistroStatus.Unsupported -> {
                StageOutcome.Failed("Unsupported distribution '${primary.distro}': ${primary.reason}")
            }
            is DistroStatus.StartupFailed -> {
                StageOutcome.Failed("Distribution '${primary.distro}' failed to start: ${primary.detail}")
            }
            is DistroStatus.NoUsableUser -> {
                StageOutcome.Failed("Distribution '${primary.distro}' has no usable non-root user: ${primary.reason}")
            }
            is DistroStatus.NoDistro -> {
                StageOutcome.Failed(
                    "No Linux distribution",
                    error = "No WSL distributions found. Please install Ubuntu LTS.",
                )
            }
            is DistroStatus.WslUnavailable -> {
                StageOutcome.Failed("WSL isn't installed", error = "WSL 2 is not installed: ${primary.detail}")
            }
        }
    }
}
