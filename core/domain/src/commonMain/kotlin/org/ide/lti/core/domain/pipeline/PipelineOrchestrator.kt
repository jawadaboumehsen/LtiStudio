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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.text.AvbSizeCalculator
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.workspace.Workspace

public sealed interface PipelineEvent {
    public data class StageStarted(val stageId: StageId) : PipelineEvent

    public data class StageSkipped(val stageId: StageId, val cacheKey: String) : PipelineEvent

    public data class StageCompleted(val stageId: StageId, val durationMs: Long) : PipelineEvent

    public data class StageFailed(val stageId: StageId, val stepIndex: Int, val exitCode: Int, val message: String) :
        PipelineEvent

    public data class StageCancelled(val stageId: StageId) : PipelineEvent

    public data class StageInterrupted(val stageId: StageId) : PipelineEvent

    public data class RunPaused(val stageId: StageId) : PipelineEvent

    public data class RunResumed(val stageId: StageId) : PipelineEvent

    public data class StepOutput(val stageId: StageId, val text: String) : PipelineEvent

    public data class RunFinished(val state: RunState) : PipelineEvent
}

/**
 * Drives the stages sequentially for one run (all, or through [BuildRun.throughStage]). Each stage is planned
 * from a [StageContext];
 * after every step that captures a result the stage is re-planned so later steps see real values
 * (sizes, digests, probe results). Cache keys skip a stage only when the recorded input digest
 * matches **and** the recorded output digest still matches the current outputs.
 */
