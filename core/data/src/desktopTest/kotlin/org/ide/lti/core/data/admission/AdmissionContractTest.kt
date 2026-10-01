/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.admission

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.AdmissionResult
import org.ide.lti.core.domain.ports.BuildAdmissionResult
import org.ide.lti.core.domain.ports.EnvironmentAdmission
import org.ide.lti.core.domain.ports.EnvironmentUse
import org.ide.lti.core.domain.ports.LeaseReconciliation
import org.ide.lti.core.domain.ports.MutationKind
import org.ide.lti.core.domain.ports.acquireBuildAdmission
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdmissionContractTest {

    @Test
    fun testBarrierReleaseTwentyCoroutinesYieldsExactlyOneAdmittedAndNineteenBusy() = runTest {
        val repo = WorkspaceAdmissionRepository(storageDir = null)
        val barrier = CompletableDeferred<Unit>()
        val kinds = listOf(
            MutationKind.SAVE,
            MutationKind.MATERIALIZE,
            MutationKind.IMPORT,
            MutationKind.START,
            MutationKind.RESUME,
            MutationKind.PUBLISH,
        )

        val jobs = (0 until 20).map { i ->
            CoroutineScope(Dispatchers.Default).async {
                barrier.await()
                repo.tryAcquire(
                    workspaceId = "ws-concurrent",
                    kind = kinds[i % kinds.size],
                    operationId = "op-$i",
                    expectedRevision = 1,
                    currentRevision = 1,
                )
            }
        }

        barrier.complete(Unit)
        val results = jobs.awaitAll()

        val admitted = results.filterIsInstance<AdmissionResult.Admitted>()
        val busy = results.filterIsInstance<AdmissionResult.Busy>()

        assertEquals(1, admitted.size)
        assertEquals(19, busy.size)

        val ownerOpId = admitted.first().lease.operationId
        for (b in busy) {
            assertEquals(ownerOpId, b.owner.operationId)
        }
    }

    @Test
    fun testReleaseAllowsNextAcquisitionAndDifferentWorkspaceAdmittedConcurrently() = runTest {
        val repo = WorkspaceAdmissionRepository(storageDir = null)

        val first = repo.tryAcquire("ws-1", MutationKind.SAVE, "op-1", null, null)
        assertTrue(first is AdmissionResult.Admitted)

        // Different workspace is admitted while first is held
        val second = repo.tryAcquire("ws-2", MutationKind.START, "op-2", null, null)
        assertTrue(second is AdmissionResult.Admitted)

        // Same workspace is busy
        val sameWs = repo.tryAcquire("ws-1", MutationKind.RESUME, "op-3", null, null)
        assertTrue(sameWs is AdmissionResult.Busy)

        // Release first
        repo.release(first.lease)

        // Now next tryAcquire on ws-1 is Admitted
        val third = repo.tryAcquire("ws-1", MutationKind.PUBLISH, "op-4", null, null)
        assertTrue(third is AdmissionResult.Admitted)
    }

    @Test
    fun testRevisionMismatchRecordsNoLease() = runTest {
        val repo = WorkspaceAdmissionRepository(storageDir = null)

        val result = repo.tryAcquire("ws-rev", MutationKind.SAVE, "op-rev", expectedRevision = 3, currentRevision = 4)
        assertEquals(AdmissionResult.RevisionMismatch(expected = 3, actual = 4), result)
        assertNull(repo.currentOwner("ws-rev"))
    }

    @Test
    fun testEnvironmentLeasesSharedBuildUseAndExclusiveMaintenance() = runTest {
        val repo = EnvironmentLeaseRepository(storageDir = null)
        val envKey = "test-env"

        val b1 = repo.tryAcquire(envKey, EnvironmentUse.BUILD_USE, "op-b1")
        val b2 = repo.tryAcquire(envKey, EnvironmentUse.BUILD_USE, "op-b2")
        val b3 = repo.tryAcquire(envKey, EnvironmentUse.BUILD_USE, "op-b3")

        assertTrue(b1 is EnvironmentAdmission.Admitted)
        assertTrue(b2 is EnvironmentAdmission.Admitted)
        assertTrue(b3 is EnvironmentAdmission.Admitted)

        val m1 = repo.tryAcquire(envKey, EnvironmentUse.MAINTENANCE, "op-m1")
        assertTrue(m1 is EnvironmentAdmission.Busy)
        assertEquals(3, m1.owners.size)
        assertEquals(setOf("op-b1", "op-b2", "op-b3"), m1.owners.map { it.operationId }.toSet())

        repo.release(b1.lease)
        repo.release(b2.lease)
        repo.release(b3.lease)

        val m2 = repo.tryAcquire(envKey, EnvironmentUse.MAINTENANCE, "op-m2")
        assertTrue(m2 is EnvironmentAdmission.Admitted)

        val bAfterM = repo.tryAcquire(envKey, EnvironmentUse.BUILD_USE, "op-b-after")
        assertTrue(bAfterM is EnvironmentAdmission.Busy)
        assertEquals(1, bAfterM.owners.size)
        assertEquals("op-m2", bAfterM.owners.first().operationId)

        repo.release(m2.lease)
        assertTrue(repo.activeLeases(envKey).isEmpty())
    }

    @Test
    fun testBuildAdmissionHelperRollsBackEnvironmentLeaseOnWorkspaceBusy() = runTest {
        val envRepo = EnvironmentLeaseRepository(storageDir = null)
        val wsRepo = WorkspaceAdmissionRepository(storageDir = null)
        val envKey = "env-order"
        val wsId = "ws-order"

        // Pre-occupy workspace
        val priorWsLease = wsRepo.tryAcquire(wsId, MutationKind.START, "prior-op", 1, 1)
        assertTrue(priorWsLease is AdmissionResult.Admitted)

        // Attempt build admission: env should succeed, but ws is busy, so env lease must be rolled back
        val result = acquireBuildAdmission(
            env = envRepo,
            ws = wsRepo,
            environmentKey = envKey,
            workspaceId = wsId,
            kind = MutationKind.MATERIALIZE,
            operationId = "new-op",
            expectedRevision = 1,
            currentRevision = 1,
        )

        assertTrue(result is BuildAdmissionResult.Busy)
        assertEquals("prior-op", result.owner.operationId)
        assertTrue(envRepo.activeLeases(envKey).isEmpty(), "Environment lease must be released when workspace is busy")
    }

    @Test
    fun testDurabilityPersistsAcrossRestartAndRequiresReconciliation() = runTest {
        val tempDir = Files.createTempDirectory("admission_contract_durability_")
        try {
            val repo1 = WorkspaceAdmissionRepository(storageDir = tempDir)
            val admitted = repo1.tryAcquire(
                workspaceId = "ws-durable",
                kind = MutationKind.START,
                operationId = "op-durable",
                expectedRevision = 1,
                currentRevision = 1,
            )
            assertTrue(admitted is AdmissionResult.Admitted)

            // Construct a NEW repository over the same storageDir
            val repo2 = WorkspaceAdmissionRepository(storageDir = tempDir)
            val unreconciled = repo2.unreconciledLeases()
            assertEquals(1, unreconciled.size)
            val orphan = unreconciled.first()
            assertEquals("ws-durable", orphan.workspaceId)
            assertEquals("op-durable", orphan.operationId)

            // tryAcquire for that workspace is Busy
            val busy = repo2.tryAcquire(
                workspaceId = "ws-durable",
                kind = MutationKind.RESUME,
                operationId = "op-resume",
                expectedRevision = 1,
                currentRevision = 1,
            )
            assertTrue(busy is AdmissionResult.Busy)
            assertEquals("op-durable", busy.owner.operationId)

            // Reconcile as CONFIRMED_ABANDONED
            repo2.reconcile(orphan, LeaseReconciliation.CONFIRMED_ABANDONED)
            assertTrue(repo2.unreconciledLeases().isEmpty())

            // After reconcile, it is Admitted
            val admittedAfterReconcile = repo2.tryAcquire(
                workspaceId = "ws-durable",
                kind = MutationKind.START,
                operationId = "op-new",
                expectedRevision = 1,
                currentRevision = 1,
            )
            assertTrue(admittedAfterReconcile is AdmissionResult.Admitted)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
