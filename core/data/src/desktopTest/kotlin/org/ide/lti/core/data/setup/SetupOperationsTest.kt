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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupOperationsTest {

    @Test
    fun testRestoredResultsCannotEstablishLiveReadiness() {
        val state = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "WSL",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.RESTORED,
                ),
            ),
        )
        assertFalse(state.isAllReady, "Restored results should not establish live readiness")

        val liveState = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "WSL",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
            ),
        )
        assertTrue(liveState.isAllReady, "Live success should establish readiness")
    }

    @Test
    fun testNullableMeasuredStorage() {
        val stateChecked = ToolchainSetupState(storageAvailableBytes = 1024L)
        assertEquals(1024L, stateChecked.storageAvailableBytes)

        val stateUnchecked = ToolchainSetupState(storageAvailableBytes = null)
        assertNull(stateUnchecked.storageAvailableBytes)
    }

    @Test
    fun testSingleOperationSerialization() {
        val stateIdle = ToolchainSetupState()
        assertFalse(stateIdle.isBusy, "State should not be busy initially")

        val stateRunning = ToolchainSetupState(isRunning = true)
        assertTrue(stateRunning.isBusy, "isRunning should make state busy")

        val stateChecking = ToolchainSetupState(isChecking = true)
        assertTrue(stateChecking.isBusy, "isChecking should make state busy")

        val stateWithOp = ToolchainSetupState(activeOperationId = "op-123")
        assertTrue(stateWithOp.isBusy, "activeOperationId should make state busy")
    }

    @Test
    fun test20BarrierSynchronizedRequestsAdmitExactlyOneAnd19Busy_SC002() {
        val threadPool = Executors.newFixedThreadPool(20)
        val dispatcher = threadPool.asCoroutineDispatcher()
        val coordinator = SetupOperationCoordinator()
        val barrier = CyclicBarrier(20)
        val outcomes = Collections.synchronizedList(mutableListOf<SetupOutcome>())
        val kinds = listOf("FULL_SETUP", "STAGE_RETRY", "REPAIR_TOOL", "TEST_TOOL", "CACHE_RESET")

        try {
            runBlocking(dispatcher) {
                val jobs = (0 until 20).map { i ->
                    launch(dispatcher) {
                        barrier.await()
                        val outcome = coordinator.withAdmission(
                            environmentKey = "Ubuntu",
                            operationId = "op-$i",
                            kind = kinds[i % kinds.size],
                        ) {
                            delay(100)
                            SetupOutcome.Succeeded()
                        }
                        outcomes.add(outcome)
                    }
                }
                jobs.forEach { it.join() }
            }

            val busyCount = outcomes.count { it is SetupOutcome.Busy }
            val admittedCount = outcomes.count { it is SetupOutcome.Succeeded }

            assertEquals(19, busyCount, "Exactly 19 of 20 concurrent requests must receive Busy")
            assertEquals(1, admittedCount, "Exactly 1 of 20 concurrent requests must be admitted and succeed")
            assertFalse(coordinator.isBusy("Ubuntu"), "Coordinator must not leak busy state after completion")
        } finally {
            threadPool.shutdown()
        }
    }

    @Test
    fun testIgnoredBooleanOrNonThrowingFailureMapsToTypedFailed() {
        runBlocking {
            val coordinator = SetupOperationCoordinator()

            val outcome = coordinator.withAdmission(
                environmentKey = "Ubuntu",
                operationId = "op-fail",
                kind = "TEST_TOOL",
            ) {
                SetupOutcome.Failed(
                    stage = "TOOLCHAIN_COMPILATION",
                    reason = "Tool binary exited with non-zero code 1",
                )
            }

            assertTrue(outcome is SetupOutcome.Failed)
            assertFalse(outcome.isSuccess)
            assertFailsWith<IllegalStateException> {
                outcome.requireSuccess()
            }
        }
    }

    @Test
    fun testCancellationExceptionYieldsTypedCancelledAndPropagates() {
        runBlocking {
            val coordinator = SetupOperationCoordinator()

            val thrown = assertFailsWith<CancellationException> {
                coordinator.withAdmission(
                    environmentKey = "Ubuntu",
                    operationId = "op-cancel",
                    kind = "FULL_SETUP",
                ) {
                    throw CancellationException("Operation cancelled by user")
                }
            }

            assertEquals("Operation cancelled by user", thrown.message)
            assertFalse(coordinator.isBusy("Ubuntu"), "Owner must be cleared on CancellationException")
        }
    }

    @Test
    fun testExplicitCancelVsDetachDistinguished() {
        runBlocking {
            val coordinator = SetupOperationCoordinator()

            // Explicit cancel when no operation is active returns Failed
            val noOpCancel = coordinator.cancelActiveOperation("Ubuntu")
            assertTrue(noOpCancel is SetupOutcome.Failed)
            assertEquals("No active operation to cancel.", (noOpCancel as SetupOutcome.Failed).reason)

            // With active operation, explicit cancel returns Cancelled
            val threadPool = Executors.newSingleThreadExecutor()
            val dispatcher = threadPool.asCoroutineDispatcher()
            try {
                val startedLatch = CountDownLatch(1)
                val finishLatch = CountDownLatch(1)
                val job = launch(dispatcher) {
                    coordinator.withAdmission("Ubuntu", "op-long", "FULL_SETUP") {
                        startedLatch.countDown()
                        finishLatch.await()
                        SetupOutcome.Succeeded()
                    }
                }

                startedLatch.await()
                assertTrue(coordinator.isBusy("Ubuntu"))

                // Explicit cancel marks active operation cancelled
                val cancelOutcome = coordinator.cancelActiveOperation("Ubuntu")
                assertEquals(SetupOutcome.Cancelled, cancelOutcome)

                finishLatch.countDown()
                job.join()
                assertFalse(coordinator.isBusy("Ubuntu"))
            } finally {
                threadPool.shutdown()
            }
        }
    }

    @Test
    fun testTerminalCleanupOnEveryPathIncludingExceptions() {
        runBlocking {
            val coordinator = SetupOperationCoordinator()

            // 1. Success path
            coordinator.withAdmission("Ubuntu", "op-1", "FULL_SETUP") {
                SetupOutcome.Succeeded()
            }
            assertFalse(coordinator.isBusy("Ubuntu"), "Owner must be cleared on Succeeded")

            // 2. Failed path
            coordinator.withAdmission("Ubuntu", "op-2", "FULL_SETUP") {
                SetupOutcome.Failed("SERVER", "Unreachable")
            }
            assertFalse(coordinator.isBusy("Ubuntu"), "Owner must be cleared on Failed")

            // 3. RuntimeException path
            coordinator.withAdmission("Ubuntu", "op-3", "FULL_SETUP") {
                throw RuntimeException("Unexpected disk I/O failure")
            }
            assertFalse(coordinator.isBusy("Ubuntu"), "Owner must be cleared on RuntimeException")

            // 4. CancellationException path
            try {
                coordinator.withAdmission("Ubuntu", "op-4", "FULL_SETUP") {
                    throw CancellationException("User cancelled")
                }
            } catch (_: CancellationException) {}
            assertFalse(coordinator.isBusy("Ubuntu"), "Owner must be cleared on CancellationException")
        }
    }
}
