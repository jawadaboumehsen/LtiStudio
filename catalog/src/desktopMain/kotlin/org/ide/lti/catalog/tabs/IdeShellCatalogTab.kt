/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeReadinessRow
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeSummaryGrid
import org.ide.lti.core.designsystem.component.display.IdeSummaryItem
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeBreadcrumbBar
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.component.layout.IdeTopAppBar
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily

private enum class CatalogThemeMode {
    Dark,
    Light,
    Blue,
}

/**
 * Catalog tab demonstrating all IDE shell frame components, layout geometry,
 * interaction states, WCAG-compliant color schemes, and readiness row patterns.
 */
@Composable
fun IdeShellCatalogTab() {
    val scrollState = rememberScrollState()
    var themeMode by remember { mutableStateOf(CatalogThemeMode.Dark) }

    val activeShellColors: IdeShellColorScheme = when (themeMode) {
        CatalogThemeMode.Dark -> IdeShellTokens.Dark
        CatalogThemeMode.Light -> IdeShellTokens.Light
        CatalogThemeMode.Blue -> IdeShellTokens.Blue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge),
    ) {
        ThemeSelectorSection(
            currentMode = themeMode,
            onSelectMode = { themeMode = it },
        )

        TopAppBarCatalogSection(colors = activeShellColors)
        BreadcrumbBarCatalogSection(colors = activeShellColors)
        PipelineRailCatalogSection(colors = activeShellColors)
        NavigatorPanelCatalogSection(colors = activeShellColors)
        StatusBadgesCatalogSection(colors = activeShellColors)
        SummaryGridCatalogSection(colors = activeShellColors)
        ReadinessMatrixCatalogSection(colors = activeShellColors)
        StatusBarCatalogSection(colors = activeShellColors)
        AppFramePreviewSection(colors = activeShellColors)
    }
}

@Composable
private fun ThemeSelectorSection(
    currentMode: CatalogThemeMode,
    onSelectMode: (CatalogThemeMode) -> Unit,
) {
    SectionContainer(
        title = "IDE Shell Palette Preview Mode",
        description = "Switch the color scheme applied to all IDE shell components below to verify dark, light, and high-contrast blue palettes.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassChip(
                label = "Dark Palette (Default)",
                selected = currentMode == CatalogThemeMode.Dark,
                onClick = { onSelectMode(CatalogThemeMode.Dark) },
            )
            GlassChip(
                label = "Light Palette",
                selected = currentMode == CatalogThemeMode.Light,
                onClick = { onSelectMode(CatalogThemeMode.Light) },
            )
            GlassChip(
                label = "Blue Studio Palette",
                selected = currentMode == CatalogThemeMode.Blue,
                onClick = { onSelectMode(CatalogThemeMode.Blue) },
            )
        }
    }
}

@Composable
private fun TopAppBarCatalogSection(colors: IdeShellColorScheme) {
    SectionContainer(
        title = "1. Top App Bar (40 dp)",
        description = "Persistent full-width top chrome with leading brand, centered project target, and trailing actions.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Standard Project", style = MaterialTheme.typography.labelMedium)
            IdeTopAppBar(
                colors = colors,
                leadingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            painter = AppIcons.FolderPainterResource(),
                            contentDescription = null,
                            tint = colors.accentBlue,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(
                            text = "LtiRom Studio",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textMain,
                            fontFamily = ideFontFamily(),
                        )
                    }
                },
                centerContent = {
                    Text(
                        text = "Pixel 8 Pro (shiba) — Android 15",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        fontFamily = codeFontFamily(),
                    )
                },
                trailingContent = {
                    Text(
                        text = "Toolchain: Ready",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentGreen,
                    )
                },
            )

            Text("Long Project Label (Truncation Test)", style = MaterialTheme.typography.labelMedium)
            IdeTopAppBar(
                colors = colors,
                leadingContent = {
                    Text(
                        text = "LtiRom Studio Enterprise Cockpit",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textMain,
                    )
                },
                centerContent = {
                    Text(
                        text = "very_long_project_name_staging_aosp_device_variant_custom_gsi_vendor_arm64_v8a_release",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                    )
                },
                trailingContent = {
                    GlassTextButton(
                        onClick = {},
                    ) {
                        Text("Settings")
                    }
                },
            )
        }
    }
}

