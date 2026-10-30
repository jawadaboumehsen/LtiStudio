/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.pipeline.toolkit.ChecksumResult
import org.ide.lti.core.domain.pipeline.toolkit.FsType
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
import org.ide.lti.core.domain.pipeline.toolkit.PartitionEntry
import org.ide.lti.core.domain.pipeline.toolkit.PartitionImage
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExtractStageEditorTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = ExtractStageEditor()
        assertEquals(StageId.FIRMWARE_EXTRACTION, editor.stageId)
        assertEquals(CanonicalStageDescriptors.EXTRACT, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.EXTRACT.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = ExtractViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals("AUTO", vm.settings.value.adapter)
        vm.updateSettings(vm.settings.value.copy(adapter = "payload-dumper-go"))
        assertEquals("payload-dumper-go", vm.settings.value.adapter)
    }

    @Test
    fun refreshFactsPopulatesDetectedFormatChecksumImagesAndRefusal() = runTest {
        val manifest = PackageManifest(
            format = PackageFormat.AndroidPayload,
            declaredChecksum = "test-hash",
            partitions = listOf(PartitionEntry(name = "system", sizeBytes = 1048576L, sourceMember = "payload.bin")),
            refusal = Refusal(
                cause = "The package is an incremental update.",
                item = "payload.bin",
                nextStep = "Import the full update instead.",
            ),
        )
        val checksum = ChecksumResult(ok = false, expected = "expected-hash", actual = "actual-hash")
        val images = listOf(
            PartitionImage(
                name = "system",
                fsType = FsType.Erofs,
                sizeBytes = 1048576L,
                sparse = false,
                path = "firmware/extracted/system.img",
            ),
        )

        val run = BuildRun(
            id = "run-1",
            workspaceId = "test-ws",
            snapshotId = "s1",
            state = RunState.SUCCEEDED,
            startedAt = Instant.fromEpochMilliseconds(1000L),
            runtimeValues = mapOf(
                RuntimeKeys.PACKAGE_MANIFEST to PackageManifest.encode(manifest),
                RuntimeKeys.PACKAGE_CHECKSUM_RESULT to ChecksumResult.encode(checksum),
                RuntimeKeys.EXTRACT_IMAGES to PartitionImage.encodeList(images),
            ),
        )

        val runRepo = object : RunRepository {
            override fun observeRuns(workspaceId: String): Flow<List<BuildRun>> = flowOf(listOf(run))
            override suspend fun upsert(run: BuildRun) {}
            override suspend fun appendEvents(runId: String, events: List<String>) {}
            override suspend fun cachedEvents(runId: String, fromSeq: Long): List<String> = emptyList()
            override suspend fun latestActive(workspaceId: String): BuildRun? = null
            override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean = true
            override suspend fun heartbeatDriver(runId: String, instanceId: String, now: Long) {}
        }

        val wsRepo = object : WorkspaceRepository {
            val ws = Workspace(id = "test-ws", name = "Test", path = "/tmp/ws", linuxPath = "/tmp/ws")
            override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(listOf(ws))
            override suspend fun getWorkspace(id: String): Workspace? = ws.takeIf { it.id == id }
            override suspend fun saveWorkspace(workspace: Workspace) {}
            override suspend fun deleteWorkspace(id: String) {}
            override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
            override suspend fun addRecentProject(project: RecentProject) {}
            override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
            override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
            override suspend fun removeRecentProject(workspaceId: String) {}
            override suspend fun clearRecentProjects() {}
            override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        }

        val port = object : PipelineExecutionPort {
            override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = ToolResult(0, "")
            override suspend fun stat(ws: Workspace, relPath: String): FileStat = FileStat(relPath, exists = false)
            override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle = error(
                "unused",
            )
            override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
            override suspend fun status(serverRunId: String): StepStatus? = null
            override suspend fun cancel(serverRunId: String): Boolean = false
            override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = false
            override suspend fun readFile(ws: Workspace, relPath: String): String? = null
            override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
        }

        val workspaceFacts = WorkspaceFacts(workspaces = wsRepo, files = port, runs = runRepo)
        val vm = ExtractViewModel(workspaceId = "test-ws", workspaceFacts = workspaceFacts)

        vm.refreshFacts()

        val facts = vm.facts.value
        assertTrue(facts.loaded)
        assertEquals(PackageFormat.AndroidPayload, facts.detectedFormat)
        assertEquals(checksum, facts.checksumResult)
        assertEquals(1, facts.partitionImages.size)
        assertEquals("system", facts.partitionImages.first().name)
        assertEquals(FsType.Erofs, facts.partitionImages.first().fsType)
        assertNotNull(facts.refusal)
        assertEquals("The package is an incremental update.", facts.refusal.cause)
    }
}
