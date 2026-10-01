/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * A single row in the readiness matrix representing a workflow prerequisite.
 * Renders five columns: Stage Identity, Label, Status Badge, Explanation, and Primary Action.
 */
@Composable
fun IdeReadinessRow(
    stageName: String,
    label: String,
    statusText: String,
    severity: IdeStatusSeverity,
    explanation: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, GlassShapes.ShellControl)
            .glassOutlineBorder(
                color = MaterialTheme.colorScheme.outline,
                width = GlassDimens.HairlineBorder,
                shape = GlassShapes.ShellControl,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Column 1: Stage Identity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            modifier = Modifier.width(GlassDimens.ReadinessStageColumnWidth),
        ) {
            val icon = resolveStageIcon(stageName)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = stageName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.code(),
                maxLines = 1,
            )
        }

        // Column 2: Label
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = GlassFontFamily.ide(),
            modifier = Modifier.width(GlassDimens.ReadinessLabelColumnWidth),
            maxLines = 1,
        )

        // Column 3: Status Badge
        Box(modifier = Modifier.width(GlassDimens.ReadinessStatusColumnWidth)) {
            IdeStatusBadge(
                label = statusText,
                severity = severity,
            )
        }

        // Column 4: Explanation
        Text(
            text = explanation,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.ide(),
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )

        // Column 5: Action Button
        if (actionLabel != null && onAction != null) {
            val actionInteractionSource = remember { MutableInteractionSource() }
            val isActionHovered by actionInteractionSource.collectIsHoveredAsState()
            val isActionPressed by actionInteractionSource.collectIsPressedAsState()

            val btnBg = when {
                !actionEnabled -> Color.Transparent
                isActionPressed -> MaterialTheme.colorScheme.surfaceContainerHigh
                isActionHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Half)
                else -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .testTag("ReadinessRow_Action")
                    .semantics {
                        role = Role.Button
                        if (!actionEnabled) disabled()
                    }
                    .background(
                        color = btnBg,
                        shape = GlassShapes.ShellControl,
                    )
                    .glassOutlineBorder(
                        width = GlassDimens.HairlineBorder,
                        color = if (actionEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = GlassShapes.ShellControl,
                    )
                    .clickable(
                        interactionSource = actionInteractionSource,
                        indication = null,
                        enabled = actionEnabled,
                        onClick = onAction,
                    )
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    color = if (actionEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }
    }
}

private fun resolveStageIcon(stageName: String): ImageVector = when (stageName.lowercase()) {
    "acquire", "source" -> Icons.Default.Download
    "extract" -> Icons.Default.Folder
    "assemble" -> Icons.Default.Layers
    "debloat" -> Icons.Default.Delete
    "patches", "customization" -> Icons.Default.Widgets
    "build" -> Icons.Default.Build
    "signing", "security" -> Icons.Default.Settings
    "metadata" -> Icons.Default.Settings
    "publish", "publication" -> Icons.Default.Public
    else -> Icons.Default.Folder
}
