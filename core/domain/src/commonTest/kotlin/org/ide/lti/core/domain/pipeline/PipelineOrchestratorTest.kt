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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("MaxLineLength")
class PipelineOrchestratorTest {
    private val fixture = PipelineFixture()
    private val ws = fixture.workspace.linuxPath!!

    private fun orchestrator(port: SimulatedExecutionPort, repo: InMemoryRunRepository, vendored: Boolean = true) =
        PipelineOrchestrator(port, repo, FakeVendoredFiles(vendored), toolStore = PipelinePinnedToolStore())

    private fun globalPort() = SimulatedExecutionPort(ws).apply {
        archiveSha256 =
            fixture.snapshotGlobalOta.acquisition.firmware.sha256
    }

    private fun chinaPort() = SimulatedExecutionPort(ws).apply {
        archiveSha256 =
            fixture.snapshotChinaRaw.acquisition.firmware.sha256
    }

    @Test
    fun cancellingAHostStepStopsItsWorkAndDoesNotResurrectTheRun() = runTest {
        // Exercise both an in-progress request and CancelRunUseCase's already-terminal result.
        for (completeCancellation in listOf(false, true)) {
            val port = globalPort()
            val repo = InMemoryRunRepository()
            val entered = CompletableDeferred<Unit>()
            val stopped = CompletableDeferred<Unit>()
            val never = CompletableDeferred<String>()
            val request = object : HostRequest {}
            val original = DefaultPipelineStages.all.first()
            val stage = object : StageDefinition by original {
                override fun outputs(ctx: StageContext): List<String> = emptyList()
                override fun plan(ctx: StageContext): List<PipelineStep> = listOf(
                    PipelineStep.Host("Apply modules", "host-result", request),
                    PipelineStep.WriteFile("after-cancel", byteArrayOf(1), "Must not run"),
                )
            }
            val registry = PipelineDefinitionRegistry(
                DefaultPipelineStages.all.associateBy { it.id } + (stage.id to stage),
            )
            val orchestrator = PipelineOrchestrator(
                port,
                repo,
                FakeVendoredFiles(true),
                registry = registry,
                toolStore = PipelinePinnedToolStore(),
                hostSteps = HostStepPort { _, _ ->
                    entered.complete(Unit)
                    try {
                        never.await()
                    } finally {
                        stopped.complete(Unit)
                    }
                },
            )
            val running = async { orchestrator.drive("run-host", fixture.globalOtaContext(), "driver").toList() }
            entered.await()
            if (completeCancellation) {
                CancelRunUseCase(repo, port)(fixture.workspace.id, "run-host").getOrThrow()
            } else {
                repo.upsert(assertNotNull(repo.getRun("run-host")).copy(state = RunState.CANCELLING))
            }
            val events = running.await()
            assertTrue(stopped.isCompleted, "the host coroutine must be cancelled")
            assertEquals(PipelineEvent.RunFinished(RunState.CANCELLED), events.last())
            val run = assertNotNull(repo.getRun("run-host"))
            assertEquals(RunState.CANCELLED, run.state)
            assertTrue(run.artifacts.isEmpty())
            assertFalse("host-result" in run.runtimeValues)
            assertFalse(port.has("after-cancel"))
            assertEquals(listOf(stage.id), events.filterIsInstance<PipelineEvent.StageStarted>().map { it.stageId })
        }
    }

