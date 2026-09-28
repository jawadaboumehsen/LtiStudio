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
import org.ide.lti.core.domain.setup.SetupLogConstants
import org.ide.lti.core.domain.setup.SetupLogEvent

/**
 * Viewport state remembered per destination (Environment vs Tools) across tab transitions (FR-012).
 */
data class ActivityDestinationState(
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val following: Boolean = true,
    val searchQuery: String = "",
)

enum class LogFilter {
    ALL,
    CHECKS,
    COMMANDS,
    ERRORS,
}

/**
 * Presentation buffer for the activity panel shared by the Environment and Tools destinations (US5).
 *
 * - Retains at most 10,000 lines and 8 MiB of UTF-8 payload; each line at most 16 KiB (FR-011, SC-004).
 * - Deduplicates by event identity (attemptId, childRunId, sequence) so replay never duplicates (FR-012).
 * - Eviction is a constant-size head removal per event; the published snapshot is rebuilt once per
 *   [appendEvents] batch, never per event.
 * - Selection is keyed by event id and intersected with the retained ids on every eviction, so a
 *   selected line that scrolls out of retention simply disappears (FR-012).
 */
class SetupActivityState(
    private val scope: CoroutineScope,
    private val maxBufferSize: Int = SetupLogConstants.MAX_BUFFER_LINES,
    private val maxPayloadBytes: Long = SetupLogConstants.MAX_PAYLOAD_BYTES,
) {
    companion object {
        const val MAX_BUFFER_LINES: Int = SetupLogConstants.MAX_BUFFER_LINES
        const val MAX_PAYLOAD_BYTES: Long = SetupLogConstants.MAX_PAYLOAD_BYTES
        private const val LOCAL_CHILD_ID = "local"
        private const val LOCAL_ATTEMPT_ID = "session"
    }

    private val deque = ArrayDeque<SetupLogEvent>()
    private val retainedIds = HashSet<String>()
    private var currentPayloadBytes: Long = 0L
    private var localSequence: Long = 1L

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    private val _visibleEvents = MutableStateFlow<List<SetupLogEvent>>(emptyList())
    val visibleEvents: StateFlow<List<SetupLogEvent>> = _visibleEvents.asStateFlow()

    /** Text of the retained lines, in order; computed on demand (tests, clipboard), not a second snapshot. */
    val lines: List<String> get() = _visibleEvents.value.map { it.text }

    private val _droppedLineCount = MutableStateFlow(0)
    val droppedLineCount: StateFlow<Int> = _droppedLineCount.asStateFlow()

    private val _totalPayloadBytes = MutableStateFlow(0L)
    val totalPayloadBytes: StateFlow<Long> = _totalPayloadBytes.asStateFlow()

    private val _followingLatest = MutableStateFlow(true)
    val followingLatest: StateFlow<Boolean> = _followingLatest.asStateFlow()

    private val _selectedEventIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedEventIds: StateFlow<Set<String>> = _selectedEventIds.asStateFlow()

    private val _operationId = MutableStateFlow<String?>(null)
    val operationId: StateFlow<String?> = _operationId.asStateFlow()

    private val destinationStates = mutableMapOf<String, ActivityDestinationState>()

    fun setOperationId(opId: String?) {
        _operationId.value = opId
    }

    fun setFilter(newFilter: LogFilter) {
        if (_filter.value == newFilter) return
        _filter.value = newFilter
        rebuildVisibleEvents()
    }

    private fun rebuildVisibleEvents() {
        _visibleEvents.value = when (_filter.value) {
            LogFilter.ALL -> deque.toList()
            LogFilter.CHECKS -> deque.filter { it.kind == org.ide.lti.core.domain.setup.SetupLogKind.CHECK }
            LogFilter.COMMANDS -> deque.filter { it.kind == org.ide.lti.core.domain.setup.SetupLogKind.RUN }
            LogFilter.ERRORS -> deque.filter { it.kind == org.ide.lti.core.domain.setup.SetupLogKind.ERROR }
        }
    }

    /** Appends a batch, enforcing bounds and identity deduplication; publishes one snapshot for the batch. */
    fun appendEvents(events: List<SetupLogEvent>) {
        if (events.isEmpty()) return
        var dropped = 0
        for (event in events) {
            val safeEvent = SetupLogEvent.create(
                attemptId = event.attemptId,
                childRunId = event.childRunId,
                sequence = event.sequence,
                text = event.text,
                kind = event.kind,
            )
            if (!retainedIds.add(safeEvent.id)) continue
            deque.addLast(safeEvent)
            currentPayloadBytes += safeEvent.text.encodeToByteArray().size.toLong()
            while (deque.size > maxBufferSize || currentPayloadBytes > maxPayloadBytes) {
                val evicted = deque.removeFirst()
                retainedIds.remove(evicted.id)
                currentPayloadBytes -= evicted.text.encodeToByteArray().size.toLong()
                dropped++
            }
        }
        _totalPayloadBytes.value = currentPayloadBytes
        if (dropped > 0) {
            _droppedLineCount.update { it + dropped }
            _selectedEventIds.update { selected -> selected.filterTo(HashSet()) { it in retainedIds } }
        }
        rebuildVisibleEvents()
    }

    fun appendEvent(event: SetupLogEvent) = appendEvents(listOf(event))

    /** Appends narration that carries no server run identity (benchmark fixtures, local notes). */
    fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return
        val attemptId = _operationId.value ?: LOCAL_ATTEMPT_ID
        appendEvents(lines.map { SetupLogEvent(attemptId, LOCAL_CHILD_ID, localSequence++, it) })
    }

    /** Replaces the retained history with [logs]. */
    fun setLogs(logs: List<String>) {
        clear()
        appendLines(logs)
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

    /** Selects by stable id; ids no longer retained are ignored. */
    fun selectEvent(id: String, isMultiSelect: Boolean = false) {
        if (id !in retainedIds) return
        _selectedEventIds.update { current ->
            when {
                !isMultiSelect -> setOf(id)
                id in current -> current - id
                else -> current + id
            }
        }
    }

    fun selectAll() {
        _selectedEventIds.value = retainedIds.toSet()
    }

    fun clearSelection() {
        _selectedEventIds.value = emptySet()
    }

    fun copyAll(): String = _visibleEvents.value.joinToString("\n") { it.text }

    /** Copies the selected retained lines in display order, or everything when nothing is selected. */
    fun copySelected(): String {
        val selected = _selectedEventIds.value
        if (selected.isEmpty()) return copyAll()
        return _visibleEvents.value.filter { it.id in selected }.joinToString("\n") { it.text }
    }

    fun clear() {
        deque.clear()
        retainedIds.clear()
        currentPayloadBytes = 0L
        _visibleEvents.value = emptyList()
        _droppedLineCount.value = 0
        _totalPayloadBytes.value = 0L
        _selectedEventIds.value = emptySet()
        _operationId.value = null
        _followingLatest.value = true
    }

    fun saveDestinationState(
        destination: String,
        scrollIndex: Int,
        scrollOffset: Int,
        following: Boolean,
        searchQuery: String = "",
    ) {
        destinationStates[destination] = ActivityDestinationState(scrollIndex, scrollOffset, following, searchQuery)
    }

    fun getDestinationState(destination: String): ActivityDestinationState? = destinationStates[destination]
}
