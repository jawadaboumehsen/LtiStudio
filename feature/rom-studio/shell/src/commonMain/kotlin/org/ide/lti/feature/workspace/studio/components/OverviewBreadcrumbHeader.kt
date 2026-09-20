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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Top header for the Workspace Overview pane displaying breadcrumbs, page title,
 * contextual subtitle, and quick-action toolbar.
 */
@Composable
public fun OverviewBreadcrumbHeader(
    title: String,
    subtitle: String,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
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
                    color = colors.textMain,
                    fontSize = FontSize.TitleLarge,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = displaySubtitle,
                    color = colors.textDim,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }

            // Reload State Action Button
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()
            val isPressed by interactionSource.collectIsPressedAsState()

            val btnBg = when {
                isPressed -> colors.controlSelected
                isHovered -> colors.controlHover
                else -> colors.cardBackground
            }

            Box(
                modifier = Modifier
                    .testTag("ReloadStateButton")
                    .semantics { role = Role.Button }
                    .background(btnBg, IdeShellShapes.Control)
                    .border(IdeShellDimens.HairlineBorder, colors.border, IdeShellShapes.Control)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onReload,
                    )
                    .padding(horizontal = Spacing.MediumSmall, vertical = Spacing.Compact),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload State",
                        tint = colors.textMuted,
                        modifier = Modifier.size(IconSize.Search),
                    )
                    Text(
                        text = "Reload State",
                        color = colors.textMuted,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GlassFontFamily.ide(),
                    )
                }
            }
        }
    }
}
