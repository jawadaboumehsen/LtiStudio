/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.FirmwareArchives
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AcquireArchivesTest {
    /** The service: copies succeed unless [copyError] is set; `find` lists [listed]; `xxd` dumps [headers]. */
    private class Service : PipelineExecutionPort {
        var copyError: String? = null
        var listed = ""

        /** First bytes (hex) of each file, by name. */
        var headers = mapOf<String, String>()
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = when {
            command.toolId == "cp" && copyError != null -> ToolResult(1, "", copyError!!)
            command.toolId == "find" -> ToolResult(0, listed)
            command.toolId == "xxd" -> ToolResult(0, headers[command.args.last().substringAfterLast('/')].orEmpty())
            else -> ToolResult(0, "")
        }
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, exists = true, sizeBytes = 7L)
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle =
            error("unused")
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = emptyFlow()
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean = false
        override suspend fun readFile(ws: Workspace, relPath: String): String? = null
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private object Workspaces : WorkspaceRepository {
        private val ws = Workspace(id = "ws", name = "ws", path = "/w", linuxPath = "/w")
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(listOf(ws))
        override suspend fun getWorkspace(id: String): Workspace? = ws.takeIf { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private val service = Service()

    private fun viewModel(picked: String? = "C:\\fw\\new.zip") = AcquireViewModel(
        workspaceId = "ws",
        archives = FirmwareArchives(Workspaces, service) { it.replace("C:\\", "/mnt/c/").replace('\\', '/') },
        pickFile = { picked },
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `importing an archive selects it for the build`() = runTest {
        service.listed = "7\tnew.zip\n"
        val vm = viewModel()

        vm.importArchive()

        assertEquals(AcquisitionMode.IMPORT_ARCHIVE, vm.settings.value.mode)
        assertEquals("firmware/downloaded/new.zip", vm.settings.value.archiveRef)
        assertEquals(listOf("new.zip"), vm.archiveState.value.archives.map { it.name })
        assertFalse(vm.archiveState.value.busy)
    }

    @Test
    fun `a failed import is shown and changes nothing`() = runTest {
        service.copyError = "cp: cannot stat '/mnt/c/fw/new.zip'"
        val vm = viewModel()

        vm.importArchive()

        assertEquals(AcquisitionMode.DOWNLOAD, vm.settings.value.mode)
        assertTrue("cannot stat" in vm.archiveState.value.error.orEmpty())
        assertFalse(vm.archiveState.value.busy)
    }

    @Test
    fun `cancelling the file picker does nothing`() = runTest {
        val vm = viewModel(picked = null)
        vm.importArchive()
        assertEquals(AcquisitionMode.DOWNLOAD, vm.settings.value.mode)
        assertFalse(vm.archiveState.value.probed)
    }

    @Test
    fun `probe lists what is in the workspace`() = runTest {
        service.listed = "1\ta.zip\n2\tb.zip\n"
        val vm = viewModel()
        vm.probe()
        assertEquals(listOf("a.zip", "b.zip"), vm.archiveState.value.archives.map { it.name })
        assertTrue(vm.archiveState.value.probed)
    }

    @Test
    fun `importing an archive detects its format from its first bytes`() = runTest {
        service.listed = "1048576\tupdate.bin\n"
        // "CrAU": a payload, whatever the file is called.
        service.headers = mapOf("update.bin" to "4372415500000000000000020000000000")
        val vm = viewModel(picked = "C:\\fw\\update.bin")

        vm.importArchive()

        assertEquals(AcquisitionMode.IMPORT_ARCHIVE, vm.settings.value.mode)
        assertEquals(PackageFormat.AndroidPayload, vm.archiveState.value.lastDetectedFormat)
    }

    @Test
    fun `probe detects the format from content, not from the name`() = runTest {
        service.listed = "100\tfirmware.tar.md5\n200\tnamed.rar\n300\tnotes.txt\n"
        service.headers = mapOf(
            "firmware.tar.md5" to "00".repeat(TAR_MAGIC_OFFSET) + "7573746172",
            "named.rar" to "504b030414000000",
            "notes.txt" to "68656c6c6f",
        )
        val vm = viewModel()
        vm.probe()

        val formats = vm.archiveState.value.archives.associate { it.name to it.detectedFormat }
        assertEquals(PackageFormat.Tar, formats["firmware.tar.md5"])
        assertEquals(PackageFormat.Zip, formats["named.rar"])
        assertEquals(null, formats["notes.txt"])
    }

    @Test
    fun `selecting an archive shows its detected format`() = runTest {
        service.listed = "200\tota.zip\n"
        service.headers = mapOf("ota.zip" to "504b030414000000")
        val vm = viewModel()
        vm.probe()

        vm.selectArchive(vm.archiveState.value.archives.first())

        assertEquals(PackageFormat.Zip, vm.archiveState.value.lastDetectedFormat)
    }

    private companion object {
        const val TAR_MAGIC_OFFSET = 257
    }
}
