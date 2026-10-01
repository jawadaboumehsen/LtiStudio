/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.install

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.PendingSwitchRecord
import org.ide.lti.core.model.setup.SwitchCheckpoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for client-side toolchain activation reconciliation (008 T082).
 */
class ActivationReconciliationTest {

    private class FakeInstallationPort : ToolchainInstallationPort {
        var currentActiveId: String? = "install-old"
        var activateCallCount: Int = 0
        var recordedRequests: MutableList<ActivationRequest> = mutableListOf()
        var outcomeMap: MutableMap<String, ActivationOutcome> = mutableMapOf()
        var activateResponses: MutableList<ActivationOutcome> = mutableListOf()

        override suspend fun state(): InstalledToolchain = InstalledToolchain(
            activeInstallId = currentActiveId?.let { InstallId(it) },
            previousInstallId = null,
        )

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId =
            InstallId("candidate-1")

        override suspend fun activate(request: ActivationRequest): ActivationOutcome {
            activateCallCount++
            recordedRequests.add(request)
            return if (activateResponses.isNotEmpty()) {
                activateResponses.removeAt(0)
            } else {
                outcomeMap[request.requestId] ?: ActivationOutcome.Committed(request.targetInstallId)
            }
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = outcomeMap[requestId]

        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }

    @Test
    fun testLostResponseReplaysSameIdAndReconcilesToCommitted() = runTest {
        val port = FakeInstallationPort()
        val reconciler = ActivationReconciler(port)
        val request = ActivationRequest(
            requestId = "req-1",
            expectedActiveInstallId = InstallId("install-old"),
            targetInstallId = InstallId("install-new"),
        )

        // First attempt times out or transport fails -> ActivationOutcome.Unknown
        port.activateResponses.add(ActivationOutcome.Unknown)
        // Record on server already committed under req-1
        port.outcomeMap["req-1"] = ActivationOutcome.Committed(InstallId("install-new"))

        val outcome = reconciler.reconcile(request)

        assertTrue(outcome is ActivationOutcome.Committed)
        assertEquals(InstallId("install-new"), outcome.activeInstallId)
        // Replay used the same requestId
        assertTrue(port.recordedRequests.all { it.requestId == "req-1" })
    }

    @Test
    fun testInProgressPollsUntilCommitted() = runTest {
        val port = FakeInstallationPort()
        val reconciler = ActivationReconciler(port)
        val request = ActivationRequest(
            requestId = "req-2",
            expectedActiveInstallId = InstallId("install-old"),
            targetInstallId = InstallId("install-new"),
        )

        port.activateResponses.add(ActivationOutcome.InProgress)
        var polls = 0
        val outcome = reconciler.reconcile(request) {
            polls++
            if (polls >= 2) {
                port.outcomeMap["req-2"] = ActivationOutcome.Committed(InstallId("install-new"))
            } else {
                port.outcomeMap["req-2"] = ActivationOutcome.InProgress
            }
        }

        assertTrue(outcome is ActivationOutcome.Committed)
        assertEquals(InstallId("install-new"), outcome.activeInstallId)
        assertTrue(polls >= 2)
    }

    @Test
    fun testBlockedRequiresNewRequestIdAndRereadingState() = runTest {
        val port = FakeInstallationPort()
        val reconciler = ActivationReconciler(port)
        val initialRequest = ActivationRequest(
            requestId = "req-blocked",
            expectedActiveInstallId = InstallId("install-old"),
            targetInstallId = InstallId("install-new"),
        )

        port.activateResponses.add(ActivationOutcome.Blocked(activeWork = 3))
        val outcome = reconciler.reconcile(initialRequest)

        assertTrue(outcome is ActivationOutcome.Blocked)
        assertEquals(3, outcome.activeWork)

        // UI Retry must create a new request with a new ID and re-read state
        port.currentActiveId = "install-interim"
        val retryRequest = reconciler.prepareRetryRequest(initialRequest)

        assertNotEquals(initialRequest.requestId, retryRequest.requestId)
        assertEquals(InstallId("install-interim"), retryRequest.expectedActiveInstallId)
        assertEquals(initialRequest.targetInstallId, retryRequest.targetInstallId)
    }

    @Test
    fun testRequestMismatchSurfacedAsFatalErrorNeverRetried() = runTest {
        val port = FakeInstallationPort()
        val reconciler = ActivationReconciler(port)
        val request = ActivationRequest(
            requestId = "req-mismatch",
            expectedActiveInstallId = InstallId("install-old"),
            targetInstallId = InstallId("install-new"),
        )

        port.activateResponses.add(ActivationOutcome.RequestMismatch)
        val outcome = reconciler.reconcile(request)

        assertTrue(outcome is ActivationOutcome.RequestMismatch)
        assertEquals(1, port.activateCallCount)
    }

    @Test
    fun testRestartWithPendingSwitchResumesReconciliation() = runTest {
        val port = FakeInstallationPort()
        val reconciler = ActivationReconciler(port)
        val pending = PendingSwitchRecord(
            activationRequestId = "req-resume-1",
            expectedActiveInstallId = "install-old",
            targetInstallId = "install-target",
            selectionRevision = 42L,
            checkpoint = SwitchCheckpoint.OUTCOME_UNKNOWN,
        )

        port.outcomeMap["req-resume-1"] = ActivationOutcome.Committed(InstallId("install-target"))

        val outcome = reconciler.resumePendingSwitch(pending)

        assertTrue(outcome is ActivationOutcome.Committed)
        assertEquals(InstallId("install-target"), outcome.activeInstallId)
    }
}
