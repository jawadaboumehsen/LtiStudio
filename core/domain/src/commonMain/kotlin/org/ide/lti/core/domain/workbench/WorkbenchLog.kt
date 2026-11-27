/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** How a line of the output reads: a step that worked, information, a warning, an error. */
enum class LogLevel { OK, INF, WRN, ERR }

data class LogLine(val atMillis: Long, val level: LogLevel, val text: String, val id: Long = 0) {
    /** The wall-clock time on this computer, `HH:MM:SS.mmm`. */
    val localTime: String
        get() = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(TimeZone.currentSystemDefault()).time.toString()
}

/**
 * What the workbench jobs did, in order, for the Studio's Output panel: each job's start and end (or why it was
 * refused), and the lines a tool reports itself (a dry run's findings, its summary). Kept in memory, the last [MAX].
 */
class WorkbenchLog(private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    private val nextId = MutableStateFlow(0L)
    val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    fun add(level: LogLevel, text: String) {
        val at = now()
        val messages = text.lines().filter(String::isNotBlank)
        if (messages.isEmpty()) return
        val firstId = nextId.getAndUpdate { it + messages.size }
        val added = messages.mapIndexed { index, message -> LogLine(at, level, message, firstId + index) }
        _lines.update { (it + added).takeLast(MAX) }
    }

    fun clear() {
        _lines.value = emptyList()
    }

    companion object {
        const val MAX: Int = 2000
    }
}
