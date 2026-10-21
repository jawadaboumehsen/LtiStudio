/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.snapshot

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import org.ide.lti.core.data.repository.workspace.WorkDirDiscoveryService
import org.ide.lti.core.data.repository.workspace.WorkspaceRepositoryImpl
import org.ide.lti.core.datastore.SnapshotPreferencesDataSource
import org.ide.lti.core.datastore.WorkspacePreferencesDataSource
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.ide.lti.core.model.workspace.SigningPolicy
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SnapshotFolderFallbackTest {
    private class Folders(val files: MutableMap<String, String>) : PipelineExecutionPort {
        override suspend fun readFile(ws: Workspace, relPath: String): String? = files["${ws.linuxPath}/$relPath"]
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = false
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, exists = false)
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private val device = DefaultTargetCatalog.PQ84P01_DEFAULT
    private val saved = ConfigurationSnapshot.create(
        id = "snap-7",
        workspaceId = "ws-1",
        profileRevision = 1,
        acquisition = AcquisitionSettings(
            region = device.availableRegions.first(),
            firmware = device.availableFirmwares.getValue(device.availableRegions.first()).first(),
        ),
        build = BuildSettings(signing = SigningPolicy()),
        assembly = AssemblySettings(buildType = "user", romVersion = "1.0"),
        release = ReleaseSettings(otaBaseUrl = "https://example.com/ota", channel = "beta"),
        createdAt = Clock.System.now(),
    )

    private fun encode(snapshot: ConfigurationSnapshot) = ConfigurationSnapshot.canonicalJson.encodeToString(snapshot)

    private suspend fun repository(files: MutableMap<String, String>): SnapshotRepositoryImpl {
        val workspaces = WorkspaceRepositoryImpl(
            preferencesDataSource = WorkspacePreferencesDataSource(
                MapSettings(),
                ioDispatcher = Dispatchers.Unconfined,
            ),
            discoveryService = WorkDirDiscoveryService(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        workspaces.saveWorkspace(
            Workspace(id = "ws-1", name = "One", path = "/w/one", linuxPath = "/w/one", effectiveSnapshotId = "snap-7"),
        )
        // A fresh, empty app store: what is left after a reinstall, or after the store failed to keep it.
        val appStore = SnapshotPreferencesDataSource(MapSettings(), ioDispatcher = Dispatchers.Unconfined)
        return SnapshotRepositoryImpl(appStore, workspaces, Folders(files))
    }

    @Test
    fun `a configuration the app lost is read back from the workspace folder`() = runTest {
        val repo = repository(mutableMapOf(CONFIG to encode(saved)))

        assertEquals(saved, repo.getSnapshot("snap-7"))
        assertEquals("beta", repo.getSnapshot("snap-7")?.release?.channel)
    }

    @Test
    fun `an older config json is not mistaken for the snapshot the workspace points at`() = runTest {
        val older = saved.copy(id = "snap-6")
        val repo = repository(mutableMapOf(CONFIG to encode(older)))

        assertNull(repo.getSnapshot("snap-7"))
    }

    @Test
    fun `no folder copy and no app copy is no snapshot`() = runTest {
        assertNull(repository(mutableMapOf()).getSnapshot("snap-7"))
    }
}

private const val CONFIG = "/w/one/config.json"