@Composable
private fun BreadcrumbBarCatalogSection(colors: IdeShellColorScheme) {
    var selectedIndex by remember { mutableStateOf(4) }
    SectionContainer(
        title = "2. Breadcrumb Bar (32 dp)",
        description = "Contextual path navigation showing workspace stage hierarchy and interactive navigation segments.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Active Project Path", style = MaterialTheme.typography.labelMedium)
            IdeBreadcrumbBar(
                segments = listOf("Workspaces", "Pixel 8 Pro", "Pipeline", "Modify", "build.prop"),
                colors = colors,
                onSegmentClick = { selectedIndex = it },
            )
            Text(
                text = "Last clicked segment index: $selectedIndex",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )

            Text("Deep Nested Long Path", style = MaterialTheme.typography.labelMedium)
            IdeBreadcrumbBar(
                segments = listOf(
                    "Workspaces",
                    "AOSP-15-Toolchain",
                    "vendor",
                    "google",
                    "proprietary",
                    "firmware",
                    "radio",
                    "modem_image_v2.bin",
                ),
                colors = colors,
            )
        }
    }
}

@Composable
private fun PipelineRailCatalogSection(colors: IdeShellColorScheme) {
    var selectedStageId by remember { mutableStateOf("modify") }

    val sampleStages = listOf(
        IdePipelineStageItem(id = "ingest", label = "Ingest", icon = "ingest"),
        IdePipelineStageItem(id = "unpack", label = "Unpack", icon = "unpack"),
        IdePipelineStageItem(id = "analyze", label = "Analyze", icon = "analyze"),
        IdePipelineStageItem(id = "modify", label = "Modify", icon = "modify"),
        IdePipelineStageItem(id = "repack", label = "Repack", icon = "repack"),
        IdePipelineStageItem(id = "sign", label = "Sign", icon = "sign"),
        IdePipelineStageItem(id = "verify", label = "Verify", icon = "verify"),
        IdePipelineStageItem(
            id = "flash",
            label = "Flash",
            icon = "flash",
            enabled = false,
            disabledReason = "Device not connected via Fastboot",
        ),
    )

    SectionContainer(
        title = "3. Pipeline Rail (56 dp)",
        description = "Eight primary pipeline stages with single active selection indicator, disabled states, and tooltips.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .height(IdeShellDimens.CompactHeightBreakpoint)
                    .border(IdeShellDimens.HairlineBorder, colors.border),
            ) {
                IdePipelineRail(
                    stages = sampleStages,
                    selectedStageId = selectedStageId,
                    onSelectStage = { selectedStageId = it },
                    colors = colors,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(
                    text = "Selected Stage: $selectedStageId",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.accentBlue,
                )
                Text(
                    text = "Width: 56 dp persistent rail",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Text(
                    text = "Includes disabled stage: 'Flash' (Device offline)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningAmber,
                )
            }
        }
    }
}

@Composable
private fun NavigatorPanelCatalogSection(colors: IdeShellColorScheme) {
    var selectedNavId by remember { mutableStateOf("overview") }

    val sampleNavItems = listOf(
        IdeNavigatorItem(id = "overview", label = "Overview", icon = "overview"),
        IdeNavigatorItem(id = "partitions", label = "Partitions", icon = "partitions", badge = "12"),
        IdeNavigatorItem(id = "payload", label = "Payload Manifest", icon = "payload"),
        IdeNavigatorItem(id = "selinux", label = "SELinux Policies", icon = "selinux"),
        IdeNavigatorItem(
            id = "crypto",
            label = "Key Store & AVB",
            icon = "crypto",
            enabled = false,
        ),
        IdeNavigatorItem(id = "settings", label = "Stage Settings", icon = "settings"),
    )

    SectionContainer(
        title = "4. Context Navigator Panel (230 dp)",
        description = "Stage-sensitive hierarchical navigation panel showing sections, badges, and unavailable state indications.",
    ) {
        Box(
            modifier = Modifier
                .width(IdeShellDimens.WorkspaceNavigatorWidth)
                .height(IdeShellDimens.CompactHeightBreakpoint)
                .border(IdeShellDimens.HairlineBorder, colors.border),
        ) {
            IdeNavigatorPanel(
                headerTitle = "WORKSPACE",
                headerSubtitle = "Pixel 8 Pro",
                items = sampleNavItems,
                selectedItemId = selectedNavId,
                onSelectItem = { selectedNavId = it },
                colors = colors,
            )
        }
    }
}

