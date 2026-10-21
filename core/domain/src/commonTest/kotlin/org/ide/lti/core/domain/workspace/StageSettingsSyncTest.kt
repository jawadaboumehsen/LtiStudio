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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.ReleaseSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageSettingsSyncTest {
    private val fixture = PipelineFixture()

    /** Records what each save asked for, applied to a running "current" snapshot. */
    private class Saves(var current: ConfigurationSnapshot) {
        var count = 0
        fun apply(change: (ConfigurationSnapshot) -> ConfigurationSnapshot): Result<ConfigurationSnapshot> {
            count++
            current = change(current)
            return Result.success(current)
        }
    }

    private fun TestScope.sync(saves: Saves?, workspaceId: String = "ws_test_01"): StageSettingsSync<ReleaseSettings> {
        val store = saves?.let { s ->
            object : WorkspaceSettingsStore {
                override suspend fun current(workspaceId: String) = s.current
                override suspend fun update(
                    workspaceId: String,
                    change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
                ) = s.apply(change)
                override suspend fun device(workspaceId: String) = fixture.targetGlobal
            }
        }
        return StageSettingsSync(
            scope = this,
            workspaceId = workspaceId,
            store = store,
            initial = ReleaseSettings(),
            read = { it.release },
            write = { snapshot, value -> snapshot.copy(release = value) },
        )
    }

    @Test
    fun `the editor opens with what the workspace saved and its own device`() = runTest {
        val saves = Saves(fixture.snapshotGlobalOta)
        val sync = sync(saves)
        advanceUntilIdle()

        assertEquals(fixture.snapshotGlobalOta.release, sync.settings.value)
        assertEquals(fixture.targetGlobal, sync.device.value)
    }

    @Test
    fun `edits are saved once typing pauses, changing only this stage's group`() = runTest {
        val saves = Saves(fixture.snapshotGlobalOta)
        val sync = sync(saves)
        advanceUntilIdle()

        sync.update(ReleaseSettings(otaBaseUrl = "https://a.example.org"))
        advanceTimeBy(100)
        sync.update(ReleaseSettings(otaBaseUrl = "https://b.example.org"))
        advanceUntilIdle()

        assertEquals(1, saves.count, "quick successive edits make one save")
        assertEquals("https://b.example.org", saves.current.release.otaBaseUrl)
        assertEquals(fixture.snapshotGlobalOta.acquisition, saves.current.acquisition, "other groups untouched")
        assertNull(sync.saveError.value)
    }

    @Test
    fun `a change another writer saves is shown, unless an edit here is waiting to be saved`() = runTest {
        val saves = Saves(fixture.snapshotGlobalOta)
        val changes = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val store = object : WorkspaceSettingsStore {
            override val changes = changes
            override suspend fun current(workspaceId: String) = saves.current
            override suspend fun update(
                workspaceId: String,
                change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
            ) = saves.apply(change)
            override suspend fun device(workspaceId: String) = fixture.targetGlobal
        }
        val sync = StageSettingsSync(
            scope = backgroundScope,
            workspaceId = "ws_test_01",
            store = store,
            initial = ReleaseSettings(),
            read = { it.release },
            write = { snapshot, value -> snapshot.copy(release = value) },
        )
        runCurrent()

        // A plugin update (say) rewrites the workspace elsewhere.
        saves.current = saves.current.copy(release = ReleaseSettings(otaBaseUrl = "https://moved.example.org"))
        changes.emit("another_ws")
        runCurrent()
        assertEquals(fixture.snapshotGlobalOta.release, sync.settings.value, "another workspace's change is ignored")
        changes.emit("ws_test_01")
        runCurrent()
        assertEquals("https://moved.example.org", sync.settings.value.otaBaseUrl)

        sync.update(ReleaseSettings(otaBaseUrl = "https://typing.example.org"))
        changes.emit("ws_test_01")
        advanceTimeBy(100)
        assertEquals("https://typing.example.org", sync.settings.value.otaBaseUrl, "the pending edit wins")
    }

    @Test
    fun `without a store or workspace the settings stay local`() = runTest {
        val sync = sync(null)
        sync.update(ReleaseSettings(otaBaseUrl = "https://local.example.org"))
        advanceUntilIdle()
        assertEquals("https://local.example.org", sync.settings.value.otaBaseUrl)
    }
}
