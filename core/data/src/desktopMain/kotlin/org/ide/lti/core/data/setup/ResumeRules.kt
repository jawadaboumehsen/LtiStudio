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

import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord

/**
 * Why the journal's [active] attempt cannot be resumed as [planId] in [distro], or null: it must be that plan or
 * attempt, waiting for the user, journaled for this distro, with every submitted command proven, and with the handoff
 * the user was shown.
 */
internal fun resumeRefusal(active: SetupAttemptRecord?, planId: String, distro: String): SetupOutcome? = when {
    active == null || (active.planId != planId && active.attemptId != planId) ||
        active.status != AttemptStatus.AWAITING_USER_ACTION -> SetupOutcome.Failed(
        stage = null,
        reason = "No pending attempt matching plan or attempt '$planId' in AWAITING_USER_ACTION state.",
    )

    active.environmentKey != distro -> SetupOutcome.Failed(
        stage = null,
        reason = "Attempt '${active.attemptId}' was for distro '${active.environmentKey}', not current '$distro'.",
    )

    active.unprovenIntents.isNotEmpty() ->
        SetupOutcome.Interrupted("Attempt '${active.attemptId}' has unproven commands; reconnect before resuming.")

    active.awaitingHandoff == null ->
        SetupOutcome.Failed(stage = null, reason = "Attempt '${active.attemptId}' has no handoff details.")

    else -> null
}

/** Whether this attempt installs system packages (a bootstrap), which resumes by checking them again. */
internal fun SetupAttemptRecord.isBootstrap(): Boolean = planKind == SetupPlanKind.BOOTSTRAP_PACKAGES.name ||
    persistedPlan?.kind == SetupPlanKind.BOOTSTRAP_PACKAGES.name ||
    bootstrapRequirementIds.isNotEmpty()
