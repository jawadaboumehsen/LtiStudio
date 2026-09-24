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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.BuildStateUi
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * Visual call-to-action banner between the readiness matrix and recent activity.
 * Matches Mockup 09: prominent CTA button on the left with status explanation on the right.
 */
@Composable
public fun OverviewCallToAction(
    buildState: BuildStateUi,
    nextAction: WorkspaceActionUi?,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("OverviewCtaBar")
            .ideCardSurface()
            .padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        // 1. Primary Action Button on the left
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        val isPressed by interactionSource.collectIsPressedAsState()

        val btnBg = when {
            isPressed -> MaterialTheme.colorScheme.primary
            isHovered -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primary
        }

        val buttonLabel = when (buildState) {
            is BuildStateUi.InProgress -> "View build"
            else -> "Configure firmware"
        }

        Box(
            modifier = Modifier
                .testTag("OverviewCtaButton")
                .semantics { role = Role.Button }
                .background(btnBg, GlassShapes.ShellControl)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (nextAction != null) {
                            onAction(nextAction)
                        }
                    },
                )
                .padding(horizontal = Spacing.Large, vertical = Spacing.SmallMedium),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Medium),
                )
                Text(
                    text = buttonLabel,
                    color = BrandColors.OnLogoBadge,
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        // 2. Vertical separator
        Box(
            modifier = Modifier
                .width(Spacing.Hairline)
                .height(Spacing.ExtraLarge)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // 3. Status prompt explanation on the right
        val promptTitle = when (buildState) {
            is BuildStateUi.InProgress -> "Build in progress (${buildState.stageLabel})"
            is BuildStateUi.Succeeded -> "Last build completed successfully."
            is BuildStateUi.Failed -> "Last build failed: ${buildState.stageLabel}"
            BuildStateUi.NotStarted -> "No build has started."
        }

        val promptDetail = when (buildState) {
            is BuildStateUi.InProgress -> "Active run is compiling firmware artifacts."
            is BuildStateUi.Succeeded -> "Artifacts are ready for review or publication."
            is BuildStateUi.Failed -> "Review build logs and diagnostic details to resolve."
            BuildStateUi.NotStarted -> "Set up the items above, then configure firmware to continue."
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = promptTitle,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
            )
            Text(
                text = promptDetail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
        }
    }
}
