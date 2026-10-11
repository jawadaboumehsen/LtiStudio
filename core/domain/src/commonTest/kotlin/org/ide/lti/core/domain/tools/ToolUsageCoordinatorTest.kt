/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolUsageCoordinatorTest {
    @Test
    fun `removal is refused when a ROM job is active`() = runTest {
        val coordinator = ToolUsageCoordinator()

        coordinator.admitJob("job-1") {
            // capture pins
            true
        }
        assertTrue(coordinator.isJobActive)

        val removalResult =
            coordinator.withRemovalAdmission {
                // Must not be executed
                true
            }
        assertTrue(removalResult.isFailure)

        coordinator.finishJob("job-1")
        assertFalse(coordinator.isJobActive)

        val removalAfterJob =
            coordinator.withRemovalAdmission {
                true
            }
        assertTrue(removalAfterJob.isSuccess)
    }

    @Test
    fun `duplicate admission does not capture pins or release the active owner`() = runTest {
        val coordinator = ToolUsageCoordinator()
        coordinator.admitJob("same") { Unit }
        var captured = false
        assertFailsWith<IllegalStateException> {
            coordinator.admitJob("same") { captured = true }
        }
        assertFalse(captured)
        assertTrue(coordinator.isJobActive)
        assertTrue(coordinator.withToolMutationAdmission { Unit }.isFailure)
        coordinator.finishJob("same")
        assertFalse(coordinator.isJobActive)
    }

    @Test
    fun `failed pin capture does not admit a job`() = runTest {
        val coordinator = ToolUsageCoordinator()
        assertFailsWith<IllegalStateException> {
            coordinator.admitJob("failed") { error("pins unavailable") }
        }
        assertFalse(coordinator.isJobActive)
        assertTrue(coordinator.withToolMutationAdmission { Unit }.isSuccess)
    }

    @Test
    fun `removal is refused during server reconciliation`() = runTest {
        val coordinator = ToolUsageCoordinator()
        coordinator.markReconciling(true)

        val removalResult =
            coordinator.withRemovalAdmission {
                true
            }
        assertTrue(removalResult.isFailure)

        coordinator.markReconciling(false)
        val afterReconciliation =
            coordinator.withRemovalAdmission {
                true
            }
        assertTrue(afterReconciliation.isSuccess)
    }

    @Test
    fun `job admission is refused while tool mutation is in progress`() = runTest {
        val coordinator = ToolUsageCoordinator()

        var jobAdmissionFailed = false
        val mutationResult =
            coordinator.withToolMutationAdmission {
                assertTrue(coordinator.isMutating)
                assertFailsWith<IllegalStateException> {
                    coordinator.admitJob("job-during-mutation") { true }
                }
                jobAdmissionFailed = true
                "mutation-done"
            }

        assertTrue(mutationResult.isSuccess)
        assertTrue(jobAdmissionFailed)
        assertFalse(coordinator.isMutating)
        assertFalse(coordinator.isJobActive)

        // After mutation finishes, admission succeeds
        val admitted = coordinator.admitJob("job-after-mutation") { "pins-captured" }
        kotlin.test.assertEquals("pins-captured", admitted)
        assertTrue(coordinator.isJobActive)
        coordinator.finishJob("job-after-mutation")
        assertFalse(coordinator.isJobActive)
    }

    @Test
    fun `multiple active jobs keep mutations refused until all jobs finish`() = runTest {
        val coordinator = ToolUsageCoordinator()

        coordinator.admitJob("job-1") { true }
        coordinator.admitJob("job-2") { true }
        assertTrue(coordinator.isJobActive)

        // Mutation refused while both jobs active
        assertTrue(coordinator.withToolMutationAdmission { Unit }.isFailure)

        // Finish first job: still active because job-2 is running
        coordinator.finishJob("job-1")
        assertTrue(coordinator.isJobActive)
        assertTrue(coordinator.withToolMutationAdmission { Unit }.isFailure)

        // Finish second job: now active is false, mutation succeeds
        coordinator.finishJob("job-2")
        assertFalse(coordinator.isJobActive)
        assertTrue(coordinator.withToolMutationAdmission { "success" }.isSuccess)
    }
}
