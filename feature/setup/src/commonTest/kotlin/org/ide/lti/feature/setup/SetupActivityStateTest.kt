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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.setup.SetupLogConstants
import org.ide.lti.core.domain.setup.SetupLogEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupActivityStateTest {

    private fun runActivityTest(
        testBody: suspend TestScope.(scope: CoroutineScope) -> Unit,
    ) = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val activityScope = CoroutineScope(testDispatcher + SupervisorJob())
        try {
            testBody(activityScope)
        } finally {
            activityScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testBufferCapHoldsAtMost10000LinesAndTracksDroppedCount() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        // Add 5,000 lines: under cap
        val lines5k = (1..5000).map { "Log line $it" }
        activityState.appendLines(lines5k)
        assertEquals(5000, activityState.lines.size)
        assertEquals(0, activityState.droppedLineCount.value)
        assertEquals("Log line 1", activityState.lines.first())
        assertEquals("Log line 5000", activityState.lines.last())

        // Add another 5,000 lines: exactly at 10,000 cap
        val next5k = (5001..10000).map { "Log line $it" }
        activityState.appendLines(next5k)
        assertEquals(10000, activityState.lines.size)
        assertEquals(0, activityState.droppedLineCount.value)
        assertEquals("Log line 1", activityState.lines.first())
        assertEquals("Log line 10000", activityState.lines.last())

        // Add 50 more lines: exceeds cap by 50
        // Enforce EXACTLY: "The in-memory activity buffer holds at most 10,000 lines."
        val extra50 = (10001..10050).map { "Log line $it" }
        activityState.appendLines(extra50)
        assertEquals(10000, activityState.lines.size, "Buffer must hold at most 10,000 lines")
        assertEquals(50, activityState.droppedLineCount.value, "Must visibly track 50 dropped lines")
        assertEquals("Log line 51", activityState.lines.first(), "Oldest 50 lines must be evicted")
        assertEquals("Log line 10050", activityState.lines.last())

        // Add 2,000 more lines
        val extra2k = (10051..12050).map { "Log line $it" }
        activityState.appendLines(extra2k)
        assertEquals(10000, activityState.lines.size)
        assertEquals(2050, activityState.droppedLineCount.value)
        assertEquals("Log line 2051", activityState.lines.first())
        assertEquals("Log line 12050", activityState.lines.last())
    }

    @Test
    fun testAutoFollowAndPausedFollowTransitions() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        // Initially follows latest
        assertTrue(activityState.followingLatest.value)

        activityState.appendLines(listOf("Line 1", "Line 2"))
        assertTrue(activityState.followingLatest.value)

        // User pauses follow (e.g. by scrolling away or clicking pause)
        activityState.pauseFollow()
        assertFalse(activityState.followingLatest.value)

        // New lines arriving while paused must not force follow back to true
        activityState.appendLines(listOf("Line 3", "Line 4"))
        assertFalse(activityState.followingLatest.value)

        // User triggers Jump to latest
        activityState.jumpToLatest()
        assertTrue(activityState.followingLatest.value)
    }

    @Test
    fun testSelectionAndCopy() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)
        activityState.appendLines(listOf("Line Alpha", "Line Beta", "Line Gamma", "Line Delta"))

        // Copy all when no selection
        assertEquals(
            "Line Alpha\nLine Beta\nLine Gamma\nLine Delta",
            activityState.copyAll(),
        )

        // Select specific events by ID
        val betaEvent = activityState.visibleEvents.value[1]
        val deltaEvent = activityState.visibleEvents.value[3]
        activityState.selectEvent(betaEvent.id)
        activityState.selectEvent(deltaEvent.id, isMultiSelect = true)
        assertEquals(setOf(betaEvent.id, deltaEvent.id), activityState.selectedEventIds.value)

        assertEquals(
            "Line Beta\nLine Delta",
            activityState.copySelected(),
        )

        // Clear selection
        activityState.clearSelection()
        assertTrue(activityState.selectedEventIds.value.isEmpty())
        assertEquals(
            "Line Alpha\nLine Beta\nLine Gamma\nLine Delta",
            activityState.copySelected(),
            "Copy selected falls back to all lines when selection is empty",
        )
    }

    @Test
    fun testSelectionPreservationAcrossEviction() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)
        val initialLines = (1..10000).map { "Line $it" }
        activityState.appendLines(initialLines)

        // Select line at index 500 ("Line 501") by its stable ID
        val selectedEvent = activityState.visibleEvents.value[500]
        activityState.selectEvent(selectedEvent.id)
        assertEquals(setOf(selectedEvent.id), activityState.selectedEventIds.value)

        // Evict 100 lines by adding 100 new lines
        val newLines = (10001..10100).map { "Line $it" }
        activityState.appendLines(newLines)

        // Stable ID is still retained and selection is preserved
        assertTrue(selectedEvent.id in activityState.selectedEventIds.value)
        assertEquals("Line 501", activityState.copySelected())
    }

    @Test
    fun testStableIdEvictionAndCopy() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        val events = (1..10_000).map { i ->
            SetupLogEvent.create(
                attemptId = "attempt-1",
                childRunId = "run-1",
                sequence = i.toLong(),
                text = "Line $i",
            )
        }
        activityState.appendEvents(events)

        // Select event 10 (which will be evicted) and event 9000 (which will remain retained)
        val event10 = activityState.visibleEvents.value[9] // Line 10
        val event9000 = activityState.visibleEvents.value[8999] // Line 9000
        activityState.selectEvent(event10.id)
        activityState.selectEvent(event9000.id, isMultiSelect = true)
        assertEquals(setOf(event10.id, event9000.id), activityState.selectedEventIds.value)

        // Evict first 50 lines by appending 50 new events
        val newEvents = (10_001..10_050).map { i ->
            SetupLogEvent.create(
                attemptId = "attempt-1",
                childRunId = "run-1",
                sequence = i.toLong(),
                text = "Line $i",
            )
        }
        activityState.appendEvents(newEvents)

        // Evicted event-10 MUST disappear from selection without selecting any replacement or out-of-bounds index
        assertFalse(
            event10.id in activityState.selectedEventIds.value,
            "Evicted event ID must be removed from selectedEventIds",
        )
        assertTrue(
            event9000.id in activityState.selectedEventIds.value,
            "Retained event ID must remain selected",
        )
        assertEquals(setOf(event9000.id), activityState.selectedEventIds.value)

        // Copy selected after eviction copies only retained lines
        assertEquals("Line 9000", activityState.copySelected())
    }

    @Test
    fun testCumulativeDropCountAcrossManyEvictions() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        // Fill buffer to 10,000
        val batch1 = (1..10_000).map { "Batch 1 Line $it" }
        activityState.appendLines(batch1)
        assertEquals(0, activityState.droppedLineCount.value)

        // Eviction batch 1: +50 lines -> dropped 50
        activityState.appendLines((1..50).map { "Batch 2 Line $it" })
        assertEquals(50, activityState.droppedLineCount.value)

        // Eviction batch 2: +100 lines -> cumulative dropped 150
        activityState.appendLines((1..100).map { "Batch 3 Line $it" })
        assertEquals(150, activityState.droppedLineCount.value)

        // Eviction batch 3: +2,000 lines -> cumulative dropped 2,150
        activityState.appendLines((1..2000).map { "Batch 4 Line $it" })
        assertEquals(2150, activityState.droppedLineCount.value)

        assertEquals(10_000, activityState.lines.size)
    }

    @Test
    fun testReplayDeduplicationDeliveredTwiceYieldsOneLine() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        val event1 = SetupLogEvent.create("attempt-1", "run-A", 1L, "Step 1 started")
        val event2 = SetupLogEvent.create("attempt-1", "run-A", 2L, "Step 1 progress")
        val event3 = SetupLogEvent.create("attempt-1", "run-A", 3L, "Step 1 finished")

        activityState.appendEvents(listOf(event1, event2, event3))
        assertEquals(3, activityState.visibleEvents.value.size)

        // Replay delivery of event 2 and event 3 (same childRunId + sequence within attempt)
        val replayEvent2 = SetupLogEvent.create("attempt-1", "run-A", 2L, "Step 1 progress (replayed)")
        val replayEvent3 = SetupLogEvent.create("attempt-1", "run-A", 3L, "Step 1 finished (replayed)")

        activityState.appendEvents(listOf(replayEvent2, replayEvent3))
        assertEquals(
            3,
            activityState.visibleEvents.value.size,
            "Replayed events with identical identity (childRunId + sequence) must be deduplicated",
        )
        assertEquals(
            listOf("Step 1 started", "Step 1 progress", "Step 1 finished"),
            activityState.lines,
        )
    }

    @Test
    fun testUnicodeByteBoundariesAndPayloadByteCeiling() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        // 1. Line truncation at 16 KiB must not split multi-byte code points
        // Japanese characters (3 bytes each) + 4-byte rocket emoji at boundary
        val repeatedJapanese = "日本語の文字コードテスト".repeat(1000) // ~36 KiB
        val event = SetupLogEvent.create("attempt-1", "run-1", 1L, repeatedJapanese)
        activityState.appendEvents(listOf(event))

        val singleLine = activityState.lines.single()
        val lineBytes = singleLine.encodeToByteArray()
        assertTrue(
            lineBytes.size <= SetupLogConstants.MAX_LINE_BYTES,
            "Truncated line must be <= 16 KiB including marker " +
                "(${lineBytes.size} > ${SetupLogConstants.MAX_LINE_BYTES})",
        )
        assertTrue(
            singleLine.endsWith(SetupLogConstants.TRUNCATION_MARKER),
            "Line must contain truncation marker",
        )
        assertFalse(
            singleLine.contains("\uFFFD"),
            "Truncation must not split multi-byte code point into replacement character \uFFFD",
        )

        // 2. 8 MiB payload bound measured in UTF-8 bytes
        activityState.clear()
        // Feed 700 lines of ~15 KiB payload (~10.5 MiB), well under 10,000 line limit
        val line15k = "A".repeat(15 * 1024)
        val largeEvents = (1..700).map { i ->
            SetupLogEvent.create("attempt-1", "run-1", i.toLong(), "Line $i: $line15k")
        }
        activityState.appendEvents(largeEvents)

        val totalRetainedBytes = activityState.totalPayloadBytes.value
        assertTrue(
            totalRetainedBytes <= SetupLogConstants.MAX_PAYLOAD_BYTES,
            "Total payload bytes must not exceed 8 MiB ($totalRetainedBytes > ${SetupLogConstants.MAX_PAYLOAD_BYTES})",
        )
        assertTrue(
            activityState.lines.size < 700,
            "Oldest lines must be evicted when payload exceeds 8 MiB ceiling even if line count < 10,000",
        )
        assertTrue(
            activityState.droppedLineCount.value > 0,
            "Dropped line count must account for lines evicted due to byte bound",
        )
    }

    @Test
    fun testFeed100000EventsHoldsBothLimitsWithMemoryProportionalState() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        val batchSize = 10_000
        for (batch in 0 until 10) {
            val events = (1..batchSize).map { i ->
                val seq = (batch * batchSize + i).toLong()
                SetupLogEvent.create("attempt-1", "run-1", seq, "Log stream event #$seq")
            }
            activityState.appendEvents(events)
        }

        // Assert line count bound
        assertEquals(
            10_000,
            activityState.visibleEvents.value.size,
            "Activity state must retain at most 10,000 events",
        )
        // Assert dropped line count
        assertEquals(
            90_000,
            activityState.droppedLineCount.value,
            "Dropped line count must be exactly 90,000 after 100,000 events",
        )
        // Assert 8 MiB byte ceiling
        assertTrue(
            activityState.totalPayloadBytes.value <= SetupLogConstants.MAX_PAYLOAD_BYTES,
            "Retained payload must be <= 8 MiB",
        )
        // Assert memory-proportional state: oldest retained event is 90,001, newest is 100,000
        assertEquals("Log stream event #90001", activityState.lines.first())
        assertEquals("Log stream event #100000", activityState.lines.last())
    }

    @Test
    fun testDestinationStatePersistenceAcrossDestinations() = runActivityTest { scope ->
        val activityState = SetupActivityState(scope = scope)

        // Environment tab state
        activityState.saveDestinationState(
            destination = "environment",
            scrollIndex = 42,
            scrollOffset = 15,
            following = false,
            searchQuery = "compiler",
        )

        // Tools tab state
        activityState.saveDestinationState(
            destination = "tools",
            scrollIndex = 100,
            scrollOffset = 0,
            following = true,
            searchQuery = "",
        )

        val envState = activityState.getDestinationState("environment")
        assertNotNull(envState)
        assertEquals(42, envState.scrollIndex)
        assertEquals(15, envState.scrollOffset)
        assertFalse(envState.following)
        assertEquals("compiler", envState.searchQuery)

        val toolsState = activityState.getDestinationState("tools")
        assertNotNull(toolsState)
        assertEquals(100, toolsState.scrollIndex)
        assertEquals(0, toolsState.scrollOffset)
        assertTrue(toolsState.following)
        assertEquals("", toolsState.searchQuery)

        // Unknown destination returns null
        assertNull(activityState.getDestinationState("unknown"))
    }
}
