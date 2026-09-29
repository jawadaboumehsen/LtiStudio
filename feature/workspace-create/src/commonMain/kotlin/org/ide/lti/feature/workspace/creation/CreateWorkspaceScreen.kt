/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.creation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.progress.GlassConsoleDrawer
import org.ide.lti.core.designsystem.component.progress.GlassStepInfo
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.progress.GlassStepper
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screen for creating an independent, validated workspace for a target device.
 *
 * Implements User Story 1 (Phase 3, T051):
 * - Target hardware device picker via [GlassDropdownMenu].
 * - Workspace name entry via [GlassTextField].
 * - AVB Signing key strategy choice (Generate new RSA-4096 pair vs Copy global key).
 * - Multi-stage progression via [GlassStepper] (layout -> config -> key -> validate -> promote).
 * - Real-time terminal logs streamed in [GlassConsoleDrawer].
 * - Strict Liquid Glass design token compliance.
 */
@Composable
fun CreateWorkspaceScreen(
    onBackClick: () -> Unit,
    onWorkspaceCreated: (Workspace) -> Unit,
    onNavigateToSetup: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: CreateWorkspaceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdWorkspace) {
        uiState.createdWorkspace?.let { ws ->
            onWorkspaceCreated(ws)
        }
    }

    CreateWorkspaceContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onNavigateToSetup = onNavigateToSetup,
        onSelectTarget = viewModel::selectTarget,
        onNameChange = viewModel::setName,
        onKeySourceChange = viewModel::setKeySource,
        onCancel = { if (uiState.isProvisioning) viewModel.cancel() else onBackClick() },
        onCreate = { viewModel.startCreation() },
        modifier = modifier,
    )
}