    @Test
    fun fullRunSucceedsWithCapturedValuesAndArtifacts() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "driver-a").toList()

        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
        val started = events.filterIsInstance<PipelineEvent.StageStarted>().map { it.stageId }
        val expectedStages = PipelineDefinitionRegistry().executableStages().map { it.id }
        assertEquals(expectedStages, started)

        val run = assertNotNull(repo.getRun("run-1"))
        assertEquals(RunState.SUCCEEDED, run.state)
        assertTrue(run.stages.all { it.state == StageState.EXECUTED })

        // Captured values drive later steps: the AVB footer uses the real salt and searched size.
        val footer =
            port.durableSteps.first {
                it.toolId == "avbtool" &&
                    it.args[0] == "add_hashtree_footer" &&
                    "--salt" in it.args
            }
        val salt = footer.args[footer.args.indexOf("--salt") + 1]
        assertEquals(SimulatedExecutionPort.fakeDigest("$ws/out/images/odm.img", 64), salt)
        val partitionSize = footer.args[footer.args.indexOf("--partition_size") + 1].toLong()
        assertTrue(partitionSize >= SimulatedExecutionPort.IMAGE_BYTES + SimulatedExecutionPort.AVB_OVERHEAD)
        assertTrue(port.syncCalls.any { "--calc_max_image_size" in it.args })

        // Generated members were uploaded before zipping, with real content.
        val stage = BuildFlashableZipStage.STAGE_DIR
        assertTrue(port.has("$stage/META-INF/com/google/android/updater-script"))
        assertEquals("ELF-updater", port.text("$stage/META-INF/com/google/android/update-binary"))
        assertTrue(port.text("$stage/META-INF/com/android/metadata")!!.contains("post-build=nubia/NX_PQ84P01"))
        assertTrue(port.text("$stage/dynamic_partitions_op_list")!!.contains("resize odm $partitionSize"))
        val zipIndex = port.durableSteps.indexOfFirst { it.toolId == "zip" }
        val uploadBeforeZip = port.durableSteps.take(zipIndex).any { it.toolId == "signapk" }
        assertFalse(uploadBeforeZip)

        // Sidecars came from the dumps, with run-as capabilities applied.
        val fsConfig = assertNotNull(port.text("firmware/extracted/fs_config-system"))
        assertTrue(fsConfig.contains("system/bin/run-as 0 2000 755 capabilities=0xc0"))
        assertTrue(
            assertNotNull(port.text("work/system/system/build.prop")).contains("org.lti.build.version=1.0.0"),
        )

        val manifest = assertNotNull(port.text("out/manifest.json"))
        assertTrue(manifest.contains("https://ota.ltirom.org/updates/Lti_1.0.0_20260911_PQ84P01-sign.zip"))
        assertTrue(
            manifest.contains("\"size\": ${SimulatedExecutionPort.ZIP_BYTES}") ||
                manifest.contains("\"size\":${SimulatedExecutionPort.ZIP_BYTES}"),
        )
        assertEquals(3, run.artifacts.size)
        assertTrue(run.artifacts.all { it.verification == VerificationState.VERIFIED })
        assertEquals(SimulatedExecutionPort.ZIP_BYTES.toLong(), run.artifacts.first().sizeBytes)
    }

    @Test
    fun aStepWhoseStreamEndsWithoutItsResultTakesTheServicesRecord() = runTest {
        val port = globalPort()
        // Fast steps on the real service: the stream can close before the Finished event is read.
        port.streamsWithoutResult += setOf("cp", "rm", "mkdir")
        val orchestrator = orchestrator(port, InMemoryRunRepository())
        val events = orchestrator.drive("run-1", fixture.globalOtaContext(), "d1").toList()
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }

    @Test
    fun aStepKilledByCancelEndsTheRunCancelledNotFailed() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        // What Cancel does while Build & Sign runs: mark the run CANCELLING, and the killed mkfs exits 130.
        port.exitCodes["mkfs.erofs"] = 130
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").onEach { event ->
            if (event is PipelineEvent.StageStarted && event.stageId == StageId.BUILD_FLASHABLE_ZIP) {
                repo.upsert(repo.getRun("run-1")!!.copy(state = RunState.CANCELLING))
            }
        }.toList()

        assertEquals(PipelineEvent.RunFinished(RunState.CANCELLED), events.last())
        assertEquals(RunState.CANCELLED, repo.getRun("run-1")?.state)
    }

    @Test
    fun resumingARunThatIsBeingDrivenDoesNothing() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val orchestrator = orchestrator(port, repo)
        var second: List<PipelineEvent>? = null
        val events = orchestrator.drive("run-1", fixture.globalOtaContext(), "d1").onEach { event ->
            if (second == null && event is PipelineEvent.StageStarted) {
                // What the Run panel does when it sees its own new run as active.
                second = orchestrator.resume(repo.getRun("run-1")!!, fixture.globalOtaContext(), "d2").toList()
            }
        }.toList()

        assertEquals(emptyList(), second)
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }

    @Test
    fun aRunQueuedLikeStartDoesStopsAfterItsThroughStage() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        // StartPipelineRunUseCase persists the run as QUEUED (no tool pins yet) before driving it.
        repo.upsert(
            org.ide.lti.core.model.run.BuildRun(
                id = "run-q",
                workspaceId = fixture.workspace.id,
                snapshotId = fixture.snapshotGlobalOta.id,
                state = RunState.QUEUED,
                startedAt = kotlinx.datetime.Clock.System.now(),
                throughStage = StageId.WORK_TREE_ASSEMBLY,
            ),
        )

        val events = orchestrator(port, repo).drive("run-q", fixture.globalOtaContext(), "d1").toList()

        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
        assertEquals(
            listOf(StageId.FIRMWARE_ACQUISITION, StageId.FIRMWARE_EXTRACTION, StageId.WORK_TREE_ASSEMBLY),
            events.filterIsInstance<PipelineEvent.StageStarted>().map { it.stageId },
        )
        val run = assertNotNull(repo.getRun("run-q"))
        assertEquals(3, run.stages.size)
        assertTrue(run.pinnedTools.isNotEmpty(), "tool paths are pinned when the queued run starts")
        assertFalse(port.durableSteps.any { it.toolId == "zip" }, "nothing after Assemble ran")
        assertTrue(run.artifacts.isEmpty(), "a run stopped before Build & Sign lists no package")
    }

    @Test
    fun aRunOnALaterDayReusesTheCachedPackageUnderItsOwnName() = runTest {
        fun at(day: String) = object : kotlinx.datetime.Clock {
            override fun now() = kotlinx.datetime.Instant.parse("${day}T10:00:00Z")
        }
        val port = globalPort()
        val repo = InMemoryRunRepository()
        fun orchestratorOn(day: String) = PipelineOrchestrator(
            port,
            repo,
            FakeVendoredFiles(true),
            toolStore = PipelinePinnedToolStore(),
            clock = at(day),
        )
        // The run date comes from the clock, not the fixture.
        val base = fixture.globalOtaContext()
        val context = base.copy(runtimeValues = base.runtimeValues - RuntimeKeys.RUN_DATE)
        orchestratorOn("2026-10-18").drive("run-1", context, "d1").toList()

        val events = orchestratorOn("2026-10-20").drive("run-2", context, "d2").toList()

        assertEquals(7, events.count { it is PipelineEvent.StageSkipped }, "a new day alone rebuilds nothing")
        val zip = repo.getRun("run-2")!!.artifacts.first()
        assertEquals(repo.getRun("run-1")!!.artifacts.first(), zip)
        assertTrue("20261018" in zip.linuxPath, "the cached package keeps the name it was built with")
    }

    @Test
    fun secondRunSkipsEveryStageWhenInputsAndOutputsMatch() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        val executedBefore = port.durableSteps.size

        val events = orchestrator(port, repo).drive("run-2", fixture.globalOtaContext(), "d2").toList()
        assertEquals(7, events.count { it is PipelineEvent.StageSkipped })
        assertEquals(executedBefore, port.durableSteps.size)
        assertTrue(repo.getRun("run-2")!!.stages.all { it.state == StageState.SKIPPED })
        assertEquals(3, repo.getRun("run-2")!!.artifacts.size, "a fully cached build still lists its package")
    }

    @Test
    fun aRunStoppedAfterAnEarlierStageClearsTheLaterStagesCachedResults() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        // The work tree changes under Assemble, and a run through Assemble re-syncs it (as the Run panel's chips do).
        port.stdoutOverrides["sha256sum $ws/work/system/system/build.prop"] = "deadbeef  build.prop"
        repo.upsert(
            org.ide.lti.core.model.run.BuildRun(
                id = "run-2",
                workspaceId = fixture.workspace.id,
                snapshotId = fixture.snapshotGlobalOta.id,
                state = RunState.QUEUED,
                startedAt = kotlinx.datetime.Clock.System.now(),
                throughStage = StageId.WORK_TREE_ASSEMBLY,
            ),
        )
        val partial = orchestrator(port, repo).drive("run-2", fixture.globalOtaContext(), "d2").toList()
        val completed = partial.filterIsInstance<PipelineEvent.StageCompleted>().map { it.stageId }
        assertTrue(StageId.WORK_TREE_ASSEMBLY in completed)

        // The next full run finds Assemble's own record valid, but nothing after it may come from the old tree.
        val events = orchestrator(port, repo).drive("run-3", fixture.globalOtaContext(), "d3").toList()

        val skipped = events.filterIsInstance<PipelineEvent.StageSkipped>().map { it.stageId }
        assertEquals(
            listOf(StageId.FIRMWARE_ACQUISITION, StageId.FIRMWARE_EXTRACTION, StageId.WORK_TREE_ASSEMBLY),
            skipped,
        )
        assertEquals(4, events.filterIsInstance<PipelineEvent.StageCompleted>().size)
    }

    @Test
    fun editedOutputInvalidatesTheCacheEvenWhenInputsMatch() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        port.stdoutOverrides["sha256sum $ws/work/system/system/build.prop"] = "deadbeef  build.prop"

        val events = orchestrator(port, repo).drive("run-2", fixture.globalOtaContext(), "d2").toList()
        val skipped = events.filterIsInstance<PipelineEvent.StageSkipped>().map { it.stageId }
        assertTrue(StageId.WORK_TREE_ASSEMBLY !in skipped)
        assertTrue(StageId.FIRMWARE_EXTRACTION in skipped)
        // Assemble re-synced the shared work tree: nothing after it may come from cache (Debloat would be lost).
        assertEquals(listOf(StageId.FIRMWARE_ACQUISITION, StageId.FIRMWARE_EXTRACTION), skipped)
        // Later stages must not depend on long values captured by a cached stage (they are not kept).
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }

    @Test
    fun aStageThatFailedIsRunAgainNotSkippedOnItsOlderCache() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val ctx = fixture.globalOtaContext()
        orchestrator(port, repo).drive("run-1", ctx, "d1").toList()
        // Run 2 changes a Build input, so Build really runs, and fails part way.
        val changed = ctx.copy(snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(level = 5)))
        port.exitCodes["mkfs.erofs"] = 2
        orchestrator(port, repo).drive("run-2", changed, "d2").toList()
        port.exitCodes.remove("mkfs.erofs")

        // Back to run 1's inputs: run 1's cache record would match, but the tree is no longer what it describes.
        val events = orchestrator(port, repo).drive("run-3", ctx, "d3").toList()
        val skipped = events.filterIsInstance<PipelineEvent.StageSkipped>().map { it.stageId }
        assertTrue(StageId.BUILD_FLASHABLE_ZIP !in skipped, "$skipped")
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
    }

    @Test
    fun failingStepFailsStageRunAndMarksRestNotRun() = runTest {
        val port = globalPort()
        port.exitCodes["mkfs.erofs"] = 2
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()

        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().single()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, failed.stageId)
        assertEquals(2, failed.exitCode)
        assertEquals(PipelineEvent.RunFinished(RunState.FAILED), events.last())
        val run = repo.getRun("run-1")!!
        assertEquals(StageState.FAILED, run.stages[5].state)
        assertEquals(StageState.NOT_RUN, run.stages[6].state)
        assertEquals(StageState.EXECUTED, run.stages[4].state)
        // A failed stage has no valid cache record.
        assertTrue(port.text(".cache/BUILD_FLASHABLE_ZIP.key").isNullOrEmpty())
    }

    @Test
    fun missingVendoredUpdaterFailsInsteadOfSubstituting() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val events =
            orchestrator(
                port,
                repo,
                vendored = false,
            ).drive("run-1", fixture.globalOtaContext(), "d1").toList()

        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().single()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, failed.stageId)
        assertTrue(failed.message.contains("update-binary"))
        assertFalse(port.durableSteps.any { it.toolId == "zip" })
        assertFalse(port.has("${BuildFlashableZipStage.STAGE_DIR}/META-INF/com/google/android/update-binary"))
    }

    @Test
    fun checksumMismatchFailsAcquisition() = runTest {
        val port = globalPort()
        port.stdoutOverrides["sha256sum $ws/firmware/downloaded"] = "0000  archive"
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()

        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().single()
        assertEquals(StageId.FIRMWARE_ACQUISITION, failed.stageId)
        assertTrue(failed.message.contains("checksum mismatch"))
    }

    @Test
    fun htmlInterstitialIsRejected() = runTest {
        val port = globalPort()
        port.stdoutOverrides["file -b --mime-type"] = "text/html"
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().single()
        assertTrue(failed.message.contains("HTML page"))
        assertFalse(port.durableSteps.any { it.toolId == "mv" })
    }

    @Test
    fun rangeFallbackReissuesPlainCurl() = runTest {
        val port = globalPort()
        port.exitCodes["curl -fL --retry 3 -C -"] = 33
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        val curls = port.durableSteps.filter { it.toolId == "curl" }
        assertEquals(2, curls.size)
        assertFalse("-C" in curls[1].args)
    }

    @Test
    fun stageStartedIsEmittedBeforeTheFirstRemoteCall() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        assertEquals(PipelineEvent.StageStarted(StageId.FIRMWARE_ACQUISITION), events.first())
        val firstOutput = events.indexOfFirst { it is PipelineEvent.StepOutput }
        assertTrue(firstOutput > 0)
    }

    @Test
    fun secondDriverIsRefusedWhileLeaseIsFresh() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        // The run finished; a fresh lease from d1 is still recorded — d2 must not take it over.
        val run = repo.getRun("run-1")!!
        repo.upsert(run.copy(state = RunState.RUNNING, driverHeartbeatEpochMs = System.currentTimeMillis()))
        assertFailsWith<IllegalStateException> {
            orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d2").toList()
        }
    }

    @Test
    fun rawImageArchiveUsesLpunpackAndSparseDetection() = runTest {
        val port = chinaPort()
        val repo = InMemoryRunRepository()
        val events = orchestrator(port, repo).drive("run-1", fixture.chinaRawContext(), "d1").toList()
        val failed = events.filterIsInstance<PipelineEvent.StageFailed>().firstOrNull()
        assertEquals(null, failed?.message)
        assertEquals(PipelineEvent.RunFinished(RunState.SUCCEEDED), events.last())
        assertTrue(port.durableSteps.any { it.toolId == "simg2img" })
        val lpunpack = port.durableSteps.single { it.toolId == "lpunpack" }
        assertEquals("--slot=0", lpunpack.args.first())
        assertTrue(lpunpack.args.contains("system_a"))
        assertFalse(port.durableSteps.any { it.toolId == "payload-dumper-go" })
        // China profile folds system_ext into system.
        assertTrue(
            port.durableSteps.any {
                it.toolId == "rsync" &&
                    it.args.last().endsWith("work/system/system/system_ext/")
            },
        )
    }
}
