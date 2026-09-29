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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.text.AvbSizeCalculator
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.repository.run.RunRepository
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
    public data class StageFailed(
        val stageId: StageId,
        val stepIndex: Int,
        val exitCode: Int,
        val message: String,
    ) : PipelineEvent
    public data class StageCancelled(val stageId: StageId) : PipelineEvent
    public data class StageInterrupted(val stageId: StageId) : PipelineEvent
    public data class RunPaused(val stageId: StageId) : PipelineEvent
    public data class RunResumed(val stageId: StageId) : PipelineEvent
    public data class StepOutput(val stageId: StageId, val text: String) : PipelineEvent
    public data class RunFinished(val state: RunState) : PipelineEvent
}

/**
 * Drives the six stages sequentially for one run. Each stage is planned from a [StageContext];
 * after every step that captures a result the stage is re-planned so later steps see real values
 * (sizes, digests, probe results). Cache keys skip a stage only when the recorded input digest
 * matches **and** the recorded output digest still matches the current outputs.
 */
@Suppress("LargeClass")
public class PipelineOrchestrator(
    private val executionPort: PipelineExecutionPort,
    private val runRepository: RunRepository,
    private val vendoredFiles: VendoredFilePort,
    private val registry: PipelineDefinitionRegistry = PipelineDefinitionRegistry(),
    private val clock: Clock = Clock.System,
) {
    private val stages: List<StageDefinition> get() = registry.executableStages()
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

        fun context(): StageContext = baseContext.copy(runtimeValues = runtimeValues.toMap())

        suspend fun persist(mutate: (BuildRun) -> BuildRun) {
            run = mutate(run).copy(runtimeValues = runtimeValues.filterValues { it.length <= PERSISTED_VALUE_LIMIT })
            runRepository.upsert(run)
        }
    }

    public fun drive(runId: String, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> = flow {
        val now = clock.now()
        val existing = runRepository.getRun(runId)
        val startedAt = existing?.startedAt ?: now
        if (existing == null) {
            runRepository.upsert(BuildRun(runId, ctx.workspace.id, ctx.snapshot.id, startedAt = startedAt))
        }
        check(runRepository.claimDriver(runId, driverInstanceId, now.toEpochMilliseconds())) {
            "Cannot drive run $runId: lease held by another active driver"
        }
        val outcomes = stages.map { stage ->
            existing?.stages?.firstOrNull { it.stageId == stage.id } ?: StageOutcome(stageId = stage.id)
        }
        val runtimeValues = (existing?.runtimeValues.orEmpty() + ctx.runtimeValues).toMutableMap()
        runtimeValues.putIfAbsent(RuntimeKeys.RUN_DATE, startedAt.yyyymmdd())
        runtimeValues.putIfAbsent(RuntimeKeys.RUN_TIMESTAMP, startedAt.epochSeconds.toString())

        val driver = Driver(
            runId = runId,
            driverInstanceId = driverInstanceId,
            collector = this,
            run = (existing ?: BuildRun(runId, ctx.workspace.id, ctx.snapshot.id, startedAt = startedAt)),
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
            for ((index, stage) in stages.withIndex()) {
                val startedStage = clock.now()
                emit(PipelineEvent.StageStarted(stage.id))
                driver.persist { it.withStage(index) { s -> s.copy(state = StageState.RUNNING, startedAt = startedStage) } }
                runRepository.heartbeatDriver(runId, driverInstanceId, startedStage.toEpochMilliseconds())

                val cacheKey = stage.computeCacheKey(driver.context(), previousKey)
                if (isCacheValid(driver, stage, cacheKey)) {
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

                val failure = runCatching { executeStage(driver, stage, isResumeStage = false) }.exceptionOrNull()
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
                    "input=$cacheKey\noutput=$outputDigest\n".encodeToByteArray(),
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
    }

    public fun resume(run: BuildRun, ctx: StageContext, driverInstanceId: String): Flow<PipelineEvent> = flow {
        val now = clock.now()
        check(runRepository.claimDriver(run.id, driverInstanceId, now.toEpochMilliseconds())) {
            "Cannot drive run ${run.id}: lease held by another active driver"
        }

        if (run.state.isTerminal) {
            emit(PipelineEvent.RunFinished(run.state))
            return@flow
        }

        val latestRun = runRepository.getRun(run.id) ?: run
        val wasPaused = latestRun.state == RunState.PAUSED_WAITING_FOR_APP
        val startedAt = latestRun.startedAt
        val runtimeValues = (latestRun.runtimeValues + ctx.runtimeValues).toMutableMap()
        runtimeValues.putIfAbsent(RuntimeKeys.RUN_DATE, startedAt.yyyymmdd())
        runtimeValues.putIfAbsent(RuntimeKeys.RUN_TIMESTAMP, startedAt.epochSeconds.toString())

        val outcomes = stages.map { stage ->
            latestRun.stages.firstOrNull { it.stageId == stage.id } ?: StageOutcome(stageId = stage.id)
        }

        val driver = Driver(
            runId = latestRun.id,
            driverInstanceId = driverInstanceId,
            collector = this,
            run = latestRun.copy(stages = outcomes),
            runtimeValues = runtimeValues,
        )
        driver.baseContext = ctx

        val startStageIndex = stages.indexOfFirst { stage ->
            val outcome = driver.run.stages.firstOrNull { it.stageId == stage.id }
            outcome == null || (outcome.state != StageState.EXECUTED && outcome.state != StageState.SKIPPED)
        }

        if (startStageIndex == -1) {
            val artifacts = collectArtifacts(driver)
            driver.persist { it.copy(state = RunState.SUCCEEDED, endedAt = clock.now(), artifacts = artifacts) }
            emit(PipelineEvent.RunFinished(RunState.SUCCEEDED))
            return@flow
        }

        var previousKey = if (startStageIndex > 0) {
            driver.run.stages.getOrNull(startStageIndex - 1)?.cacheKey.orEmpty()
        } else {
            ""
        }

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
                    driver.persist { it.withStage(index) { s -> s.copy(state = StageState.RUNNING, startedAt = startedStage) } }
                }
                runRepository.heartbeatDriver(driver.runId, driverInstanceId, clock.now().toEpochMilliseconds())

                val cacheKey = stage.computeCacheKey(driver.context(), previousKey)
                if (isCacheValid(driver, stage, cacheKey)) {
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

                val failure = runCatching {
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
                    "input=$cacheKey\noutput=$outputDigest\n".encodeToByteArray(),
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
        val digest = outputDigest(driver, stage.outputs(driver.context()))
            ?: return StageFailure(-1, -1, "Output verification failed for ${stage.id}: a declared output is missing")
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
        var steps = stage.plan(driver.context())
        var index = 0
        while (index < steps.size) {
            val step = steps[index]
            val recorded = if (isResumeStage) {
                driver.run.steps.firstOrNull { it.stageId == stage.id && it.index == index }
            } else {
                null
            }

            val captured = if (recorded != null && recorded.serverRunId != null && step is PipelineStep.Tool) {
                reconcileRecordedToolStep(driver, stage, index, step, recorded)
            } else {
                when (step) {
                    is PipelineStep.Tool -> runTool(driver, stage, index, step)
                    is PipelineStep.WriteFile -> false.also { writeFile(driver, index, step.relPath, step.content) }
                    is PipelineStep.WriteVendoredFile -> false.also { writeVendored(driver, index, step) }
                    is PipelineStep.EditFile -> false.also { editFile(driver, index, step) }
                    is PipelineStep.AvbSizeSearch -> true.also { avbSearch(driver, index, step) }
                    is PipelineStep.Check -> false.also { check(driver, index, step) }
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
            val fromSeq = lastSeq + 1L
            val stdout = StringBuilder()
            var exitCode = -1
            var currentSeq = lastSeq

            executionPort.observe(serverRunId, fromSeq).collect { event ->
                when (event) {
                    is StepEvent.Output -> {
                        currentSeq = maxOf(currentSeq, event.seq)
                        if (!event.isError) stdout.append(event.text)
                        runRepository.appendEvents(driver.runId, listOf(event.text))
                        driver.collector.emit(PipelineEvent.StepOutput(stage.id, event.text))
                    }
                    is StepEvent.Finished -> exitCode = event.exitCode
                    StepEvent.Heartbeat -> runRepository.heartbeatDriver(
                        driver.runId,
                        driver.driverInstanceId,
                        clock.now().toEpochMilliseconds(),
                    )
                }
            }

            driver.persist { r ->
                val updatedSteps = r.steps.map {
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
                throw StageFailure(index, exitCode, "${step.label}: expected '$missing' in ${step.command.toolId} output")
            }
            return step.resultKey != null
        }

        if (status.status == "COMPLETED" || (status.exitCode != null && status.exitCode == 0)) {
            driver.persist { r ->
                val updatedSteps = r.steps.map {
                    if (it.stageId == stage.id && it.index == index) {
                        it.copy(state = StepState.COMPLETED, exitCode = status.exitCode ?: 0, lastSeq = status.lastSeq)
                    } else {
                        it
                    }
                }
                r.copy(steps = updatedSteps)
            }
            if (step.resultKey != null && !driver.runtimeValues.containsKey(step.resultKey)) {
                val cached = runRepository.cachedEvents(driver.runId)
                val out = cached.joinToString("").trim()
                driver.runtimeValues[step.resultKey] = out
                driver.runtimeValues[RuntimeKeys.exitOf(step.resultKey)] = "0"
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

    /** Runs one durable step; returns true when a result was captured (caller re-plans). */
    private suspend fun runTool(driver: Driver, stage: StageDefinition, index: Int, step: PipelineStep.Tool): Boolean {
        val idempotencyKey = StageStep.deriveIdempotencyKey(driver.runId, stage.id, index)
        val handle = executionPort.startStep(driver.ws, step.command, idempotencyKey)
        val initialStep = StageStep(
            stageId = stage.id,
            index = index,
            serverRunId = handle.runId,
            idempotencyKey = idempotencyKey,
            command = step.command,
            state = StepState.RUNNING,
            lastSeq = 0L,
        )
        driver.persist { r ->
            val updated = r.steps.filterNot { it.stageId == stage.id && it.index == index } + initialStep
            r.copy(steps = updated)
        }

        val stdout = StringBuilder()
        var exitCode = -1
        var lastSeq = 0L
        executionPort.observe(handle.runId).collect { event ->
            when (event) {
                is StepEvent.Output -> {
                    lastSeq = maxOf(lastSeq, event.seq)
                    if (!event.isError) stdout.append(event.text)
                    runRepository.appendEvents(driver.runId, listOf(event.text))
                    driver.collector.emit(PipelineEvent.StepOutput(stage.id, event.text))
                }
                is StepEvent.Finished -> exitCode = event.exitCode
                StepEvent.Heartbeat -> runRepository.heartbeatDriver(
                    driver.runId,
                    driver.driverInstanceId,
                    clock.now().toEpochMilliseconds(),
                )
            }
        }

        driver.persist { r ->
            val updated = r.steps.map {
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
        val file = vendoredFiles.load(step.resourceId) ?: throw StageFailure(
            index,
            -1,
            "Vendored file '${step.resourceId}' is missing or its sha256 does not match the pin; " +
                "place the real prebuilt at core/data/src/desktopMain/resources/${step.resourceId} " +
                "with a matching .sha256",
        )
        writeFile(driver, index, step.relPath, file.bytes)
    }

    private suspend fun editFile(driver: Driver, index: Int, step: PipelineStep.EditFile) {
        val original = executionPort.readFile(driver.ws, step.relPath) ?: throw StageFailure(
            index,
            -1,
            "Cannot read ${step.relPath}",
        )
        writeFile(driver, index, step.relPath, step.transform(original).encodeToByteArray())
    }

    private suspend fun avbSearch(driver: Driver, index: Int, step: PipelineStep.AvbSizeSearch) {
        val size = AvbSizeCalculator.findPartitionSize(step.imageSizeBytes) { probe ->
            val result = executionPort.execute(
                driver.ws,
                StepCommand(
                    toolId = "avbtool",
                    args = listOf("add_hashtree_footer", "--partition_size", probe.toString(), "--calc_max_image_size"),
                    workingDir = driver.ws.linuxPath,
                ),
            )
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

    private suspend fun isCacheValid(driver: Driver, stage: StageDefinition, cacheKey: String): Boolean {
        val recorded = executionPort.readFile(driver.ws, cacheFile(stage)) ?: return false
        val fields = recorded.lineSequence().mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { it[0].trim() to it[1].trim() }
        val inputMatches = fields["input"] == cacheKey
        return inputMatches && fields["output"] == outputDigest(driver, stage.outputs(driver.context()))
    }

    /** sha256 over the sorted `sha256sum` lines of every declared output; empty outputs digest to a constant. */
    private suspend fun outputDigest(driver: Driver, outputs: List<String>): String? {
        val lines = outputs.sorted().map { rel ->
            val result = executionPort.execute(
                driver.ws,
                StepCommand("sha256sum", listOf(driver.baseContext.abs(rel)), driver.ws.linuxPath),
            )
            if (result.exitCode != 0) return null
            "${result.stdout.digestToken()} $rel"
        }
        return CacheKeys.sha256(lines.joinToString("\n"))
    }

    private suspend fun collectArtifacts(driver: Driver): List<Artifact> {
        val ctx = driver.context()
        val zipRel = BuildFlashableZipStage.zipRelPath(ctx)
        val sha = ctx.value(RuntimeKeys.ZIP_SHA256)?.digestToken()
        return listOf(
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
            Artifact(
                ArtifactKind.OTA_MANIFEST,
                ctx.abs("out/manifest.json"),
                null,
                null,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ),
        )
    }

    private fun cacheFile(stage: StageDefinition) = ".cache/${stage.id.name}.key"

    private companion object {
        const val PERSISTED_VALUE_LIMIT = 512
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
