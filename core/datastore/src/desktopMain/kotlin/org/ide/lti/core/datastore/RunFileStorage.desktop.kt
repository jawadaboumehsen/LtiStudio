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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop JVM implementation of [RunFileStorage] storing runs as JSON files
 * and event logs as JSONL lines in the platform's local application data directory.
 */
public class DesktopRunFileStorage(
    public val baseDir: File = resolveDefaultBaseDir(),
) : RunFileStorage {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    override suspend fun saveRun(runId: String, json: String): Unit = withContext(Dispatchers.IO) {
        val file = File(baseDir, "$runId.json")
        file.parentFile?.mkdirs()
        file.writeText(json, Charsets.UTF_8)
    }

    override suspend fun getRun(runId: String): String? = withContext(Dispatchers.IO) {
        val file = File(baseDir, "$runId.json")
        if (file.exists() && file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            null
        }
    }

    override suspend fun appendEvents(runId: String, events: List<String>): Unit = withContext(Dispatchers.IO) {
        if (events.isEmpty()) return@withContext
        val file = File(baseDir, "$runId.events.jsonl")
        file.parentFile?.mkdirs()
        file.appendText(events.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }

    override suspend fun getEvents(runId: String, fromSeq: Long): List<String> = withContext(Dispatchers.IO) {
        val file = File(baseDir, "$runId.events.jsonl")
        if (!file.exists() || !file.isFile) return@withContext emptyList()
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        if (fromSeq <= 0L) {
            lines
        } else {
            // fromSeq is 1-based and inclusive (matches KtorRemoteTransportAdapter.attachRun's
            // fromSeq/lastSeq convention): line index 0 corresponds to seq 1, so the event at
            // fromSeq itself is line index (fromSeq - 1).
            lines.drop((fromSeq - 1).coerceAtLeast(0L).toInt())
        }
    }

    override suspend fun deleteRun(runId: String): Unit = withContext(Dispatchers.IO) {
        val jsonFile = File(baseDir, "$runId.json")
        if (jsonFile.exists()) jsonFile.delete()
        val eventsFile = File(baseDir, "$runId.events.jsonl")
        if (eventsFile.exists()) eventsFile.delete()
    }

    public companion object {
        public fun resolveDefaultBaseDir(): File {
            val localAppData = System.getenv("LOCALAPPDATA")
            return if (!localAppData.isNullOrBlank()) {
                File(localAppData, "LtiRomGui/runs")
            } else {
                val userHome = System.getProperty("user.home") ?: "."
                val osName = System.getProperty("os.name").orEmpty().lowercase()
                when {
                    osName.contains("mac") -> File(userHome, "Library/Application Support/LtiRomGui/runs")
                    else -> File(userHome, ".local/share/LtiRomGui/runs")
                }
            }
        }
    }
}

public actual fun defaultRunFileStorage(): RunFileStorage = DesktopRunFileStorage()
