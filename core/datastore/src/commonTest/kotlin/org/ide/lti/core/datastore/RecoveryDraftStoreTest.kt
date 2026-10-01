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
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecoveryDraftStoreTest {
    @Test
    fun latestRevisionCoalescesAndSurvivesRestart() = runTest {
        val settings = MapSettings()
        val store = RecoveryDraftStore(settings, ioDispatcher = Dispatchers.Unconfined)
        store.persist(record(1L))
        store.persist(record(2L))
        val restarted = RecoveryDraftStore(settings, ioDispatcher = Dispatchers.Unconfined)
        assertEquals(2L, restarted.get("workspace")?.record?.draftRevision)
    }

    @Test
    fun changedBaseIsReportedWithoutDiscardingRecord() = runTest {
        val store = RecoveryDraftStore(MapSettings(), ioDispatcher = Dispatchers.Unconfined)
        store.persist(record(1L))
        val result = store.get("workspace", "different-base", "different-digest")
        assertNotNull(result)
        assertTrue(result.baseDiverged)
        assertEquals(1L, result.record?.draftRevision)
    }

    @Test
    fun exactRevisionRetirementLeavesNewerEdit() = runTest {
        val store = RecoveryDraftStore(MapSettings(), ioDispatcher = Dispatchers.Unconfined)
        store.persist(record(1L))
        store.persist(record(2L))
        assertFalse(store.retireIfRevision("workspace", 1L).getOrThrow())
        assertEquals(2L, store.get("workspace")?.record?.draftRevision)
        assertTrue(store.retireIfRevision("workspace", 2L).getOrThrow())
        assertEquals(null, store.get("workspace"))
    }

    @Test
    fun olderGenerationWriteIsRejectedAfterDiscard() = runTest {
        val store = RecoveryDraftStore(MapSettings(), ioDispatcher = Dispatchers.Unconfined)
        store.persist(record(1L))
        store.advanceDiscardGeneration("workspace", 1L).getOrThrow()
        assertTrue(store.persist(record(2L, generation = 0L)).isFailure)
        assertEquals(null, store.get("workspace"))
    }

    @Test
    fun checksumCorruptionIsSafeAndSignalsUnrecoverable() = runTest {
        val settings = MapSettings()
        val store = RecoveryDraftStore(settings, ioDispatcher = Dispatchers.Unconfined)
        store.persist(record(1L))
        settings.remove(RecoveryDraftStore.KEY_BACKUP)
        settings.putString(RecoveryDraftStore.KEY_RECORDS, "{not-json")
        val loaded = RecoveryDraftStore(settings, ioDispatcher = Dispatchers.Unconfined).get("workspace")
        assertTrue(loaded?.wasCorrupt == true)
        assertEquals(null, loaded.record)
    }

    @Test
    fun writeFailureIsReturned() = runTest {
        val store = RecoveryDraftStore(ThrowingSettings(), ioDispatcher = Dispatchers.Unconfined)
        assertTrue(store.persist(record(1L)).isFailure)
    }

    private fun record(revision: Long, generation: Long = 0L) = RecoveryDraftRecord(
        workspaceId = "workspace",
        baseSnapshotId = "base",
        baseSnapshotDigest = "sha256:base",
        draftRevision = revision,
        discardGeneration = generation,
        acquisition = AcquisitionSettings(
            region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
        extraction = ExtractionSettings(), assembly = AssemblySettings(), debloat = DebloatSettings(),
        customization = CustomizationSettings(), build = BuildSettings(), release = ReleaseSettings(),
        publish = PublishSettings(), lastPersistedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private class ThrowingSettings : Settings by MapSettings() {
        override fun putString(key: String, value: String): Nothing = throw IllegalStateException("disk full")
    }
}
