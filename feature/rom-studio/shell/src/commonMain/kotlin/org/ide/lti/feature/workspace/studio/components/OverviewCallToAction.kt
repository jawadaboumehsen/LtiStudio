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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.BuildStateUi
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * Visual call-to-action banner between the readiness matrix and recent activity.
 * Displays build status prompt and primary action button (e.g. "Configure firmware").
 */
@Composable
public fun OverviewCallToAction(
    buildState: BuildStateUi,
    nextAction: WorkspaceActionUi?,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("OverviewCtaBar")
            .ideCardSurface(colors = colors)
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Status indicator dot and prompt
        val dotColor = when (buildState) {
            is BuildStateUi.InProgress -> colors.accentBlue
            is BuildStateUi.Succeeded -> colors.accentGreen
            is BuildStateUi.Failed -> colors.errorRed
            BuildStateUi.NotStarted -> colors.textDim
        }

        val promptText = when (buildState) {
            is BuildStateUi.InProgress -> "Build in progress (${buildState.stageLabel})"
            is BuildStateUi.Succeeded -> "Last build completed successfully."
            is BuildStateUi.Failed -> "Last build failed: ${buildState.stageLabel}"
            BuildStateUi.NotStarted -> "No build has started."
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .background(dotColor, GlassShapes.Full),
            )
            Text(
                text = promptText,
                color = colors.textDim,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        // Primary Action Button
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()

        val btnBg = when {
            isPressed -> colors.accentBlueHover
            isHovered -> colors.accentBlueHover
            else -> colors.accentBlue
        }

        val buttonLabel = when (buildState) {
            is BuildStateUi.InProgress -> "View build"
            else -> "Configure firmware"
        }

        Box(
            modifier = Modifier
                .testTag("OverviewCtaButton")
                .semantics { role = Role.Button }
                .background(btnBg, IdeShellShapes.Control)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (nextAction != null) {
                            onAction(nextAction)
                        }
                    },
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = buttonLabel,
                    color = BrandColors.OnLogoBadge,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Icon(
                    painter = AppIcons.SendPainterResource(),
                    contentDescription = null,
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Search),
                )
            }
        }
    }
}