@Suppress("LargeClass", "TooManyFunctions")
public class PipelineOrchestrator(
    private val executionPort: PipelineExecutionPort,
    private val runRepository: RunRepository,
    private val vendoredFiles: VendoredFilePort,
    private val registry: PipelineDefinitionRegistry = PipelineDefinitionRegistry(),
    private val clock: Clock = Clock.System,
    private val toolStore: ToolStorePort? = null,
    private val toolUsageCoordinator: ToolUsageCoordinator? = null,
    private val hostSteps: HostStepPort? = null,
) {
    /** The stages this run executes: all of them, or up to and including [BuildRun.throughStage]. */
    private fun stagesOf(run: BuildRun?): List<StageDefinition> {
        val all = registry.executableStages()
        val last = run?.throughStage?.let { through -> all.indexOfFirst { it.id == through } } ?: -1
        return if (last >= 0) all.take(last + 1) else all
    }

    private class StageFailure(val stepIndex: Int, val exitCode: Int, message: String) : RuntimeException(message)

    private class StageInterruptedFailure(message: String) : RuntimeException(message)

    private class StageCancelledFailure(message: String) : RuntimeException(message)

    private inner class Driver(
        val runId: String,
        val driverInstanceId: String,
        val collector: FlowCollector<PipelineEvent>,
        var run: BuildRun,
        val runtimeValues: MutableMap<String, String>,
    ) {
        lateinit var baseContext: StageContext
        val ws: Workspace get() = baseContext.workspace

        fun context(): StageContext = baseContext.copy(
            runtimeValues = runtimeValues.toMap(),
            toolPaths = run.pinnedTools,
        )

        suspend fun persist(mutate: (BuildRun) -> BuildRun) {
            val stored = runRepository.getRun(runId)
            // CancelRunUseCase may already have completed cancellation while a host step was running.
            if (stored?.state == RunState.CANCELLED) {
                run = stored
                return
            }
            val cancelling = stored?.state == RunState.CANCELLING
            run = mutate(run).copy(runtimeValues = runtimeValues.filterValues { it.length <= PERSISTED_VALUE_LIMIT })
            if (cancelling && run.state != RunState.CANCELLED) run = run.copy(state = RunState.CANCELLING)
            runRepository.upsert(run)
        }

        suspend fun checkCancellation() {
            if (runRepository.getRun(runId)?.state in setOf(RunState.CANCELLING, RunState.CANCELLED)) {
                throw StageCancelledFailure("Run cancelled by user")
            }
        }
    }

    /**
     * Step output is stored and shown in batches: a progress bar redraws thousands of times a second
     * (payload-dumper sent 50,000 events in two minutes), and one write and one UI update per redraw
     * made the run wait 25 minutes after the tool had finished.
     */
    private inner class StepOutputBuffer(private val driver: Driver, private val stageId: StageId) {
        private val pending = mutableListOf<String>()
        private var lastFlush = clock.now()

        suspend fun add(text: String) {
            pending += text
            val due = (clock.now() - lastFlush).inWholeMilliseconds >= OUTPUT_BATCH_MS
            if (pending.size >= OUTPUT_BATCH_SIZE || due) {
                flush()
            }
        }

        suspend fun flush() {
            if (pending.isEmpty()) return
            val batch = pending.toList()
            pending.clear()
            lastFlush = clock.now()
            runRepository.appendEvents(driver.runId, batch)
            driver.collector.emit(PipelineEvent.StepOutput(stageId, batch.joinToString("")))
        }
    }

    /** Runs this app is driving now; a second drive/resume of one of them emits nothing (see [exclusive]). */
    private val driving: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    public fun drive(runId: String, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> =
        exclusive(runId) { driveRun(runId, ctx, driverInstanceId) }

    public fun resume(run: BuildRun, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> =
        exclusive(run.id) { resumeRun(run, ctx, driverInstanceId) }

    /**
     * One driver per run in this app. The Run panel reattaches to whatever run is active, which includes a run
     * it has just started; a second driver would run the same steps at the same time.
     */
    private fun exclusive(runId: String, body: () -> Flow<PipelineEvent>): Flow<PipelineEvent> = flow {
        if (!driving.add(runId)) return@flow
        try {
            emitAll(body())
        } finally {
            driving.remove(runId)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun driveRun(runId: String, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> = flow {
        val now = clock.now()
        val existing = runRepository.getRun(runId)
        val startedAt = existing?.startedAt ?: now
        // A run StartPipelineRunUseCase only queued has not pinned its tools yet: pin them now, as for a new run.
        val notStarted = existing == null || (existing.state == RunState.QUEUED && existing.pinnedTools.isEmpty())
        val pins =
            if (notStarted && toolStore != null) {
                toolUsageCoordinator?.admitJob(runId) {
                    toolStore.paths(ToolGroupCatalog).getOrElse { cause ->
                        throw IllegalStateException("Cannot capture tool paths for this ROM run", cause)
                    }
                } ?: toolStore.paths(ToolGroupCatalog).getOrElse { cause ->
                    throw IllegalStateException("Cannot capture tool paths for this ROM run", cause)
                }
            } else {
                toolUsageCoordinator?.admitJob(runId) { null }
                null
            }
        // Admission succeeded: every later initialization failure must release our job.
        // A duplicate admission throws above, outside this cleanup boundary.
        try {
            val pinnedPaths = pins?.paths.orEmpty().toMap()
            val pinnedVersions = pins?.versions.orEmpty().toMap()
            if (toolStore != null) {
                val capturedPaths = existing?.pinnedTools?.takeIf { it.isNotEmpty() } ?: pinnedPaths
                check(
                    capturedPaths.isNotEmpty() &&
                        capturedPaths.values.all {
                            it.startsWith("/") && "/current/" !in it && !it.endsWith("/current")
                        },
                ) {
                    "Cannot admit a ROM run without immutable tool pins"
                }
            }
            if (existing == null) {
                runRepository.upsert(
                    BuildRun(
                        runId,
                        ctx.workspace.id,
                        ctx.snapshot.id,
                        startedAt = startedAt,
                        pinnedTools = pinnedPaths,
                        toolVersions = pinnedVersions,
                    ),
                )
            }
            check(runRepository.claimDriver(runId, driverInstanceId, now.toEpochMilliseconds())) {
                "Cannot drive run $runId: lease held by another active driver"
            }
            val stages = stagesOf(existing)
            val outcomes =
                stages.map { stage ->
                    existing?.stages?.firstOrNull { it.stageId == stage.id } ?: StageOutcome(stageId = stage.id)
                }
            val runtimeValues = (existing?.runtimeValues.orEmpty() + ctx.runtimeValues).toMutableMap()
            runtimeValues.putIfAbsent(RuntimeKeys.RUN_DATE, startedAt.yyyymmdd())
            runtimeValues.putIfAbsent(RuntimeKeys.RUN_TIMESTAMP, startedAt.epochSeconds.toString())

            val initialRun =
                existing?.let { queued ->
                    if (queued.pinnedTools.isEmpty()) {
                        queued.copy(pinnedTools = pinnedPaths, toolVersions = pinnedVersions)
                    } else {
                        queued
                    }
                } ?: BuildRun(
                    runId,
                    ctx.workspace.id,
                    ctx.snapshot.id,
                    startedAt = startedAt,
                    pinnedTools = pinnedPaths,
                    toolVersions = pinnedVersions,
                )
            val driver =
                Driver(
                    runId = runId,
                    driverInstanceId = driverInstanceId,
                    collector = this,
                    run = initialRun,
                    runtimeValues = runtimeValues,
                )
            driver.baseContext = ctx
            driver.persist {
                it.copy(
                    state = RunState.RUNNING,
                    stages = outcomes,
                    driverInstanceId = driverInstanceId,
                    driverHeartbeatEpochMs = now.toEpochMilliseconds(),
                )
            }

            try {
                var previousKey = ""
                // Stages share one work tree: once a stage really ran, later stages must run too, even when
                // their own inputs match (Assemble re-syncs the tree, so a cached Debloat would be lost).
                var upstreamExecuted = false
                for ((index, stage) in stages.withIndex()) {
                    val startedStage = clock.now()
                    emit(PipelineEvent.StageStarted(stage.id))
                    driver.persist {
                        it.withStage(
                            index,
                        ) { s -> s.copy(state = StageState.RUNNING, startedAt = startedStage) }
                    }
                    runRepository.heartbeatDriver(runId, driverInstanceId, startedStage.toEpochMilliseconds())

                    val cacheKey = computeStageCacheKey(driver, stage, previousKey)
                    if (!upstreamExecuted && isCacheValid(driver, stage, cacheKey)) {
                        val ended = clock.now()
                        driver.persist {
                            it.withStage(index) { s ->
                                s.copy(
                                    state = StageState.SKIPPED,
                                    endedAt = ended,
                                    durationMs = ended.millisSince(startedStage),
                                    cacheKey = cacheKey,
                                )
                            }
                        }
                        emit(PipelineEvent.StageSkipped(stage.id, cacheKey))
                        previousKey = cacheKey
                        continue
                    }

                    invalidateLaterStages(driver, stage)
                    val failure =
                        runCatching { executeStage(driver, stage, isResumeStage = false) }.exceptionOrNull()
                            ?.also { if (it is CancellationException) throw it }
                            ?: outputDigestOrFailure(driver, stage)
                    if (failure != null) {
                        if (failure is StageInterruptedFailure) {
                            handleInterruption(driver, index, stage, failure.message ?: "Stage interrupted")
                            return@flow
                        }
                        if (failure is StageCancelledFailure) {
                            handleCancellation(driver, index, stage, failure.message ?: "Stage cancelled")
                            return@flow
                        }
                        failStage(driver, index, stage, startedStage, failure)
                        return@flow
                    }
                    val ended = clock.now()
                    val outputDigest = requireNotNull(driver.runtimeValues.remove(OUTPUT_DIGEST_SCRATCH))
                    executionPort.uploadFile(
                        driver.ws,
                        cacheFile(stage),
                        cacheRecord(cacheKey, outputDigest, driver).encodeToByteArray(),
                    )
                    driver.persist {
                        it.withStage(index) { s ->
                            s.copy(
                                state = StageState.EXECUTED,
                                endedAt = ended,
                                durationMs = ended.millisSince(startedStage),
                                cacheKey = cacheKey,
                            )
                        }
                    }
                    emit(PipelineEvent.StageCompleted(stage.id, ended.millisSince(startedStage)))
                    previousKey = cacheKey
                    upstreamExecuted = true
                }

                val artifacts = collectArtifacts(driver)
                driver.persist { it.copy(state = RunState.SUCCEEDED, endedAt = clock.now(), artifacts = artifacts) }
                emit(PipelineEvent.RunFinished(RunState.SUCCEEDED))
            } catch (e: CancellationException) {
                if (driver.run.state == RunState.RUNNING) {
                    driver.persist { it.copy(state = RunState.PAUSED_WAITING_FOR_APP) }
                }
                throw e
            }
        } finally {
            toolUsageCoordinator?.finishJob(runId)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun resumeRun(run: BuildRun, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> = flow {
        val now = clock.now()
        if (run.state.isTerminal) {
            emit(PipelineEvent.RunFinished(run.state))
            return@flow
        }

        if (toolStore != null) {
            check(run.pinnedTools.isNotEmpty() && run.toolVersions.isNotEmpty()) {
                "Cannot resume a ROM run without persisted component tool pins"
            }
        }
        toolUsageCoordinator?.admitJob(run.id) { Unit }

        try {
            check(runRepository.claimDriver(run.id, driverInstanceId, now.toEpochMilliseconds())) {
                "Cannot drive run ${run.id}: lease held by another active driver"
            }
            val latestRun = runRepository.getRun(run.id) ?: run
            val wasPaused = latestRun.state == RunState.PAUSED_WAITING_FOR_APP
            val startedAt = latestRun.startedAt
            val runtimeValues = (latestRun.runtimeValues + ctx.runtimeValues).toMutableMap()
            runtimeValues.putIfAbsent(RuntimeKeys.RUN_DATE, startedAt.yyyymmdd())
            runtimeValues.putIfAbsent(RuntimeKeys.RUN_TIMESTAMP, startedAt.epochSeconds.toString())

            val stages = stagesOf(latestRun)
            val outcomes =
                stages.map { stage ->
                    latestRun.stages.firstOrNull { it.stageId == stage.id } ?: StageOutcome(stageId = stage.id)
                }

            val driver =
                Driver(
                    runId = latestRun.id,
                    driverInstanceId = driverInstanceId,
                    collector = this,
                    run = latestRun.copy(stages = outcomes),
                    runtimeValues = runtimeValues,
                )
            driver.baseContext = ctx

            val startStageIndex =
                stages.indexOfFirst { stage ->
                    val outcome = driver.run.stages.firstOrNull { it.stageId == stage.id }
                    outcome == null || (outcome.state != StageState.EXECUTED && outcome.state != StageState.SKIPPED)
                }

            if (startStageIndex == -1) {
                val artifacts = collectArtifacts(driver)
                driver.persist { it.copy(state = RunState.SUCCEEDED, endedAt = clock.now(), artifacts = artifacts) }
                emit(PipelineEvent.RunFinished(RunState.SUCCEEDED))
                return@flow
            }

            var previousKey =
                if (startStageIndex > 0) {
                    driver.run.stages.getOrNull(startStageIndex - 1)?.cacheKey.orEmpty()
                } else {
                    ""
                }

            var upstreamExecuted =
                driver.run.stages.take(startStageIndex).any { it.state == StageState.EXECUTED }
            try {
                for (index in startStageIndex until stages.size) {
                    val stage = stages[index]
                    val existingOutcome = driver.run.stages.firstOrNull { it.stageId == stage.id }
                    val startedStage = existingOutcome?.startedAt ?: clock.now()

                    if (wasPaused && index == startStageIndex) {
                        emit(PipelineEvent.RunResumed(stage.id))
                        driver.persist { it.copy(state = RunState.RUNNING) }
                    }

                    if (existingOutcome?.state != StageState.RUNNING) {
                        emit(PipelineEvent.StageStarted(stage.id))
                        driver.persist {
                            it.withStage(
                                index,
                            ) { s -> s.copy(state = StageState.RUNNING, startedAt = startedStage) }
                        }
                    }
                    runRepository.heartbeatDriver(driver.runId, driverInstanceId, clock.now().toEpochMilliseconds())

                    val cacheKey = computeStageCacheKey(driver, stage, previousKey)
                    if (!upstreamExecuted && isCacheValid(driver, stage, cacheKey)) {
                        val ended = clock.now()
                        driver.persist {
                            it.withStage(index) { s ->
                                s.copy(
                                    state = StageState.SKIPPED,
                                    endedAt = ended,
                                    durationMs = ended.millisSince(startedStage),
                                    cacheKey = cacheKey,
                                )
                            }
                        }
                        emit(PipelineEvent.StageSkipped(stage.id, cacheKey))
                        previousKey = cacheKey
                        continue
                    }

                    invalidateLaterStages(driver, stage)
                    val failure =
                        runCatching {
                            executeStage(driver, stage, isResumeStage = (index == startStageIndex))
                        }.exceptionOrNull()
                            ?.also { if (it is CancellationException) throw it }
                            ?: outputDigestOrFailure(driver, stage)

                    if (failure != null) {
                        if (failure is StageInterruptedFailure) {
                            handleInterruption(driver, index, stage, failure.message ?: "Stage interrupted")
                            return@flow
                        }
                        if (failure is StageCancelledFailure) {
                            handleCancellation(driver, index, stage, failure.message ?: "Stage cancelled")
                            return@flow
                        }
                        failStage(driver, index, stage, startedStage, failure)
                        return@flow
                    }

                    val ended = clock.now()
                    val outputDigest = requireNotNull(driver.runtimeValues.remove(OUTPUT_DIGEST_SCRATCH))
                    executionPort.uploadFile(
                        driver.ws,
                        cacheFile(stage),
                        cacheRecord(cacheKey, outputDigest, driver).encodeToByteArray(),
                    )
                    driver.persist {
                        it.withStage(index) { s ->
                            s.copy(
                                state = StageState.EXECUTED,
                                endedAt = ended,
                                durationMs = ended.millisSince(startedStage),
                                cacheKey = cacheKey,
                            )
                        }
                    }
                    emit(PipelineEvent.StageCompleted(stage.id, ended.millisSince(startedStage)))
                    previousKey = cacheKey
                    upstreamExecuted = true
                }

                val artifacts = collectArtifacts(driver)
                driver.persist { it.copy(state = RunState.SUCCEEDED, endedAt = clock.now(), artifacts = artifacts) }
                emit(PipelineEvent.RunFinished(RunState.SUCCEEDED))
            } catch (e: CancellationException) {
                if (driver.run.state == RunState.RUNNING) {
                    driver.persist { it.copy(state = RunState.PAUSED_WAITING_FOR_APP) }
                }
                throw e
            }
        } finally {
            toolUsageCoordinator?.finishJob(run.id)
        }
    }

    public suspend fun pause(runId: String): Boolean {
        val run = runRepository.getRun(runId) ?: return false
        if (run.state.isTerminal || run.state == RunState.CANCELLING) return false
        runRepository.upsert(run.copy(state = RunState.PAUSED_WAITING_FOR_APP))
        return true
    }

    private suspend fun FlowCollector<PipelineEvent>.handleInterruption(
        driver: Driver,
        index: Int,
        stage: StageDefinition,
        message: String,
    ) {
        val ended = clock.now()
        driver.persist { r ->
            r.copy(state = RunState.INTERRUPTED, endedAt = ended)
                .withStage(index) { s ->
                    s.copy(
                        state = StageState.INTERRUPTED,
                        endedAt = ended,
                        message = message,
                    )
                }
                .markRemainingNotRun(index + 1)
        }
        emit(PipelineEvent.StageInterrupted(stage.id))
        emit(PipelineEvent.RunFinished(RunState.INTERRUPTED))
    }

    private suspend fun FlowCollector<PipelineEvent>.handleCancellation(
        driver: Driver,
        index: Int,
        stage: StageDefinition,
        message: String,
    ) {
        val ended = clock.now()
        driver.persist { r ->
            r.copy(state = RunState.CANCELLED, endedAt = ended)
                .withStage(index) { s ->
                    s.copy(
                        state = StageState.CANCELLED,
                        endedAt = ended,
                        message = message,
                    )
                }
                .markRemainingNotRun(index + 1)
        }
        emit(PipelineEvent.StageCancelled(stage.id))
        emit(PipelineEvent.RunFinished(RunState.CANCELLED))
    }

    /** Digests the stage outputs into a scratch value, or returns the failure to report. */
    private suspend fun outputDigestOrFailure(driver: Driver, stage: StageDefinition): Throwable? {
        val digest =
            outputDigest(driver, stage.outputs(driver.context()))
                ?: return StageFailure(
                    -1,
                    -1,
                    "Output verification failed for ${stage.id}: a declared output is missing",
                )
        driver.runtimeValues[OUTPUT_DIGEST_SCRATCH] = digest
        return null
    }

    private suspend fun FlowCollector<PipelineEvent>.failStage(
        driver: Driver,
        index: Int,
        stage: StageDefinition,
        startedStage: Instant,
        failure: Throwable,
    ) {
        // Cancel kills the running step, which then fails (mkfs exits 130): that is the cancel, not a failure.
        val state = runRepository.getRun(driver.runId)?.state
        if (state == RunState.CANCELLING || state == RunState.CANCELLED) {
            handleCancellation(driver, index, stage, "Run cancelled by user")
            return
        }
        val ended = clock.now()
        val stepIndex = (failure as? StageFailure)?.stepIndex ?: -1
        val exitCode = (failure as? StageFailure)?.exitCode ?: -1
        val message = failure.message ?: failure.toString()
        driver.persist { run ->
            run.copy(state = RunState.FAILED, endedAt = ended)
                .withStage(index) { s ->
                    s.copy(
                        state = StageState.FAILED,
                        endedAt = ended,
                        durationMs = ended.millisSince(startedStage),
                        message = message,
                    )
                }
                .markRemainingNotRun(index + 1)
        }
        emit(PipelineEvent.StageFailed(stage.id, stepIndex, exitCode, message))
        emit(PipelineEvent.RunFinished(RunState.FAILED))
    }

    private suspend fun executeStage(driver: Driver, stage: StageDefinition, isResumeStage: Boolean = false) {
        driver.checkCancellation()
        // A stage that starts running is no longer what its old cache record describes: if it fails part way
        // (apps deleted, sidecars not yet edited), a later run must not skip it on that record.
        executionPort.uploadFile(driver.ws, cacheFile(stage), ByteArray(0))
        var steps = stage.plan(driver.context())
        var index = 0
        while (index < steps.size) {
            driver.checkCancellation()
            val step = steps[index]
            val recorded =
                if (isResumeStage) {
                    driver.run.steps.firstOrNull { it.stageId == stage.id && it.index == index }
                } else {
                    null
                }

            val captured =
                if (recorded != null && recorded.serverRunId != null && step is PipelineStep.Tool) {
                    reconcileRecordedToolStep(driver, stage, index, step, recorded)
                } else {
                    when (step) {
                        is PipelineStep.Tool -> runTool(driver, stage, index, step)
                        is PipelineStep.WriteFile -> false.also { writeFile(driver, index, step.relPath, step.content) }
                        is PipelineStep.WriteVendoredFile -> false.also { writeVendored(driver, index, step) }
                        is PipelineStep.EditFile -> false.also { editFile(driver, index, step) }
                        is PipelineStep.AvbSizeSearch -> true.also { avbSearch(driver, index, step) }
                        is PipelineStep.Check -> false.also { check(driver, index, step) }
                        is PipelineStep.Host -> runHost(driver, stage, index, step)
                    }
                }
            index++
            if (captured) {
                val replanned = stage.plan(driver.context())
                check(replanned.take(index).map { it.label } == steps.take(index).map { it.label }) {
                    "Stage ${stage.id} re-plan changed already executed steps"
                }
                steps = replanned
            }
        }
        driver.checkCancellation()
    }

    private suspend fun reconcileRecordedToolStep(
        driver: Driver,
        stage: StageDefinition,
        index: Int,
        step: PipelineStep.Tool,
        recorded: StageStep,
    ): Boolean {
        val serverRunId = requireNotNull(recorded.serverRunId)
        val status = executionPort.status(serverRunId)

        if (status == null || status.status == "INTERRUPTED" ||
            (status.status == "RUNNING" && status.pid == null && status.endedAtEpochMs != null)
        ) {
            throw StageInterruptedFailure("Step ${step.label} on server is dead or unknown")
        }

        if (status.status == "CANCELLED") {
            throw StageCancelledFailure("Step ${step.label} was cancelled")
        }

        if (status.status == "RUNNING" || status.status == "QUEUED") {
            val lastSeq = driver.run.lastSeqByStep[serverRunId] ?: recorded.lastSeq
            val stdout = StringBuilder()
            val outputBuffer = StepOutputBuffer(driver, stage.id)
            val (exitCode, currentSeq) = streamToEnd(driver, serverRunId, stdout, outputBuffer, fromSeq = lastSeq)
            outputBuffer.flush()

            driver.persist { r ->
                val updatedSteps =
                    r.steps.map {
                        if (it.stageId == stage.id && it.index == index) {
                            it.copy(
                                state = if (exitCode == 0) StepState.COMPLETED else StepState.FAILED,
                                exitCode = exitCode,
                                lastSeq = currentSeq,
                            )
                        } else {
                            it
                        }
                    }
                val updatedSeqMap = r.lastSeqByStep + (serverRunId to currentSeq)
                r.copy(steps = updatedSteps, lastSeqByStep = updatedSeqMap)
            }

            val output = stdout.toString().trim()
            step.resultKey?.let { key ->
                driver.runtimeValues[key] = output
                driver.runtimeValues[RuntimeKeys.exitOf(key)] = exitCode.toString()
            }
            if (exitCode != 0 && !step.allowFailure) {
                throw StageFailure(index, exitCode, "${step.label}: ${step.command.toolId} exited with $exitCode")
            }
            step.expectStdoutContains.firstOrNull { it !in output }?.let { missing ->
                throw StageFailure(
                    index,
                    exitCode,
                    "${step.label}: expected '$missing' in ${step.command.toolId} output",
                )
            }
            return step.resultKey != null
        }

        if (status.status == "COMPLETED" || (status.exitCode != null && status.exitCode == 0)) {
            driver.persist { r ->
                val updatedSteps =
                    r.steps.map {
                        if (it.stageId == stage.id && it.index == index) {
                            it.copy(
                                state = StepState.COMPLETED,
                                exitCode = status.exitCode ?: 0,
                                lastSeq = status.lastSeq,
                            )
                        } else {
                            it
                        }
                    }
                r.copy(steps = updatedSteps)
            }
            if (step.resultKey != null && !driver.runtimeValues.containsKey(step.resultKey)) {
                // This step's own output, replayed by the service (not the whole run's log).
                val out = StringBuilder()
                executionPort.observe(serverRunId, 1L).collect { event ->
                    if (event is StepEvent.Output && !event.isError) out.append(event.text).append('\n')
                }
                driver.runtimeValues[step.resultKey] = out.toString().trim()
                driver.runtimeValues[RuntimeKeys.exitOf(step.resultKey)] = (status.exitCode ?: 0).toString()
            }
            return step.resultKey != null
        }

        if (status.status == "FAILED" || (status.exitCode != null && status.exitCode != 0)) {
            val code = status.exitCode ?: -1
            if (!step.allowFailure) {
                throw StageFailure(index, code, "${step.label}: ${step.command.toolId} exited with $code")
            }
            return false
        }

        throw StageInterruptedFailure("Unknown status '${status.status}' for step ${step.label}")
    }

    /**
     * Reads a step's output to its end; returns its exit code and last sequence number. A stream can end without
     * its Finished event (a step done in milliseconds, a dropped connection): then the service's record of the
     * step decides, and a step still running is read on from where it was.
     */
    private suspend fun streamToEnd(
        driver: Driver,
        runId: String,
        stdout: StringBuilder,
        outputBuffer: StepOutputBuffer,
        fromSeq: Long = 0L,
    ): Pair<Int, Long> {
        var finished: Int? = null
        var lastSeq = fromSeq
        while (finished == null) {
            executionPort.observe(runId, if (lastSeq == 0L) 0L else lastSeq + 1).collect { event ->
                when (event) {
                    is StepEvent.Output -> {
                        lastSeq = maxOf(lastSeq, event.seq)
                        // The service sends one event per line, with the line ending stripped.
                        if (!event.isError) stdout.append(event.text).append('\n')
                        outputBuffer.add(event.text)
                    }
                    is StepEvent.Finished -> finished = event.exitCode
                    StepEvent.Heartbeat ->
                        runRepository.heartbeatDriver(
                            driver.runId,
                            driver.driverInstanceId,
                            clock.now().toEpochMilliseconds(),
                        )
                }
            }
            if (finished == null) {
                val status = executionPort.status(runId)
                if (status == null || status.status !in STILL_RUNNING) {
                    finished = status?.exitCode ?: -1
                } else {
                    delay(STREAM_RETRY_MS)
                }
            }
        }
        return checkNotNull(finished) to lastSeq
    }

    /** Runs one durable step; returns true when a result was captured (caller re-plans). */
    private suspend fun runTool(driver: Driver, stage: StageDefinition, index: Int, step: PipelineStep.Tool): Boolean {
        val resolvedCommand = resolvePinnedCommand(driver, step.command)
        val idempotencyKey = StageStep.deriveIdempotencyKey(driver.runId, stage.id, index)
        val handle = executionPort.startStep(driver.ws, resolvedCommand, idempotencyKey)
        val initialStep =
            StageStep(
                stageId = stage.id,
                index = index,
                serverRunId = handle.runId,
                idempotencyKey = idempotencyKey,
                command = resolvedCommand,
                state = StepState.RUNNING,
                lastSeq = 0L,
            )
        driver.persist { r ->
            val updated = r.steps.filterNot { it.stageId == stage.id && it.index == index } + initialStep
            r.copy(steps = updated)
        }

        val stdout = StringBuilder()
        val outputBuffer = StepOutputBuffer(driver, stage.id)
        val (exitCode, lastSeq) = streamToEnd(driver, handle.runId, stdout, outputBuffer)
        outputBuffer.flush()

        driver.persist { r ->
            val updated =
                r.steps.map {
                    if (it.stageId == stage.id && it.index == index) {
                        it.copy(
                            state = if (exitCode == 0) StepState.COMPLETED else StepState.FAILED,
                            exitCode = exitCode,
                            lastSeq = lastSeq,
                        )
                    } else {
                        it
                    }
                }
            val updatedSeqMap = r.lastSeqByStep + (handle.runId to lastSeq)
            r.copy(steps = updated, lastSeqByStep = updatedSeqMap)
        }

        val output = stdout.toString().trim()
        step.resultKey?.let { key ->
            driver.runtimeValues[key] = output
            driver.runtimeValues[RuntimeKeys.exitOf(key)] = exitCode.toString()
        }
        if (exitCode != 0 && !step.allowFailure) {
            throw StageFailure(index, exitCode, "${step.label}: ${step.command.toolId} exited with $exitCode")
        }
        step.expectStdoutContains.firstOrNull { it !in output }?.let { missing ->
            throw StageFailure(index, exitCode, "${step.label}: expected '$missing' in ${step.command.toolId} output")
        }
        return step.resultKey != null
    }

    /** Returns true (re-plan) once the result is recorded; a result already present is never redone. */
    private suspend fun runHost(driver: Driver, stage: StageDefinition, index: Int, step: PipelineStep.Host): Boolean {
        if (driver.runtimeValues.containsKey(step.resultKey)) return true
        val output = runCatching {
            coroutineScope {
                val work = async {
                    checkNotNull(hostSteps) { "this app cannot run host steps" }.run(driver.ws, step.request)
                }
                val cancellation = async {
                    runRepository.observeRuns(driver.ws.id).first { runs ->
                        runs.any { it.id == driver.runId && it.state in setOf(RunState.CANCELLING, RunState.CANCELLED) }
                    }
                }
                try {
                    select {
                        cancellation.onAwait { throw StageCancelledFailure("Run cancelled by user") }
                        work.onAwait { it }
                    }
                } finally {
                    work.cancel()
                    cancellation.cancel()
                }
            }
        }.getOrElse { e ->
            throw when (e) {
                is CancellationException, is StageCancelledFailure -> e
                else -> StageFailure(index, -1, "${step.label}: ${e.message ?: e::class.simpleName}")
            }
        }
        driver.checkCancellation()
        driver.runtimeValues[step.resultKey] = output
        driver.collector.emit(PipelineEvent.StepOutput(stage.id, output))
        return true
    }

    private fun check(driver: Driver, index: Int, step: PipelineStep.Check) {
        step.verify(driver.runtimeValues.toMap())?.let { message ->
            throw StageFailure(index, -1, "${step.label}: $message")
        }
    }

    private suspend fun writeFile(driver: Driver, index: Int, relPath: String, content: ByteArray) {
        if (!executionPort.uploadFile(driver.ws, relPath, content)) {
            throw StageFailure(
                index,
                -1,
                "Failed to upload $relPath",
            )
        }
    }

    private suspend fun writeVendored(driver: Driver, index: Int, step: PipelineStep.WriteVendoredFile) {
        val file =
            vendoredFiles.load(step.resourceId) ?: throw StageFailure(
                index,
                -1,
                "Vendored file '${step.resourceId}' is missing or its sha256 does not match the pin; " +
                    "place the real prebuilt at core/data/src/desktopMain/resources/${step.resourceId} " +
                    "with a matching .sha256",
            )
        writeFile(driver, index, step.relPath, file.bytes)
    }

    private suspend fun editFile(driver: Driver, index: Int, step: PipelineStep.EditFile) {
        val original =
            executionPort.readFile(driver.ws, step.relPath) ?: throw StageFailure(
                index,
                -1,
                "Cannot read ${step.relPath}",
            )
        writeFile(driver, index, step.relPath, step.transform(original).encodeToByteArray())
    }

    private suspend fun avbSearch(driver: Driver, index: Int, step: PipelineStep.AvbSizeSearch) {
        val size =
            AvbSizeCalculator.findPartitionSize(step.imageSizeBytes) { probe ->
                val cmd =
                    resolvePinnedCommand(
                        driver,
                        StepCommand(
                            toolId = "avbtool",
                            args =
                            listOf(
                                "add_hashtree_footer",
                                "--partition_size",
                                probe.toString(),
                                "--calc_max_image_size",
                                // Same layout as the real footer: no FEC (see BuildFlashableZipStage).
                                "--do_not_generate_fec",
                            ),
                            workingDir = driver.ws.linuxPath,
                        ),
                    )
                val result = executionPort.execute(driver.ws, cmd)
                if (result.exitCode != 0) {
                    throw StageFailure(
                        index,
                        result.exitCode,
                        "${step.label}: avbtool probe failed: ${result.stderr.trim()}",
                    )
                }
                result.stdout.trim().lines().last().trim().toLongOrNull()
                    ?: throw StageFailure(
                        index,
                        -1,
                        "${step.label}: unparseable --calc_max_image_size output '${result.stdout.trim()}'",
                    )
            }
        driver.runtimeValues[step.resultKey] = size.toString()
    }

    private suspend fun computeStageCacheKey(driver: Driver, stage: StageDefinition, previousKey: String): String {
        val baseCacheKey = stage.computeCacheKey(driver.context(), previousKey)
        val stageToolsFingerprint =
            stage.requiredToolIds.sorted().joinToString(";") { toolId ->
                val pin = driver.run.pinnedTools[toolId] ?: ""
                val ver = driver.run.toolVersions[toolId] ?: ""
                "$toolId=$pin@$ver"
            }
        // The pipeline's own version too: when a stage's commands change, its old outputs are not reused.
        return CacheKeys.sha256("$baseCacheKey|$stageToolsFingerprint|${CacheKeys.PIPELINE_VERSION}")
    }

    private suspend fun isCacheValid(driver: Driver, stage: StageDefinition, cacheKey: String): Boolean {
        val recorded = executionPort.readFile(driver.ws, cacheFile(stage)) ?: return false
        val fields =
            recorded.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0].trim() to it[1].trim() }
        // The values the stage ran with (its run date names the package): today's date would name a file that
        // does not exist, so a cached Build would always miss and the artifacts would point at nothing.
        val recordedValues = fields["values"]?.let { runCatching { json.decodeFromString(valuesSerializer, it) } }
            ?.getOrNull().orEmpty()
        val context = driver.context().let { it.copy(runtimeValues = it.runtimeValues + recordedValues) }
        val valid = fields["input"] == cacheKey && fields["output"] == outputDigest(driver, stage.outputs(context))
        if (valid) driver.runtimeValues.putAll(recordedValues)
        return valid
    }

    /** A stage's cache file: its input key, its outputs' digest, and the run's values when it ran. */
    private fun cacheRecord(cacheKey: String, outputDigest: String, driver: Driver): String {
        val values = driver.runtimeValues.filter { (k, v) ->
            k != OUTPUT_DIGEST_SCRATCH && v.length <= CACHED_VALUE_LIMIT
        }
        return "input=$cacheKey\noutput=$outputDigest\nvalues=${json.encodeToString(valuesSerializer, values)}\n"
    }

    /** sha256 over the sorted `sha256sum` lines of every declared output; empty outputs digest to a constant. */
    private suspend fun outputDigest(driver: Driver, outputs: List<String>): String? {
        val lines =
            outputs.sorted().map { rel ->
                val result =
                    executionPort.execute(
                        driver.ws,
                        StepCommand("sha256sum", listOf(driver.baseContext.abs(rel)), driver.ws.linuxPath),
                    )
                if (result.exitCode != 0) return null
                "${result.stdout.digestToken()} $rel"
            }
        return CacheKeys.sha256(lines.joinToString("\n"))
    }

    private fun resolvePinnedCommand(driver: Driver, cmd: StepCommand): StepCommand {
        val pinned = driver.run.pinnedTools[cmd.toolId]
        val isCatalogTool =
            ToolGroupCatalog.DEFAULT_GROUPS.any { group ->
                group.outputs.any { it.toolId == cmd.toolId || cmd.toolId in it.aliases }
            }
        if (isCatalogTool) {
            check(pinned != null && pinned.startsWith("/") && "/current/" !in pinned && !pinned.endsWith("/current")) {
                "Required tool '${cmd.toolId}' has no immutable pin in run ${driver.runId}"
            }
        }
        return if (pinned != null) cmd.copy(toolId = pinned) else cmd
    }

    private suspend fun collectArtifacts(driver: Driver): List<Artifact> {
        val ctx = driver.context()
        val zipRel = BuildFlashableZipStage.zipRelPath(ctx)
        val sha = ctx.value(RuntimeKeys.ZIP_SHA256)?.digestToken()
        // Only what this run built: a run stopped before Build & Sign has no package and no manifest.
        val ran = stagesOf(driver.run).map { it.id }
        if (StageId.BUILD_FLASHABLE_ZIP !in ran) return emptyList()
        val built = listOf(
            Artifact(
                ArtifactKind.FLASHABLE_ZIP,
                ctx.abs(zipRel),
                ctx.longValue(RuntimeKeys.ZIP_SIZE),
                sha,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ),
            Artifact(
                ArtifactKind.CHECKSUM,
                ctx.abs("$zipRel.sha256"),
                null,
                sha,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ),
        )
        val manifest = Artifact(
            ArtifactKind.OTA_MANIFEST,
            ctx.abs("out/manifest.json"),
            null,
            null,
            VerificationState.VERIFIED,
            Presence.PRESENT,
        )
        return if (StageId.GENERATE_OTA_MANIFEST in ran) built + manifest else built
    }

    private fun cacheFile(stage: StageDefinition) = ".cache/${stage.id.name}.key"

    /**
     * A stage that runs rewrites the shared work tree, so what the stages after it left there is gone, whether or not
     * this run reaches them (`throughStage` stops it early): their cached results are cleared, or a later run would
     * skip them and build on a tree without their changes. An empty record never matches a key.
     */
    private suspend fun invalidateLaterStages(driver: Driver, stage: StageDefinition) {
        val all = registry.executableStages()
        all.drop(all.indexOfFirst { it.id == stage.id } + 1).forEach { later ->
            executionPort.uploadFile(driver.ws, cacheFile(later), ByteArray(0))
        }
    }

    private companion object {
        const val PERSISTED_VALUE_LIMIT = 512
        const val CACHED_VALUE_LIMIT = 4096
        val json = Json
        val valuesSerializer = MapSerializer(String.serializer(), String.serializer())
        val STILL_RUNNING = setOf("RUNNING", "QUEUED")
        const val STREAM_RETRY_MS = 500L
        const val OUTPUT_BATCH_SIZE = 500
        const val OUTPUT_BATCH_MS = 1_000L
        const val OUTPUT_DIGEST_SCRATCH = "orchestrator.outputDigest"

        fun Instant.yyyymmdd(): String {
            val d = toLocalDateTime(TimeZone.UTC).date
            return "%04d%02d%02d".format(d.year, d.monthNumber, d.dayOfMonth)
        }

        fun Instant.millisSince(other: Instant): Long = toEpochMilliseconds() - other.toEpochMilliseconds()

        fun BuildRun.withStage(index: Int, mutate: (StageOutcome) -> StageOutcome): BuildRun =
            copy(stages = stages.mapIndexed { i, s -> if (i == index) mutate(s) else s })

        fun BuildRun.markRemainingNotRun(from: Int): BuildRun =
            copy(stages = stages.mapIndexed { i, s -> if (i >= from) s.copy(state = StageState.NOT_RUN) else s })
    }
}
