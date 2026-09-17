/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.GlobalDestination
import org.ide.lti.feature.workspace.studio.OperationUiState
import org.ide.lti.feature.workspace.studio.WorkspaceShellUiState

@Suppress("LongParameterList")
@Composable
public fun StudioTopAppBar(
    uiState: WorkspaceShellUiState,
    onSelectGlobalDestination: (GlobalDestination) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
) {
    GlobalAppTopBar(
        modifier = modifier,
        selectedDestination = GlobalAppDestination.Workspace,
        tagPrefix = "TopBar",
        onSelectDestination = { destination ->
            onSelectGlobalDestination(
                when (destination) {
                    GlobalAppDestination.Setup -> GlobalDestination.Setup
                    GlobalAppDestination.Workspace -> GlobalDestination.Workspace
                    GlobalAppDestination.Settings -> GlobalDestination.Settings
                },
            )
        },
        contextLabel = "Workspace",
        colors = colors,
        centerContent = {
            val targetContext = uiState.targetContext
            if (targetContext != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.testTag("TopBarTargetContext"),
                ) {
                    val targetText = when (val target = targetContext.targetId) {
                        is DisplayValue.Available -> target.text
                        is DisplayValue.Unavailable -> "Target: ${target.reason}"
                    }
                    Text(
                        text = targetText,
                        color = colors.textMain,
                        fontFamily = codeFontFamily(),
                        fontWeight = FontWeight.Medium,
                    )

                    val revText = when (val rev = targetContext.revision) {
                        is DisplayValue.Available -> rev.text
                        is DisplayValue.Unavailable -> "Unversioned"
                    }
                    IdeStatusBadge(
                        label = revText,
                        severity = IdeStatusSeverity.Neutral,
                        colors = colors,
                    )

                    if (uiState.hasUnsavedChanges || targetContext.draftBadge != null) {
                        IdeStatusBadge(
                            label = targetContext.draftBadge ?: "Unsaved",
                            severity = IdeStatusSeverity.Warning,
                            colors = colors,
                        )
                    }
                }
            }
        },
        actions = {
            // Validate Action
            val isValidating = uiState.validateOperation is OperationUiState.Running
            TopBarActionButton(
                label = "Validate",
                icon = Icons.Default.Check,
                isLoading = isValidating,
                isEnabled = !isValidating,
                colors = colors,
                onClick = onValidate,
                testTag = "TopBar_Validate",
            )

            // Save Action
            val isSaving = uiState.saveOperation is OperationUiState.Running
            val isSaveEnabled = uiState.hasUnsavedChanges && !isSaving
            TopBarActionButton(
                label = "Save",
                icon = Icons.Default.Save,
                isLoading = isSaving,
                isEnabled = isSaveEnabled,
                colors = colors,
                onClick = onSave,
                testTag = "TopBar_Save",
            )
        },
        windowControls = windowControls,
        onDragWindow = onDragWindow,
    )
}

@Composable
private fun TopBarActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    isEnabled: Boolean,
    colors: IdeShellColorScheme,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val bg = if (isEnabled) colors.controlSelected else colors.topBarBackground
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(IdeShellShapes.Control)
            .border(
                width = StrokeWidth.Hairline,
                color = colors.border,
                shape = IdeShellShapes.Control,
            )
            .background(bg)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = colors.accentBlue,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small),
                    tint = if (isEnabled) colors.textMain else colors.textMuted,
                )
            }
            Text(
                text = label,
                color = if (isEnabled) colors.textMain else colors.textMuted,
                fontFamily = ideFontFamily(),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
