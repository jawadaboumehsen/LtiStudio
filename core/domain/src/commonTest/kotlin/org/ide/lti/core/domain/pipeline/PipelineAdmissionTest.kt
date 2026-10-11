/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PipelineAdmissionTest {
    private val fixture = PipelineFixture()

    @Test
    fun emptyPinsNeverExecuteManagedCommands() = runTest {
        val port = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
        port.archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
        val repo = InMemoryRunRepository()
        val coordinator = ToolUsageCoordinator()
        val orchestrator =
            PipelineOrchestrator(
                port,
                repo,
                FakeVendoredFiles(true),
                toolStore = PipelinePinnedToolStore(PinnedTools(emptyMap(), emptyMap())),
                toolUsageCoordinator = coordinator,
            )
        assertFailsWith<IllegalStateException> {
            orchestrator.drive("empty-pins", fixture.globalOtaContext(), "driver").toList()
        }
        assertFalse(port.submittedCommands.any { it.toolId == "payload-dumper-go" })
        assertFalse(coordinator.isJobActive)
    }

    @Test
    fun relativeAndCurrentPinsNeverExecuteManagedCommands() = runTest {
        for (path in listOf("payload-dumper-go", "/tools/bin/payload-dumper-go/current/payload-dumper-go")) {
            val port = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
            port.archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
            val pins = PinnedTools(mapOf("payload-dumper-go" to path), emptyMap())
            val orchestrator =
                PipelineOrchestrator(
                    port,
                    InMemoryRunRepository(),
                    FakeVendoredFiles(true),
                    toolStore = PipelinePinnedToolStore(pins),
                )
            assertFailsWith<IllegalStateException> {
                orchestrator.drive("invalid-pin", fixture.globalOtaContext(), "driver").toList()
            }
            assertFalse(port.submittedCommands.any { it.toolId == path })
        }
    }

    @Test
    fun immutablePinsAreUsedForManagedCommands() = runTest {
        val port = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
        port.archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
        val events =
            PipelineOrchestrator(
                port,
                InMemoryRunRepository(),
                FakeVendoredFiles(true),
                toolStore = PipelinePinnedToolStore(),
            ).drive("pinned", fixture.globalOtaContext(), "driver").toList()
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
        assertTrue(
            port.submittedCommands.any {
                it.toolId == PipelinePinnedToolStore.defaultPins.paths.getValue("payload-dumper-go")
            },
        )
        assertFalse(port.submittedCommands.any { it.toolId in PipelinePinnedToolStore.defaultPins.paths })
    }

    @Test
    fun initializationFailuresReleaseAdmissionForDriveAndResume() = runTest {
        for (resume in listOf(false, true)) {
            for (failurePoint in listOf("upsert", "claim", "persist")) {
                // Upsert is fresh-drive-only; resumed runs already exist.
                if (resume && failurePoint == "upsert") continue
                for (cancel in listOf(false, true)) {
                    val coordinator = ToolUsageCoordinator()
                    val base = InMemoryRunRepository()
                    val run =
                        BuildRun(
                            id = "initialization",
                            workspaceId = fixture.workspace.id,
                            snapshotId = fixture.snapshotGlobalOta.id,
                            state = RunState.PAUSED_WAITING_FOR_APP,
                            startedAt = Clock.System.now(),
                            pinnedTools = PipelinePinnedToolStore.defaultPins.paths,
                            toolVersions = PipelinePinnedToolStore.defaultPins.versions,
                        )
                    if (resume) base.upsert(run)

                    fun fail(): Nothing = if (cancel) {
                        throw CancellationException("initialization")
                    } else {
                        error("initialization")
                    }
                    var upserts = 0
                    val repo =
                        object : RunRepository by base {
                            override suspend fun upsert(run: BuildRun) {
                                upserts++
                                if (failurePoint == "upsert" ||
                                    (failurePoint == "persist" && upserts >= (if (resume) 1 else 2))
                                ) {
                                    fail()
                                }
                                base.upsert(run)
                            }

                            override suspend fun claimDriver(runId: String, instanceId: String, now: Long): Boolean {
                                if (failurePoint == "claim") fail()
                                return base.claimDriver(runId, instanceId, now)
                            }
                        }
                    val port = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
                    val orchestrator =
                        PipelineOrchestrator(
                            port,
                            repo,
                            FakeVendoredFiles(true),
                            toolStore = PipelinePinnedToolStore(),
                            toolUsageCoordinator = coordinator,
                        )
                    val error =
                        try {
                            if (resume) {
                                orchestrator.resume(run, fixture.globalOtaContext(), "driver").toList()
                            } else {
                                orchestrator.drive(run.id, fixture.globalOtaContext(), "driver").toList()
                            }
                            null
                        } catch (e: Exception) {
                            e
                        }
                    if (cancel) assertIs<CancellationException>(error) else assertIs<IllegalStateException>(error)
                    assertFalse(coordinator.isJobActive, "$resume $failurePoint $cancel")
                    assertTrue(coordinator.withToolMutationAdmission { Unit }.isSuccess)
                }
            }
        }
    }

    @Test
    fun rejectedDuplicateDoesNotReleaseOriginalAdmission() = runTest {
        val coordinator = ToolUsageCoordinator()
        val entered = CompletableDeferred<Unit>()
        val base = InMemoryRunRepository()
        val repo =
            object : RunRepository by base {
                override suspend fun upsert(run: BuildRun) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
        val orchestrator =
            PipelineOrchestrator(
                SimulatedExecutionPort(fixture.workspace.linuxPath!!),
                repo,
                FakeVendoredFiles(true),
                toolStore = PipelinePinnedToolStore(),
                toolUsageCoordinator = coordinator,
            )
        val original = launch { orchestrator.drive("duplicate", fixture.globalOtaContext(), "one").toList() }
        entered.await()
        assertFailsWith<IllegalStateException> {
            orchestrator.drive("duplicate", fixture.globalOtaContext(), "two").toList()
        }
        assertTrue(coordinator.isJobActive)
        assertTrue(coordinator.withToolMutationAdmission { Unit }.isFailure)
        original.cancel()
        original.join()
        assertFalse(coordinator.isJobActive)
    }

    @Test
    fun cacheKeyIncludesToolVersionIds() = runTest {
        val paths = PipelinePinnedToolStore.defaultPins.paths
        val pins1 = PinnedTools(paths, paths.mapValues { "v1.8.10" })
        val pins2 = PinnedTools(paths, paths.mapValues { "v1.8.11" })

        val repo1 = InMemoryRunRepository()
        val port1 = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
        port1.archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
        val orch1 =
            PipelineOrchestrator(
                port1,
                repo1,
                FakeVendoredFiles(true),
                toolStore = PipelinePinnedToolStore(pins1),
            )
        val events1 = orch1.drive("run-v1", fixture.globalOtaContext(), "driver-1").toList()
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events1.last())
        val run1 = repo1.getRun("run-v1")!!

        val repo2 = InMemoryRunRepository()
        val port2 = SimulatedExecutionPort(fixture.workspace.linuxPath!!)
        port2.archiveSha256 = fixture.snapshotGlobalOta.acquisition.firmware.sha256
        val orch2 =
            PipelineOrchestrator(
                port2,
                repo2,
                FakeVendoredFiles(true),
                toolStore = PipelinePinnedToolStore(pins2),
            )
        val events2 = orch2.drive("run-v2", fixture.globalOtaContext(), "driver-2").toList()
        val failed2 = events2.filterIsInstance<PipelineEvent.StageFailed>().firstOrNull()
        kotlin.test.assertNull(failed2, "events2 failed with: ${failed2?.stageId}: ${failed2?.message}")
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events2.last())
        val run2 = repo2.getRun("run-v2")!!

        // FIRMWARE_ACQUISITION runs before any managed tools and its cache key is identical
        val acq1 = run1.stages.first { it.stageId == StageId.FIRMWARE_ACQUISITION }
        val acq2 = run2.stages.first { it.stageId == StageId.FIRMWARE_ACQUISITION }
        assertEquals(acq1.cacheKey, acq2.cacheKey, "Stage before managed tools must retain identical cache key")

        // FIRMWARE_EXTRACTION requires payload-dumper-go, so its cache key must differ
        val ext1 = run1.stages.first { it.stageId == StageId.FIRMWARE_EXTRACTION }
        val ext2 = run2.stages.first { it.stageId == StageId.FIRMWARE_EXTRACTION }
        assertNotEquals(ext1.cacheKey, ext2.cacheKey, "Extraction cache key must differ across tool version IDs")
    }
}
