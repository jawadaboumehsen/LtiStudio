/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import org.ide.lti.core.domain.setup.SetupLogConstants
import org.ide.lti.core.domain.setup.SetupLogEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * Receiver of streamed run output carrying its real journal identity, so that reconnect/replay
 * deduplicates by (attemptId, childRunId, sequence) instead of re-appending under a synthetic counter.
 */
public fun interface SetupActivitySink {
    public fun record(attemptId: String, childRunId: String, sequence: Long, text: String)
}

/**
 * Bounded activity log buffer enforcing producer-side retention and deduplication (FR-011, FR-012, SC-004).
 *
 * Invariants:
 * - "Log identity is childRunId plus sequence within an attempt."
 * - "Each producer and presentation buffer retains at most 10,000 lines and 8 MiB of UTF-8 payload;
 *    each line is at most 16 KiB including its truncation marker."
 * - Eviction is constant-size per event (ArrayDeque head removal); snapshots never exceed the retention caps.
 *
 * [onRecorded] is invoked for every newly retained event (after deduplication) so the owner can mirror
 * a short tail into its immutable state without re-reading the whole buffer.
 */
public class SetupLogBuffer(
    private val maxLines: Int = SetupLogConstants.MAX_BUFFER_LINES,
    private val maxPayloadBytes: Long = SetupLogConstants.MAX_PAYLOAD_BYTES,
    private val onRecorded: (SetupLogEvent) -> Unit = {},
) : SetupActivitySink {
    private val lock = Any()
    private val deque = ArrayDeque<SetupLogEvent>()
    private val retainedIds = HashSet<String>()

    /**
     * Live stream sized to the retention cap: a subscriber slower than the buffer's own eviction loses
     * exactly the lines it would have evicted anyway, never newer ones.
     */
    private val events = MutableSharedFlow<SetupLogEvent>(
        replay = 0,
        extraBufferCapacity = maxLines,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val localSequence = AtomicLong(1L)

    private var payloadBytes: Long = 0L
    private var dropped: Long = 0L

    public val size: Int
        get() = synchronized(lock) { deque.size }

    public val totalPayloadBytes: Long
        get() = synchronized(lock) { payloadBytes }

    public val droppedLineCount: Long
        get() = synchronized(lock) { dropped }

    override fun record(attemptId: String, childRunId: String, sequence: Long, text: String) {
        append(attemptId, childRunId, sequence, text)
    }

    /**
     * Appends [event]; returns false when its identity is already retained (replay duplicate).
     * Applies the 16 KiB line limit, then evicts from the head until both retention caps hold.
     */
    public fun append(event: SetupLogEvent): Boolean {
        val safeEvent = SetupLogEvent.create(event.attemptId, event.childRunId, event.sequence, event.text)
        val eventBytes = safeEvent.text.encodeToByteArray().size.toLong()

        synchronized(lock) {
            if (!retainedIds.add(safeEvent.id)) return false
            deque.addLast(safeEvent)
            payloadBytes += eventBytes
            while (deque.size > maxLines || payloadBytes > maxPayloadBytes) {
                val evicted = deque.removeFirst()
                retainedIds.remove(evicted.id)
                payloadBytes -= evicted.text.encodeToByteArray().size.toLong()
                dropped++
            }
        }

        events.tryEmit(safeEvent)
        onRecorded(safeEvent)
        return true
    }

    public fun append(
        attemptId: String,
        childRunId: String,
        sequence: Long,
        text: String,
    ): Boolean = append(SetupLogEvent(attemptId, childRunId, sequence, text))

    /** Appends narration that has no server run identity, under a process-local monotonic sequence. */
    public fun append(
        text: String,
        attemptId: String,
        childRunId: String,
    ): Boolean = append(SetupLogEvent(attemptId, childRunId, localSequence.getAndIncrement(), text))

    /** Point-in-time copy of retained events; bounded by [maxLines] and [maxPayloadBytes]. */
    public fun snapshot(): List<SetupLogEvent> = synchronized(lock) { deque.toList() }

    /**
     * Retained history followed by live events. Subscription is registered before the snapshot is
     * taken so no event can fall between them; an event seen in both is a duplicate by identity,
     * which every consumer must ignore (the buffer itself does for its own retention).
     */
    public fun observe(): Flow<SetupLogEvent> = events.onSubscription {
        for (event in snapshot()) emit(event)
    }

    public fun clear() {
        synchronized(lock) {
            deque.clear()
            retainedIds.clear()
            payloadBytes = 0L
            dropped = 0L
        }
    }
}
