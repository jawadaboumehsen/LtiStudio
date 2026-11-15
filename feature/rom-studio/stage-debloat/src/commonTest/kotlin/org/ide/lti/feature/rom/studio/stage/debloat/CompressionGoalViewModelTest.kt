/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.workbench.CompressionStep
import org.ide.lti.core.domain.workbench.TrialOption
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressionGoalViewModelTest {
    @Test
    fun repeatedUseAndQueuedUndoRestoreTheOriginalOptionsWithoutLosingOtherSettings() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val store = Store()
            val original = store.snapshot.build.copy(partitionCompression = mapOf("system" to "lz4"))
            store.snapshot = store.snapshot.copy(build = original)
            val vm = CompressionGoalViewModel("ws", store = store)
            val step = CompressionStep("system", TrialOption("lzma", "lzma", true, 64), 200, 100, 1.0, 2.0)
            vm.use(listOf(step))
            vm.use(listOf(step))
            runCurrent()
            assertEquals("lzma", store.snapshot.build.partitionCompression["system"])
            store.snapshot = store.snapshot.copy(build = store.snapshot.build.copy(brotliLevel = 9))
            vm.undo()
            runCurrent()
            assertEquals(original.copy(brotliLevel = 9), store.snapshot.build)
            assertTrue(vm.state.value.changed.isEmpty())

            // Undo is captured after a queued Use has committed, not before the coroutine starts.
            vm.use(listOf(step))
            vm.undo()
            runCurrent()
            assertEquals(original.copy(brotliLevel = 9), store.snapshot.build)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class Store : WorkspaceSettingsStore {
        var snapshot = ConfigurationSnapshot.create(
            id = "snap",
            workspaceId = "ws",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
                firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
            ),
            createdAt = Clock.System.now(),
        )
        override suspend fun current(workspaceId: String) = snapshot
        override suspend fun device(workspaceId: String) = null
        override suspend fun update(
            workspaceId: String,
            change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
        ): Result<ConfigurationSnapshot> = Result.success(change(snapshot).also { snapshot = it })
    }
}
