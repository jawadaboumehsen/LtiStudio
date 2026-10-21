/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.ReleaseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspacePendingEditsTest {
    private class Store : WorkspaceSettingsStore {
        var snapshot = PipelineFixture().snapshotGlobalOta
        var readGate: CompletableDeferred<Unit>? = null
        var writeGate: CompletableDeferred<Unit>? = null
        var fail = false
        var writes = 0
        override val changes = MutableSharedFlow<String>(extraBufferCapacity = 4)
        override val pendingEdits = PendingWorkspaceEdits(::update)
        override suspend fun current(workspaceId: String): ConfigurationSnapshot {
            val captured = snapshot
            readGate?.await()
            return captured
        }
        override suspend fun device(workspaceId: String) = null
        override suspend fun update(
            workspaceId: String,
            change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
        ): Result<ConfigurationSnapshot> {
            writeGate?.await()
            if (fail) return Result.failure(IllegalStateException("Cannot save"))
            snapshot = change(snapshot)
            writes++
            changes.emit(workspaceId)
            return Result.success(snapshot)
        }
    }

    @Test
    fun delayedLoadsNeverReplaceAnEditMadeWhileTheyWereReading() = runTest {
        for (refresh in listOf(false, true)) {
            val store = Store()
            if (!refresh) store.readGate = CompletableDeferred()
            val sync = StageSettingsSync(
                backgroundScope,
                "ws",
                store,
                ReleaseSettings(),
                { it.release },
                { snapshot, value -> snapshot.copy(release = value) },
            )
            runCurrent()
            if (refresh) {
                store.readGate = CompletableDeferred()
                store.changes.emit("ws")
                runCurrent()
            }
            val edited = ReleaseSettings(otaBaseUrl = "https://edited.example.org")
            sync.update(edited)
            store.readGate!!.complete(Unit)
            runCurrent()
            assertEquals(edited, sync.settings.value)
            store.flush("ws").getOrThrow()
            assertEquals(edited, store.snapshot.release)
        }
    }

    @Test
    fun flushSavesAllEditorsBeforeTheDebounceWithoutDuplicateWrites() = runTest {
        val store = Store()
        val first = StageSettingsSync(
            backgroundScope,
            "ws",
            store,
            ReleaseSettings(),
            { it.release },
            { snapshot, value -> snapshot.copy(release = value) },
        )
        val second = StageSettingsSync(
            backgroundScope,
            "ws",
            store,
            "",
            { it.assembly.romVersion },
            { snapshot, value -> snapshot.copy(assembly = snapshot.assembly.copy(romVersion = value)) },
        )
        runCurrent()
        first.update(ReleaseSettings(otaBaseUrl = "https://new.example.org"))
        second.update("2.0.0")
        assertEquals(0, store.writes)
        store.flush("ws").getOrThrow()
        assertEquals("2.0.0", store.snapshot.assembly.romVersion)
        assertEquals("https://new.example.org", store.snapshot.release.otaBaseUrl)
        advanceTimeBy(601)
        runCurrent()
        assertEquals(2, store.writes)
    }

    @Test
    fun typingDuringACommitDoesNotCancelItAndFlushKeepsTheNewestEdit() = runTest {
        val store = Store()
        val sync = StageSettingsSync(
            backgroundScope,
            "ws",
            store,
            ReleaseSettings(),
            { it.release },
            { snapshot, value -> snapshot.copy(release = value) },
        )
        runCurrent()
        store.writeGate = CompletableDeferred()
        sync.update(ReleaseSettings(otaBaseUrl = "https://first.example.org"))
        advanceTimeBy(601)
        runCurrent()
        sync.update(ReleaseSettings(otaBaseUrl = "https://second.example.org"))
        val flushed = async { store.flush("ws").getOrThrow() }
        runCurrent()
        assertFalse(flushed.isCompleted)
        store.writeGate!!.complete(Unit)
        flushed.await()
        assertEquals(2, store.writes, "the first commit finishes before the second one starts")
        assertEquals("https://second.example.org", store.snapshot.release.otaBaseUrl)
    }

    @Test
    fun reopeningAnEditorShowsItsPendingValueAndKeepsOnlyItsLatestDraft() = runTest {
        val store = Store()
        fun editor() = StageSettingsSync(
            backgroundScope,
            "ws",
            store,
            ReleaseSettings(),
            { it.release },
            { snapshot, value -> snapshot.copy(release = value) },
        )
        val first = editor()
        runCurrent()
        first.update(ReleaseSettings(otaBaseUrl = "https://first.example.org"))
        val reopened = editor()
        runCurrent()
        assertEquals(first.settings.value, reopened.settings.value)
        reopened.update(ReleaseSettings(otaBaseUrl = "https://latest.example.org"))
        store.flush("ws").getOrThrow()
        assertEquals(1, store.writes)
        assertEquals("https://latest.example.org", store.snapshot.release.otaBaseUrl)
        advanceTimeBy(601)
        runCurrent()
        assertEquals(1, store.writes)
    }

    @Test
    fun aFailedFlushRetainsEditsForRetry() = runTest {
        val store = Store()
        val sync = StageSettingsSync(
            backgroundScope,
            "ws",
            store,
            ReleaseSettings(),
            { it.release },
            { snapshot, value -> snapshot.copy(release = value) },
        )
        runCurrent()
        sync.update(ReleaseSettings(otaBaseUrl = "https://retry.example.org"))
        store.fail = true
        assertTrue(store.flush("ws").isFailure)
        store.fail = false
        store.flush("ws").getOrThrow()
        assertEquals("https://retry.example.org", store.snapshot.release.otaBaseUrl)
    }
}
