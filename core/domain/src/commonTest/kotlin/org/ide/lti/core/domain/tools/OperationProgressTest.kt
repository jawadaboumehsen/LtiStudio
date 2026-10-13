/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationProgressTest {
    private val op =
        ToolOperation("o", ToolGroupId("g"), OperationKind.Install, null, OperationPhase.Fetching, percent = 0)

    @Test
    fun `a percentage is read from what git, make and ninja print, and only from that`() {
        assertEquals(0.45, OperationProgress.fractionIn("Receiving objects:  45% (900/2000)"))
        assertEquals(1.0, OperationProgress.fractionIn("Resolving deltas: 100% (10/10), done."))
        assertEquals(0.3, OperationProgress.fractionIn("[ 30%] Building CXX object a.o"))
        assertEquals(0.25, OperationProgress.fractionIn("[25/100] Linking CXX executable x"))
        assertEquals(0.6, OperationProgress.fractionIn("[ 10%] a\n[ 60%] b"), "the latest line wins")
        assertNull(OperationProgress.fractionIn("100% of the tests passed"), "a stray percent is not progress")
        assertNull(OperationProgress.fractionIn("Compiling main.c"))
        assertNull(OperationProgress.fractionIn("[0/0] nothing to do"))
    }

    @Test
    fun `output moves the bar inside its phase and never backwards`() {
        val building = OperationProgress.enter(op, OperationPhase.Building)
        assertEquals(20, building.percent, "a phase starts at its own range")

        val half = OperationProgress.onOutput(building, "[ 50%] Building")
        assertEquals(52, half.percent) // 20 + 65 * 0.5

        assertEquals(52, OperationProgress.onOutput(half, "[ 10%] Building").percent, "never backwards")
        assertEquals(52, OperationProgress.onOutput(half, "Compiling main.c").percent, "no percent printed, no move")
    }

    @Test
    fun `later phases ignore a percent in output and start where their range starts`() {
        val building = op.copy(percent = 52, phase = OperationPhase.Building)
        val verifying = OperationProgress.enter(building, OperationPhase.Verifying)
        assertEquals(85, verifying.percent)
        assertEquals(85, OperationProgress.onOutput(verifying, "[ 90%] probe").percent)
        assertEquals(98, OperationProgress.enter(verifying, OperationPhase.Switching).percent)
    }

    @Test
    fun `an operation with no percentage stays without one`() {
        val use = op.copy(kind = OperationKind.Use, phase = OperationPhase.Switching, percent = null)
        assertNull(OperationProgress.enter(use, OperationPhase.Switching).percent)
        assertNull(OperationProgress.onOutput(use, "[ 50%]").percent)
    }

    private class Port : ToolStorePort {
        var afterPhase = CompletableDeferred<Unit>()
        val useGate = CompletableDeferred<Unit>()

        override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> =
            Result.success(ToolInventory(emptyMap()))

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> =
            Result.success(RefListing(emptyList(), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = "1".repeat(40)))

        override fun install(request: InstallRequest): Flow<InstallEvent> = flow {
            emit(InstallEvent.Started(request.opId))
            emit(InstallEvent.Phase(OperationPhase.Building))
            emit(InstallEvent.Output("[ 40%] Building CXX object a.o"))
            afterPhase.await()
            emit(InstallEvent.Phase(OperationPhase.Verifying))
            afterPhase.await()
            emit(InstallEvent.Finished(OperationResult.Installed("v")))
        }

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> {
            useGate.await()
            return Result.success(GroupLinks(versionId, null))
        }

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> =
            Result.success(PinnedTools(emptyMap(), emptyMap()))
    }

    private fun connected(port: Port) = ToolManager(port = port, catalog = ToolGroupCatalog)

    @Test
    fun `an install shows the percentage it has really reached and moves on with its phase`() = runTest {
        val port = Port()
        val manager = connected(port)
        val group = ToolGroupCatalog.EROFS_UTILS
        val source = manager.defaultSourceForGroup(group)

        val job = launch { manager.installAndUse(group.id, source) }
        runCurrent()

        val running = manager.state.value.activeOperations.getValue(group.id)
        assertEquals(OperationPhase.Building, running.phase)
        assertEquals(46, running.percent) // 20 + 65 * 0.4

        port.afterPhase.complete(Unit)
        runCurrent()
        job.join()
        assertTrue(manager.state.value.activeOperations.isEmpty(), "nothing is left running when it ends")
    }

    @Test
    fun `switching to an installed version is a visible operation that blocks a second change`() = runTest {
        val port = Port()
        val manager = connected(port)
        val group = ToolGroupCatalog.EROFS_UTILS.id

        val first = launch { manager.use(group, "v1") }
        runCurrent()

        val running = manager.state.value.activeOperations.getValue(group)
        assertEquals(OperationKind.Use, running.kind)
        assertNull(running.percent, "a switch has no percentage to report")
        val second = manager.use(group, "v2")
        assertIs<OperationResult.Failed>(second)
        assertIs<ToolError.Busy>(second.error)

        port.useGate.complete(Unit)
        runCurrent()
        first.join()
        assertTrue(manager.state.value.activeOperations.isEmpty())
    }
}
