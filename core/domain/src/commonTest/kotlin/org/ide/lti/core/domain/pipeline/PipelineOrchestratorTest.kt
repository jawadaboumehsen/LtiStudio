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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
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
        PipelineOrchestrator(port, repo, FakeVendoredFiles(vendored))

    private fun globalPort() = SimulatedExecutionPort(ws).apply {
        archiveSha256 =
            fixture.snapshotGlobalOta.acquisition.firmware.sha256
    }
    private fun chinaPort() = SimulatedExecutionPort(ws).apply {
        archiveSha256 =
            fixture.snapshotChinaRaw.acquisition.firmware.sha256
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
        val footer = port.durableSteps.first {
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
        assertTrue(assertNotNull(port.text("work/system/system/build.prop")).contains("org.lti.build.version=1.0.0"))

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
    fun secondRunSkipsEveryStageWhenInputsAndOutputsMatch() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        orchestrator(port, repo).drive("run-1", fixture.globalOtaContext(), "d1").toList()
        val executedBefore = port.durableSteps.size

        val events = orchestrator(port, repo).drive("run-2", fixture.globalOtaContext(), "d2").toList()
        assertEquals(7, events.count { it is PipelineEvent.StageSkipped })
        assertEquals(executedBefore, port.durableSteps.size)
        assertTrue(repo.getRun("run-2")!!.stages.all { it.state == StageState.SKIPPED })
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
        assertFalse(port.has(".cache/BUILD_FLASHABLE_ZIP.key"))
    }

    @Test
    fun missingVendoredUpdaterFailsInsteadOfSubstituting() = runTest {
        val port = globalPort()
        val repo = InMemoryRunRepository()
        val events = orchestrator(
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
