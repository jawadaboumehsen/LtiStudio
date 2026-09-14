/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.SigningPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SnapshotPreferencesDataSourceTest {

    private fun createSampleSnapshot(id: String = "snap-1", workspaceId: String = "ws-1"): ConfigurationSnapshot =
        ConfigurationSnapshot.create(
            id = id,
            workspaceId = workspaceId,
            profileRevision = 1,
            acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                mode = AcquisitionMode.DOWNLOAD,
                region = TargetRegion.GLOBAL,
                firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
            ),
            build = org.ide.lti.core.model.workspace.BuildSettings(
                packagePolicy = PackagePolicy(
                    flashablePartitions = listOf("system", "vendor"),
                    flashableBootPartitions = listOf("boot"),
                ),
                signing = SigningPolicy(),
            ),
            assembly = org.ide.lti.core.model.workspace.AssemblySettings(
                buildType = "user",
                romVersion = "1.0.0",
            ),
            release = org.ide.lti.core.model.workspace.ReleaseSettings(
                otaBaseUrl = "https://example.com/updates",
            ),
            createdAt = Instant.fromEpochMilliseconds(1000L),
        )

    @Test
    fun testDefaultStateIsEmpty() = runTest {
        val settings = MapSettings()
        val dataSource = SnapshotPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        assertTrue(dataSource.currentSnapshots.isEmpty())
        assertTrue(dataSource.snapshots.first().isEmpty())
    }

    @Test
    fun testSaveAndRetrieveSnapshot() = runTest {
        val settings = MapSettings()
        val dataSource = SnapshotPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val snapshot = createSampleSnapshot("snap-1", "ws-1")
        dataSource.saveSnapshot(snapshot)

        assertEquals(1, dataSource.currentSnapshots.size)
        val retrieved = dataSource.getSnapshot("snap-1")
        assertNotNull(retrieved)
        assertEquals("snap-1", retrieved.id)
        assertEquals("ws-1", retrieved.workspaceId)
        assertEquals(snapshot.digest, retrieved.digest)

        // Verify round trip via new instance
        val reloaded = SnapshotPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertEquals(1, reloaded.currentSnapshots.size)
        val reloadedSnapshot = reloaded.getSnapshot("snap-1")
        assertNotNull(reloadedSnapshot)
        assertEquals("snap-1", reloadedSnapshot.id)
    }

    @Test
    fun testImmutabilityRejectingSaveOfExistingId() = runTest {
        val settings = MapSettings()
        val dataSource = SnapshotPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val snapshot1 = createSampleSnapshot("snap-1", "ws-1")
        dataSource.saveSnapshot(snapshot1)

        val snapshotDuplicate = snapshot1.copy(assembly = snapshot1.assembly.copy(romVersion = "2.0.0"))
        assertFailsWith<IllegalArgumentException> {
            dataSource.saveSnapshot(snapshotDuplicate)
        }

        // Verify original remains unmodified
        val current = dataSource.getSnapshot("snap-1")
        assertNotNull(current)
        assertEquals("1.0.0", current.assembly.romVersion)
    }

    @Test
    fun testSnapshotsForWorkspace() = runTest {
        val settings = MapSettings()
        val dataSource = SnapshotPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val snap1 = createSampleSnapshot("snap-1", "ws-1")
        val snap2 = createSampleSnapshot("snap-2", "ws-1")
        val snap3 = createSampleSnapshot("snap-3", "ws-2")

        dataSource.saveSnapshot(snap1)
        dataSource.saveSnapshot(snap2)
        dataSource.saveSnapshot(snap3)

        val ws1Snapshots = dataSource.snapshotsForWorkspace("ws-1").first()
        assertEquals(2, ws1Snapshots.size)
        assertTrue(ws1Snapshots.any { it.id == "snap-1" })
        assertTrue(ws1Snapshots.any { it.id == "snap-2" })

        val ws2Snapshots = dataSource.snapshotsForWorkspace("ws-2").first()
        assertEquals(1, ws2Snapshots.size)
        assertEquals("snap-3", ws2Snapshots.first().id)
    }
}
