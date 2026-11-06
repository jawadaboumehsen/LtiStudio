/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.run

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.datastore.RunFileStorage
import org.ide.lti.core.datastore.RunRecordDataSource
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RunRepositoryWritesTest {
    private class Storage : RunFileStorage {
        val runs = mutableMapOf<String, String>()
        var deleteFails = false

        /** Holds the first read after taking its copy, as a slow disk would. */
        var holdFirstRead = false
        private var reads = 0
        val readStarted = CompletableDeferred<Unit>()
        val finishRead = CompletableDeferred<Unit>()

        override suspend fun saveRun(runId: String, json: String) {
            runs[runId] = json
        }

        override suspend fun getRun(runId: String): String? {
            val copy = runs[runId]
            if (holdFirstRead && ++reads == 1) {
                readStarted.complete(Unit)
                finishRead.await()
            }
            return copy
        }

        override suspend fun appendEvents(runId: String, events: List<String>) {}
        override suspend fun getEvents(runId: String, fromSeq: Long): List<String> = emptyList()

        override suspend fun deleteRun(runId: String) {
            if (deleteFails) throw IOException("disk full")
            runs.remove(runId)
        }
    }

    private class FailingIndex(private val base: MapSettings = MapSettings()) : Settings by base {
        var fails = false

        override fun putString(key: String, value: String) {
            if (fails && key == RunRecordDataSource.KEY_RUNS) throw IOException("index unavailable")
            base.putString(key, value)
        }
    }

    private val finished = BuildRun(
        id = "run-1",
        workspaceId = "ws-1",
        snapshotId = "snap-1",
        state = RunState.SUCCEEDED,
        startedAt = Instant.fromEpochSeconds(1),
        artifacts = listOf(Artifact(ArtifactKind.FLASHABLE_ZIP, "out/rom.zip", presence = Presence.PRESENT)),
    )

    @Test
    fun aFailedDeleteLeavesTheRunWhereItWas() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = Storage()
        val repository = RunRepositoryImpl(
            RunRecordDataSource(MapSettings(), fileStorage = storage, ioDispatcher = dispatcher),
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher),
        )
        repository.upsert(finished)

        storage.deleteFails = true
        assertFailsWith<IOException> { repository.deleteRun(finished.id) }
        assertNotNull(repository.getRun(finished.id))

        storage.deleteFails = false
        repository.deleteRun(finished.id)
        assertNull(repository.getRun(finished.id))
    }

    @Test
    fun aReadBegunBeforeADeleteCannotBringTheRunBack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val storage = Storage()
        val source = RunRecordDataSource(MapSettings(), fileStorage = storage, ioDispatcher = dispatcher)
        source.saveRun(finished)
        storage.holdFirstRead = true
        val repository = RunRepositoryImpl(source, ioDispatcher = dispatcher, scope = CoroutineScope(dispatcher))
        runCurrent()
        storage.readStarted.await()

        repository.deleteRun(finished.id)
        storage.finishRead.complete(Unit)
        runCurrent()

        assertNull(repository.getRun(finished.id))
    }

    @Test
    fun aFailedIndexWriteKeepsTheRunOnDisk() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = Storage()
        val settings = FailingIndex()
        val source = RunRecordDataSource(settings, fileStorage = storage, ioDispatcher = dispatcher)
        source.saveRun(finished)

        settings.fails = true
        assertFailsWith<IOException> { source.deleteRun(finished.id) }

        assertNotNull(source.getRun(finished.id))
        assertEquals(listOf(finished.id), source.currentSummaries.map { it.id })
    }

    @Test
    fun aFailedFileDeleteKeepsTheRunListed() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = Storage()
        val settings = MapSettings()
        val source = RunRecordDataSource(settings, fileStorage = storage, ioDispatcher = dispatcher)
        source.saveRun(finished)

        storage.deleteFails = true
        assertFailsWith<IOException> { source.deleteRun(finished.id) }

        // As a restart reads it: from the stored index, not from memory.
        val reopened = RunRecordDataSource(settings, fileStorage = storage, ioDispatcher = dispatcher)
        assertEquals(listOf(finished.id), reopened.currentSummaries.map { it.id })
        assertNotNull(reopened.getRun(finished.id))
    }

    @Test
    fun aRunDeletedWhileItsArtifactsAreCheckedStaysDeleted() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val storage = Storage()
        val repository = RunRepositoryImpl(
            RunRecordDataSource(MapSettings(), fileStorage = storage, ioDispatcher = dispatcher),
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher),
        )
        repository.upsert(finished)

        repository.refreshPresence(finished.workspaceId) {
            repository.deleteRun(finished.id)
            Presence.MISSING
        }

        assertNull(repository.getRun(finished.id))
        assertEquals(emptyMap(), storage.runs)
    }
}
