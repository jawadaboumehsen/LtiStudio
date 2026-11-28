/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.desktop.BackgroundAlertItem
import org.ide.lti.core.domain.desktop.BackgroundSessionItem
import org.ide.lti.core.domain.desktop.BackgroundSessionJumpTarget
import org.ide.lti.core.domain.desktop.BackgroundSessionKind
import org.ide.lti.core.domain.desktop.BackgroundSessionSnapshot
import org.ide.lti.core.domain.desktop.BackgroundSessionState
import org.ide.lti.core.domain.desktop.ClassifiedDropFile
import org.ide.lti.core.domain.desktop.DropArtifactCategory
import org.ide.lti.core.domain.desktop.DropOverlayState
import org.ide.lti.core.domain.workspace.TimelineStepNode
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.StageStep
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.StepState
import org.ide.lti.core.ui.drop.DropIngestOverlay
import org.ide.lti.core.ui.sessions.BackgroundSessionDrawer
import org.ide.lti.core.ui.sessions.BackgroundSessionStatusPill
import org.ide.lti.core.ui.timeline.InteractiveBuildTimeline
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopErgonomicsAndTimelineUiTest {

    private fun sampleRun(): BuildRun {
        val t0 = Instant.fromEpochSeconds(2_000)
        return BuildRun(
            id = "run_ui",
            workspaceId = "ws_ui",
            snapshotId = "snap_ui",
            state = RunState.SUCCEEDED,
            startedAt = t0,
            endedAt = Instant.fromEpochSeconds(2_080),
            stages = listOf(
                StageOutcome(StageId.FIRMWARE_ACQUISITION, StageState.SKIPPED),
                StageOutcome(StageId.WORK_TREE_ASSEMBLY, StageState.SKIPPED),
                StageOutcome(
                    StageId.BUILD_FLASHABLE_ZIP,
                    StageState.EXECUTED,
                    startedAt = Instant.fromEpochSeconds(2_010),
                    durationMs = 70_000L,
                    whyRan = "build config modified",
                ),
            ),
            steps = listOf(
                StageStep(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    index = 0,
                    idempotencyKey = "k0",
                    command = StepCommand("mkfs.erofs", listOf("system.img")),
                    state = StepState.COMPLETED,
                    label = "Pack system EROFS",
                    startedAt = Instant.fromEpochSeconds(2_010),
                    endedAt = Instant.fromEpochSeconds(2_060),
                    exitCode = 0,
                ),
            ),
        )
    }

    @Test
    fun interactiveBuildTimelineSwitchesTabsAndJumpsToStageAndStepLogs() = runDesktopComposeUiTest(
        width = 1100,
        height = 800,
    ) {
        var selectedStage: StageId? = null
        var jumpedStage: StageId? = null
        var jumpedStep: TimelineStepNode? = null
        var inspectedStage: StageId? = null

        setContent {
            GlassSceneHost(effectsEnabled = false) {
                LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) {
                    InteractiveBuildTimeline(
                        run = sampleRun(),
                        selectedStageId = selectedStage,
                        onSelectStage = { selectedStage = it },
                        onJumpToLogs = { st, step ->
                            jumpedStage = st
                            jumpedStep = step
                        },
                        onInspectDiff = { inspectedStage = it },
                    )
                }
            }
        }

        onNodeWithTag("InteractiveBuildTimeline").assertIsDisplayed()
        onNodeWithTag("DagNode_BUILD_FLASHABLE_ZIP").performClick()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, selectedStage)

        onNodeWithTag("JumpToStageLogsButton").performClick()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, jumpedStage)

        onNodeWithTag("InspectStageDiffButton").performClick()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, inspectedStage)

        onNodeWithText("Bottlenecks (1)").performClick()
        onNodeWithTag("BottleneckStep_BUILD_FLASHABLE_ZIP_0").performClick()
        assertEquals("Pack system EROFS", jumpedStep?.label)
    }

    @Test
    fun backgroundSessionDrawerAndDropOverlaySurfaceTasksAlertsAndDropPreview() = runDesktopComposeUiTest(
        width = 960,
        height = 720,
    ) {
        var cancelledId: String? = null
        var jumpedTarget: BackgroundSessionJumpTarget? = null
        var dismissedBanner = false

        val snapshot = BackgroundSessionSnapshot(
            sessions = listOf(
                BackgroundSessionItem(
                    id = "job_repack",
                    kind = BackgroundSessionKind.REPACK,
                    title = "Repack super.img",
                    detail = "Running lpmake",
                    progress = 0.6f,
                    state = BackgroundSessionState.RUNNING,
                    startedAt = Instant.fromEpochSeconds(100),
                    updatedAt = Instant.fromEpochSeconds(120),
                    jumpTarget = BackgroundSessionJumpTarget.BuildRunPanel,
                    canCancel = true,
                ),
            ),
            alerts = listOf(
                BackgroundAlertItem(
                    id = "al_1",
                    title = "Device connected",
                    message = "Pixel 8 online",
                    timestamp = Instant.fromEpochSeconds(130),
                    unread = true,
                    jumpTarget = BackgroundSessionJumpTarget.DevicesPanel,
                ),
            ),
            activeCount = 1,
            unreadAlertCount = 1,
        )

        val dropState = DropOverlayState(
            isDraggingOver = true,
            hoverFiles = listOf(
                ClassifiedDropFile("/tmp/vendor.img", "vendor.img", "img", DropArtifactCategory.PARTITION_IMAGE),
            ),
            lastMessage = "Opened vendor.img in Partition Explorer",
        )

        setContent {
            GlassSceneHost(effectsEnabled = false) {
                LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) {
                    BackgroundSessionStatusPill(
                        snapshot = snapshot,
                        isDrawerOpen = true,
                        onToggleDrawer = {},
                    )
                    BackgroundSessionDrawer(
                        snapshot = snapshot,
                        onJump = { jumpedTarget = it },
                        onCancelSession = { cancelledId = it },
                        onDismissSession = {},
                        onDismissAlert = {},
                        onClearCompleted = {},
                        onMarkAllRead = {},
                        onClose = {},
                    )
                    DropIngestOverlay(
                        state = dropState,
                        onDismissBanner = { dismissedBanner = true },
                    )
                }
            }
        }

        onNodeWithTag("BackgroundSessionStatusPill").assertIsDisplayed()
        onNodeWithTag("BackgroundSessionDrawer").assertIsDisplayed()
        onNodeWithText("Repack super.img").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        assertEquals("job_repack", cancelledId)

        onNodeWithText("Notifications (1)").performClick()
        onNodeWithText("View").performClick()
        assertEquals(BackgroundSessionJumpTarget.DevicesPanel, jumpedTarget)

        onNodeWithTag("DragDropActiveOverlay").assertIsDisplayed()
        onNodeWithText("vendor.img (Partition Image (.img))").assertIsDisplayed()
        onNodeWithTag("DropIngestFeedbackBanner").assertIsDisplayed()
        assertTrue(!dismissedBanner)
    }
}
