/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.configuration

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnapshotMaterializerTest {

    private lateinit var tempDir: Path
    private lateinit var materializer: SnapshotMaterializer

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("snapshot-intents-test")
        materializer = SnapshotMaterializer(storageDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun findIncompleteMaterializationsIsEmptyWhenNothingIsInFlight() = runTest {
        assertTrue(materializer.findIncompleteMaterializations().isEmpty())
    }

    @Test
    fun markMaterializingSurvivesAsAnIncompleteMaterializationUntilCleared() = runTest {
        materializer.markMaterializing("ws-1", "snap-1")

        val incomplete = materializer.findIncompleteMaterializations()
        assertEquals(1, incomplete.size)
        assertEquals("ws-1", incomplete.first().workspaceId)
        assertEquals("snap-1", incomplete.first().snapshotId)
        assertEquals("materializing", incomplete.first().status)

        materializer.clearIntent("ws-1", "snap-1")
        assertTrue(materializer.findIncompleteMaterializations().isEmpty())
    }

    @Test
    fun independentMaterializerInstanceOverTheSameDirectorySeesAnInterruptedIntent() = runTest {
        // Simulates a process crash between markMaterializing and clearIntent: a fresh instance
        // reading the same directory must still discover the orphaned marker.
        materializer.markMaterializing("ws-crash", "snap-crash")

        val recovered = SnapshotMaterializer(storageDir = tempDir)
        val incomplete = recovered.findIncompleteMaterializations()

        assertEquals(1, incomplete.size)
        assertEquals("ws-crash", incomplete.first().workspaceId)
        assertEquals("snap-crash", incomplete.first().snapshotId)
    }

    @Test
    fun distinctWorkspacesAndSnapshotsAreTrackedIndependently() = runTest {
        materializer.markMaterializing("ws-a", "snap-a")
        materializer.markMaterializing("ws-b", "snap-b")

        assertEquals(2, materializer.findIncompleteMaterializations().size)

        materializer.clearIntent("ws-a", "snap-a")

        val remaining = materializer.findIncompleteMaterializations()
        assertEquals(1, remaining.size)
        assertEquals("ws-b", remaining.first().workspaceId)
    }

    @Test
    fun clearIntentForANeverMarkedIntentIsANoOp() = runTest {
        materializer.clearIntent("ws-never", "snap-never")
        assertTrue(materializer.findIncompleteMaterializations().isEmpty())
    }
}
