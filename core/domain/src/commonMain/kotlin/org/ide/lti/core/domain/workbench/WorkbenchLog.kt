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
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** How a line of the output reads: a step that worked, information, a warning, an error. */
public enum class LogLevel { OK, INF, WRN, ERR }

public data class LogLine(val atMillis: Long, val level: LogLevel, val text: String) {
    /** The wall-clock time on this computer, `HH:MM:SS.mmm`. */
    public val localTime: String
        get() = Instant.fromEpochMilliseconds(atMillis).toLocalDateTime(TimeZone.currentSystemDefault()).time.toString()
}

/**
 * What the workbench jobs did, in order, for the Studio's Output panel: each job's start and end (or why it was
 * refused), and the lines a tool reports itself (a dry run's findings, its summary). Kept in memory, the last [MAX].
 */
public class WorkbenchLog(private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    public val lines: StateFlow<List<LogLine>> = _lines.asStateFlow()

    public fun add(level: LogLevel, text: String) {
        val at = now()
        val added = text.lines().filter(String::isNotBlank).map { LogLine(at, level, it) }
        _lines.update { (it + added).takeLast(MAX) }
    }

    public fun clear() {
        _lines.value = emptyList()
    }

    public companion object {
        public const val MAX: Int = 2000
    }
}
