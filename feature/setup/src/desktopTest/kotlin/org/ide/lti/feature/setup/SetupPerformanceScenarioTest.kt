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

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.junit.Rule
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic tests for the performance benchmark harness (T031, SC-004).
 *
 * Verifies:
 * - Default startup remains completely unchanged when LTI_SETUP_BENCHMARK is absent.
 * - Benchmark mode loads exactly 100 recents, 100 tool fixtures, and 12,000 log lines (steady cap at 10k).
 * - Keyboard-accessible "Start capture scenario" action and HUD overlay are rendered when enabled.
 * - Modal provisioning preview open and close cycle is functional.
 * - Segment markers JSON file is serialized deterministically.
 */
@OptIn(ExperimentalTestApi::class)
class SetupPerformanceScenarioTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var tempMarkerFile: File? = null

    @BeforeTest
    fun setUp() {
        SetupBenchmarkConfig.overrideBenchmarkActive = null
        SetupBenchmarkConfig.customMarkerFilePath = null
        tempMarkerFile = File.createTempFile("benchmark-markers-test", ".json")
    }

    @AfterTest
    fun tearDown() {
        SetupBenchmarkConfig.overrideBenchmarkActive = null
        SetupBenchmarkConfig.customMarkerFilePath = null
        tempMarkerFile?.delete()
    }

    @Test
    fun testDefaultStartupHookIsNoOpAndRendersDefaultContent() {
        SetupBenchmarkConfig.overrideBenchmarkActive = false
        var defaultContentRendered = false

        composeTestRule.setContent {
            SetupPerformanceScenarioHook(
                modifier = Modifier,
                defaultContent = {
                    defaultContentRendered = true
                    Text("Default Setup Screen Content")
                },
            )
        }

        assertTrue(defaultContentRendered, "Default content must be rendered when benchmark is disabled")
        composeTestRule.onNodeWithText("Default Setup Screen Content").assertIsDisplayed()
        composeTestRule.onNodeWithText("BENCHMARK HARNESS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start capture scenario").assertDoesNotExist()
    }

    @Test
    fun testSyntheticFixturesPreloadExactCounts() {
        val recents = createBenchmarkRecents()
        assertEquals(100, recents.size, "Benchmark harness must preload exactly 100 recents")
        assertEquals(100, recents.distinctBy { it.workspaceId }.size, "All 100 recents must have unique workspaceIds")

        val tools = createBenchmarkTools()
        assertEquals(100, tools.size, "Benchmark harness must preload exactly 100 tool fixtures")
        val categories = tools.map { it.category }.distinct()
        assertTrue(categories.size >= 5, "Tool fixtures must span across multiple categories")

        val logs = createBenchmarkLogs()
        assertEquals(12_000, logs.size, "Benchmark harness must generate 12,000 activity lines to exercise steady cap")
        assertTrue(logs.first().contains("00000"), "First log line format must be deterministic")
        assertTrue(logs.last().contains("11999"), "Last log line format must be deterministic")

        val previewOp = createBenchmarkPreviewOperation()
        assertEquals("bench-op-preview-01", previewOp.operationId)
        assertEquals(SetupOperationKind.FULL_SETUP, previewOp.kind)
        assertEquals(SetupOperationState.PREVIEW, previewOp.executionState)
        assertTrue(previewOp.plannedChanges.requiresElevation)
    }

    @Test
    fun testBoundedSteadyCapLogsHoldsAtCapWhenExceedingLimit() {
        val activityState = SetupActivityState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            maxBufferSize = 10_000,
        )
        val initialLogs = createBenchmarkLogs(12_000)
        activityState.setLogs(initialLogs)
        assertEquals(10_000, activityState.lines.size, "Activity state must cap visible lines at 10,000")
        assertEquals(2_000, activityState.droppedLineCount.value, "Activity state must record 2,000 dropped lines")

        // Stream additional 500 lines past limit
        val additional = List(500) { "[EXTRA] Streamed event line $it" }
        activityState.appendLines(additional)
        assertEquals(10_000, activityState.lines.size, "Activity state must remain capped at 10,000 lines")
        assertEquals(2_500, activityState.droppedLineCount.value, "Dropped count must increment by 500")
    }

    @Test
    fun testBenchmarkModeExercisesPreviewModalOpenAndClose() {
        SetupBenchmarkConfig.overrideBenchmarkActive = true
        SetupBenchmarkConfig.warmupDurationMs = 10_000L

        composeTestRule.setContent {
            LtiTheme {
                SetupPerformanceScenarioHook(
                    modifier = Modifier,
                    defaultContent = {
                        Text("Default Setup Content Should Not Render")
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("BENCHMARK HARNESS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Provisioning Preview").assertDoesNotExist()

        // Trigger modal preview using shortcut Ctrl+Shift+P
        composeTestRule.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            keyDown(Key.P)
            keyUp(Key.P)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }

        // Modal should open
        composeTestRule.onNodeWithText("Provisioning Preview").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirm & Set Up").assertIsDisplayed()

        // Dismiss modal using Cancel button
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Provisioning Preview").assertDoesNotExist()
    }

    @Test
    fun testBenchmarkModeRendersHudAndStartCaptureScenarioAction() {
        SetupBenchmarkConfig.overrideBenchmarkActive = true
        SetupBenchmarkConfig.warmupDurationMs = 10_000L

        composeTestRule.setContent {
            LtiTheme {
                SetupPerformanceScenarioHook(
                    modifier = Modifier,
                    defaultContent = {
                        Text("Default Setup Content Should Not Render")
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("BENCHMARK HARNESS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start capture scenario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ctrl+Shift+B").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default Setup Content Should Not Render").assertDoesNotExist()
    }

    @Test
    fun testMarkerFileSerializationCreatesValidJson() {
        val markerFile = tempMarkerFile ?: error("tempMarkerFile was null")
        SetupBenchmarkConfig.customMarkerFilePath = markerFile.absolutePath

        val markers = listOf(
            BenchmarkSegmentMarker(
                name = "Workspaces",
                startSeconds = 0.0,
                endSeconds = 10.0,
                startNanos = 1_000_000_000L,
                endNanos = 11_000_000_000L,
            ),
            BenchmarkSegmentMarker(
                name = "Tools",
                startSeconds = 10.0,
                endSeconds = 20.0,
                startNanos = 11_000_000_000L,
                endNanos = 21_000_000_000L,
            ),
            BenchmarkSegmentMarker(
                name = "Environment",
                startSeconds = 20.0,
                endSeconds = 30.0,
                startNanos = 21_000_000_000L,
                endNanos = 31_000_000_000L,
            ),
        )

        val payload = BenchmarkMarkersPayload(
            scenarioStartNanos = 1_000_000_000L,
            scenarioEndNanos = 31_000_000_000L,
            captureStart = 0.0,
            captureEnd = 30.0,
            segments = markers,
        )

        saveMarkersToFile(payload)

        assertTrue(markerFile.exists(), "Marker file must exist on disk")
        val content = markerFile.readText()
        assertTrue(content.contains("\"scenarioStartNanos\": 1000000000"), "Must contain scenarioStartNanos")
        assertTrue(content.contains("\"scenarioEndNanos\": 31000000000"), "Must contain scenarioEndNanos")
        assertTrue(content.contains("\"captureStart\": 0.0"), "Must contain captureStart")
        assertTrue(content.contains("\"captureEnd\": 30.0"), "Must contain captureEnd")
        assertTrue(content.contains("\"name\": \"Workspaces\""), "Must contain Workspaces segment")
        assertTrue(content.contains("\"name\": \"Tools\""), "Must contain Tools segment")
        assertTrue(content.contains("\"name\": \"Environment\""), "Must contain Environment segment")
    }
}
