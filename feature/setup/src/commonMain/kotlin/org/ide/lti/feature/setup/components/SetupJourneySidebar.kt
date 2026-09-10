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
import androidx.compose.ui.graphics.Color
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
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.SetupTab

/**
 * Modern Linear/Fleet-tier Left Sidebar presenting the cohesive setup journey:
 * 1. Environment & Toolchain
 * 2. Workspace Launchpad
 *
 * Adheres strictly to:
 * - Single Responsibility: Manages setup navigation and journey step states.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Composable
fun SetupJourneySidebar(
    currentStep: SetupTab,
    onStepSelected: (SetupTab) -> Unit,
    toolchainSetupState: ToolchainSetupState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isEnvironmentReady = toolchainSetupState.isAllReady
    val isEnvironmentRunning = toolchainSetupState.isRunning
    val hasEnvironmentIssues = toolchainSetupState.steps.any { it.status == StepStatus.FAILED } ||
        toolchainSetupState.diagnostics.any { it.status == StepStatus.FAILED }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.PanelPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        // Brand Header (Linear style)
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
                label = if (isEnvironmentReady) "Ready" else "v0.0.1",
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
            // Domain 1: WORKSPACE
            Text(
                text = "WORKSPACE",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = glassColors.onGlassMuted,
                modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            )

            GlassSidebarItem(
                label = "Projects Hub",
                icon = AppIcons.FolderOpen,
                selected = currentStep == SetupTab.PROJECTS,
                onClick = { onStepSelected(SetupTab.PROJECTS) },
            )

            Spacer(modifier = Modifier.height(Spacing.Small))
            GlassHorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            // Domain 2: SYSTEM HEALTH
            Text(
                text = "SYSTEM HEALTH",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = glassColors.onGlassMuted,
                modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            )

            // Step 1: Environment & Toolchain
            JourneyStepCard(
                stepNumber = "1",
                title = "Environment & Tools",
                subtitle = when {
                    toolchainSetupState.isFixing -> "Fixing Packages..."
                    toolchainSetupState.isChecking -> "Checking Health..."
                    isEnvironmentRunning -> "Configuring..."
                    isEnvironmentReady -> "All Checks Verified"
                    hasEnvironmentIssues -> "Action Required"
                    else -> "WSL2 & Compilers"
                },
                isComplete = isEnvironmentReady && currentStep != SetupTab.ENVIRONMENT,
                isActive = currentStep == SetupTab.ENVIRONMENT,
                enabled = !toolchainSetupState.isBusy || currentStep == SetupTab.ENVIRONMENT,
                isRunning = isEnvironmentRunning || toolchainSetupState.isChecking || toolchainSetupState.isFixing,
                isError = hasEnvironmentIssues && !toolchainSetupState.isBusy,
                statusBadge = when {
                    toolchainSetupState.isFixing -> "Fixing"
                    toolchainSetupState.isChecking -> "Checking"
                    isEnvironmentRunning -> "Running"
                    isEnvironmentReady -> "Ready"
                    hasEnvironmentIssues -> "Fix Needed"
                    else -> "Pending"
                },
                onClick = { onStepSelected(SetupTab.ENVIRONMENT) },
            )

            // Step 2: Machine Security & Launchpad
            JourneyStepCard(
                stepNumber = "2",
                title = "Security & Keys",
                subtitle = when {
                    isEnvironmentReady && toolchainSetupState.isAvbKeyProvisioned -> "Ready to Launch"
                    !toolchainSetupState.isAvbKeyProvisioned -> "AVB Key Required"
                    else -> "Machine Signing & Keys"
                },
                isComplete = isEnvironmentReady && toolchainSetupState.isAvbKeyProvisioned && currentStep != SetupTab.LAUNCHPAD,
                isActive = currentStep == SetupTab.LAUNCHPAD,
                enabled = !toolchainSetupState.isBusy || currentStep == SetupTab.LAUNCHPAD,
                statusBadge = if (isEnvironmentReady && toolchainSetupState.isAvbKeyProvisioned) "Ready" else if (!toolchainSetupState.isAvbKeyProvisioned) "Key Needed" else null,
                onClick = { onStepSelected(SetupTab.LAUNCHPAD) },
            )
        }

        GlassHorizontalDivider()
        Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

        GlassSidebarItem(
            label = "Settings",
            icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
            selected = false,
            onClick = onOpenSettings,
        )
    }
}

/**
 * Rich, high-density Journey Step Card for the setup sidebar navigation.
 */
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

    val targetBackground = when {
        isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.Glow)
        isHovered && enabled -> glassColors.onGlass.copy(alpha = AlphaTokens.UltraFaint)
        else -> Color.Transparent
    }

    val targetBorder = when {
        isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.Faded)
        isHovered && enabled -> glassColors.glassBorder.copy(alpha = AlphaTokens.Glow)
        else -> Color.Transparent
    }

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
        // Step Indicator Icon/Badge
        Box(
            modifier = Modifier
                .size(IconSize.Large)
                .clip(GlassShapes.Capsule)
                .background(
                    when {
                        isComplete -> diagnostics.success.copy(alpha = AlphaTokens.GlassSecondaryTint)
                        isError -> diagnostics.error.copy(alpha = AlphaTokens.GlassSecondaryTint)
                        isActive -> glassColors.glassAccent.copy(alpha = AlphaTokens.GlassSecondaryTint)
                        else -> glassColors.onGlassMuted.copy(alpha = AlphaTokens.Faint)
                    }
                )
                .border(
                    width = StrokeWidth.Hairline,
                    color = when {
                        isComplete -> diagnostics.success.copy(alpha = AlphaTokens.Border)
                        isError -> diagnostics.error.copy(alpha = AlphaTokens.Border)
                        isActive -> glassColors.glassAccent
                        else -> glassColors.glassBorder
                    },
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
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isError -> diagnostics.error
                        isActive -> glassColors.glassAccent
                        else -> glassColors.onGlassMuted
                    },
                )
            }
        }

        // Title and Subtitle with integrated status badge
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
