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
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SetupAttemptRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResumeRulesTest {
    private val waiting = SetupAttemptRecord(
        attemptId = "a1",
        environmentKey = "Ubuntu",
        planId = "p1",
        planRevisionHash = "r",
        planKind = SetupPlanKind.FULL_SETUP.name,
        status = AttemptStatus.AWAITING_USER_ACTION,
        awaitingHandoff = PersistedUserHandoff(actionId = "install_packages", distro = "Ubuntu", command = "sudo …"),
    )

    @Test
    fun aWaitingAttemptOfThisDistroWithItsHandoffResumesByPlanOrAttemptId() {
        assertNull(resumeRefusal(waiting, "p1", "Ubuntu"))
        assertNull(resumeRefusal(waiting, "a1", "Ubuntu"))
    }

    @Test
    fun eachMissingConditionRefusesWithItsOwnReason() {
        val wrongId = resumeRefusal(waiting, "other", "Ubuntu")
        assertTrue((wrongId as SetupOutcome.Failed).reason.contains("No pending attempt"))
        assertIs<SetupOutcome.Failed>(resumeRefusal(waiting.copy(status = AttemptStatus.RUNNING), "p1", "Ubuntu"))
        val otherDistro = resumeRefusal(waiting, "p1", "Debian") as SetupOutcome.Failed
        assertTrue(otherDistro.reason.contains("was for distro 'Ubuntu'"))
        // A command whose end is not proven could still be running: reconnect first, never resume over it.
        val intent = ChildIntentRecord(
            childIndex = 0,
            stage = "SYSTEM_PACKAGES",
            actionId = "install_packages",
            request = PersistedExecutionRequest(toolId = "apt-get"),
            idempotencyKey = "k",
            workspaceLock = "lock",
        )
        val unproven = waiting.copy(orderedIntents = listOf(intent))
        assertIs<SetupOutcome.Interrupted>(resumeRefusal(unproven, "p1", "Ubuntu"))
        assertIs<SetupOutcome.Failed>(resumeRefusal(waiting.copy(awaitingHandoff = null), "p1", "Ubuntu"))
        assertIs<SetupOutcome.Failed>(resumeRefusal(null, "p1", "Ubuntu"))
    }

    @Test
    fun aBootstrapIsKnownByItsKindOrItsRequirements() {
        assertFalse(waiting.isBootstrap())
        assertTrue(waiting.copy(planKind = SetupPlanKind.BOOTSTRAP_PACKAGES.name).isBootstrap())
        assertTrue(waiting.copy(bootstrapRequirementIds = listOf("pkg-zstd")).isBootstrap())
        assertEquals(SetupPlanKind.FULL_SETUP.name, waiting.planKind)
    }
}
