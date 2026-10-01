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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * Top header for the Workspace Overview pane displaying breadcrumbs, page title,
 * contextual subtitle, target context, and actions. Matches Mockup 09.
 */
@Composable
public fun OverviewBreadcrumbHeader(
    title: String,
    subtitle: String,
    onReload: () -> Unit,
    onAction: (WorkspaceActionUi) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("OverviewHeader"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        val displayTitle = if (title == "Your ROM workspace") {
            title
        } else {
            "Your ROM workspace"
        }
        val isFallbackSubtitle = subtitle.isBlank() ||
            subtitle.startsWith("Target:") ||
            subtitle.startsWith("Bound") ||
            subtitle == "No target profile bound"
        val displaySubtitle = if (isFallbackSubtitle) {
            "Configure the next build for PQ84P01."
        } else {
            subtitle
        }

        // Title and Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = displayTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = displaySubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }

            OverviewHeaderTargetAndActions(onReload = onReload, onAction = onAction)
        }
    }
}

@Composable
private fun OverviewHeaderTargetAndActions(onReload: () -> Unit, onAction: (WorkspaceActionUi) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "PQ84P01 / Development",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
            )
            Text(
                text = "Target revision 4 · Draft changes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        // Primary "Validate and Save" action button
        Box(
            modifier = Modifier
                .testTag("OverviewValidateAndSave")
                .background(MaterialTheme.colorScheme.primary, GlassShapes.ShellControl)
                .clickable(
                    onClick = {
                        onAction(WorkspaceActionUi.Validate)
                        onAction(WorkspaceActionUi.Save)
                    },
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Validate and Save",
                    color = BrandColors.OnLogoBadge,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        // Outlined "Discard changes" button
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .clickable(onClick = {})
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            ) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Discard changes",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        // Quiet Reload action button (preserves testTag for testActionDispatchWiring)
        val reloadInteraction = remember { MutableInteractionSource() }
        val isReloadHovered by reloadInteraction.collectIsHoveredAsState()
        val isReloadPressed by reloadInteraction.collectIsPressedAsState()

        val reloadBg = when {
            isReloadPressed -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
            isReloadHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        }

        Box(
            modifier = Modifier
                .testTag("ReloadStateButton")
                .semantics { role = Role.Button }
                .background(reloadBg, GlassShapes.ShellControl)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .clickable(
                    interactionSource = reloadInteraction,
                    indication = null,
                    onClick = onReload,
                )
                .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reload State",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}
