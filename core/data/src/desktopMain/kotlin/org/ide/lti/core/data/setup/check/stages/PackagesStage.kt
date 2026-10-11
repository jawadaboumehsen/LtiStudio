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

import org.ide.lti.core.data.setup.RemotePrerequisiteProbe
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.PackagesPreparationResult
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.RequirementStatus

/**
 * System packages prerequisite check stage (T043).
 * Queries prerequisite packages through the build service via [RemotePrerequisiteProbe]
 * or [ServicePreparation].
 */
public class PackagesStage(
    private val servicePreparation: ServicePreparation? = null,
    private val probe: RemotePrerequisiteProbe? = null,
) : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.SYSTEM_PACKAGES

    @Suppress("ReturnCount")
    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val prep = servicePreparation
        if (prep != null) {
            val res = prep.verifySystemPackages(ctx.environment)
            ctx.onPrerequisiteReport?.invoke(res.prerequisiteReport)
            return when (res) {
                is PackagesPreparationResult.Success -> StageOutcome.Success("All packages present")
                is PackagesPreparationResult.Failure -> StageOutcome.Failed(res.description, error = res.error)
            }
        }

        val activeProbe =
            probe ?: ctx.transport?.let { RemotePrerequisiteProbe(it) }
                ?: return StageOutcome.Failed(
                    "Couldn't query system packages through build service.",
                    error = "Remote transport is unavailable.",
                )

        val report =
            try {
                activeProbe.probe(ctx.environment)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return StageOutcome.Failed(
                    "Couldn't query system packages through build service: ${e.message}",
                    error = e.message,
                )
            }
        ctx.onPrerequisiteReport?.invoke(report)

        val missingPkgs = report.missingPackages
        val checkFailure =
            report.results.values
                .filterIsInstance<RequirementStatus.CheckFailed>()
                .firstOrNull()
        val unavailable = report.results.filterValues { it is RequirementStatus.Unavailable }.keys

        return when {
            checkFailure != null ->
                StageOutcome.Failed("Couldn't check packages: ${checkFailure.reason}", error = checkFailure.reason)
            unavailable.isNotEmpty() ->
                StageOutcome.Failed(
                    "${unavailable.joinToString(", ")} isn't available on Ubuntu ${ctx.environment.osVersionId}",
                    error = "Enable the Ubuntu 'universe' repository or use a supported Ubuntu LTS release.",
                )
            missingPkgs.isNotEmpty() -> {
                val suffix = if (missingPkgs.size > 3) "..." else ""
                val summary =
                    "${missingPkgs.size} package(s) missing: " +
                        "${missingPkgs.take(3).joinToString(", ")}$suffix"
                StageOutcome.Failed(summary, error = report.installCommand.orEmpty())
            }
            else -> StageOutcome.Success("All packages present")
        }
    }
}
