/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetValidationError
import org.ide.lti.feature.configuration.components.ConfigurationSidebar
import org.ide.lti.feature.configuration.components.ConfigurationTabHeader
import org.ide.lti.feature.configuration.components.tabs.GeneralConfigTab
import org.ide.lti.feature.configuration.components.tabs.HardwareConfigTab
import org.ide.lti.feature.configuration.components.tabs.OsStreamsConfigTab
import org.ide.lti.feature.configuration.components.tabs.PartitionsConfigTab
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConfigurationScreen(
    onBackClick: () -> Unit,
    initialTargetId: String? = null,
    initialMode: ConfigurationNavMode = ConfigurationNavMode.EDIT,
    initialTab: ConfigurationTab? = null,
    modifier: Modifier = Modifier,
    viewModel: ConfigurationViewModel = koinViewModel(),
) {
    LaunchedEffect(initialTargetId, initialMode, initialTab) {
        viewModel.applyNavigationIntent(initialTargetId, initialMode, initialTab)
    }

    val state by viewModel.uiState.collectAsState()

    state.pendingTargetId?.let { pendingTargetId ->
        PendingTargetDecisionDialog(
            pendingTargetId = pendingTargetId,
            onDecision = viewModel::resolvePendingTargetSelection,
        )
    }

    ConfigurationScreenContent(
        state = state,
        onBackClick = onBackClick,
        onSearchChange = viewModel::onSearchQueryChange,
        onSelectTarget = viewModel::onSelectTarget,
        onAddTarget = viewModel::onAddNewTarget,
        onDuplicateTarget = viewModel::onDuplicateTarget,
        onDeleteTarget = viewModel::onDeleteTarget,
        onSelectTab = viewModel::onSelectTab,
        onUpdateEditingTarget = viewModel::onUpdateEditingTarget,
        onRevert = viewModel::onRevertChanges,
        onApply = { viewModel.onApplyChanges(onSuccess = onBackClick) },
        modifier = modifier,
    )
}

@Composable
fun ConfigurationScreenContent(
    state: ConfigurationUiState,
    onBackClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectTarget: (String) -> Unit,
    onAddTarget: () -> Unit,
    onDuplicateTarget: (TargetDevice) -> Unit,
    onDeleteTarget: (String) -> Unit,
    onSelectTab: (ConfigurationTab) -> Unit,
    onUpdateEditingTarget: (TargetDevice) -> Unit,
    onRevert: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isBlueGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
    ) {
        // TOP BAR
        GlassTopBar(
            navigation = TopBarNavigation.Back(onBackClick),
            centerContent = {
                Text(
                    text = "Target & Run Configurations",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            drawOwnChrome = true,
        )

        // BODY: Two-Pane Studio
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Left Pane: Target Tree / Hierarchy
            ConfigurationSidebar(
                targets = state.availableTargets,
                selectedTargetId = state.selectedTargetId,
                searchQuery = state.searchQuery,
                onSearchChange = onSearchChange,
                onSelectTarget = onSelectTarget,
                onAddTarget = onAddTarget,
                onDuplicateTarget = onDuplicateTarget,
                onDeleteTarget = onDeleteTarget,
                modifier = Modifier.width(ComponentSize.ConfigurationSidebarWidth),
            )

            GlassVerticalDivider(specular = true)

            // Right Pane: Tabbed Configuration Inspector
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                ConfigurationTabHeader(
                    activeTab = state.activeTab,
                    onTabSelect = onSelectTab,
                )

                GlassHorizontalDivider(specular = true)

                // Validation or Status Notification Alert
                if (state.validationErrors.isNotEmpty()) {
                    ConfigurationValidationBanner(
                        validationErrors = state.validationErrors,
                    )
                }

                // Scrollable tab content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(Spacing.Medium)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (state.activeTab) {
                        ConfigurationTab.GENERAL -> GeneralConfigTab(
                            target = state.editingTarget,
                            onTargetChange = onUpdateEditingTarget,
                        )
                        ConfigurationTab.OS_STREAMS -> OsStreamsConfigTab(
                            target = state.editingTarget,
                            onTargetChange = onUpdateEditingTarget,
                        )
                        ConfigurationTab.HARDWARE -> HardwareConfigTab(
                            target = state.editingTarget,
                            onTargetChange = onUpdateEditingTarget,
                        )
                        ConfigurationTab.PARTITIONS -> PartitionsConfigTab(
                            target = state.editingTarget,
                            onTargetChange = onUpdateEditingTarget,
                        )
                    }
                }

                GlassHorizontalDivider(specular = true)

                // Bottom Action Footer Bar
                ConfigurationActionBar(
                    isDirty = state.isDirty,
                    isSyncing = state.isSyncing,
                    hasValidationErrors = state.validationErrors.isNotEmpty(),
                    onRevert = onRevert,
                    onCancel = onBackClick,
                    onApply = onApply,
                )
            }
        }
    }
}

@Composable
private fun ConfigurationValidationBanner(validationErrors: List<TargetValidationError>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
            .clip(GlassShapes.Small)
            .background(GlassTheme.diagnosticColors.error.copy(alpha = AlphaTokens.Hover))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = GlassTheme.diagnosticColors.error.copy(alpha = AlphaTokens.Border),
                shape = GlassShapes.Small,
            )
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = GlassTheme.diagnosticColors.error,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = validationErrors.joinToString("; ") { it.message },
            style = MaterialTheme.typography.bodySmall,
            color = GlassTheme.diagnosticColors.error,
        )
    }
}

@Composable
private fun ConfigurationActionBar(
    isDirty: Boolean,
    isSyncing: Boolean,
    hasValidationErrors: Boolean,
    onRevert: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Faint))
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        GlassButton(
            onClick = onRevert,
            enabled = isDirty,
            variant = GlassButtonVariant.Standard,
        ) {
            Text("Revert")
        }

        GlassButton(
            onClick = onCancel,
            variant = GlassButtonVariant.Standard,
        ) {
            Text("Cancel")
        }

        GlassPrimaryButton(
            onClick = onApply,
            enabled = !hasValidationErrors && !isSyncing,
        ) {
            Text(if (isSyncing) "Syncing..." else "Apply & Close")
        }
    }
}

@Composable
private fun PendingTargetDecisionDialog(pendingTargetId: String, onDecision: (DirtyTargetSelectionDecision) -> Unit) {
    GlassDialog(
        onDismissRequest = { onDecision(DirtyTargetSelectionDecision.STAY) },
        title = "Unsaved target changes",
        content = {
            Text("Save, discard, or stay before opening $pendingTargetId.")
        },
        confirmButton = {
            GlassTextButton(onClick = { onDecision(DirtyTargetSelectionDecision.SAVE) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassTextButton(onClick = { onDecision(DirtyTargetSelectionDecision.DISCARD) }) {
                    Text("Discard")
                }
                GlassTextButton(onClick = { onDecision(DirtyTargetSelectionDecision.STAY) }) {
                    Text("Stay")
                }
            }
        },
    )
}
