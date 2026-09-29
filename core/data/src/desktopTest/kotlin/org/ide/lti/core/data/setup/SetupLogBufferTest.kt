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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.SetupLogConstants
import org.ide.lti.core.domain.setup.SetupLogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetupLogBufferTest {

    @Test
    fun testRetainedLineCountBoundedAt10000() = runTest {
        val buffer = SetupLogBuffer(
            maxLines = SetupLogConstants.MAX_BUFFER_LINES,
            maxPayloadBytes = SetupLogConstants.MAX_PAYLOAD_BYTES,
        )

        for (i in 1..12_000) {
            buffer.append(
                SetupLogEvent.create(
                    attemptId = "attempt-1",
                    childRunId = "run-1",
                    sequence = i.toLong(),
                    text = "Log entry $i",
                ),
            )
        }

        // Producer buffer retention
        assertEquals(10_000, buffer.size)
        assertEquals(2_000, buffer.droppedLineCount)
        assertEquals("Log entry 2001", buffer.snapshot().first().text)
        assertEquals("Log entry 12000", buffer.snapshot().last().text)

        // Presentation-facing snapshot
        val snapshot = buffer.snapshot()
        assertEquals(10_000, snapshot.size)
        assertEquals("Log entry 2001", snapshot.first().text)
        assertEquals("Log entry 12000", snapshot.last().text)
    }

    @Test
    fun testRetainedPayloadBoundedAt8MiB() = runTest {
        val buffer = SetupLogBuffer(
            maxLines = SetupLogConstants.MAX_BUFFER_LINES,
            maxPayloadBytes = SetupLogConstants.MAX_PAYLOAD_BYTES,
        )

        // 700 lines of ~15 KiB payload each = ~10.5 MiB, exceeding 8 MiB limit while well below 10,000 lines
        val line15k = "A".repeat(15 * 1024)
        for (i in 1..700) {
            buffer.append(
                SetupLogEvent.create(
                    attemptId = "attempt-1",
                    childRunId = "run-1",
                    sequence = i.toLong(),
                    text = "Line $i: $line15k",
                ),
            )
        }

        assertTrue(
            buffer.totalPayloadBytes <= SetupLogConstants.MAX_PAYLOAD_BYTES,
            "Producer payload bytes must not exceed 8 MiB " +
                "(${buffer.totalPayloadBytes} > ${SetupLogConstants.MAX_PAYLOAD_BYTES})",
        )
        assertTrue(buffer.size < 700, "Oldest lines must be evicted when payload exceeds 8 MiB")
        assertTrue(buffer.droppedLineCount > 0, "Dropped line count must track byte-ceiling evictions")

        val snapshot = buffer.snapshot()
        val snapshotBytes = snapshot.sumOf { it.text.encodeToByteArray().size.toLong() }
        assertTrue(
            snapshotBytes <= SetupLogConstants.MAX_PAYLOAD_BYTES,
            "Presentation snapshot bytes must not exceed 8 MiB " +
                "($snapshotBytes > ${SetupLogConstants.MAX_PAYLOAD_BYTES})",
        )
    }

    @Test
    fun testPerLineTruncationAt16KiBWithMarker() = runTest {
        val buffer = SetupLogBuffer()

        // 30 KiB oversized line containing multi-byte characters
        val oversized = "Japanese あいうえお " + "X".repeat(30 * 1024)
        val event = SetupLogEvent.create(
            attemptId = "attempt-1",
            childRunId = "run-1",
            sequence = 1L,
            text = oversized,
        )
        buffer.append(event)

        val retained = buffer.snapshot().single()
        val bytes = retained.text.encodeToByteArray()
        assertTrue(
            bytes.size <= SetupLogConstants.MAX_LINE_BYTES,
            "Line must be <= 16 KiB including truncation marker (${bytes.size} > ${SetupLogConstants.MAX_LINE_BYTES})",
        )
        assertTrue(
            retained.text.endsWith(SetupLogConstants.TRUNCATION_MARKER),
            "Truncation marker must be present on oversized line",
        )
        assertFalse(
            retained.text.contains("�"),
            "Truncation must not split multi-byte characters or create replacement characters",
        )
    }

    @Test
    fun testTruncationNeverSplitsFourByteCodePoint() = runTest {
        val marker = SetupLogConstants.TRUNCATION_MARKER
        val budget = SetupLogConstants.MAX_LINE_BYTES - marker.encodeToByteArray().size
        // Fill to exactly budget - 1 bytes, then place a 4-byte emoji straddling the boundary.
        val text = "a".repeat(budget - 1) + "😀" + "z".repeat(64)
        val buffer = SetupLogBuffer()
        buffer.append(SetupLogEvent("attempt-1", "run-1", 1L, text))

        val retained = buffer.snapshot().single().text
        assertEquals("a".repeat(budget - 1) + marker, retained, "The straddling emoji is dropped whole, never halved")
        assertTrue(retained.encodeToByteArray().size <= SetupLogConstants.MAX_LINE_BYTES)
    }

    @Test
    fun testReplayDeduplicationByIdentity() = runTest {
        val buffer = SetupLogBuffer()

        val event1 = SetupLogEvent.create("attempt-1", "run-1", 1L, "First line")
        val event2 = SetupLogEvent.create("attempt-1", "run-1", 2L, "Second line")
        val event2Duplicate = SetupLogEvent.create("attempt-1", "run-1", 2L, "Second line duplicate")

        assertTrue(buffer.append(event1))
        assertTrue(buffer.append(event2))
        assertFalse(
            buffer.append(event2Duplicate),
            "Duplicate event by (attemptId, childRunId, sequence) must be rejected",
        )

        assertEquals(2, buffer.size)
        assertEquals(listOf("First line", "Second line"), buffer.snapshot().map { it.text })
    }

    @Test
    fun testObserveReplaysRetainedHistoryBeforeLiveEvents() = runTest {
        val buffer = SetupLogBuffer(maxLines = 3)
        for (i in 1..5) buffer.append("attempt-1", "run-1", i.toLong(), "history $i")

        val seen = mutableListOf<String>()
        val job = launch(Dispatchers.Unconfined) {
            buffer.observe().collect { seen.add(it.text) }
        }
        buffer.append("attempt-1", "run-1", 6L, "live 6")
        buffer.append("attempt-1", "run-1", 6L, "live 6 again")
        job.cancel()

        assertEquals(listOf("history 3", "history 4", "history 5", "live 6"), seen)
        assertEquals(3L, buffer.droppedLineCount, "history 1-3 evicted; the replayed seq 6 evicts nothing")
    }

    @Test
    fun testOnRecordedFiresOnlyForNewlyRetainedEvents() = runTest {
        val recorded = mutableListOf<String>()
        val buffer = SetupLogBuffer(onRecorded = { recorded.add(it.text) })
        buffer.record("attempt-1", "run-1", 1L, "first")
        buffer.record("attempt-1", "run-1", 1L, "first replayed")
        buffer.append("narration", attemptId = "local", childRunId = "provisioner")

        assertEquals(listOf("first", "narration"), recorded)
    }

    @Test
    fun testSnapshotsEmittedToObserversDoNotRetainFullHistoryAfter100000Events() = runTest {
        val buffer = SetupLogBuffer()

        for (i in 1..100_000) {
            buffer.append(
                SetupLogEvent.create(
                    attemptId = "attempt-1",
                    childRunId = "run-1",
                    sequence = i.toLong(),
                    text = "Event line $i",
                ),
            )
        }

        assertEquals(10_000, buffer.size, "Producer buffer must be capped at 10,000")
        assertEquals(90_000, buffer.droppedLineCount, "Dropped count must be 90,000")
        assertTrue(
            buffer.totalPayloadBytes <= SetupLogConstants.MAX_PAYLOAD_BYTES,
            "Producer payload bytes must not exceed 8 MiB",
        )

        val snapshot = buffer.snapshot()
        assertEquals(10_000, snapshot.size, "Snapshot must not retain full 100,000 events history")
        assertEquals("Event line 90001", snapshot.first().text)
        assertEquals("Event line 100000", snapshot.last().text)

        val snapshotBytes = snapshot.sumOf { it.text.encodeToByteArray().size.toLong() }
        assertTrue(snapshotBytes <= SetupLogConstants.MAX_PAYLOAD_BYTES)
    }
}