@Composable
private fun StatusBadgesCatalogSection(colors: IdeShellColorScheme) {
    SectionContainer(
        title = "5. Status Badges & Severity",
        description = "Standardized severity badges with high WCAG contrast, background tints, and iconography.",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            IdeStatusBadge(label = "Ready", severity = IdeStatusSeverity.Ready, colors = colors)
            IdeStatusBadge(label = "Running", severity = IdeStatusSeverity.Running, colors = colors)
            IdeStatusBadge(label = "Warning", severity = IdeStatusSeverity.Warning, colors = colors)
            IdeStatusBadge(label = "Blocking", severity = IdeStatusSeverity.Blocking, colors = colors)
            IdeStatusBadge(label = "Failed", severity = IdeStatusSeverity.Failed, colors = colors)
            IdeStatusBadge(label = "Neutral", severity = IdeStatusSeverity.Neutral, colors = colors)
        }
    }
}

@Composable
private fun SummaryGridCatalogSection(colors: IdeShellColorScheme) {
    val items = listOf(
        IdeSummaryItem(title = "Device Target", value = "Pixel 8 Pro", detail = "Google (Tensor G3)"),
        IdeSummaryItem(title = "Build Fingerprint", value = "google/shiba/shiba:15", detail = "AP2A.240805.005"),
        IdeSummaryItem(title = "Partitions", value = "14 Images", detail = "Super (EROFS, EXT4)"),
        IdeSummaryItem(title = "AVB 2.0 Key", value = "Not Configured", detail = "Unavailable", isUnavailable = true),
    )

    SectionContainer(
        title = "6. Four-Column Summary Grid",
        description = "Structured metric grid maintaining fixed four-column layout geometry across dynamic data updates.",
    ) {
        IdeSummaryGrid(items = items, colors = colors)
    }
}

@Composable
private fun ReadinessMatrixCatalogSection(colors: IdeShellColorScheme) {
    SectionContainer(
        title = "7. Readiness Check Matrix Rows",
        description = "Five-column prerequisite check rows providing stage identity, check name, status badge, details, and action.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            IdeReadinessRow(
                stageName = "Ingest",
                label = "ROM Archive Verification",
                statusText = "Ready",
                severity = IdeStatusSeverity.Ready,
                explanation = "Payload archive integrity validated with SHA-256 checksum.",
                actionLabel = "Re-check",
                onAction = {},
            )
            IdeReadinessRow(
                stageName = "Unpack",
                label = "Payload Dumper Extractor",
                statusText = "Ready",
                severity = IdeStatusSeverity.Ready,
                explanation = "Native dumper toolchain binary available and executable.",
                actionLabel = "Run",
                onAction = {},
            )
            IdeReadinessRow(
                stageName = "Sign",
                label = "AVB Signing Certificate",
                statusText = "Warning",
                severity = IdeStatusSeverity.Warning,
                explanation = "Custom signing key not provided; default test keys will be used.",
                actionLabel = "Configure",
                onAction = {},
            )
            IdeReadinessRow(
                stageName = "Flash",
                label = "Fastboot Target Connectivity",
                statusText = "Blocked",
                severity = IdeStatusSeverity.Blocking,
                explanation = "No Android target device detected over USB in fastbootd mode.",
                actionLabel = "Scan",
                onAction = {},
                actionEnabled = false,
            )
        }
    }
}

