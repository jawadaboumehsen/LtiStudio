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

import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoToolsGateTest {
    private fun createState(
        distroStatus: StepStatus = StepStatus.SUCCESS,
        serverStatus: StepStatus = StepStatus.SUCCESS,
        packagesStatus: StepStatus = StepStatus.SUCCESS,
        toolsStatus: StepStatus = StepStatus.PENDING,
        isRunning: Boolean = false,
        isChecking: Boolean = false,
        activeOperationId: String? = null,
        pendingAttemptId: String? = null,
        pendingAttempt: SetupAttemptRecord? = null,
        recoveryBlockReason: String? = null,
    ): ToolchainSetupState {
        val steps =
            listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "Ubuntu (WSL)",
                    description = "WSL distro",
                    status = distroStatus,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Build service",
                    description = "Server connection",
                    status = serverStatus,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "System packages",
                    description = "Package requirements",
                    status = packagesStatus,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tools",
                    description = "Toolchain components",
                    status = toolsStatus,
                ),
            )
        return ToolchainSetupState(
            steps = steps,
            isRunning = isRunning,
            isChecking = isChecking,
            activeOperationId = activeOperationId,
            pendingAttemptId = pendingAttemptId,
            pendingAttempt = pendingAttempt,
            recoveryBlockReason = recoveryBlockReason,
        )
    }

    @Test
    fun startsWhenStages1To3AreSuccessAndToolsPendingAndNotAttempted() {
        val state = createState(toolsStatus = StepStatus.PENDING)
        assertTrue(AutoToolsGate.shouldStart(state, attemptedThisLaunch = false))
    }

    @Test
    fun startsWhenStages1To3AreSuccessAndToolsFailedAndNotAttempted() {
        val state = createState(toolsStatus = StepStatus.FAILED)
        assertTrue(AutoToolsGate.shouldStart(state, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenAlreadyAttemptedThisLaunch() {
        val state = createState(toolsStatus = StepStatus.PENDING)
        assertFalse(AutoToolsGate.shouldStart(state, attemptedThisLaunch = true))
    }

    @Test
    fun doesNotStartWhenToolsIsAlreadySuccess() {
        val state = createState(toolsStatus = StepStatus.SUCCESS)
        assertFalse(AutoToolsGate.shouldStart(state, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenStage1IsNotSuccess() {
        val statePending = createState(distroStatus = StepStatus.PENDING)
        assertFalse(AutoToolsGate.shouldStart(statePending, attemptedThisLaunch = false))

        val stateFailed = createState(distroStatus = StepStatus.FAILED)
        assertFalse(AutoToolsGate.shouldStart(stateFailed, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenStage2IsNotSuccessOrDisconnected() {
        val statePending = createState(serverStatus = StepStatus.PENDING)
        assertFalse(AutoToolsGate.shouldStart(statePending, attemptedThisLaunch = false))

        val stateFailed = createState(serverStatus = StepStatus.FAILED)
        assertFalse(AutoToolsGate.shouldStart(stateFailed, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenStage3IsNotSuccess() {
        val statePending = createState(packagesStatus = StepStatus.PENDING)
        assertFalse(AutoToolsGate.shouldStart(statePending, attemptedThisLaunch = false))

        val stateFailed = createState(packagesStatus = StepStatus.FAILED)
        assertFalse(AutoToolsGate.shouldStart(stateFailed, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenAnythingIsRunningOrChecking() {
        val runningState = createState(isRunning = true)
        assertFalse(AutoToolsGate.shouldStart(runningState, attemptedThisLaunch = false))

        val checkingState = createState(isChecking = true)
        assertFalse(AutoToolsGate.shouldStart(checkingState, attemptedThisLaunch = false))

        val activeOpState = createState(activeOperationId = "op-123")
        assertFalse(AutoToolsGate.shouldStart(activeOpState, attemptedThisLaunch = false))
    }

    @Test
    fun doesNotStartWhenRecoveryIsPending() {
        val pendingIdState = createState(pendingAttemptId = "att-123")
        assertFalse(AutoToolsGate.shouldStart(pendingIdState, attemptedThisLaunch = false))

        val record =
            SetupAttemptRecord(
                attemptId = "att-123",
                environmentKey = "env-key",
                planId = "plan-1",
                planRevisionHash = "hash1",
                planKind = "FULL_SETUP",
                status = AttemptStatus.RUNNING,
            )
        val pendingRecordState = createState(pendingAttempt = record)
        assertFalse(AutoToolsGate.shouldStart(pendingRecordState, attemptedThisLaunch = false))

        val blockedState = createState(recoveryBlockReason = "Ambiguous journal")
        assertFalse(AutoToolsGate.shouldStart(blockedState, attemptedThisLaunch = false))
    }

    @Test
    fun blockReasonNamesWhatIsInTheWay() {
        assertNull(
            AutoToolsGate.blockReason(createState(toolsStatus = StepStatus.PENDING), attemptedThisLaunch = false),
        )
        assertEquals(
            "System packages is WARNING",
            AutoToolsGate.blockReason(
                createState(packagesStatus = StepStatus.WARNING),
                attemptedThisLaunch = false,
            ),
        )
        assertEquals(
            "setup attempt att-1 is still open",
            AutoToolsGate.blockReason(
                createState(toolsStatus = StepStatus.PENDING).copy(pendingAttemptId = "att-1"),
                attemptedThisLaunch = false,
            ),
        )
    }
}
