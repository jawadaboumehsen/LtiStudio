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
import org.ide.lti.core.domain.pipeline.stages.AbOtaSteps
import org.ide.lti.core.domain.pipeline.stages.BuildChecks
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.text.AvbSizeCalculator
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.ports.appendCaptured
import org.ide.lti.core.domain.ports.requireNoLinkInWorkspace
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
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.PackageType
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
    /** The app is closing: the service stops under the running step, which says nothing about the build. */
    private val appClosing: () -> Boolean = { false },
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

        /** Each stage's output files and their digests as last read (cache check or after running). */
        val outputFiles: MutableMap<StageId, Map<String, String>> = mutableMapOf()

        /** The work-tree stages' cache decision, made once per run when the first of them is reached. */
        var workGroup: WorkGroupCheck? = null

        /** Why the work-tree stages run, once: only the first of them reports it. */
        fun takeWorkGroupReason(): String? = workGroup?.reason?.also { workGroup = workGroup?.copy(reason = null) }

        fun context(): StageContext = baseContext.copy(
            runtimeValues = runtimeValues.toMap(),
            toolPaths = run.pinnedTools,
        )

        /** One atomic change of the stored run: a cancel written meanwhile is kept, never overwritten. */
        suspend fun persist(mutate: (BuildRun) -> BuildRun) {
            val next = mutate(
                run,
            ).copy(runtimeValues = runtimeValues.filterValues { it.length <= PERSISTED_VALUE_LIMIT })
            run = runRepository.update(runId) { stored ->
                when {
                    // CancelRunUseCase may already have completed cancellation while a host step was running.
                    stored.state == RunState.CANCELLED -> stored

                    stored.state == RunState.CANCELLING && next.state != RunState.CANCELLED ->
                        next.copy(state = RunState.CANCELLING)

                    else -> next
                }
            } ?: next.also { runRepository.upsert(it) }
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

    /** Whether this app drives [runId] now: a cancel then waits for this driver to stop. */
    public fun isDriving(runId: String): Boolean = runId in driving

    /** A queued run whose driver could not start is failed, never left queued (it would block every start). */
    private suspend fun failQueued(runId: String, cause: Throwable): Nothing {
        runRepository.update(runId) {
            if (it.state == RunState.QUEUED) it.copy(state = RunState.FAILED, endedAt = clock.now()) else it
        }
        throw cause
    }

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
        // A refused admission (a tool being changed) fails a queued run too, as a pin failure does.
        val pins = try {
            if (notStarted && toolStore != null) {
                toolUsageCoordinator?.admitJob(runId) {
                    toolStore.paths(ToolGroupCatalog).getOrElse { cause ->
                        failQueued(runId, IllegalStateException("Cannot capture tool paths for this ROM run", cause))
                    }
                } ?: toolStore.paths(ToolGroupCatalog).getOrElse { cause ->
                    failQueued(runId, IllegalStateException("Cannot capture tool paths for this ROM run", cause))
                }
            } else {
                toolUsageCoordinator?.admitJob(runId) { null }
                null
            }
        } catch (e: IllegalStateException) {
            failQueued(runId, e)
        }
        // Admission succeeded: every later initialization failure must release our job.
        // A duplicate admission throws above, outside this cleanup boundary.
        try {
            val pinnedPaths = pins?.paths.orEmpty().toMap()
            val pinnedVersions = pins?.versions.orEmpty().toMap()
            if (toolStore != null) {
                val capturedPaths = existing?.pinnedTools?.takeIf { it.isNotEmpty() } ?: pinnedPaths
                val immutable = capturedPaths.isNotEmpty() &&
                    capturedPaths.values.all { it.startsWith("/") && "/current/" !in it && !it.endsWith("/current") }
                if (!immutable) {
                    failQueued(
                        runId,
                        IllegalStateException("Cannot admit a ROM run without immutable tool pins"),
                    )
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
                    if (!upstreamExecuted && isCached(driver, stages, index, cacheKey, previousKey)) {
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

                    // Read before the stage clears its record: what changed since it last ran.
                    val inputs = cacheInputs(driver, stage, previousKey)
                    val whyRan = driver.takeWorkGroupReason() ?: whyRan(driver, stage, inputs, upstreamExecuted)
                    driver.persist { it.withStage(index) { s -> s.copy(whyRan = whyRan) } }
                    val failure =
                        runCatching {
                            // Before anything is cleared: a stage writes these folders from its first step.
                            executionPort.requireNoLinkInWorkspace(driver.ws)
                            invalidateLaterStages(driver, stage)
                            executeStage(driver, stage, isResumeStage = false)
                        }.exceptionOrNull()
                            ?.also { if (it is CancellationException) throw it }
                            ?: outputDigestOrFailure(driver, stage)
                    if (failure != null) {
                        if (closedByApp(driver)) {
                            handleInterruption(driver, index, stage, "The app was closed during this stage")
                            return@flow
                        }
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
                        cacheRecord(cacheKey, outputDigest, driver, inputs, driver.outputFiles[stage.id].orEmpty())
                            .encodeToByteArray(),
                    )
                    recordWorkTree(driver, stages, index)
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
                    if (!upstreamExecuted && isCached(driver, stages, index, cacheKey, previousKey)) {
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

                    // Read before the stage clears its record: what changed since it last ran.
                    val inputs = cacheInputs(driver, stage, previousKey)
                    val whyRan = driver.takeWorkGroupReason() ?: whyRan(driver, stage, inputs, upstreamExecuted)
                    driver.persist { it.withStage(index) { s -> s.copy(whyRan = whyRan) } }
                    val failure =
                        runCatching {
                            // Before anything is cleared: a stage writes these folders from its first step.
                            executionPort.requireNoLinkInWorkspace(driver.ws)
                            invalidateLaterStages(driver, stage)
                            executeStage(driver, stage, isResumeStage = (index == startStageIndex))
                        }.exceptionOrNull()
                            ?.also { if (it is CancellationException) throw it }
                            ?: outputDigestOrFailure(driver, stage)

                    if (failure != null) {
                        if (closedByApp(driver)) {
                            handleInterruption(driver, index, stage, "The app was closed during this stage")
                            return@flow
                        }
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
                        cacheRecord(cacheKey, outputDigest, driver, inputs, driver.outputFiles[stage.id].orEmpty())
                            .encodeToByteArray(),
                    )
                    recordWorkTree(driver, stages, index)
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

    /** A run the person cancelled stays cancelled, even when they then close the app. */
    private suspend fun closedByApp(driver: Driver): Boolean =
        appClosing() && runRepository.getRun(driver.runId)?.state != RunState.CANCELLING

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
            outputDigest(driver, stage.outputs(driver.context()), stage.id)
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
        clearCache(driver, stage)
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
            val stdout = StringBuilder().takeIf { step.keepsOutput() }
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

            return finishTool(driver, index, step, stdout?.toString().orEmpty().trim(), exitCode)
        }

        val succeeded = status.status == "COMPLETED" || (status.exitCode != null && status.exitCode == 0)
        val failed = status.status == "FAILED" || (status.exitCode != null && status.exitCode != 0)
        if (!succeeded && !failed) {
            throw StageInterruptedFailure(
                "Unknown status '${status.status}' for step ${step.label}",
            )
        }
        // Finished while no driver watched: recorded, then judged exactly as a step that ran now.
        val code = status.exitCode ?: if (succeeded) 0 else -1
        driver.persist { r ->
            r.copy(
                steps = r.steps.map {
                    if (it.stageId == stage.id && it.index == index) {
                        it.copy(
                            state = if (code == 0) StepState.COMPLETED else StepState.FAILED,
                            exitCode = code,
                            lastSeq = status.lastSeq,
                        )
                    } else {
                        it
                    }
                },
            )
        }
        // Its recorded result when the run kept it; else this step's own output, replayed by the service (not the
        // whole run's log), when it is used.
        val recordedOutput = step.resultKey?.let { driver.runtimeValues[it] }
        val output = recordedOutput ?: if (step.keepsOutput()) {
            val out = StringBuilder()
            executionPort.observe(serverRunId, 1L).collect { event ->
                if (event is StepEvent.Output && !event.isError) out.appendCaptured(event.text)
            }
            out.toString().trim()
        } else {
            ""
        }
        return finishTool(driver, index, step, output, code)
    }

    /**
     * Reads a step's output to its end; returns its exit code and last sequence number. A stream can end without
     * its Finished event (a step done in milliseconds, a dropped connection): then the service's record of the
     * step decides, and a step still running is read on from where it was.
     */
    private suspend fun streamToEnd(
        driver: Driver,
        runId: String,
        stdout: StringBuilder?,
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
                        if (!event.isError) stdout?.appendCaptured(event.text)
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

        val stdout = StringBuilder().takeIf { step.keepsOutput() }
        val outputBuffer = StepOutputBuffer(driver, stage.id)
        val (exitCode, lastSeq) = try {
            streamToEnd(driver, handle.runId, stdout, outputBuffer)
        } catch (e: CancellationException) {
            // The app is closing: the step goes on and a later session reattaches to it.
            throw e
        } catch (e: Exception) {
            // The run fails here: its step must not go on changing the work tree behind it.
            runCatching { executionPort.cancel(handle.runId) }
            throw e
        }
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

        return finishTool(driver, index, step, stdout?.toString().orEmpty().trim(), exitCode)
    }

    /**
     * A tool step's end, the same whether it ran now or is reconciled on resume: its result and exit code recorded,
     * then its exit code and expected output checked. Returns true when a result was captured (caller re-plans).
     */
    private fun finishTool(
        driver: Driver,
        index: Int,
        step: PipelineStep.Tool,
        output: String,
        exitCode: Int,
    ): Boolean {
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

    /** Only a step whose output is used keeps it: a log-only step's (possibly huge) output just streams. */
    private fun PipelineStep.Tool.keepsOutput() = resultKey != null || expectStdoutContains.isNotEmpty()

    /**
     * Records that [step] started, under the key this returns. A host step is not all-or-nothing: finding that record
     * on a resume means what it did is in the tree, and a second go would apply it on top of that.
     */
    private suspend fun markHostStarted(driver: Driver, step: PipelineStep.Host): String {
        val started = "host.started.${step.resultKey}"
        if (driver.runtimeValues.containsKey(started)) {
            executionPort.uploadFile(driver.ws, stageCacheFile(StageId.WORK_TREE_ASSEMBLY), ByteArray(0))
            throw StageInterruptedFailure("${step.label} was cut off part way: start the build again")
        }
        driver.runtimeValues[started] = "true"
        driver.persist { it }
        return started
    }

    /** Returns true (re-plan) once the result is recorded; a result already present is never redone. */
    private suspend fun runHost(driver: Driver, stage: StageDefinition, index: Int, step: PipelineStep.Host): Boolean {
        if (driver.runtimeValues.containsKey(step.resultKey)) return true
        val started = markHostStarted(driver, step)
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
        driver.runtimeValues -= started
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
            (stage.requiredToolIds + stage.optionalToolIds).sorted().joinToString(";") { toolId ->
                val pin = driver.run.pinnedTools[toolId] ?: ""
                val ver = driver.run.toolVersions[toolId] ?: ""
                "$toolId=$pin@$ver"
            }
        val files = stage.cacheInputFiles(driver.context()).sorted().map { rel -> "$rel=${fileDigest(driver, rel)}" }
            .joinToString(";")
        // The pipeline's own version too: when a stage's commands change, its old outputs are not reused.
        return CacheKeys.sha256("$baseCacheKey|$stageToolsFingerprint|$files|${CacheKeys.PIPELINE_VERSION}")
    }

    /** A cache input's sha256, or "missing": a key file that appears later still changes the key. */
    private suspend fun fileDigest(driver: Driver, rel: String): String {
        val cmd = StepCommand("sha256sum", listOf(driver.baseContext.abs(rel)), driver.ws.linuxPath)
        val result = executionPort.execute(driver.ws, cmd)
        return if (result.exitCode == 0) result.stdout.digestToken() else MISSING
    }

    /** Whether the stage at [index] comes from cache: the work-tree stages decide together ([workGroupCheck]). */
    private suspend fun isCached(
        driver: Driver,
        stages: List<StageDefinition>,
        index: Int,
        cacheKey: String,
        previousKey: String,
    ): Boolean {
        val stage = stages[index]
        if (stage.id !in WORK_GROUP) return isCacheValid(driver, stage, cacheKey)
        val check = driver.workGroup
            ?: workGroupCheck(driver, stages, index, previousKey).also { driver.workGroup = it }
        return check.cachedThrough?.let { order(stage.id) <= order(it) } == true
    }

    /**
     * Assemble, Debloat and the mods edit one work tree and cannot be applied twice to it (a text patch would land
     * again, an app picked back could not come back). `WORK_RECORD` says which stage the last run left work/ after:
     * the work-tree stages up to it come from cache when each one's inputs match its record and work/ is still what
     * that run left; the ones after it run on that tree. A run that stops before it needs a tree assembled again.
     * Their own output files are not compared: the later stages edit Assemble's sidecars and build.prop.
     */
    private suspend fun workGroupCheck(
        driver: Driver,
        stages: List<StageDefinition>,
        index: Int,
        previousKey: String,
    ): WorkGroupCheck {
        val before = driver.runtimeValues.toMap()
        val recorded = executionPort.readFile(driver.ws, WORK_RECORD)?.trim().orEmpty()
        val leftAfter = StageId.entries.firstOrNull { it.name == recorded.substringBefore(' ') }
        val last = lastWorkEditor(stages, index)
        val stopsBefore = leftAfter != null && last != null && order(last) < order(leftAfter)
        val group = if (leftAfter == null || last == null || stopsBefore) {
            emptyList()
        } else {
            stages.drop(index).takeWhile { it.id in WORK_GROUP && order(it.id) <= order(leftAfter) }
        }
        val stale = staleStage(driver, group, previousKey)
        val unchanged = group.isNotEmpty() && stale == null &&
            recorded.substringAfter(' ') == workTreeFingerprint(driver)
        if (unchanged) return WorkGroupCheck(cachedThrough = group.last().id, reason = null)
        driver.runtimeValues.clear()
        driver.runtimeValues.putAll(before)
        val reason = when {
            stopsBefore -> "work/ holds what ${leftAfter?.let(::label)} did, and this run stops before it"

            // The first stage says why itself; a later one is why the whole group runs again.
            stale != null -> stale.first.takeUnless { it == stages[index] }?.let { s ->
                val why = whyRan(driver, s, cacheInputs(driver, s, stale.second), upstreamExecuted = false)
                "${label(s.id)} must run ($why), and it needs a freshly assembled work/"
            }

            group.isEmpty() -> null

            else -> "work/ changed since the last run left it (a toolkit edit, or a run that stopped part way)"
        }
        return WorkGroupCheck(cachedThrough = null, reason = reason)
    }

    /** The first of [group] whose record does not match its inputs, and the key before it; null when all match. */
    private suspend fun staleStage(
        driver: Driver,
        group: List<StageDefinition>,
        previousKey: String,
    ): Pair<StageDefinition, String>? {
        var key = previousKey
        for (stage in group) {
            val previous = key
            key = computeStageCacheKey(driver, stage, previous)
            if (!isCacheValid(driver, stage, key, compareOutputs = false)) return stage to previous
        }
        return null
    }

    private fun order(id: StageId): Int = registry.executableStages().indexOfFirst { it.id == id }

    /** After the last stage of this run that edits work/, what it left there (read back by [workGroupCheck]). */
    private suspend fun recordWorkTree(driver: Driver, stages: List<StageDefinition>, index: Int) {
        val stage = stages[index]
        if (stage.id !in WORK_EDITORS || lastWorkEditor(stages, index) != stage.id) return
        val fingerprint = workTreeFingerprint(driver) ?: return
        executionPort.uploadFile(driver.ws, WORK_RECORD, "${stage.id.name} $fingerprint\n".encodeToByteArray())
    }

    /** The last stage from [index] on, in this run, that edits work/ (Build edits it too, idempotently). */
    private fun lastWorkEditor(stages: List<StageDefinition>, index: Int): StageId? =
        stages.drop(index).lastOrNull { it.id in WORK_EDITORS }?.id

    /** Content fingerprint of work/ without work/package (Build's own output); null when it cannot be read. */
    private suspend fun workTreeFingerprint(driver: Driver): String? {
        val cmd = StepCommand(
            "sh",
            listOf("-c", WORK_FINGERPRINT_SCRIPT, "sh", driver.baseContext.abs("work")),
            driver.ws.linuxPath,
        )
        val result = executionPort.execute(driver.ws, cmd)
        return result.stdout.trim().takeIf { result.exitCode == 0 && it.isNotEmpty() }
    }

    private fun label(id: StageId): String = when (id) {
        StageId.WORK_TREE_ASSEMBLY -> "Assemble"
        StageId.DEBLOAT -> "Debloat"
        StageId.MODULE_APPLICATION -> "Patches & mods"
        StageId.BUILD_FLASHABLE_ZIP -> "Build & sign"
        else -> id.name
    }

    private suspend fun isCacheValid(
        driver: Driver,
        stage: StageDefinition,
        cacheKey: String,
        compareOutputs: Boolean = true,
    ): Boolean {
        val recorded = executionPort.readFile(driver.ws, cacheFile(stage)) ?: return false
        val fields =
            recorded.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { it[0].trim() to it[1].trim() }
        // The values the stage ran with (its run date names the package): today's date would name a file that
        // does not exist, so a cached Build would always miss and the artifacts would point at nothing.
        val recordedValues = fields["values"]?.let { runCatching { json.decodeFromString(valuesSerializer, it) } }
            ?.getOrNull().orEmpty()
        val context = driver.context().let { it.copy(runtimeValues = it.runtimeValues + recordedValues) }
        val valid = fields["input"] == cacheKey &&
            (!compareOutputs || fields["output"] == outputDigest(driver, stage.outputs(context), stage.id)) &&
            stage.requiredPaths(context).all { executionPort.stat(driver.ws, it).exists }
        // The run's own identity stays this run's: only a cached Build brings back the date its package is named
        // by; the device revision a resume is checked against is never an older run's.
        if (valid) {
            val runOwned = if (stage.id == StageId.BUILD_FLASHABLE_ZIP) RUN_DEVICE else RUN_DEVICE + RUN_DATE_KEYS
            driver.runtimeValues.putAll(recordedValues - runOwned)
        }
        return valid
    }

    /** A stage's cache file: its input key, its outputs' digest, and the run's values when it ran. */
    private fun cacheRecord(
        cacheKey: String,
        outputDigest: String,
        driver: Driver,
        inputs: Map<String, String>,
        outputs: Map<String, String>,
    ): String {
        val values = driver.runtimeValues.filter { (k, v) ->
            k != OUTPUT_DIGEST_SCRATCH && v.length <= CACHED_VALUE_LIMIT
        }
        return "input=$cacheKey\noutput=$outputDigest\nvalues=${json.encodeToString(valuesSerializer, values)}\n" +
            "inputs=${json.encodeToString(valuesSerializer, inputs)}\n" +
            "outputs=${json.encodeToString(valuesSerializer, outputs)}\n"
    }

    /**
     * What a stage's cache key is made of, by name, so a later run can say what changed: the pipeline version, the
     * earlier stage's key, the pinned tools, the input files and the stage's own settings, field by field.
     */
    private suspend fun cacheInputs(driver: Driver, stage: StageDefinition, previousKey: String): Map<String, String> {
        val ctx = driver.context()
        val tools = (stage.requiredToolIds + stage.optionalToolIds).associate { id ->
            "tool $id" to "${driver.run.pinnedTools[id].orEmpty()}@${driver.run.toolVersions[id].orEmpty()}"
        }
        val files = stage.cacheInputFiles(ctx).associate { rel -> "file $rel" to fileDigest(driver, rel) }
        return mapOf("pipeline version" to CacheKeys.PIPELINE_VERSION, "earlier stage" to previousKey.take(KEY_SHOWN)) +
            tools + files + StageInputs.settings(stage.id, ctx.snapshot, json) +
            ("device" to CacheKeys.sha256(json.encodeToString(TargetDevice.serializer(), ctx.target)).take(KEY_SHOWN))
    }

    /** Why [stage] runs instead of coming from cache (`StageOutcome.whyRan`). */
    private suspend fun whyRan(
        driver: Driver,
        stage: StageDefinition,
        inputs: Map<String, String>,
        upstreamExecuted: Boolean,
    ): String {
        val recorded = if (upstreamExecuted) "" else executionPort.readFile(driver.ws, cacheFile(stage)).orEmpty()
        val fields = recorded.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim() to it[1].trim() }
        fun decoded(key: String) =
            fields[key]?.let { runCatching { json.decodeFromString(valuesSerializer, it) }.getOrNull() }
        val before = decoded("inputs")
        return when {
            upstreamExecuted -> "an earlier stage ran in this run, and they share work/"

            fields["input"].isNullOrEmpty() -> "it has no cache record (first run, or an earlier run cleared it)"

            before == null -> "its cache record is older than the reasons kept now"

            else -> StageInputs.changes(before, inputs)
                .ifEmpty { changedOutputs(decoded("outputs").orEmpty(), driver.outputFiles[stage.id].orEmpty()) }
                .joinToString("; ")
        }
    }

    /** sha256 over the sorted `sha256sum` lines of every declared output; empty outputs digest to a constant. */
    private suspend fun outputDigest(driver: Driver, outputs: List<String>, stage: StageId): String? {
        val files = outputs.sorted().associateWith { rel -> fileDigest(driver, rel) }
        driver.outputFiles[stage] = files
        if (MISSING in files.values) return null
        return CacheKeys.sha256(files.entries.joinToString("\n") { (rel, digest) -> "$digest $rel" })
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
        val type = ctx.snapshot.build.packageType
        // With both packages the flashable zip is the main one and the OTA comes with it.
        val ota = listOfNotNull(
            Artifact(
                ArtifactKind.AB_OTA_ZIP,
                ctx.abs(BuildFlashableZipStage.otaRelPath(ctx)),
                ctx.longValue(AbOtaSteps.SIZE),
                ctx.value(AbOtaSteps.SHA256)?.digestToken(),
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ).takeIf { type == PackageType.BOTH },
        )
        val built = ota + listOf(
            Artifact(
                if (type == PackageType.AB_OTA) ArtifactKind.AB_OTA_ZIP else ArtifactKind.FLASHABLE_ZIP,
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
        // Fastboot images zip and Odin AP: listed (and found missing) like the main package.
        val extras = BuildChecks.extraPackageFiles(ctx).map {
            Artifact(ArtifactKind.EXTRA_PACKAGE, ctx.abs(it), null, null, VerificationState.VERIFIED, Presence.PRESENT)
        }
        return (if (StageId.GENERATE_OTA_MANIFEST in ran) built + manifest else built) + extras
    }

    private fun cacheFile(stage: StageDefinition) = stageCacheFile(stage.id)

    /**
     * A stage that runs rewrites the shared work tree, so what the stages after it left there is gone, whether or not
     * this run reaches them (`throughStage` stops it early): their cached results are cleared, or a later run would
     * skip them and build on a tree without their changes. An empty record never matches a key.
     */
    private suspend fun invalidateLaterStages(driver: Driver, stage: StageDefinition) {
        val all = registry.executableStages()
        all.drop(all.indexOfFirst { it.id == stage.id } + 1).forEach { later -> clearCache(driver, later) }
    }

    /** Fails when the record stays: work must not start while a later run could still skip it on that record. */
    private suspend fun clearCache(driver: Driver, stage: StageDefinition) {
        check(executionPort.uploadFile(driver.ws, cacheFile(stage), ByteArray(0))) {
            "Could not clear the cached result of ${stage.id}"
        }
    }

    private companion object {
        const val PERSISTED_VALUE_LIMIT = 512
        const val CACHED_VALUE_LIMIT = 4096
        val json = Json
        val valuesSerializer = MapSerializer(String.serializer(), String.serializer())
        val STILL_RUNNING = setOf("RUNNING", "QUEUED")

        val RUN_DEVICE = setOf(RuntimeKeys.DEVICE_REVISION)
        val RUN_DATE_KEYS = setOf(RuntimeKeys.RUN_DATE, RuntimeKeys.RUN_TIMESTAMP)
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

private const val KEY_SHOWN = 12

/** Stages that edit work/ and run together from Assemble (see `workGroupCheck`). */
private val WORK_GROUP = setOf(StageId.WORK_TREE_ASSEMBLY, StageId.DEBLOAT, StageId.MODULE_APPLICATION)

/** Every stage that edits work/: the group, and Build (it replaces its `ro.lti.*` lines, adds fstab lines once). */
private val WORK_EDITORS = WORK_GROUP + StageId.BUILD_FLASHABLE_ZIP

/** `<last editing stage> <fingerprint>` of work/ as the last run left it. */
private const val WORK_RECORD = ".cache/work.fp"

/** `$1` work/: every entry's path, kind, size, mode and link, and every file's md5, without package/. */
private const val WORK_FINGERPRINT_SCRIPT = """cd "$1" || exit 1
{ find . -path ./package -prune -o -printf '%P\t%y\t%s\t%m\t%l\n' | LC_ALL=C sort
  find . -path ./package -prune -o -type f -print0 | xargs -0 -r -P8 -n64 md5sum | LC_ALL=C sort
} | sha256sum | cut -d' ' -f1"""

/** The work-tree stages' cache decision: those up to [cachedThrough] come from cache; [reason] is why they run. */
private data class WorkGroupCheck(val cachedThrough: StageId?, val reason: String?)
private const val OUTPUTS_SHOWN = 3
private const val MISSING = "missing"

/** The workspace file that holds [id]'s cache record; an empty record never matches a key. */
internal fun stageCacheFile(id: StageId): String = ".cache/${id.name}.key"

/** The output files whose digest differs from the cache record's: a later stage edited them, or they are gone. */
internal fun changedOutputs(recorded: Map<String, String>, now: Map<String, String>): List<String> {
    val changed = now.filter { (rel, digest) -> recorded[rel] != null && recorded[rel] != digest }.keys
    if (changed.isEmpty()) return listOf("its outputs changed or are missing")
    val named = changed.take(OUTPUTS_SHOWN).joinToString() +
        if (changed.size > OUTPUTS_SHOWN) " and ${changed.size - OUTPUTS_SHOWN} more" else ""
    return listOf("$named changed after it ran (a later stage edits them) or are missing")
}