@Composable
fun CreateWorkspaceContent(
    uiState: CreateWorkspaceUiState,
    onBackClick: () -> Unit,
    onNavigateToSetup: (() -> Unit)? = null,
    onSelectTarget: (TargetDevice) -> Unit,
    onNameChange: (String) -> Unit,
    onKeySourceChange: (KeySource) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isBlueGlass) Color.Transparent else MaterialTheme.colorScheme.surface)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        CreateWorkspaceHeader(onBackClick = onBackClick, enabled = !uiState.isProvisioning)
        GlassHorizontalDivider()
        AnimatedVisibility(
            visible =
            uiState.environmentReadiness != null &&
                uiState.environmentReadiness?.state != EnvironmentReadinessState.READY,
        ) {
            GlassBanner(
                severity = GlassBannerSeverity.WARNING,
                title = uiState.failingCheck ?: "Environment Unavailable",
                description = uiState.remediation
                    ?: "The build environment or service daemon is currently unreachable.",
                primaryAction = GlassBannerAction("Go to Setup") {
                    if (onNavigateToSetup != null) onNavigateToSetup() else onBackClick()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = uiState.errorMessage != null) {
            uiState.errorMessage?.let { errorMsg ->
                GlassBanner(
                    severity = GlassBannerSeverity.ERROR,
                    title = "Provisioning Error",
                    description = errorMsg,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            TargetPickerSection(uiState, onSelectTarget)
            WorkspaceNameSection(uiState, onNameChange)
            KeySourceSection(uiState, onKeySourceChange)
            AnimatedVisibility(visible = uiState.isProvisioning || uiState.logs.isNotEmpty()) {
                ProvisioningProgressCard(uiState)
            }
            AnimatedVisibility(visible = uiState.logs.isNotEmpty()) {
                GlassConsoleDrawer(
                    logs = uiState.logs,
                    isRunning = uiState.isProvisioning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlassHorizontalDivider()
        CreateWorkspaceActions(uiState, onCancel, onCreate)
    }
}

@Composable
private fun CreateWorkspaceHeader(onBackClick: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(onClick = onBackClick, enabled = enabled) {
            Icon(
                painter = AppIcons.ArrowBackPainterResource(),
                contentDescription = "Back",
                modifier = Modifier.size(IconSize.Medium),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "New Target Workspace",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Provision an isolated, verified build workspace with official firmware baselines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun TargetPickerSection(uiState: CreateWorkspaceUiState, onSelectTarget: (TargetDevice) -> Unit) {
    var isTargetPickerOpen by remember { mutableStateOf(false) }
    val currentTarget = uiState.selectedTarget
    val targetText = if (currentTarget != null) {
        "${currentTarget.name} (${currentTarget.codename} • ${currentTarget.socPlatform})"
    } else {
        "Select a target hardware profile..."
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Target Hardware Device")
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle))
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                        shape = GlassShapes.Small,
                    )
                    .clickable(enabled = !uiState.isProvisioning) {
                        isTargetPickerOpen = !isTargetPickerOpen
                    }
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (currentTarget !=
                            null
                        ) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Icon(
                        painter = AppIcons.ChevronDownPainterResource(),
                        contentDescription = "Open target picker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }

            GlassDropdownMenu(
                expanded = isTargetPickerOpen,
                onDismissRequest = { isTargetPickerOpen = false },
                offset = IntOffset(x = 0, y = 8),
            ) {
                GlassDropdownMenuHeader(title = "Supported Hardware Targets")
                uiState.availableTargets.forEach { target ->
                    GlassDropdownMenuItem(
                        text = target.name,
                        subtitle = "${target.codename} • ${target.socPlatform} • " +
                            target.filesystemType.uppercase(),
                        onClick = {
                            onSelectTarget(target)
                            isTargetPickerOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceNameSection(uiState: CreateWorkspaceUiState, onNameChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Workspace Name")
        GlassTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            placeholder = "e.g. astra-daily-build",
            enabled = !uiState.isProvisioning,
            modifier = Modifier.fillMaxWidth(),
        )
        uiState.nameError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.diagnosticColors.error,
            )
        }
    }
}

@Composable
private fun KeySourceSection(uiState: CreateWorkspaceUiState, onKeySourceChange: (KeySource) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("AVB 2.0 Signing Key Strategy")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip(
                label = "Generate Isolated RSA-4096 Key",
                selected = uiState.keySource == KeySource.GENERATE,
                enabled = !uiState.isProvisioning,
                onClick = { onKeySourceChange(KeySource.GENERATE) },
            )
            GlassChip(
                label = "Copy Global Machine Key",
                selected = uiState.keySource == KeySource.COPY_GLOBAL,
                enabled = !uiState.isProvisioning,
                onClick = { onKeySourceChange(KeySource.COPY_GLOBAL) },
            )
        }
    }
}

@Composable
private fun ProvisioningProgressCard(uiState: CreateWorkspaceUiState) {
    val stepperSteps = remember(
        uiState.currentStage,
        uiState.isProvisioning,
        uiState.errorMessage,
        uiState.createdWorkspace,
    ) {
        CreationStage.entries.map { stage ->
            GlassStepInfo(id = stage.stepId, shortTitle = stage.shortTitle, status = stage.status(uiState))
        }
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text(
                text = "Provisioning Progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GlassStepper(steps = stepperSteps, isBusy = uiState.isProvisioning)
            Text(
                text = uiState.currentStage.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CreationStage.shortTitle: String
    get() = when (this) {
        CreationStage.LAYOUT -> "Layout"
        CreationStage.CONFIG -> "Config"
        CreationStage.KEY -> "Keys"
        CreationStage.VALIDATE -> "Validate"
        CreationStage.PROMOTE -> "Promote"
    }

private fun CreationStage.status(uiState: CreateWorkspaceUiState): GlassStepStatus = when {
    uiState.createdWorkspace != null -> GlassStepStatus.SUCCESS
    ordinal < uiState.currentStage.ordinal -> GlassStepStatus.SUCCESS
    ordinal > uiState.currentStage.ordinal -> GlassStepStatus.PENDING
    uiState.errorMessage != null -> GlassStepStatus.FAILED
    uiState.isProvisioning -> GlassStepStatus.RUNNING
    else -> GlassStepStatus.PENDING
}

@Composable
private fun CreateWorkspaceActions(uiState: CreateWorkspaceUiState, onCancel: () -> Unit, onCreate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(
            onClick = onCancel,
            variant = GlassButtonVariant.Secondary,
        ) {
            Text(if (uiState.isProvisioning) "Cancel Provisioning" else "Cancel")
        }

        Spacer(modifier = Modifier.width(Spacing.SmallMedium))

        GlassButton(
            onClick = onCreate,
            variant = GlassButtonVariant.Primary,
            enabled =
            !uiState.isProvisioning &&
                uiState.name.isNotBlank() &&
                uiState.selectedTarget != null &&
                uiState.canCreate,
        ) {
            if (uiState.isProvisioning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Standard,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text("Provisioning...")
            } else {
                Text("Create Workspace")
            }
        }
    }
}
