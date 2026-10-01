/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.state

import kotlinx.coroutines.flow.MutableStateFlow
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupStateScopesTest {

    @Test
    fun operationScope_updatesOnlyItsOwnFields() {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }

        scope.start(SetupStepStage.REPO_SYNCHRONIZATION)
        scope.appendLog("Cloning repo...")
        scope.setStepRunId(SetupStepStage.REPO_SYNCHRONIZATION, "run-101")

        val state = stateFlow.value
        assertTrue(state.isRunning)
        assertEquals(SetupStepStage.REPO_SYNCHRONIZATION, state.currentStage)
        assertEquals(listOf("Cloning repo..."), state.logs)
        assertEquals("run-101", state.stepRunIds[SetupStepStage.REPO_SYNCHRONIZATION])

        // Verify other scope fields remained untouched
        assertFalse(state.isChecking)
        assertNull(state.pendingAttemptId)
    }

    @Test
    fun operationScope_dropsWritesWhenSuperseded() {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        var currentActiveOp: String? = "op-1"
        val oldScope = OperationScope("Ubuntu", "op-1", stateFlow) { currentActiveOp }

        oldScope.appendLog("Log from op-1")
        assertEquals(listOf("Log from op-1"), stateFlow.value.logs)

        // op-2 supersedes op-1
        currentActiveOp = "op-2"
        val newScope = OperationScope("Ubuntu", "op-2", stateFlow) { currentActiveOp }
        newScope.appendLog("Log from op-2")

        // Old scope write should be dropped
        oldScope.appendLog("Stale log from op-1")

        assertEquals(listOf("Log from op-1", "Log from op-2"), stateFlow.value.logs)
    }

    @Test
    fun checkScope_updatesOnlyCheckFieldsAndDropsWhenSuperseded() {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        var currentCheckId: String? = "check-1"
        val scope1 = CheckScope("Ubuntu", "check-1", stateFlow) { currentCheckId }

        scope1.setChecking(true)
        val diag = listOf(DiagnosticCheckItem("cmake", "CMake", StepStatus.SUCCESS, "v3.28"))
        scope1.setDiagnostics(diag)
        scope1.setStorage(availableBytes = 50_000_000_000L, measuredAt = 12345L)

        val state = stateFlow.value
        assertTrue(state.isChecking)
        assertEquals(diag, state.diagnostics)
        assertEquals(50_000_000_000L, state.storageAvailableBytes)
        assertFalse(state.isRunning)

        // Superseded check
        currentCheckId = "check-2"
        scope1.setDiagnostics(emptyList()) // should be dropped
        assertEquals(diag, stateFlow.value.diagnostics)
    }

    @Test
    fun installedScope_updatesOnlyInstalledInventoryFields() {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = InstalledScope("Ubuntu", stateFlow)

        scope.setToolsMatrix(
            listOf(
                ToolComponentItem(
                    id = "adb",
                    name = "ADB",
                    category = ToolCategory.BRIDGE_AND_FLASHING,
                    binaryName = "adb",
                ),
            ),
        )
        scope.setPublishedToolIds(setOf("adb", "fastboot"), unpublished = setOf("erofsfuse"))
        scope.setVerifiedTimestamp(99999L)

        val state = stateFlow.value
        assertEquals(1, state.toolsMatrix.size)
        assertEquals(setOf("adb", "fastboot"), state.publishedToolIds)
        assertEquals(setOf("erofsfuse"), state.unpublishedToolIds)
        assertEquals(99999L, state.lastVerifiedTimestamp)
        assertFalse(state.isRunning)
        assertFalse(state.isChecking)
    }

    @Test
    fun recoveryScope_updatesOnlyRecoveryFields() {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = RecoveryScope("Ubuntu", stateFlow)

        scope.setPendingAttempt(attemptId = "att-99", attempt = null, reason = "Interrupted by reboot")

        val state = stateFlow.value
        assertEquals("att-99", state.pendingAttemptId)
        assertEquals("Interrupted by reboot", state.recoveryBlockReason)
        assertFalse(state.isRunning)
        assertFalse(state.isChecking)
    }
}
