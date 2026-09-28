/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetupStatusPresentationTest {
    @Test
    fun testMapStatusBarRunningOperation() {
        val op =
            SetupOperation(
                operationId = "op-1",
                executionState = SetupOperationState.RUNNING,
            )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Running environment setup…", statusBar.leadingStatusText)
        assertNull(statusBar.failureText)
    }

    @Test
    fun testMapStatusBarReconnectingOperation() {
        val op =
            SetupOperation(
                operationId = "op-2",
                executionState = SetupOperationState.RECONNECTING,
            )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Reconnecting to background setup…", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarAwaitingUserAction() {
        val op =
            SetupOperation(
                operationId = "op-3",
                executionState = SetupOperationState.AWAITING_USER_ACTION,
            )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Terminal authorization required", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarSucceededAndCancelledOperations() {
        val opSucceeded =
            SetupOperation(
                operationId = "op-4",
                executionState = SetupOperationState.SUCCEEDED,
            )
        val statusBarSucceeded = SetupPresentationMapper.mapStatusBar(opSucceeded, ToolchainSetupState())
        assertEquals("Environment setup completed", statusBarSucceeded.leadingStatusText)

        val opCancelled =
            SetupOperation(
                operationId = "op-5",
                executionState = SetupOperationState.CANCELLED,
            )
        val statusBarCancelled = SetupPresentationMapper.mapStatusBar(opCancelled, ToolchainSetupState())
        assertEquals("Environment setup cancelled", statusBarCancelled.leadingStatusText)
    }

    @Test
    fun testMapStatusBarCheckingWithoutActiveOperation() {
        val toolchain = ToolchainSetupState(isChecking = true)
        val statusBar = SetupPresentationMapper.mapStatusBar(null, toolchain)
        assertEquals("Verifying machine environment…", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarPendingAttemptWithoutActiveOperation() {
        val toolchain = ToolchainSetupState(pendingAttemptId = "attempt-old")
        val statusBar = SetupPresentationMapper.mapStatusBar(null, toolchain)
        assertEquals("A previous setup didn't finish • Review in Recovery", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarFailureExposed() {
        val op =
            SetupOperation(
                operationId = "op-6",
                executionState = SetupOperationState.FAILED,
                failure = "WSL command failed with exit 1",
            )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Environment setup failed", statusBar.leadingStatusText)
        assertEquals("WSL command failed with exit 1", statusBar.failureText)
    }

    @Test
    fun testMapEnvironmentSummaryHealthyServerWithFailedTools() {
        val toolchain =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Build Service",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "",
                        status = StepStatus.FAILED,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
            )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val buildServiceItem = summary.items.first { it.label == "Build service" }
        assertEquals("Ready", buildServiceItem.value)
        assertEquals(IdeStatusSeverity.Ready, buildServiceItem.severity)

        val toolReadinessItem = summary.items.first { it.label == "Tool readiness" }
        assertEquals("Needs attention", toolReadinessItem.value)
        assertEquals(IdeStatusSeverity.Failed, toolReadinessItem.severity)
    }

    @Test
    fun testMapEnvironmentSummaryWslFailure() {
        val toolchain =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "",
                        status = StepStatus.FAILED,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
            )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val runtimeItem = summary.items.first { it.label == "Runtime" }
        assertEquals("Unavailable", runtimeItem.value)
        assertEquals(IdeStatusSeverity.Failed, runtimeItem.severity)
    }

    @Test
    fun testMapEnvironmentSummaryRunningState() {
        val toolchain =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Build Service",
                        description = "",
                        status = StepStatus.RUNNING,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
            )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val buildServiceItem = summary.items.first { it.label == "Build service" }
        assertEquals("Checking…", buildServiceItem.value)
        assertEquals(IdeStatusSeverity.Running, buildServiceItem.severity)
    }
}
