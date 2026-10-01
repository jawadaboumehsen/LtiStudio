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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.koin.compose.viewmodel.koinViewModel

/**
 * Workspace Configuration Panel for editing per-workspace target configuration snapshot drafts.
 *
 * Implements User Story 1 (Phase 3, T049):
 * - Binds directly to [WorkspaceConfigurationViewModel] and its [ConfigurationDraft].
 * - Supports region variant switching, firmware baseline selection, acquisition mode (DOWNLOAD vs IMPORT_ARCHIVE).
 * - Edit controls for buildType, romVersion, otaBaseUrl with Save and Discard actions.
 * - Conforms strictly to Liquid Glass design tokens and components.
 */
@Composable
fun WorkspaceConfigurationPanel(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceConfigurationViewModel = koinViewModel(),
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
            GlassHorizontalDivider()
            RegionSection(uiState.availableRegions, draft.region, viewModel::updateDraftRegion)
            FirmwareSection(
                firmwares = uiState.availableFirmwares[draft.region].orEmpty(),
                selected = draft.firmware,
                onSelect = viewModel::updateDraftFirmware,
            )
            GlassHorizontalDivider()
            AcquisitionSection(draft, uiState.isUploadingArchive, viewModel)
            BuildTypeSection(draft.buildType, viewModel::setBuildType)
            TextSection("ROM Version", draft.romVersion, "1.0.0", viewModel::setRomVersion)
            TextSection("OTA Base URL", draft.otaBaseUrl, "https://ota.ltirom.org", viewModel::setOtaBaseUrl)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegionSection(regions: List<TargetRegion>, selected: TargetRegion, onSelect: (TargetRegion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Target Region")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            regions.forEach { region ->
                GlassChip(
                    label = "${if (region == TargetRegion.GLOBAL) "🌐" else "🇨🇳"} ${region.displayName}",
                    selected = selected == region,
                    onClick = { onSelect(region) },
                )
            }
        }
    }
}

@Composable
private fun FirmwareSection(
    firmwares: List<TargetFirmware>,
    selected: TargetFirmware,
    onSelect: (TargetFirmware) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Firmware Baseline")
        if (firmwares.isEmpty()) {
            FirmwareItemCard(firmware = selected, isSelected = true, onClick = {})
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                firmwares.forEach { fw ->
                    FirmwareItemCard(
                        firmware = fw,
                        isSelected = selected.version == fw.version,
                        onClick = { onSelect(fw) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AcquisitionSection(
    draft: ConfigurationDraft,
    isUploadingArchive: Boolean,
    viewModel: WorkspaceConfigurationViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        SectionTitle("Firmware Acquisition Mode")
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassChip(
                label = "Download OTA",
                selected = draft.acquisitionMode == AcquisitionMode.DOWNLOAD,
                onClick = { viewModel.selectAcquisitionMode(AcquisitionMode.DOWNLOAD) },
            )
            GlassChip(
                label = "Import Local Archive",
                selected = draft.acquisitionMode == AcquisitionMode.IMPORT_ARCHIVE,
                onClick = { viewModel.selectAcquisitionMode(AcquisitionMode.IMPORT_ARCHIVE) },
            )
        }
        if (draft.acquisitionMode == AcquisitionMode.IMPORT_ARCHIVE) {
            ArchiveImportCard(draft.importedArchiveName, isUploadingArchive) { viewModel.pickAndUploadArchive() }
        }
    }
}

@Composable
private fun ArchiveImportCard(importedArchiveName: String?, isUploading: Boolean, onPickArchive: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(
                text = "Archive File: ${importedArchiveName ?: "None selected"}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = codeFontFamily(),
                color = if (importedArchiveName !=
                    null
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(
                    onClick = onPickArchive,
                    variant = GlassButtonVariant.Secondary,
                    enabled = !isUploading,
                ) {
                    Text(if (isUploading) "Uploading Archive..." else "Choose Archive File")
                }
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(IconSize.Small),
                        strokeWidth = StrokeWidth.Standard,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
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

@Composable
private fun FirmwareItemCard(
    firmware: TargetFirmware,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle)
                },
            )
            .border(
                width = if (isSelected) StrokeWidth.Focused else StrokeWidth.Hairline,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
                },
                shape = GlassShapes.Small,
            )
            .clickable(onClick = onClick)
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = firmware.version,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (firmware.isRecommended) {
                        GlassChip(
                            label = "Recommended",
                            selected = true,
                        )
                    }
                    if (firmware.isOfficial) {
                        GlassChip(
                            label = "Official",
                            selected = false,
                        )
                    }
                }

                Text(
                    text = "${firmware.buildId} • Android ${firmware.androidVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
