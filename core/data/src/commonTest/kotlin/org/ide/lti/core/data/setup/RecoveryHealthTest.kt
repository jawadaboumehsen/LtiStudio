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

import io.ltirom.tooling.core.remote.RunPurpose
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ports.InstallId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for toolchain recovery health and the four separate facts (008 T083).
 */
class RecoveryHealthTest {

    @Test
    fun testFourFactsReportedSeparatelyAndCombineToHealthy() = runTest {
        val healthyReport = ToolchainHealthReport(
            selectedInstallId = InstallId("install-1"),
            isIntact = true,
            serviceResolvesTools = true,
            lastOperationCompleted = true,
        )

        assertEquals(InstallId("install-1"), healthyReport.selectedInstallId)
        assertTrue(healthyReport.isIntact)
        assertTrue(healthyReport.serviceResolvesTools)
        assertTrue(healthyReport.lastOperationCompleted)
        assertTrue(healthyReport.isHealthy)
        assertFalse(healthyReport.activeInstallCorrupt)
    }

    @Test
    fun testCorruptActiveInstallMarksNeedsRecoveryNeverSuccess() = runTest {
        val corruptReport = ToolchainHealthReport(
            selectedInstallId = InstallId("install-1"),
            isIntact = false, // corrupt binaries or missing files
            serviceResolvesTools = true,
            lastOperationCompleted = true,
        )

        assertFalse(corruptReport.isHealthy)
        assertTrue(corruptReport.activeInstallCorrupt)

        // When selected install is missing or uninitialized
        val uninitializedReport = ToolchainHealthReport(
            selectedInstallId = null,
            isIntact = false,
            serviceResolvesTools = false,
            lastOperationCompleted = true,
        )
        assertFalse(uninitializedReport.isHealthy)
        assertFalse(uninitializedReport.activeInstallCorrupt)
    }

    @Test
    fun testRepairFromRecoveryRunsWithPurposeSetup() = runTest {
        val purpose = RecoveryHealthCheck.determineRunPurpose(isRecovery = true)
        assertEquals(RunPurpose.SETUP, purpose)

        val normalPurpose = RecoveryHealthCheck.determineRunPurpose(isRecovery = false)
        assertEquals(RunPurpose.WORKSPACE, normalPurpose)
    }
}
