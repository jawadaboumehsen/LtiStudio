/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.BuildRunSummary
import org.ide.lti.core.model.run.RunIndex
import org.ide.lti.core.model.run.toSummary
import org.ide.lti.core.model.workspace.DecodeWarning

/**
 * Thread-safe multiplatform persistent data source for build run records.
 *
 * Backed by [Settings] for the lightweight [RunIndex] (`lti_runs_v1`) and [RunFileStorage]
 * for full [BuildRun] JSON state and mirrored event logs (`.events.jsonl`).
 */
public class RunRecordDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val fileStorage: RunFileStorage = defaultRunFileStorage(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    private val _decodeWarnings = MutableSharedFlow<DecodeWarning>(replay = 64, extraBufferCapacity = 64)
    public val decodeWarnings: Flow<DecodeWarning> = _decodeWarnings.asSharedFlow()

    private val _runsIndex = MutableStateFlow(readRunsIndexSafe())
    public val runsIndex: Flow<List<BuildRunSummary>> = _runsIndex.asStateFlow()
    public val currentSummaries: List<BuildRunSummary> get() = _runsIndex.value

    public suspend fun saveRun(run: BuildRun): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val summary = run.toSummary()
            val current = _runsIndex.value.toMutableList()
            val existingIdx = current.indexOfFirst { it.id == run.id }
            if (existingIdx >= 0) {
                current[existingIdx] = summary
            } else {
                current.add(0, summary)
            }
            current.sortByDescending { it.startedAt }

            val encoded = json.encodeToString(run)
            try {
                fileStorage.saveRun(run.id, encoded)
            } catch (e: Exception) {
                // Don't advance the index to point at a run file that failed to write --
                // getRun(run.id) would otherwise silently return null for an "existing" run.
                val warning = DecodeWarning(
                    key = "run_file.${run.id}",
                    quarantineKey = "run_file.${run.id}.quarantine.${System.currentTimeMillis()}",
                    message = "Failed to persist run file: ${e.message}",
                    timestampEpochMs = System.currentTimeMillis(),
                )
                _decodeWarnings.tryEmit(warning)
                throw e
            }

            persistRunsIndexInternal(current)
            _runsIndex.value = current
        }
    }

    public suspend fun updateSummaries(summaries: List<BuildRunSummary>): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            persistRunsIndexInternal(summaries)
            _runsIndex.value = summaries
        }
    }

    public suspend fun getRun(runId: String): BuildRun? = withContext(ioDispatcher) {
        val rawJson = fileStorage.getRun(runId) ?: return@withContext null
        try {
            json.decodeFromString<BuildRun>(rawJson)
        } catch (e: Exception) {
            handleDecodeFailure("run_file.$runId", rawJson, "Failed to decode run file: ${e.message}")
            null
        }
    }

    public suspend fun appendEvents(runId: String, events: List<String>): Unit = withContext(ioDispatcher) {
        fileStorage.appendEvents(runId, events)
    }

    public suspend fun getEvents(runId: String, fromSeq: Long = 0L): List<String> = withContext(ioDispatcher) {
        fileStorage.getEvents(runId, fromSeq)
    }

    public suspend fun deleteRun(runId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _runsIndex.value.filterNot { it.id == runId }
            persistRunsIndexInternal(current)
            _runsIndex.value = current
            fileStorage.deleteRun(runId)
        }
    }

    public suspend fun clearRuns(): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val allIds = _runsIndex.value.map { it.id }
            settings.remove(KEY_RUNS)
            _runsIndex.value = emptyList()
            for (id in allIds) {
                fileStorage.deleteRun(id)
            }
        }
    }

    private fun readRunsIndexSafe(): List<BuildRunSummary> {
        val raw = settings.getStringOrNull(KEY_RUNS)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()

        return try {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<BuildRunSummary>>(trimmed)
            } else {
                json.decodeFromString<RunIndex>(trimmed).items
            }
        } catch (e: Exception) {
            handleDecodeFailure(KEY_RUNS, raw ?: "", "Runs index could not be loaded; a backup was kept")
            emptyList()
        }
    }

    private fun persistRunsIndexInternal(items: List<BuildRunSummary>) {
        try {
            val wrapper = RunIndex(schemaVersion = 1, items = items)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_RUNS] = encoded
        } catch (_: Exception) {
            // Keep in-memory
        }
    }

    private fun handleDecodeFailure(key: String, raw: String, message: String) {
        val timestamp = System.currentTimeMillis()
        val quarantineKey = "$key.quarantine.$timestamp"
        try {
            settings[quarantineKey] = raw
        } catch (_: Exception) {}
        val warning = DecodeWarning(
            key = key,
            quarantineKey = quarantineKey,
            message = message,
            timestampEpochMs = timestamp,
        )
        _decodeWarnings.tryEmit(warning)
    }

    public companion object {
        public const val KEY_RUNS: String = "lti_runs_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
