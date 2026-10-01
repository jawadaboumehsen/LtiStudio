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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import kotlinx.datetime.Clock
import org.ide.lti.core.designsystem.component.navigation.GlassSidebarItem
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.feature.setup.SetupDestination
import org.ide.lti.feature.setup.SetupTab

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val MILLIS_PER_MINUTE = SECONDS_PER_MINUTE * MILLIS_PER_SECOND

/**
 * Clean Setup Navigation Sidebar matching the Machine Environment specification.
 *
 * Renders the 4 canonical machine environment tabs:
 * 1. Projects
 * 2. Environment
 * 3. Tools
 * 4. Recovery
 */
@Suppress("UnusedParameter")
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
            SetupDestination.TOOLS -> onStepSelected(SetupTab.TOOLS)
            SetupDestination.RECOVERY -> onStepSelected(SetupTab.RECOVERY)
        }
    },
) {
    val isBlue = GlassTheme.appTheme == org.ide.lti.core.designsystem.theme.AppTheme.Blue

    val isWorkspacesActive = currentStep == SetupTab.PROJECTS || selectedDestination == SetupDestination.WORKSPACES
    val isSelectedEnv = selectedDestination == SetupDestination.ENVIRONMENT || currentStep == SetupTab.ENVIRONMENT
    val isEnvironmentActive = isSelectedEnv && !isWorkspacesActive
    val isToolsActive = selectedDestination == SetupDestination.TOOLS || currentStep == SetupTab.TOOLS
    val isRecoveryActive = selectedDestination == SetupDestination.RECOVERY || currentStep == SetupTab.RECOVERY

    val sidebarBackgroundModifier = if (isBlue) {
        Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    StudioBackdropColors.SidebarStart,
                    StudioBackdropColors.SidebarCenter,
                    StudioBackdropColors.SidebarEnd,
                ),
            ),
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(sidebarBackgroundModifier)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = "Machine environment",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        )

        GlassSidebarItem(
            label = "Projects",
            icon = AppIcons.Home,
            selected = isWorkspacesActive,
            onClick = {
                onDestinationSelected(SetupDestination.WORKSPACES)
                onStepSelected(SetupTab.PROJECTS)
            },
        )

        GlassSidebarItem(
            label = "Environment",
            icon = AppIcon.Vector(Icons.Default.Terminal),
            selected = isEnvironmentActive && !isToolsActive && !isRecoveryActive,
            onClick = {
                onDestinationSelected(SetupDestination.ENVIRONMENT)
                onStepSelected(SetupTab.ENVIRONMENT)
            },
        )

        GlassSidebarItem(
            label = "Tools",
            icon = AppIcon.Vector(Icons.Default.GridView),
            selected = isToolsActive,
            onClick = {
                onDestinationSelected(SetupDestination.TOOLS)
                onStepSelected(SetupTab.TOOLS)
            },
        )

        GlassSidebarItem(
            label = "Recovery",
            icon = AppIcons.Refresh,
            selected = isRecoveryActive,
            onClick = {
                onDestinationSelected(SetupDestination.RECOVERY)
                onStepSelected(SetupTab.RECOVERY)
            },
        )
    }
}

fun formatRelativeAgo(epochMs: Long, nowEpochMs: Long = Clock.System.now().toEpochMilliseconds()): String {
    val durationMs = (nowEpochMs - epochMs).coerceAtLeast(0L)
    val minutes = durationMs / MILLIS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    return when {
        minutes < 1L -> "just now"
        minutes < MINUTES_PER_HOUR -> "${minutes}m ago"
        hours < HOURS_PER_DAY -> "${hours}h ago"
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
        state.isAllReady -> "Ready"
        hasFailedSteps && !state.isBusy -> "Fix needed"
        hasRestoredSteps -> "Checking"
        else -> "Pending"
    }
}
