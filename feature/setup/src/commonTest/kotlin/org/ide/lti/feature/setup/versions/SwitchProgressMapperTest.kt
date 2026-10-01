/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.InstallId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UI state tests for switch progress, blocked, unknown, restored, and maintenance messages (008 T084).
 */
class SwitchProgressMapperTest {

    @Test
    fun testActivationBlockedMessageOffersRetry() {
        val uiState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.Blocked(activeWork = 3),
        )

        assertEquals("Activation blocked by 3 running jobs", uiState.message)
        assertTrue(uiState.canRetry)
        assertFalse(uiState.isTerminalSuccess)
    }

    @Test
    fun testSwitchOutcomeUnknownReconcilingMessage() {
        val unknownState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.Unknown,
        )
        assertEquals("Switch outcome unknown — reconciling…", unknownState.message)
        assertFalse(unknownState.canRetry)
        assertTrue(unknownState.isReconciling)

        val inProgressState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.InProgress,
        )
        assertEquals("Switch outcome unknown — reconciling…", inProgressState.message)
        assertTrue(inProgressState.isReconciling)
    }

    @Test
    fun testRestoredAfterVerifyFailureMessage() {
        val uiState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.VerifyFailedRestored(
                reason = "android-tools missing fastboot binary",
                activeInstallId = InstallId("install-prev"),
            ),
        )

        assertEquals(
            "Switched toolchain failed verification; restored previous toolchain: " +
                "android-tools missing fastboot binary",
            uiState.message,
        )
        assertFalse(uiState.isTerminalSuccess)
        assertFalse(uiState.canRetry)
    }

    @Test
    fun testMaintenanceFailedMessage() {
        val uiState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.MaintenanceFailed,
        )

        assertEquals("Toolchain restore failed; environment requires manual recovery", uiState.message)
        assertFalse(uiState.isTerminalSuccess)
        assertTrue(uiState.requiresRecovery)
    }

    @Test
    fun testCommittedOutcomeMessage() {
        val uiState = SwitchProgressMapper.mapOutcome(
            outcome = ActivationOutcome.Committed(InstallId("install-new")),
        )

        assertEquals("Toolchain switched successfully to install-new", uiState.message)
        assertTrue(uiState.isTerminalSuccess)
    }

    @Test
    fun testConsoleStagesMappedToHumanLabels() {
        assertEquals("Resolving sources…", SwitchProgressMapper.mapStage(SwitchStage.RESOLVING))
        assertEquals("Downloading packages…", SwitchProgressMapper.mapStage(SwitchStage.DOWNLOADING))
        assertEquals("Building tools…", SwitchProgressMapper.mapStage(SwitchStage.BUILDING))
        assertEquals("Verifying toolchain…", SwitchProgressMapper.mapStage(SwitchStage.VERIFYING))
        assertEquals("Switching active toolchain…", SwitchProgressMapper.mapStage(SwitchStage.SWITCHING))
    }
}
