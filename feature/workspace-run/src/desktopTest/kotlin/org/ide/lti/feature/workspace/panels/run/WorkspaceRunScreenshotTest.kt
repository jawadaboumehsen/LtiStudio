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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.Instant
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.workspace.Workspace
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

private const val WIDTH_STANDARD: Int = 1120
private const val HEIGHT_STANDARD: Int = 700
private const val WIDTH_COMPACT: Int = 900
private const val HEIGHT_COMPACT: Int = 600

@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class WorkspaceRunScreenshotTest {

    private val sampleWorkspace = Workspace(
        id = "ws-astra-01",
        name = "REDMAGIC Astra Port",
        path = "C:/Users/Mohammad/Documents/LtiRomProject/workspaces/ws-astra-01",
    )

    private fun createIdleState(): BuildRunUiState = BuildRunUiState(
        currentWorkspace = sampleWorkspace,
        latestRun = null,
        isStartingRun = false,
        isCancelling = false,
        logs = listOf(
            "[INIT] Workspace workspace-astra loaded.",
            "[INFO] Target: SM8650-AB (Snapdragon 8 Gen 3)",
            "[READY] Press 'Start Pipeline' to begin build sequence.",
        ),
    )

    private fun createActiveProgressState(): BuildRunUiState {
        val now = Instant.fromEpochMilliseconds(1710000000000L)
        val stages = listOf(
            StageOutcome(
                stageId = StageId.FIRMWARE_ACQUISITION,
                state = StageState.EXECUTED,
                durationMs = 12400L,
            ),
            StageOutcome(
                stageId = StageId.FIRMWARE_EXTRACTION,
                state = StageState.EXECUTED,
                durationMs = 45000L,
            ),
            StageOutcome(
                stageId = StageId.WORK_TREE_ASSEMBLY,
                state = StageState.RUNNING,
            ),
            StageOutcome(
                stageId = StageId.MODULE_APPLICATION,
                state = StageState.PENDING,
            ),
            StageOutcome(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                state = StageState.PENDING,
            ),
            StageOutcome(
                stageId = StageId.GENERATE_OTA_MANIFEST,
                state = StageState.PENDING,
            ),
        )

        val run = BuildRun(
            id = "run-001",
            workspaceId = sampleWorkspace.id,
            snapshotId = "snap-001",
            state = RunState.RUNNING,
            startedAt = now,
            stages = stages,
            artifacts = listOf(
                Artifact(
                    kind = ArtifactKind.FLASHABLE_ZIP,
                    linuxPath = "/build/output/astra-rom-v1.0.zip",
                    sizeBytes = 2411724800L,
                    sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    verification = VerificationState.VERIFIED,
                    presence = Presence.PRESENT,
                ),
            ),
        )

        return BuildRunUiState(
            currentWorkspace = sampleWorkspace,
            latestRun = run,
            isStartingRun = false,
            isCancelling = false,
            logs = listOf(
                "[STEP 1] Firmware acquisition completed (duration: 12.4s).",
                "[STEP 2] Payload extracted 28 partitions successfully (duration: 45.0s).",
                "[STEP 3] Assembling sparse image work tree in /tmp/worktree...",
                "[INFO] Writing super.img dynamic partitions...",
            ),
        )
    }

    private fun createInterruptedErrorState(): BuildRunUiState {
        val now = Instant.fromEpochMilliseconds(1710000000000L)
        val run = BuildRun(
            id = "run-002",
            workspaceId = sampleWorkspace.id,
            snapshotId = "snap-001",
            state = RunState.INTERRUPTED,
            startedAt = now,
            stages = listOf(
                StageOutcome(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    state = StageState.FAILED,
                    message = "Connection reset by peer during OTA download",
                ),
            ),
        )

        return BuildRunUiState(
            currentWorkspace = sampleWorkspace,
            latestRun = run,
            errorMessage = "Pipeline process terminated unexpectedly: Connection reset",
            failingCheck = "Execution Service Interrupted",
            remediation = "Ensure the WSL daemon is running and restart the build pipeline.",
            logs = listOf(
                "[FATAL] Connection to OTA server terminated unexpectedly at byte 48192004",
                "[ERROR] Stage FIRMWARE_ACQUISITION aborted.",
            ),
        )
    }

    @Test
    fun testWorkspaceRunDark() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_dark",
            state = createIdleState(),
            theme = AppTheme.Dark,
        )
    }

    @Test
    fun testWorkspaceRunLight() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_light",
            state = createIdleState(),
            theme = AppTheme.Light,
        )
    }

    @Test
    fun testWorkspaceRunBlue() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_blue",
            state = createIdleState(),
            theme = AppTheme.Blue,
        )
    }

    @Test
    fun testWorkspaceRunEffectsOff() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_effects_off",
            state = createIdleState(),
            theme = AppTheme.Dark,
            effectsEnabled = false,
        )
    }

    @Test
    fun testWorkspaceRunCompact() = runDesktopComposeUiTest(
        width = WIDTH_COMPACT,
        height = HEIGHT_COMPACT,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_compact",
            state = createIdleState(),
            theme = AppTheme.Blue,
            width = WIDTH_COMPACT,
            height = HEIGHT_COMPACT,
        )
    }

    @Test
    fun testWorkspaceRunFontScale200() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_font_scale_200",
            state = createIdleState(),
            theme = AppTheme.Dark,
            fontScale = 2.0f,
        )
    }

    @Test
    fun testWorkspaceRunActiveProgress() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_active_progress",
            state = createActiveProgressState(),
            theme = AppTheme.Dark,
        )
    }

    @Test
    fun testWorkspaceRunErrorBanner() = runDesktopComposeUiTest(
        width = WIDTH_STANDARD,
        height = HEIGHT_STANDARD,
    ) {
        verifyRunScreenshot(
            name = "workspace_run_error_banner",
            state = createInterruptedErrorState(),
            theme = AppTheme.Light,
        )
    }

    private fun DesktopComposeUiTest.verifyRunScreenshot(
        name: String,
        state: BuildRunUiState,
        theme: AppTheme,
        effectsEnabled: Boolean = true,
        width: Int = WIDTH_STANDARD,
        height: Int = HEIGHT_STANDARD,
        fontScale: Float = 1.0f,
    ) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.0f, fontScale = fontScale),
            ) {
                LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        ActiveRunContent(
                            uiState = state,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        onRoot().assertIsDisplayed()
        onRoot().captureToImage().also { image ->
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            saveScreenshot(name, image)
        }
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/workspace-run")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) File(reportsDir, "$name.png").writeBytes(bytes)

        val targetPath = "src/desktopTest/resources/screenshots/$name.png"
        bitmap.captureRoboImage(
            filePath = targetPath,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
            ),
        )
    }
}
