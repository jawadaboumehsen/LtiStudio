/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.toolkit

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.pipeline.toolkit.withLock
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JobLockTest {

    private lateinit var tempDir: Path
    private lateinit var workspace: Workspace
    private lateinit var stores: WorkspaceToolkitStores

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("joblock-test-")
        workspace = Workspace(
            id = "test-ws",
            name = "Test Workspace",
            path = tempDir.toString(),
        )
        stores = WorkspaceToolkitStores()
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun testAcquireAndRelease() = runTest {
        assertNull(stores.currentHolder(workspace))

        val result1 = stores.acquire(workspace, "FirmwareExtraction")
        assertIs<LockResult.Acquired>(result1)
        assertEquals("FirmwareExtraction", result1.handle.holderName)
        assertEquals("FirmwareExtraction", stores.currentHolder(workspace))

        // Second acquire while held must return Locked naming the holder (FR-039)
        val result2 = stores.acquire(workspace, "DebloatTool")
        assertIs<LockResult.Locked>(result2)
        assertEquals("FirmwareExtraction", result2.holderName)

        // Release the first lock
        result1.handle.release()
        assertNull(stores.currentHolder(workspace))

        // Now DebloatTool can acquire
        val result3 = stores.acquire(workspace, "DebloatTool")
        assertIs<LockResult.Acquired>(result3)
        assertEquals("DebloatTool", stores.currentHolder(workspace))
        result3.handle.release()
        assertNull(stores.currentHolder(workspace))
    }

    @Test
    fun testLockReleasedOnFailure() = runTest {
        var lockedSeen: String? = null

        val failed = runCatching {
            stores.withLock(
                workspace = workspace,
                holderName = "FailingStep",
                onLocked = { lockedSeen = it },
            ) {
                assertEquals("FailingStep", stores.currentHolder(workspace))
                error("Simulated step crash")
            }
        }

        assertEquals("Simulated step crash", failed.exceptionOrNull()?.message)
        assertNull(lockedSeen)
        // Lock MUST be released despite the exception
        assertNull(stores.currentHolder(workspace), "Lock must be released when step fails")

        // New job can acquire immediately
        val nextAcquire = stores.acquire(workspace, "RecoveryStep")
        assertIs<LockResult.Acquired>(nextAcquire)
        nextAcquire.handle.release()
    }

    @Test
    fun testLockReleasedOnCancellation() = runTest {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            stores.withLock(
                workspace = workspace,
                holderName = "LongRunningBuild",
                onLocked = {},
            ) {
                started.complete(Unit)
                // Suspend indefinitely until cancelled
                CompletableDeferred<Unit>().await()
            }
        }

        started.await()
        assertEquals("LongRunningBuild", stores.currentHolder(workspace))

        // Cancel the job
        job.cancelAndJoin()

        // Lock MUST be released on cancellation
        assertNull(stores.currentHolder(workspace), "Lock must be released on cancellation")

        val nextAcquire = stores.acquire(workspace, "NextJob")
        assertIs<LockResult.Acquired>(nextAcquire)
        nextAcquire.handle.release()
    }
}
