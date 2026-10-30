/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

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
import org.ide.lti.core.domain.pipeline.toolkit.AckStore
import org.ide.lti.core.domain.pipeline.toolkit.Acknowledgements
import org.ide.lti.core.domain.pipeline.toolkit.JobLock
import org.ide.lti.core.domain.pipeline.toolkit.JobLockHandle
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore
import org.ide.lti.core.domain.pipeline.toolkit.VbmetaFlag
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workbench.BootWorkbench
import org.ide.lti.core.domain.workbench.RiskyFeature
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AssembleStageEditorTest {

    /** The build service: each command is answered by [answer]; files are [files] (path -> text). */
    private class Service(val answer: (StepCommand) -> ToolResult) : PipelineExecutionPort {
        val ran = mutableListOf<StepCommand>()
        val files = mutableMapOf<String, String>()
        private var next = 0
        private val results = mutableMapOf<String, ToolResult>()

        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle {
            ran += step
            val id = "run-${next++}"
            results[id] = answer(step)
            return RunHandle(id, "RUNNING", 0L)
        }

        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> {
            val result = results.getValue(serverRunId)
            val output = listOfNotNull(
                result.stdout.takeIf { it.isNotEmpty() }?.let { StepEvent.Output(1, it.trimEnd('\n')) },
                result.stderr.takeIf { it.isNotEmpty() }?.let { StepEvent.Output(2, it.trimEnd('\n'), isError = true) },
            )
            return flowOf(*(output + StepEvent.Finished(result.exitCode)).toTypedArray())
        }

        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = true
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
            files[relPath] = bytes.decodeToString()
            return true
        }

        override suspend fun readFile(ws: Workspace, relPath: String): String? = files[relPath]
        override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, exists = relPath in files)
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult = answer(command)
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private object Workspaces : WorkspaceRepository {
        private val ws = Workspace(id = "ws", name = "ws", path = "/ws", linuxPath = "/ws")
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(listOf(ws))
        override suspend fun getWorkspace(id: String): Workspace? = ws.takeIf { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = emptyFlow()
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private class Stores :
        JobLock,
        RestorePointStore,
        AckStore {
        var index = RestoreIndex.Empty
        var acks = Acknowledgements.Empty

        override suspend fun acquire(workspace: Workspace, holderName: String): LockResult = LockResult.Acquired(
            object : JobLockHandle {
                override val holderName = holderName
                override val workspace = workspace
                override suspend fun release() {}
                override fun close() {}
            },
        )

        override suspend fun currentHolder(workspace: Workspace): String? = null
        override suspend fun release(workspace: Workspace, holderName: String?) {}
        override suspend fun readIndex(workspace: Workspace) = index
        override suspend fun writeIndex(workspace: Workspace, index: RestoreIndex) {
            this.index = index
        }

        override suspend fun addRestorePoint(workspace: Workspace, point: RestorePoint) {
            index = index.add(point)
        }

        override suspend fun updateRestorePointState(workspace: Workspace, id: String, state: RestorePointState) {
            index = index.withState(id, state)
        }

        override suspend fun readAcknowledgements(workspace: Workspace) = acks
        override suspend fun acknowledge(
            workspace: Workspace,
            featureId: String,
            acceptedAt: String,
        ): Acknowledgements {
            acks = acks.accept(featureId, acceptedAt)
            return acks
        }

        override suspend fun isAcknowledged(workspace: Workspace, featureId: String) = acks.isAcknowledged(featureId)
    }

    private val stores = Stores()

    private fun viewModel(service: Service): AssembleViewModel {
        val runner = WorkbenchRunner(Workspaces, service, stores, stores, stores)
        return AssembleViewModel(
            workspaceId = "ws",
            boot = BootWorkbench(runner) { it },
            runner = runner,
        )
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = AssembleStageEditor()
        assertEquals(StageId.WORK_TREE_ASSEMBLY, editor.stageId)
        assertEquals(CanonicalStageDescriptors.ASSEMBLE, editor.descriptor)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.ASSEMBLE.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = AssembleViewModel(workspaceId = "test-ws")
        assertEquals("user", vm.settings.value.buildType)
        vm.updateSettings(vm.settings.value.copy(buildType = "userdebug"))
        assertEquals("userdebug", vm.settings.value.buildType)
    }

    @Test
    fun `vbmeta flags are read, acknowledged once, and written as a copy in out toolkit`() = runTest {
        val service = Service { cmd ->
            when {
                cmd.toolId == "xxd" && "-s" !in cmd.args -> ToolResult(0, "41564230\n", "")
                cmd.toolId == "xxd" -> ToolResult(0, "00000000\n", "")
                else -> ToolResult(0, "", "")
            }
        }
        service.files[BootWorkbench.VBMETA] = "binary"
        val vm = viewModel(service)

        vm.bootPane.actions.loadVbmeta()
        assertEquals(0, vm.bootPane.state.value.vbmetaFlags)
        vm.bootPane.actions.toggleVbmeta(VbmetaFlag.DISABLE_VERITY)
        vm.bootPane.actions.toggleVbmeta(VbmetaFlag.DISABLE_VERIFICATION)
        assertEquals(
            VbmetaFlag.DISABLE_VERITY.bitMask or VbmetaFlag.DISABLE_VERIFICATION.bitMask,
            vm.bootPane.state.value.vbmetaEdited,
        )

        vm.bootPane.actions.writeVbmeta()
        assertEquals(RiskyFeature.VBMETA_FLAGS, vm.bootPane.state.value.pendingAck)
        assertTrue(service.ran.none { it.toolId == "sh" }, "nothing is written before the acknowledgement")

        vm.bootPane.actions.acknowledge(RiskyFeature.VBMETA_FLAGS)
        val script = service.ran.filter { it.toolId == "sh" }.joinToString("\n") { it.args.joinToString(" ") }
        assertTrue("seek=120" in script && "\\000\\000\\000\\003" in script, script)
        assertTrue("out/toolkit/vbmeta.img" in vm.bootPane.state.value.message.orEmpty())
        assertTrue(stores.acks.isAcknowledged(RiskyFeature.VBMETA_FLAGS))
    }

    @Test
    fun `a repack before unpacking is refused and records no override`() = runTest {
        stores.acks = stores.acks.accept(RiskyFeature.KERNEL_OVERRIDE, "now")
        val vm = viewModel(Service { ToolResult(0, "", "") })
        vm.bootPane.actions.setSign(false)

        vm.bootPane.actions.repack()

        assertEquals("The image is not unpacked.", vm.bootPane.state.value.refusal?.cause)
        assertTrue(vm.settings.value.kernelOverrides.isEmpty())
    }

    @Test
    fun `a ramdisk that does not match its folder after packing is refused`() = runTest {
        stores.acks = stores.acks.accept(RiskyFeature.KERNEL_OVERRIDE, "now")
        val service = Service { cmd ->
            val script = cmd.args.firstOrNull { "cpio -it" in it }
            when {
                cmd.toolId == "sh" && script != null -> ToolResult(0, "init\nsystem\n", "")
                cmd.toolId == "find" && "-mindepth" in cmd.args -> ToolResult(0, "init\nsystem\nadded.rc\n", "")
                cmd.toolId == "sh" && "tools.d" in cmd.args.first { it != "-c" } -> ToolResult(0, "/bin/mkbootfs\n", "")
                else -> ToolResult(0, "", "")
            }
        }
        service.files["workbench/boot/boot/state.json"] =
            """{"source":"firmware/extracted/boot.img","ramdisk":"ramdisk","compression":"LZ4_LEGACY","footerImageSize":null}"""
        val vm = viewModel(service)
        vm.bootPane.actions.setSign(false)

        vm.bootPane.actions.repack()

        assertEquals("The rebuilt ramdisk does not match the folder.", vm.bootPane.state.value.refusal?.cause)
        assertTrue(vm.settings.value.kernelOverrides.isEmpty())
        assertTrue(service.ran.none { it.toolId == "mkbootimg" }, "no image is built from a bad ramdisk")
    }

    @Test
    fun `switching back to the stock image drops the override from the settings`() {
        val vm = AssembleViewModel(
            workspaceId = "ws",
            initialSettings = AssemblySettings(kernelOverrides = mapOf("boot" to "abc")),
        )
        vm.bootPane.actions.dropOverride("boot")
        assertNull(vm.settings.value.kernelOverrides["boot"])
    }
}
