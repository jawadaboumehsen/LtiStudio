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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BuildQueueTest {

    @Test
    fun stopDuringProfileApplyPreventsLaunchAndBlocksReplacementUntilWorkerExits() = runTest {
        val applying = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        val starts = mutableListOf<String>()
        val runs = InMemoryRunRepository()
        val queue = BuildQueue(
            profiles = profiles { id ->
                if (id == "first") {
                    applying.complete(Unit)
                    resume.await()
                }
            },
            start = { id ->
                starts += id
                Result.success(run(id).copy(state = RunState.SUCCEEDED).also { runs.upsert(it) })
            },
            runs = runs,
            scope = backgroundScope,
        )

        queue.start("first", listOf("profile"))
        runCurrent()
        assertTrue(applying.isCompleted)
        queue.stop()
        queue.start("second", listOf("profile"))
        assertEquals("first", queue.state.value.workspaceId)
        resume.complete(Unit)
        runCurrent()

        assertEquals(emptyList(), starts)
        assertEquals("not started", queue.state.value.builds.single().status)
        assertFalse(queue.state.value.running)

        queue.start("second", listOf("profile"))
        runCurrent()
        assertEquals(listOf("second"), starts)
        assertEquals("built", queue.state.value.builds.single().status)
    }

    @Test
    fun stopWhilePreflightRunsPreventsTheCheckedBuildFromStarting() = runTest {
        val entered = CompletableDeferred<Unit>()
        val checked = CompletableDeferred<Unit>()
        var launched = false
        val queue = BuildQueue(
            profiles = profiles(),
            start = {
                launched = true
                Result.success(run(it))
            },
            runs = InMemoryRunRepository(),
            scope = backgroundScope,
            check = {
                entered.complete(Unit)
                checked.await()
                emptyList()
            },
        )

        queue.start("workspace", listOf("profile"))
        runCurrent()
        assertTrue(entered.isCompleted)
        queue.stop()
        checked.complete(Unit)
        runCurrent()

        assertFalse(launched)
        assertFalse(queue.state.value.running)
        assertEquals("not started", queue.state.value.builds.single().status)
    }

    @Test
    fun stopLetsAdmittedBuildFinishWithoutStartingNextProfileOrReplacingQueue() = runTest {
        val starts = mutableListOf<String>()
        val runs = InMemoryRunRepository()
        val queue = BuildQueue(profiles(), { id ->
            starts += id
            Result.success(run(id).also { runs.upsert(it) })
        }, runs, backgroundScope)
        queue.start("first", listOf("profile", "profile"))
        runCurrent()
        assertEquals("building", queue.state.value.builds.first().status)

        queue.stop()
        queue.start("second", listOf("profile"))
        runs.upsert(run("first").copy(state = RunState.SUCCEEDED))
        runCurrent()

        assertEquals(listOf("first"), starts)
        assertEquals("first", queue.state.value.workspaceId)
        assertEquals(listOf("built", "not started"), queue.state.value.builds.map { it.status })
        assertFalse(queue.state.value.running)
    }

    private fun run(id: String) = BuildRun(id, id, "snapshot", startedAt = Instant.fromEpochSeconds(0))

    private fun profiles(onApply: suspend (String) -> Unit = {}): BuildProfiles {
        val saved = PipelineFixture().snapshotGlobalOta
        val profiles = listOf(BuildProfile("profile", Instant.fromEpochSeconds(0), saved))
        val profileStore = object : BuildProfileStore {
            override suspend fun load(workspaceId: String) = profiles
            override suspend fun update(workspaceId: String, change: (List<BuildProfile>) -> List<BuildProfile>) =
                change(profiles)
        }
        val settings = object : WorkspaceSettingsStore {
            override suspend fun current(workspaceId: String) = saved
            override suspend fun device(workspaceId: String): TargetDevice? = null
            override suspend fun update(
                workspaceId: String,
                change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
            ): Result<ConfigurationSnapshot> {
                onApply(workspaceId)
                return Result.success(change(saved))
            }
        }
        return BuildProfiles(profileStore, settings)
    }
}
