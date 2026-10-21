/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.koin.compose.viewmodel.koinViewModel

/**
 * Workspace Configuration Panel for editing per-workspace target configuration snapshot drafts.
 *
 * Implements User Story 1 (Phase 3, T049):
 * - Binds directly to [WorkspaceConfigurationViewModel] and its [ConfigurationDraft].
 * - Edit controls for buildType and romVersion with Save and Discard actions; the device section above.
 * - Conforms strictly to Liquid Glass design tokens and components.
 */
@Composable
fun WorkspaceConfigurationPanel(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceConfigurationViewModel = koinViewModel(),
    deviceViewModel: WorkspaceDeviceViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val draft = uiState.draft

    if (uiState.currentWorkspace == null || draft == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(Spacing.Large),
            contentAlignment = Alignment.Center,
        ) {
            GlassEmptyState(
                title = "No Active Workspace",
                description = "Open or create a workspace to view and edit its target configuration.",
                icon = AppIcon.Painted(AppIcons.SettingsPainterResource),
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            ConfigurationHeader(uiState)
            ConfigurationNotices(uiState)
            uiState.currentTarget?.let { TargetProfileCard(it) }
            WorkspaceDeviceSection(deviceViewModel)
            GlassHorizontalDivider()
            // One place per value: these are edited in their pipeline stage, not here.
            Text(
                text = "Region, firmware, download or imported archive, and checksum are set in the Acquire stage; " +
                    "the OTA base URL in the Release metadata stage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BuildTypeSection(draft.buildType, viewModel::setBuildType)
            TextSection("ROM Version", draft.romVersion, "1.0.0", viewModel::setRomVersion)
            GlassHorizontalDivider()
            ConfigurationActions(uiState, viewModel)
        }
    }
}

@Composable
private fun ConfigurationHeader(uiState: WorkspaceConfigurationUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Target Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = uiState.currentWorkspace?.name ?: "Workspace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassChip(
            label = if (uiState.isDirty) "Modified" else "Saved",
            selected = uiState.isDirty,
        )
    }
}

@Composable
private fun ConfigurationNotices(uiState: WorkspaceConfigurationUiState) {
    AnimatedVisibility(visible = uiState.errorMessage != null) {
        uiState.errorMessage?.let { errorMsg ->
            GlassBanner(severity = GlassBannerSeverity.ERROR, title = "Error", description = errorMsg)
        }
    }
    AnimatedVisibility(visible = uiState.successMessage != null) {
        uiState.successMessage?.let { successMsg ->
            GlassBanner(severity = GlassBannerSeverity.SUCCESS, title = "Success", description = successMsg)
        }
    }
    if (uiState.environmentReadiness != EnvironmentReadinessState.READY) {
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = "Environment Notice",
            description = "Build environment is not ready. Local configuration edits are preserved.",
        )
    }
}

@Composable
private fun TargetProfileCard(target: TargetDevice) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.CardPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${target.codename} • ${target.socPlatform}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GlassChip(label = target.filesystemType.uppercase(), selected = false)
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
private fun BuildTypeSection(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Target Build Type")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BUILD_TYPES.forEach { buildType ->
                GlassChip(
                    label = buildType,
                    selected = selected == buildType,
                    onClick = { onSelect(buildType) },
                )
            }
        }
    }
}

@Composable
private fun TextSection(title: String, value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle(title)
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfigurationActions(uiState: WorkspaceConfigurationUiState, viewModel: WorkspaceConfigurationViewModel) {
    val canAct = uiState.isDirty && !uiState.isSaving
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.Large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(
            onClick = { viewModel.saveConfiguration() },
            variant = GlassButtonVariant.Primary,
            enabled = canAct,
        ) {
            Text(if (uiState.isSaving) "Saving..." else "Save Changes")
        }
        GlassButton(
            onClick = { viewModel.discardChanges() },
            variant = GlassButtonVariant.Secondary,
            enabled = canAct,
        ) {
            Text("Discard")
        }
    }
}

private val BUILD_TYPES = listOf("user", "userdebug", "eng")
