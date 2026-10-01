/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticItem
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticTile
import org.ide.lti.core.designsystem.component.display.IdeReadinessRow
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeSummaryGrid
import org.ide.lti.core.designsystem.component.display.IdeSummaryItem
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipItem
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipRow
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.component.progress.GlassStepInfo
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.progress.GlassStepper
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class GlassDisplayAndFeedbackScreenshotTest {

    @Test
    fun testGlassBannerSeverities() = runDesktopComposeUiTest(width = 680, height = 480) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassBanner(
                            severity = GlassBannerSeverity.INFO,
                            title = "Toolchain Ready",
                            description = "Official firmware package verified and ready for deployment.",
                            primaryAction = GlassBannerAction("View Details") {},
                        )
                        GlassBanner(
                            severity = GlassBannerSeverity.SUCCESS,
                            title = "Build Succeeded",
                            description = "Target flash image built and signed in 14.2 seconds.",
                        )
                        GlassBanner(
                            severity = GlassBannerSeverity.WARNING,
                            title = "Missing Recommended Package",
                            description = "avbtool is missing; signing will fall back to legacy keys.",
                            primaryAction = GlassBannerAction("Install") {},
                        )
                        GlassBanner(
                            severity = GlassBannerSeverity.ERROR,
                            title = "WSL Daemon Unreachable",
                            description = "Failed to establish bridge transport to Linux execution environment.",
                            primaryAction = GlassBannerAction("Retry") {},
                            secondaryAction = GlassBannerAction("Configure") {},
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_banner_severities", image)
    }

    @Test
    fun testGlassChipsAndFilterRow() = runDesktopComposeUiTest(width = 540, height = 200) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            GlassChip(label = "Selected Chip", selected = true, onClick = {})
                            GlassChip(label = "Unselected Chip", selected = false, onClick = {})
                            GlassChip(label = "Disabled Chip", selected = false, enabled = false, onClick = {})
                        }
                        GlassFilterChipRow(
                            items = listOf(
                                GlassFilterChipItem(label = "All Stages", count = 3, value = "all"),
                                GlassFilterChipItem(label = "Core Hooks", count = 5, value = "core"),
                                GlassFilterChipItem(label = "Mods", count = 2, value = "mods"),
                            ),
                            selectedValue = "core",
                            onSelect = {},
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_chips_and_filter_row", image)
    }

    @Test
    fun testGlassStepperProgression() = runDesktopComposeUiTest(width = 600, height = 180) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassStepper(
                        steps = listOf(
                            GlassStepInfo("1", "Layout", GlassStepStatus.SUCCESS),
                            GlassStepInfo("2", "Config", GlassStepStatus.SUCCESS),
                            GlassStepInfo("3", "Signing", GlassStepStatus.RUNNING),
                            GlassStepInfo("4", "Validate", GlassStepStatus.PENDING),
                            GlassStepInfo("5", "Promote", GlassStepStatus.FAILED),
                        ),
                        isBusy = true,
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_stepper_progression", image)
    }

    @Test
    fun testGlassConsoleDrawer() = runDesktopComposeUiTest(width = 640, height = 240) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                ) {
                    GlassConsoleDrawer(
                        logs = listOf(
                            "[INFO] Initializing workspace build container...",
                            "[INFO] Mounting sparse filesystem overlay at /mnt/vendor",
                            "[EXEC] avbtool calculate_kernel_cmdline --image boot.img",
                            "[SUCCESS] Boot verification payload injected successfully.",
                        ),
                        isRunning = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_console_drawer", image)
    }

    @Test
    fun testGlassDiagnosticTileAndReadiness() = runDesktopComposeUiTest(width = 680, height = 260) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GlassDiagnosticTile(
                            item = GlassDiagnosticItem(
                                id = "wsl_check",
                                title = "WSL2 Ubuntu 22.04 LTS Runtime",
                                detail = "Bridge connection active. Verified build tools: make, gcc, python3.",
                                status = GlassStepStatus.SUCCESS,
                                category = "Environment",
                                copyableCommand = "wsl -d Ubuntu-22.04 uname -a",
                            ),
                        )
                        IdeReadinessRow(
                            stageName = "BUILD",
                            label = "Toolchain Compiler",
                            statusText = "READY",
                            severity = IdeStatusSeverity.Ready,
                            explanation = "Precompiled binaries validated against sha256 checksums.",
                            actionLabel = "Rebuild",
                            onAction = {},
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_diagnostic_tile_and_readiness", image)
    }

    @Test
    fun testIdeStatusBadgeAndSummaryGrid() = runDesktopComposeUiTest(width = 680, height = 240) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            IdeStatusBadge("Ready", IdeStatusSeverity.Ready)
                            IdeStatusBadge("Warning", IdeStatusSeverity.Warning)
                            IdeStatusBadge("Blocking", IdeStatusSeverity.Blocking)
                            IdeStatusBadge("Neutral", IdeStatusSeverity.Neutral)
                            IdeStatusBadge("Running", IdeStatusSeverity.Running)
                        }
                        IdeSummaryGrid(
                            items = listOf(
                                IdeSummaryItem("Target", "PQ84P01", "Astra"),
                                IdeSummaryItem("Platform", "SM8650", "Snapdragon"),
                                IdeSummaryItem("Partition", "EROFS", "Super 16G"),
                                IdeSummaryItem("Status", "Qualified", "Ready"),
                            ),
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("ide_status_badge_and_summary_grid", image)
    }
}
