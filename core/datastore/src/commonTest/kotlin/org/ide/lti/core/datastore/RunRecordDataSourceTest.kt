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
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.run.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunRecordDataSourceTest {

    private class InMemoryRunFileStorage : RunFileStorage {
        val runs = mutableMapOf<String, String>()
        val events = mutableMapOf<String, MutableList<String>>()

        override suspend fun saveRun(runId: String, json: String) {
            runs[runId] = json
        }

        override suspend fun getRun(runId: String): String? = runs[runId]

        override suspend fun appendEvents(runId: String, events: List<String>) {
            this.events.getOrPut(runId) { mutableListOf() }.addAll(events)
        }

        override suspend fun getEvents(runId: String, fromSeq: Long): List<String> {
            val list = this.events[runId] ?: return emptyList()
            return if (fromSeq <= 0L) list else list.drop(fromSeq.toInt().coerceAtLeast(0))
        }

        override suspend fun deleteRun(runId: String) {
            runs.remove(runId)
            events.remove(runId)
        }
    }

    private fun sampleRun(
        id: String,
        startedEpochSec: Long = 1700000000L,
        state: RunState = RunState.SUCCEEDED,
    ): BuildRun {
        return BuildRun(
            id = id,
            workspaceId = "ws-test-1",
            snapshotId = "snap-test-1",
            state = state,
            startedAt = Instant.fromEpochSeconds(startedEpochSec),
            endedAt = Instant.fromEpochSeconds(startedEpochSec + 120),
            stages = listOf(
                StageOutcome(stageId = StageId.BUILD_FLASHABLE_ZIP, state = StageState.EXECUTED),
            ),
            steps = listOf(
                StageStep(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    index = 0,
                    idempotencyKey = "key-$id-0",
                    command = StepCommand(toolId = "build", args = listOf("all")),
                    state = StepState.COMPLETED,
                    exitCode = 0,
                ),
            ),
            artifacts = listOf(
                Artifact(
                    kind = ArtifactKind.FLASHABLE_ZIP,
                    linuxPath = "/tmp/payload.zip",
                    sizeBytes = 1048576L,
                    sha256 = "abc123sha",
                    verification = VerificationState.VERIFIED,
                    presence = Presence.PRESENT,
                ),
            ),
        )
    }

    @Test
    fun testIndexRoundTripAndWriteThrough() = runTest {
        val settings = MapSettings()
        val fileStorage = InMemoryRunFileStorage()
        val dataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val run1 = sampleRun("run-1", startedEpochSec = 1700000100L)
        val run2 = sampleRun("run-2", startedEpochSec = 1700000200L)

        dataSource.saveRun(run1)
        dataSource.saveRun(run2)

        // Settings index round-trip check
        val rawIndex = settings.getStringOrNull(RunRecordDataSource.KEY_RUNS)
        assertNotNull(rawIndex)
        assertTrue(rawIndex.contains("run-1"))
        assertTrue(rawIndex.contains("run-2"))

        // Index order is newest first
        val summaries = dataSource.runsIndex.first()
        assertEquals(2, summaries.size)
        assertEquals("run-2", summaries[0].id)
        assertEquals("run-1", summaries[1].id)

        // Write-through to file storage
        assertNotNull(fileStorage.runs["run-1"])
        assertNotNull(fileStorage.runs["run-2"])

        // Full run loaded from file storage
        val loadedRun1 = dataSource.getRun("run-1")
        assertNotNull(loadedRun1)
        assertEquals("run-1", loadedRun1.id)
        assertEquals("ws-test-1", loadedRun1.workspaceId)
        assertEquals(1, loadedRun1.artifacts.size)
        assertEquals(ArtifactKind.FLASHABLE_ZIP, loadedRun1.artifacts[0].kind)
    }

    @Test
    fun testAppendAndRetrieveEvents() = runTest {
        val settings = MapSettings()
        val fileStorage = InMemoryRunFileStorage()
        val dataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val eventsBatch1 = listOf("{\"seq\":1,\"msg\":\"Starting build\"}", "{\"seq\":2,\"msg\":\"Extracting\"}")
        val eventsBatch2 = listOf("{\"seq\":3,\"msg\":\"Finished build\"}")

        dataSource.appendEvents("run-1", eventsBatch1)
        dataSource.appendEvents("run-1", eventsBatch2)

        val allEvents = dataSource.getEvents("run-1")
        assertEquals(3, allEvents.size)
        assertEquals("{\"seq\":1,\"msg\":\"Starting build\"}", allEvents[0])
        assertEquals("{\"seq\":3,\"msg\":\"Finished build\"}", allEvents[2])

        val droppedEvents = dataSource.getEvents("run-1", fromSeq = 2L)
        assertEquals(1, droppedEvents.size)
        assertEquals("{\"seq\":3,\"msg\":\"Finished build\"}", droppedEvents[0])
    }

    @Test
    fun testDeleteRunRemovesFromIndexAndFileStorage() = runTest {
        val settings = MapSettings()
        val fileStorage = InMemoryRunFileStorage()
        val dataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val run1 = sampleRun("run-1")
        dataSource.saveRun(run1)
        dataSource.appendEvents("run-1", listOf("event1"))

        assertEquals(1, dataSource.currentSummaries.size)
        assertNotNull(fileStorage.runs["run-1"])
        assertNotNull(fileStorage.events["run-1"])

        dataSource.deleteRun("run-1")

        assertEquals(0, dataSource.currentSummaries.size)
        assertNull(fileStorage.runs["run-1"])
        assertNull(fileStorage.events["run-1"])
        assertNull(dataSource.getRun("run-1"))
    }

    @Test
    fun testCorruptRunsIndexTriggersQuarantineAndRecovers() = runTest {
        val settings = MapSettings()
        val fileStorage = InMemoryRunFileStorage()
        val corruptJson = "NOT_VALID_JSON{bad[data"
        settings.putString(RunRecordDataSource.KEY_RUNS, corruptJson)

        val dataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        // Loading should not throw exception and should return empty list
        val summaries = dataSource.currentSummaries
        assertTrue(summaries.isEmpty())

        // Corrupt content moved to quarantine key
        val quarantineKeys = settings.keys.filter { it.startsWith("${RunRecordDataSource.KEY_RUNS}.quarantine.") }
        assertEquals(1, quarantineKeys.size)
        assertEquals(corruptJson, settings.getStringOrNull(quarantineKeys.first()))
    }

    @Test
    fun testReadableWithTransportAbsent() = runTest {
        // Simulates total disconnection from daemon/network/transport:
        // dataSource reads from local storage without external ports or dependencies.
        val settings = MapSettings()
        val fileStorage = InMemoryRunFileStorage()
        val dataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val run = sampleRun("run-offline-1")
        dataSource.saveRun(run)
        dataSource.appendEvents("run-offline-1", listOf("{\"seq\":1,\"type\":\"STDOUT\"}"))

        // Re-instantiate data source to simulate app restart in offline mode
        val newDataSource = RunRecordDataSource(
            settings = settings,
            fileStorage = fileStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )

        val runs = newDataSource.runsIndex.first()
        assertEquals(1, runs.size)
        assertEquals("run-offline-1", runs[0].id)

        val loadedRun = newDataSource.getRun("run-offline-1")
        assertNotNull(loadedRun)
        assertEquals(RunState.SUCCEEDED, loadedRun.state)

        val events = newDataSource.getEvents("run-offline-1")
        assertEquals(1, events.size)
    }
}
