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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupActivityStateTest {

    private fun runActivityTest(
        testBody: suspend TestScope.(scope: CoroutineScope) -> Unit
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
        assertEquals(5000, activityState.visibleLines.value.size)
        assertEquals(0, activityState.droppedLineCount.value)
        assertEquals("Log line 1", activityState.visibleLines.value.first())
        assertEquals("Log line 5000", activityState.visibleLines.value.last())

        // Add another 5,000 lines: exactly at 10,000 cap
        val next5k = (5001..10000).map { "Log line $it" }
        activityState.appendLines(next5k)
        assertEquals(10000, activityState.visibleLines.value.size)
        assertEquals(0, activityState.droppedLineCount.value)
        assertEquals("Log line 1", activityState.visibleLines.value.first())
        assertEquals("Log line 10000", activityState.visibleLines.value.last())

        // Add 50 more lines: exceeds cap by 50
        // Enforce EXACTLY: "The in-memory activity buffer holds at most 10,000 lines."
        val extra50 = (10001..10050).map { "Log line $it" }
        activityState.appendLines(extra50)
        assertEquals(10000, activityState.visibleLines.value.size, "Buffer must hold at most 10,000 lines")
        assertEquals(50, activityState.droppedLineCount.value, "Must visibly track 50 dropped lines")
        assertEquals("Log line 51", activityState.visibleLines.value.first(), "Oldest 50 lines must be evicted")
        assertEquals("Log line 10050", activityState.visibleLines.value.last())

        // Add 2,000 more lines
        val extra2k = (10051..12050).map { "Log line $it" }
        activityState.appendLines(extra2k)
        assertEquals(10000, activityState.visibleLines.value.size)
        assertEquals(2050, activityState.droppedLineCount.value)
        assertEquals("Log line 2051", activityState.visibleLines.value.first())
        assertEquals("Log line 12050", activityState.visibleLines.value.last())
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

        // Select specific lines
        activityState.selectLine(1) // "Line Beta"
        activityState.selectLine(3, isMultiSelect = true) // "Line Delta"
        assertEquals(setOf(1, 3), activityState.selectedIndices.value)

        assertEquals(
            "Line Beta\nLine Delta",
            activityState.copySelected(),
        )

        // Clear selection
        activityState.clearSelection()
        assertTrue(activityState.selectedIndices.value.isEmpty())
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

        // Select line at index 500 ("Line 501")
        activityState.selectLine(500)
        assertEquals(setOf(500), activityState.selectedIndices.value)

        // Evict 100 lines by adding 100 new lines
        val newLines = (10001..10100).map { "Line $it" }
        activityState.appendLines(newLines)

        // Selected index should be adjusted by 100: 500 - 100 = 400
        assertEquals(setOf(400), activityState.selectedIndices.value)
        assertEquals("Line 501", activityState.visibleLines.value[400])
    }
}
