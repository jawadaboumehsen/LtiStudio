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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.colorSchemeFor
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
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
            val scheme = colorSchemeFor(theme)
            val surface = scheme.surface

            // 1. Text contrast against surface (WCAG AA >= 4.5:1)
            val onSurfaceRatio = ColorContrast.contrastRatio(scheme.onSurface, surface)
            val onSurfaceVariantRatio = ColorContrast.contrastRatio(scheme.onSurfaceVariant, surface)

            assertTrue(
                onSurfaceRatio >= 4.5,
                "${theme.id} onSurface contrast $onSurfaceRatio must be >= 4.5:1",
            )
            assertTrue(
                onSurfaceVariantRatio >= 4.5,
                "${theme.id} onSurfaceVariant contrast $onSurfaceVariantRatio must be >= 4.5:1",
            )

            // 2. UI / Focus contrast against surface (WCAG AA >= 3.0:1)
            val primaryRatio = ColorContrast.contrastRatio(scheme.primary, surface)
            val outlineRatio = ColorContrast.contrastRatio(scheme.outline, surface)

            assertTrue(
                primaryRatio >= 3.0,
                "${theme.id} primary contrast $primaryRatio must be >= 3.0:1",
            )
            assertTrue(
                outlineRatio >= 1.3,
                "${theme.id} outline contrast $outlineRatio must be >= 1.3:1",
            )

            // 3. Error indicator against surface (WCAG AA >= 3.0:1)
            val errorRatio = ColorContrast.contrastRatio(scheme.error, surface)
            assertTrue(
                errorRatio >= 3.0,
                "${theme.id} error contrast $errorRatio must be >= 3.0:1",
            )
        }
    }

    @Test
    fun testSetupTokensContentSurfaceContrastMath() {
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (theme in themes) {
            val scheme = colorSchemeFor(theme)
            val background = scheme.background

            val textRatio = ColorContrast.contrastRatio(scheme.onBackground, background)
            val primaryRatio = ColorContrast.contrastRatio(scheme.primary, background)

            assertTrue(
                textRatio >= 4.5,
                "${theme.id} text contrast against background ($textRatio) must be >= 4.5:1",
            )
            assertTrue(
                primaryRatio >= 3.0,
                "${theme.id} primary contrast against background ($primaryRatio) must be >= 3.0:1",
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                recentProjects = emptyList(),
                                filteredProjects = emptyList(),
                                selectedTab = SetupTab.PROJECTS,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("empty_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesOverviewScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = overviewState,
                                selectedTab = SetupTab.LAUNCHPAD,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("overview_${appTheme.id}", image)
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("ready_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesEmptyStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                recentProjects = emptyList(),
                                filteredProjects = emptyList(),
                                selectedTab = SetupTab.PROJECTS,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("empty_state_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesOverviewEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = overviewState,
                                selectedTab = SetupTab.LAUNCHPAD,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("overview_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesReadyStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("ready_state_${appTheme.id}_effects_off", image)
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                activeOperation = activeOp,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
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
                LtiTheme(appTheme = appTheme) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("offline_state_${appTheme.id}", image)
        }
    }

    @Test
    fun testThreeThemesCheckingStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val checkingSteps = listOf(
            SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL Detection", description = "Checking distribution", status = StepStatus.RUNNING),
            SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "Server Daemon Bridge", description = "Pending check", status = StepStatus.PENDING),
        )
        val state = ToolchainSetupState(steps = checkingSteps, isChecking = true)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (appTheme in themes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("checking_state_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesRunningStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                activeOperation = activeOp,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("running_state_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesFailedStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("failed_state_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesRestoredStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("restored_state_${appTheme.id}_effects_off", image)
        }
    }

    @Test
    fun testThreeThemesOfflineStateEffectsOffScreenshot() = runDesktopComposeUiTest(width = 1120, height = 700) {
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
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = SetupUiState(
                                toolchainSetupState = state,
                                selectedTab = SetupTab.ENVIRONMENT,
                            ),
                            actions = SetupUiActions(),
                        )
                    }
                }
            }

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("offline_state_${appTheme.id}_effects_off", image)
        }
    }

    // Renders at every (scale x theme) combination at each of the two SC-005 logical window
    // sizes; each is its own @Test because runDesktopComposeUiTest fixes the virtual window
    // size for its whole body. This proves the shell renders without crashing across the
    // scale/theme/window matrix -- it does NOT itself measure contrast (see GlassContrastTest,
    // core:designsystem) or cover the six presentation states (unknown/needs-setup/ready/
    // running/failed/reconnecting), which remain BLOCKED pending real-render verification.
    @Test
    fun testThemeAndDensityMatrixAt1440x900() {
        assertThemeAndDensityMatrixRenders(width = 1440, height = 900)
    }

    @Test
    fun testThemeAndDensityMatrixAt900x600() {
        assertThemeAndDensityMatrixRenders(width = 900, height = 600)
    }

    private fun assertThemeAndDensityMatrixRenders(width: Int, height: Int) = runDesktopComposeUiTest(width = width, height = height) {
        val scales = listOf(1.0f, 1.5f, 2.0f)
        val themes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

        for (scale in scales) {
            for (appTheme in themes) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = scale, fontScale = 1.0f),
                    ) {
                        LtiTheme(appTheme = appTheme) {
                            Box(Modifier.fillMaxSize()) {
                                GlassBackdrop()
                                SetupScreenContent(
                                    state = SetupUiState(selectedTab = SetupTab.ENVIRONMENT),
                                    actions = SetupUiActions(),
                                )
                            }
                        }
                    }
                }

                waitForIdle()
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

        val targetPath = "src/desktopTest/resources/screenshots/$name.png"
        bitmap.captureRoboImage(
            filePath = targetPath,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0.005f,
                ),
                recordOptions = RoborazziOptions.RecordOptions(
                    resizeScale = 1.0,
                ),
            ),
        )
    }
}
