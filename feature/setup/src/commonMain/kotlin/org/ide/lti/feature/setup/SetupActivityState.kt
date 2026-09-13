/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Presentation model for the live activity log panel (FR-010, SC-004).
 */
data class ActivityPresentation(
    val visibleLines: List<String> = emptyList(),
    val followingLatest: Boolean = true,
    val droppedLineCount: Int = 0,
    val operationId: String? = null,
    val selectedIndices: Set<Int> = emptySet(),
)

/**
 * Feature-local state holder for live activity visualization across Environment and Tools destinations (US3).
 *
 * Responsibilities:
 * - Enforces EXACTLY: "The in-memory activity buffer holds at most 10,000 lines." (FR-010, SC-004).
 * - Exposes a visible dropped-line count when the buffer cap is exceeded (FR-010).
 * - Manages pause-follow, resume, and Jump to latest transitions.
 * - Supports copying all or selected lines, preserving selection across eviction.
 */
class SetupActivityState(
    private val scope: CoroutineScope,
    private val maxBufferSize: Int = MAX_BUFFER_LINES,
) {
    companion object {
        const val MAX_BUFFER_LINES = 10_000
    }

    private val _visibleLines = MutableStateFlow<List<String>>(emptyList())
    val visibleLines: StateFlow<List<String>> = _visibleLines.asStateFlow()

    private val _droppedLineCount = MutableStateFlow(0)
    val droppedLineCount: StateFlow<Int> = _droppedLineCount.asStateFlow()

    private val _followingLatest = MutableStateFlow(true)
    val followingLatest: StateFlow<Boolean> = _followingLatest.asStateFlow()

    private val _selectedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndices: StateFlow<Set<Int>> = _selectedIndices.asStateFlow()

    private val _operationId = MutableStateFlow<String?>(null)
    val operationId: StateFlow<String?> = _operationId.asStateFlow()

    fun setOperationId(opId: String?) {
        _operationId.value = opId
    }

    /**
     * Appends a batch of log lines to the in-memory buffer.
     * Enforces the 10,000 line cap, tracks dropped lines, and adjusts selected indices.
     */
    fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return

        val current = _visibleLines.value
        val combinedSize = current.size + lines.size

        if (combinedSize <= maxBufferSize) {
            _visibleLines.value = current + lines
        } else {
            val totalLines = current + lines
            val dropCount = totalLines.size - maxBufferSize
            val keptLines = totalLines.takeLast(maxBufferSize)

            _visibleLines.value = keptLines
            _droppedLineCount.update { it + dropCount }

            // Adjust selected indices for evicted lines
            _selectedIndices.update { selected ->
                selected.mapNotNull { idx ->
                    val shifted = idx - dropCount
                    if (shifted in keptLines.indices) shifted else null
                }.toSet()
            }
        }
    }

    fun appendLine(line: String) {
        appendLines(listOf(line))
    }

    /**
     * Sets the complete logs from a snapshot or stream (e.g. from ToolchainSetupState).
     */
    fun setLogs(logs: List<String>) {
        if (logs.size <= maxBufferSize) {
            _visibleLines.value = logs
            _droppedLineCount.value = 0
        } else {
            val dropCount = logs.size - maxBufferSize
            _visibleLines.value = logs.takeLast(maxBufferSize)
            _droppedLineCount.value = dropCount

            _selectedIndices.update { selected ->
                selected.mapNotNull { idx ->
                    val shifted = idx - dropCount
                    if (shifted in 0 until maxBufferSize) shifted else null
                }.toSet()
            }
        }
    }

    fun pauseFollow() {
        _followingLatest.value = false
    }

    fun jumpToLatest() {
        _followingLatest.value = true
    }

    fun setFollowingLatest(following: Boolean) {
        _followingLatest.value = following
    }

    fun selectLine(index: Int, isMultiSelect: Boolean = false) {
        if (index !in _visibleLines.value.indices) return
        _selectedIndices.update { current ->
            if (isMultiSelect) {
                if (index in current) current - index else current + index
            } else {
                setOf(index)
            }
        }
    }

    fun selectAll() {
        _selectedIndices.value = _visibleLines.value.indices.toSet()
    }

    fun clearSelection() {
        _selectedIndices.value = emptySet()
    }

    fun copyAll(): String = _visibleLines.value.joinToString("\n")

    fun copySelected(): String {
        val selected = _selectedIndices.value
        if (selected.isEmpty()) {
            return copyAll()
        }
        val lines = _visibleLines.value
        return selected.sorted()
            .mapNotNull { lines.getOrNull(it) }
            .joinToString("\n")
    }

    fun clear() {
        _visibleLines.value = emptyList()
        _droppedLineCount.value = 0
        _selectedIndices.value = emptySet()
        _operationId.value = null
        _followingLatest.value = true
    }
}
