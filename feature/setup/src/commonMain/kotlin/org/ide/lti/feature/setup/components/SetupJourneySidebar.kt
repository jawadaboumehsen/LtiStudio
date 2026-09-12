/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.navigation.GlassSidebarItem
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.SetupDestination
import org.ide.lti.feature.setup.SetupTab

/**
 * Clean Setup Navigation Sidebar (FR-001, FR-003, FR-011, FR-012).
 *
 * Provides:
 * - Workspaces destination navigation.
 * - Environment destination navigation with live status.
 * - Destination actions: Manage Targets, Settings, and Help.
 * - Environment status summary when navigating outside Environment.
 * - Launchpad step removed; Tools kept unexposed until US3.
 */
@Composable
fun SetupJourneySidebar(
    currentStep: SetupTab,
    onStepSelected: (SetupTab) -> Unit,
    toolchainSetupState: ToolchainSetupState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenManageTargets: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    selectedDestination: SetupDestination = SetupDestination.fromId(currentStep.id),
    onDestinationSelected: (SetupDestination) -> Unit = { dest ->
        when (dest) {
            SetupDestination.WORKSPACES -> onStepSelected(SetupTab.PROJECTS)
            SetupDestination.ENVIRONMENT -> onStepSelected(SetupTab.ENVIRONMENT)
            SetupDestination.TOOLS -> {}
        }
    },
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isEnvironmentReady = toolchainSetupState.isAllReady
    val isAnyStepRunning = toolchainSetupState.hasAnyStepRunning()
    val isEnvironmentBusy = toolchainSetupState.isRunning || isAnyStepRunning || toolchainSetupState.isFixing
    val hasEnvironmentIssues = toolchainSetupState.hasAnyFailedSteps()

    val isWorkspacesActive = selectedDestination == SetupDestination.WORKSPACES || currentStep == SetupTab.PROJECTS
    val isEnvironmentActive = selectedDestination == SetupDestination.ENVIRONMENT || currentStep == SetupTab.ENVIRONMENT

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        // Brand Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .clip(GlassShapes.Capsule)
                    .background(if (isEnvironmentReady) diagnostics.success else glassColors.glassAccent),
            )
            Text(
                text = "LTIROM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = LetterSpacing.Widest,
                ),
                color = glassColors.onGlass,
            )
            Spacer(Modifier.weight(1f))
            GlassChip(
                label = if (isEnvironmentReady) "Ready" else "Setup",
                selected = isEnvironmentReady,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "DESTINATIONS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = glassColors.onGlassMuted,
                modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            )

            // Workspaces Destination
            GlassSidebarItem(
                label = "Workspaces",
                icon = AppIcons.FolderOpen,
                selected = isWorkspacesActive,
                onClick = {
                    onDestinationSelected(SetupDestination.WORKSPACES)
                    onStepSelected(SetupTab.PROJECTS)
                },
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Environment Destination
            JourneyStepCard(
                stepNumber = "2",
                title = "Environment",
                subtitle = computeSidebarSubtitle(toolchainSetupState),
                isComplete = isEnvironmentReady && !isEnvironmentActive,
                isActive = isEnvironmentActive,
                enabled = !toolchainSetupState.isBusy || isEnvironmentActive,
                isRunning = isEnvironmentBusy,
                isError = hasEnvironmentIssues && !toolchainSetupState.isBusy,
                statusBadge = computeSidebarBadge(toolchainSetupState),
                onClick = {
                    onDestinationSelected(SetupDestination.ENVIRONMENT)
                    onStepSelected(SetupTab.ENVIRONMENT)
                },
            )
        }

        // Summary Status outside Environment
        if (!isEnvironmentActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Box(
                        modifier = Modifier
                            .size(IconSize.Indicator)
                            .clip(GlassShapes.Capsule)
                            .background(
                                when {
                                    isEnvironmentReady -> diagnostics.success
                                    hasEnvironmentIssues -> diagnostics.error
                                    isEnvironmentBusy -> glassColors.glassAccent
                                    else -> glassColors.onGlassMuted
                                }
                            ),
                    )
                    Text(
                        text = "Env: ${computeSidebarBadge(toolchainSetupState)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.onGlassSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        // Actions: Manage Targets, Settings, Help
        GlassSidebarItem(
            label = "Manage Targets",
            icon = AppIcon.Painted(AppIcons.EditPainterResource),
            selected = false,
            onClick = onOpenManageTargets,
        )

        GlassSidebarItem(
            label = "Settings",
            icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
            selected = false,
            onClick = onOpenSettings,
        )

        GlassSidebarItem(
            label = "Help",
            icon = AppIcon.Painted(AppIcons.InfoPainterResource),
            selected = false,
            onClick = onOpenHelp,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun JourneyStepCard(
    stepNumber: String,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isRunning: Boolean = false,
    isError: Boolean = false,
    statusBadge: String? = null,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics
    var isHovered by remember { mutableStateOf(false) }

    val targetBackground = resolveStepBackgroundColor(isActive, isHovered, enabled, glassColors)
    val targetBorder = resolveStepBorderColor(isActive, isHovered, enabled, glassColors)

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(backgroundColor)
            .border(width = StrokeWidth.Hairline, color = borderColor, shape = GlassShapes.Small)
            .clickable(enabled = enabled, onClick = onClick)
            .onPointerEvent(PointerEventType.Enter) { if (enabled) isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.Large)
                .clip(GlassShapes.Capsule)
                .background(resolveStepIconBackground(isComplete, isError, isActive, glassColors, diagnostics))
                .border(
                    width = StrokeWidth.Hairline,
                    color = resolveStepIconBorder(isComplete, isError, isActive, glassColors, diagnostics),
                    shape = GlassShapes.Capsule,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = glassColors.glassAccent,
                )
            } else if (isComplete) {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = null,
                    tint = diagnostics.success,
                    modifier = Modifier.size(IconSize.Small),
                )
            } else {
                Icon(
                    painter = AppIcons.SparklesPainterResource(),
                    contentDescription = null,
                    tint = when {
                        isError -> diagnostics.error
                        isActive -> glassColors.glassAccent
                        else -> glassColors.onGlassMuted
                    },
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (isActive) glassColors.onGlass else glassColors.onGlassSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                statusBadge?.let { badge ->
                    GlassChip(
                        label = badge,
                        selected = isActive || isComplete,
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isComplete -> diagnostics.success
                    isError -> diagnostics.error
                    isActive -> glassColors.glassAccent
                    else -> glassColors.onGlassMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun formatRelativeAgo(epochMs: Long, nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): String {
    val durationMs = (nowEpochMs - epochMs).coerceAtLeast(0L)
    val minutes = durationMs / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

private fun ToolchainSetupState.hasAnyStepRunning(): Boolean =
    steps.any { it.status == StepStatus.RUNNING } || isChecking
private fun ToolchainSetupState.hasAnyFailedSteps(): Boolean =
    steps.any { it.status == StepStatus.FAILED } || diagnostics.any { it.status == StepStatus.FAILED }
private fun ToolchainSetupState.hasAnyRestoredSteps(): Boolean = steps.any { it.provenance == StepProvenance.RESTORED }
private fun ToolchainSetupState.findLastVerifiedTime(): Long? =
    steps.mapNotNull { it.verifiedAtEpochMs }.maxOrNull() ?: lastVerifiedTimestamp

fun computeSidebarSubtitle(
    state: ToolchainSetupState,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val isAnyStepRunning = state.hasAnyStepRunning()
    val hasFailedSteps = state.hasAnyFailedSteps()
    val hasRestoredSteps = state.hasAnyRestoredSteps()

    return when {
        isAnyStepRunning -> "Verifying…"
        state.isRunning -> "Configuring…"
        state.isFixing -> "Fixing packages…"
        state.isAllReady -> {
            val ts = state.lastVerifiedTimestamp
            if (ts != null && ts > 0) {
                "Verified ${formatRelativeAgo(ts, nowEpochMs)}"
            } else {
                "Verified just now"
            }
        }
        hasFailedSteps && !state.isBusy -> "Action required"
        hasRestoredSteps -> {
            val lastTime = state.findLastVerifiedTime()
            if (lastTime != null && lastTime > 0) {
                "Last verified ${formatRelativeAgo(lastTime, nowEpochMs)} — re-checking…"
            } else {
                "Last verified recently — re-checking…"
            }
        }
        else -> "WSL2 & Compilers"
    }
}

fun computeSidebarBadge(state: ToolchainSetupState): String {
    val isAnyStepRunning = state.hasAnyStepRunning()
    val hasFailedSteps = state.hasAnyFailedSteps()
    val hasRestoredSteps = state.hasAnyRestoredSteps()

    return when {
        isAnyStepRunning -> "Checking"
        state.isRunning -> "Running"
        state.isFixing -> "Fixing"
        state.isAllReady -> "Ready"
        hasFailedSteps && !state.isBusy -> "Fix needed"
        hasRestoredSteps -> "Checking"
        else -> "Pending"
    }
}

private fun resolveStepBackgroundColor(
    isActive: Boolean,
    isHovered: Boolean,
    enabled: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): androidx.compose.ui.graphics.Color = when {
    isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.Glow)
    isHovered && enabled -> glassColors.onGlass.copy(alpha = AlphaTokens.UltraFaint)
    else -> androidx.compose.ui.graphics.Color.Transparent
}

private fun resolveStepBorderColor(
    isActive: Boolean,
    isHovered: Boolean,
    enabled: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): androidx.compose.ui.graphics.Color = when {
    isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.Faded)
    isHovered && enabled -> glassColors.glassBorder.copy(alpha = AlphaTokens.Glow)
    else -> androidx.compose.ui.graphics.Color.Transparent
}

private fun resolveStepIconBackground(
    isComplete: Boolean,
    isError: Boolean,
    isActive: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors
): androidx.compose.ui.graphics.Color = when {
    isComplete -> diagnostics.success.copy(alpha = AlphaTokens.GlassSecondaryTint)
    isError -> diagnostics.error.copy(alpha = AlphaTokens.GlassSecondaryTint)
    isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.GlassSecondaryTint)
    else -> glassColors.onGlassMuted.copy(alpha = AlphaTokens.Faint)
}

private fun resolveStepIconBorder(
    isComplete: Boolean,
    isError: Boolean,
    isActive: Boolean,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
    diagnostics: org.ide.lti.core.designsystem.theme.DiagnosticColors
): androidx.compose.ui.graphics.Color = when {
    isComplete -> diagnostics.success.copy(alpha = AlphaTokens.Border)
    isError -> diagnostics.error.copy(alpha = AlphaTokens.Border)
    isActive -> glassColors.glassAccent
    else -> glassColors.glassBorder
}
