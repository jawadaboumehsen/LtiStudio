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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceToolkitStoresTest {

    private lateinit var tempDir: Path
    private lateinit var workspace: Workspace
    private lateinit var stores: WorkspaceToolkitStores

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("stores-test-")
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
    fun testRestorePointStore() = runTest {
        val initialIndex = stores.readIndex(workspace)
        assertTrue(initialIndex.items.isEmpty())

        val rp1 = RestorePoint(
            id = "rp-1",
            tool = "debloat",
            itemPath = "system/app/Foo",
            createdAt = "2026-10-25T10:00:00Z",
            backupPath = "restore/backups/Foo.bak",
        )

        stores.addRestorePoint(workspace, rp1)

        val indexAfterAdd = stores.readIndex(workspace)
        assertEquals(1, indexAfterAdd.items.size)
        assertEquals("rp-1", indexAfterAdd.items.first().id)
        assertEquals(RestorePointState.Available, indexAfterAdd.items.first().state)

        stores.updateRestorePointState(workspace, "rp-1", RestorePointState.Restored)

        val indexAfterUpdate = stores.readIndex(workspace)
        assertEquals(RestorePointState.Restored, indexAfterUpdate.findById("rp-1")?.state)
    }

    @Test
    fun testAckStore() = runTest {
        assertFalse(stores.isAcknowledged(workspace, "super_overlap"))

        val acks = stores.acknowledge(workspace, "super_overlap", "2026-10-25T10:00:00Z")
        assertTrue(stores.isAcknowledged(workspace, "super_overlap"))
        assertEquals(1, acks.items.size)

        // Idempotent acknowledge
        val secondAcks = stores.acknowledge(workspace, "super_overlap", "2026-10-25T11:00:00Z")
        assertEquals(1, secondAcks.items.size)
        assertEquals("2026-10-25T10:00:00Z", secondAcks.items.first().acceptedAt)

        val loaded = stores.readAcknowledgements(workspace)
        assertEquals(secondAcks, loaded)
    }
}
