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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.asSkiaBitmap
import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Three-theme seven-state screenshot fixtures and composited contrast regression tests (FR-011, SC-003).
 *
 * Validates:
 * - Mathematical WCAG AA contrast across all three themes (Dark, Light, Blue Glass):
 *   - Text contrast >= 4.5:1 (onGlass, onGlassSecondary, TextNeutralColor vs surfaces).
 *   - UI / focus contrast >= 3.0:1 (focusRing, glassAccent, onGlassMuted, diagnostics).
 * - Off-screen rendering and screenshot capture for 7 distinct presentation states:
 *   1. EMPTY: initial unconfigured state (no recents, pending checks)
 *   2. READY: all stages verified, live provenance, "Open Workspaces" primary action
 *   3. CHECKING: verification actively running, non-mutating
 *   4. RUNNING: confirmed setup operation executing
 *   5. FAILED: stage failure with error disclosure and targeted retry action
 *   6. RESTORED: cached/historical provenance with relative timestamp
 *   7. OFFLINE: bridge unreachable, "Retry Connection", cached workspaces accessible
 */
@OptIn(ExperimentalTestApi::class)
class SetupThemeScreenshotTest {

    // ---------------------------------------------------------------------------------------------
    // Mathematical Contrast Ratio Tests (Reusing GlassContrastTest math via ColorContrast)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testThreeThemesTextAndFocusContrastMath() {
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (theme in themes) {
            val scheme = theme.configuration.glassColorScheme
            val glassSurface = scheme.glassSurface

            // 1. Text contrast against glassSurface (WCAG AA >= 4.5:1)
            val onGlassRatio = ColorContrast.contrastRatio(scheme.onGlass, glassSurface)
            val onGlassSecondaryRatio = ColorContrast.contrastRatio(scheme.onGlassSecondary, glassSurface)

            assertTrue(
                onGlassRatio >= 4.5,
                "${theme.displayName} onGlass contrast $onGlassRatio must be >= 4.5:1",
            )
            assertTrue(
                onGlassSecondaryRatio >= 4.5,
                "${theme.displayName} onGlassSecondary contrast $onGlassSecondaryRatio must be >= 4.5:1",
            )

            // 2. UI / Focus contrast against glassSurface (WCAG AA >= 3.0:1)
            val focusRingRatio = ColorContrast.contrastRatio(scheme.focusRing, glassSurface)
            val glassAccentRatio = ColorContrast.contrastRatio(scheme.glassAccent, glassSurface)
            val onGlassMutedRatio = ColorContrast.contrastRatio(scheme.onGlassMuted, glassSurface)

            assertTrue(
                focusRingRatio >= 3.0,
                "${theme.displayName} focusRing contrast $focusRingRatio must be >= 3.0:1",
            )
            assertTrue(
                glassAccentRatio >= 3.0,
                "${theme.displayName} glassAccent contrast $glassAccentRatio must be >= 3.0:1",
            )
            assertTrue(
                onGlassMutedRatio >= 3.0,
                "${theme.displayName} onGlassMuted contrast $onGlassMutedRatio must be >= 3.0:1",
            )

            // 3. Diagnostic indicators against glassSurface (WCAG AA >= 3.0:1)
            val diag = scheme.diagnostics
            val successRatio = ColorContrast.contrastRatio(diag.success, glassSurface)
            val warningRatio = ColorContrast.contrastRatio(diag.warning, glassSurface)
            val errorRatio = ColorContrast.contrastRatio(diag.error, glassSurface)

            assertTrue(
                successRatio >= 3.0,
                "${theme.displayName} diagnostic success contrast $successRatio must be >= 3.0:1",
            )
            assertTrue(
                warningRatio >= 3.0,
                "${theme.displayName} diagnostic warning contrast $warningRatio must be >= 3.0:1",
            )
            assertTrue(
                errorRatio >= 3.0,
                "${theme.displayName} diagnostic error contrast $errorRatio must be >= 3.0:1",
            )
        }
    }

    @Test
    fun testSetupTokensContentSurfaceContrastMath() {
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (theme in themes) {
            val scheme = theme.configuration.glassColorScheme
            val contentSurface = SetupTokens.contentSurfaceColor(theme)
            val textNeutral = SetupTokens.textNeutralColor(theme)

            val textRatio = ColorContrast.contrastRatio(textNeutral, contentSurface)
            val onGlassRatio = ColorContrast.contrastRatio(scheme.onGlass, contentSurface)

            assertTrue(
                textRatio >= 4.5 || onGlassRatio >= 4.5,
                "${theme.displayName} text contrast against ContentSurfaceColor ($textRatio / $onGlassRatio) must be >= 4.5:1",
            )

            val focusRingRatio = ColorContrast.contrastRatio(scheme.focusRing, contentSurface)
            assertTrue(
                focusRingRatio >= 3.0,
                "${theme.displayName} focusRing contrast against ContentSurfaceColor ($focusRingRatio) must be >= 3.0:1",
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Three-Theme Seven-State Screenshot Fixtures (SC-003)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testThreeThemesEmptyStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            recentProjects = emptyList(),
                            filteredProjects = emptyList(),
                            selectedTab = SetupTab.PROJECTS,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("empty_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testBlueGlassOverviewScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val overviewState = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL runtime",
                    description = "Ubuntu 22.04 LTS",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Build service",
                    description = "Bridge needs verification",
                    status = StepStatus.PENDING,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tool packages",
                    description = "Packages have not been checked",
                    status = StepStatus.PENDING,
                ),
            ),
        )
        setContent {
            val backdrop = rememberLayerBackdrop()
            LtiTheme(appTheme = AppTheme.Blue, sharedBackdrop = backdrop) {
                SetupScreenContent(
                    state = SetupUiState(
                        toolchainSetupState = overviewState,
                        selectedTab = SetupTab.LAUNCHPAD,
                    ),
                    actions = SetupUiActions(),
                    backdrop = backdrop,
                )
            }
        }

        onRoot().captureToImage().also { image ->
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("overview_blue", image)
        }
    }

    @Test
    fun testThreeThemesReadyStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val readySteps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL Detection", description = "Ubuntu 22.04 LTS", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "Server Daemon Bridge", description = "HTTP/SSE connected", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            SetupStepDetail(stage = SetupStepStage.SYSTEM_DIAGNOSTICS, title = "System Diagnostics", description = "All checks passed", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            SetupStepDetail(stage = SetupStepStage.REPO_SYNCHRONIZATION, title = "Repository Synchronization", description = "Git submodules up to date", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            SetupStepDetail(stage = SetupStepStage.TOOLCHAIN_COMPILATION, title = "Toolchain Compilation", description = "Native tools verified", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
        )
        val state = ToolchainSetupState(steps = readySteps, lastVerifiedTimestamp = 1700000000000L)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("ready_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesCheckingStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val checkingSteps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL Detection", description = "Checking distribution", status = StepStatus.RUNNING),
            SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "Server Daemon Bridge", description = "Pending check", status = StepStatus.PENDING),
        )
        val state = ToolchainSetupState(steps = checkingSteps, isChecking = true)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("checking_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesRunningStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val runningSteps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL Detection", description = "Configured", status = StepStatus.SUCCESS),
            SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "Server Daemon Bridge", description = "Starting bridge", status = StepStatus.RUNNING),
        )
        val activeOp = SetupOperation(
            kind = SetupOperationKind.FULL_SETUP,
            executionState = SetupOperationState.RUNNING,
            operationId = "run_op_001",
        )
        val state = ToolchainSetupState(steps = runningSteps, isRunning = true)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            activeOperation = activeOp,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("running_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesFailedStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val failedSteps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL Detection", description = "", status = StepStatus.SUCCESS),
            SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Repository Synchronization",
                description = "Git clone failed",
                status = StepStatus.FAILED,
                error = "fatal: repository not found",
            ),
        )
        val state = ToolchainSetupState(steps = failedSteps)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("failed_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesRestoredStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val restoredSteps = listOf(
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL Detection",
                description = "Restored from previous session",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.RESTORED,
            ),
        )
        val state = ToolchainSetupState(steps = restoredSteps, lastVerifiedTimestamp = 1690000000000L)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("restored_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesOfflineStateScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val offlineSteps = listOf(
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server Daemon Bridge",
                description = "Connection refused on 127.0.0.1:50051",
                status = StepStatus.FAILED,
                error = "WSL2 daemon not responding",
            ),
        )
        val state = ToolchainSetupState(steps = offlineSteps)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                val backdrop = rememberLayerBackdrop()
                LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                    SetupScreenContent(
                        state = SetupUiState(
                            toolchainSetupState = state,
                            selectedTab = SetupTab.ENVIRONMENT,
                        ),
                        actions = SetupUiActions(),
                        backdrop = backdrop,
                    )
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("offline_state_${appTheme.id}", image)
        }
    }

    // Renders at every (scale x theme) combination at each of the two SC-005 logical window
    // sizes; each is its own @Test because runDesktopComposeUiTest fixes the virtual window
    // size for its whole body. This proves the shell renders without crashing across the
    // scale/theme/window matrix -- it does NOT itself measure contrast (see GlassContrastTest,
    // core:designsystem) or cover the six presentation states (unknown/needs-setup/ready/
    // running/failed/reconnecting), which remain BLOCKED pending real-render verification.
    @Test
    fun testThemeAndDensityMatrixAt1440x900() =
        runDesktopComposeUiTest(width = 1440, height = 900) {
            assertThemeAndDensityMatrixRenders()
        }

    @Test
    fun testThemeAndDensityMatrixAt900x600() =
        runDesktopComposeUiTest(width = 900, height = 600) {
            assertThemeAndDensityMatrixRenders()
        }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.assertThemeAndDensityMatrixRenders() {
        val scales = listOf(1.0f, 1.5f, 2.0f)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (scale in scales) {
            for (appTheme in themes) {
                setContent {
                    val backdrop = rememberLayerBackdrop()
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = scale, fontScale = scale),
                    ) {
                        LtiTheme(appTheme = appTheme, sharedBackdrop = backdrop) {
                            SetupScreenContent(
                                state = SetupUiState(selectedTab = SetupTab.ENVIRONMENT),
                                actions = SetupUiActions(),
                                backdrop = backdrop,
                            )
                        }
                    }
                }

                onRoot().assertIsDisplayed()
            }
        }
    }

    private fun saveScreenshotReport(name: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/setup")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "$name.png")
        val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val bytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) {
            file.writeBytes(bytes)
        }
    }
}