@Composable
private fun StatusBarCatalogSection(colors: IdeShellColorScheme) {
    SectionContainer(
        title = "8. Status Bar (24 dp)",
        description = "Persistent bottom chrome communicating environment, git branch, cursor metrics, and background task progress.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Ready State", style = MaterialTheme.typography.labelMedium)
            IdeStatusBar(
                colors = colors,
                leadingContent = {
                    Text(
                        text = "git: feature/ide-shell",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        fontFamily = codeFontFamily(),
                    )
                    Text(
                        text = "Toolchain: Linux-x64",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                    )
                },
                trailingContent = {
                    Text(
                        text = "UTF-8 • LF • Ln 124, Col 40",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        fontFamily = codeFontFamily(),
                    )
                },
            )

            Text("Active Background Task", style = MaterialTheme.typography.labelMedium)
            IdeStatusBar(
                colors = colors,
                leadingContent = {
                    Text(
                        text = "Building super.img partition table...",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accentBlue,
                    )
                },
                trailingContent = {
                    Text(
                        text = "48% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMain,
                    )
                },
            )
        }
    }
}

@Composable
private fun AppFramePreviewSection(colors: IdeShellColorScheme) {
    var activeStage by remember { mutableStateOf("modify") }

    val stages = listOf(
        IdePipelineStageItem(id = "ingest", label = "Ingest", icon = "ingest"),
        IdePipelineStageItem(id = "unpack", label = "Unpack", icon = "unpack"),
        IdePipelineStageItem(id = "modify", label = "Modify", icon = "modify"),
        IdePipelineStageItem(id = "flash", label = "Flash", icon = "flash", enabled = false),
    )

    val navItems = listOf(
        IdeNavigatorItem(id = "overview", label = "Overview", icon = "overview"),
        IdeNavigatorItem(id = "partitions", label = "Partitions", icon = "partitions", badge = "8"),
        IdeNavigatorItem(id = "settings", label = "Settings", icon = "settings"),
    )

    SectionContainer(
        title = "9. Complete Frame Integration Preview",
        description = "Stateless full frame combining top bar, pipeline rail, navigator, breadcrumb, scrollable content, and status bar.",
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IdeShellDimens.CompactHeightBreakpoint)
                .clip(GlassShapes.ShellCard)
                .border(IdeShellDimens.HairlineBorder, colors.border, GlassShapes.ShellCard),
        ) {
            IdeAppFrame(
                colors = colors,
                topBar = {
                    IdeTopAppBar(
                        colors = colors,
                        leadingContent = {
                            Text(
                                text = "LtiRom Studio",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textMain,
                            )
                        },
                        trailingContent = {
                            Text(
                                text = "Target: Pixel 8 Pro",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                            )
                        },
                    )
                },
                pipelineRail = {
                    IdePipelineRail(
                        stages = stages,
                        selectedStageId = activeStage,
                        onSelectStage = { activeStage = it },
                        colors = colors,
                    )
                },
                navigator = {
                    IdeNavigatorPanel(
                        headerTitle = "STUDIO",
                        headerSubtitle = "shiba",
                        items = navItems,
                        selectedItemId = "overview",
                        onSelectItem = {},
                        colors = colors,
                    )
                },
                breadcrumb = {
                    IdeBreadcrumbBar(
                        segments = listOf("Workspace", "Pixel 8 Pro", activeStage.replaceFirstChar { it.uppercase() }),
                        colors = colors,
                    )
                },
                statusBar = {
                    IdeStatusBar(
                        colors = colors,
                        leadingContent = {
                            Text("Ready", style = MaterialTheme.typography.labelSmall, color = colors.accentGreen)
                        },
                        trailingContent = {
                            Text("UTF-8", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        },
                    )
                },
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.Large),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Central Content Area (Owns Bounded Scroll)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textMain,
                        )
                    }
                },
            )
        }
    }
}
