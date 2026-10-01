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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.ReadinessId
import org.ide.lti.feature.workspace.studio.ReadinessItemUi
import org.ide.lti.feature.workspace.studio.ReadinessSeverity
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * 5-row structured readiness matrix displaying prerequisite status across pipeline steps
 * (Source firmware, Debloat, Patches, Signing key, and Publication).
 */
@Composable
public fun ReadinessChecklistSection(
    items: List<ReadinessItemUi>,
    reviewCount: Int,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ReadinessMatrix")
            .ideCardSurface(),
    ) {
        // Card Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Build readiness",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "Complete the required items to prepare your next build.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
            if (reviewCount > 0) {
                Text(
                    text = "$reviewCount require review",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.Hairline)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }

            ReadinessRowItem(
                item = item,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ReadinessRowItem(
    item: ReadinessItemUi,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ReadinessRow_${item.id.name}")
            .background(
                if (isHovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // Stage Icon Box & Identity
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            modifier = Modifier.width(
                GlassDimens.ReadinessLabelColumnWidth + GlassDimens.OverviewStageIconBoxSize,
            ),
        ) {
            Box(
                modifier = Modifier
                    .size(GlassDimens.OverviewStageIconBoxSize)
                    .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                    .border(
                        GlassDimens.HairlineBorder,
                        MaterialTheme.colorScheme.outlineVariant,
                        GlassShapes.ShellControl,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = resolveReadinessIcon(item.id),
                    contentDescription = item.title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = item.contextLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        // Status Badge (useDot variant for sleek presentation)
        Box(modifier = Modifier.width(GlassDimens.ReadinessStatusColumnWidth + Spacing.ExtraExtraLarge)) {
            IdeStatusBadge(
                label = item.statusLabel,
                severity = mapReadinessSeverity(item),
                useDot = true,
            )
        }

        // Description
        Text(
            text = item.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = if (item.id == ReadinessId.PUBLICATION) {
                GlassFontFamily.code()
            } else {
                GlassFontFamily.ide()
            },
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )

        // Action Button
        if (item.action != null) {
            ReadinessActionButton(
                item = item,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ReadinessActionButton(item: ReadinessItemUi, onAction: (WorkspaceActionUi) -> Unit) {
    val action = item.action ?: return
    val btnInteraction = remember { MutableInteractionSource() }
    val isBtnHovered by btnInteraction.collectIsHoveredAsState()
    val isBtnPressed by btnInteraction.collectIsPressedAsState()

    val actionLabel = resolveActionLabel(item)
    val style = resolveActionButtonStyle(
        isPrimary = item.reviewRequired && item.id == ReadinessId.SOURCE,
        isHovered = isBtnHovered,
        isPressed = isBtnPressed,
        isPublication = item.id == ReadinessId.PUBLICATION,
    )

    Box(
        modifier = Modifier
            .testTag("ReadinessAction_${item.id.name}")
            .semantics { role = Role.Button }
            .background(style.bg, GlassShapes.ShellControl)
            .border(GlassDimens.HairlineBorder, style.border, GlassShapes.ShellControl)
            .clickable(
                interactionSource = btnInteraction,
                indication = null,
                onClick = { onAction(action) },
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = actionLabel,
            color = style.text,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}

private data class ActionButtonStyle(val bg: Color, val text: Color, val border: Color)

@Composable
private fun resolveActionButtonStyle(
    isPrimary: Boolean,
    isHovered: Boolean,
    isPressed: Boolean,
    isPublication: Boolean,
): ActionButtonStyle = when {
    isPrimary -> ActionButtonStyle(
        bg = if (isPressed || isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
        text = BrandColors.OnLogoBadge,
        border = MaterialTheme.colorScheme.primary,
    )
    isPublication -> ActionButtonStyle(
        bg = Color.Transparent,
        text = MaterialTheme.colorScheme.onSurfaceVariant,
        border = Color.Transparent,
    )
    else -> {
        val bg = when {
            isPressed -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
            isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        }
        ActionButtonStyle(
            bg = bg,
            text = MaterialTheme.colorScheme.onSurfaceVariant,
            border = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun resolveReadinessIcon(id: ReadinessId): ImageVector = when (id) {
    ReadinessId.SOURCE -> Icons.Default.Download
    ReadinessId.DEBLOAT -> Icons.Default.Delete
    ReadinessId.PATCHES -> Icons.Default.Edit
    ReadinessId.SIGNING -> Icons.Default.VpnKey
    ReadinessId.PUBLICATION -> Icons.Default.Public
}

private fun mapReadinessSeverity(item: ReadinessItemUi): IdeStatusSeverity = when {
    item.id == ReadinessId.SOURCE && item.statusLabel == "Needs selection" -> IdeStatusSeverity.Warning
    else -> when (item.severity) {
        ReadinessSeverity.Ready -> IdeStatusSeverity.Ready
        ReadinessSeverity.Warning -> IdeStatusSeverity.Warning
        ReadinessSeverity.Blocking -> IdeStatusSeverity.Blocking
        ReadinessSeverity.Neutral -> IdeStatusSeverity.Neutral
        ReadinessSeverity.Running -> IdeStatusSeverity.Running
        ReadinessSeverity.Failed -> IdeStatusSeverity.Failed
    }
}

private fun resolveActionLabel(item: ReadinessItemUi): String = when (item.id) {
    ReadinessId.SOURCE -> "Choose source"
    ReadinessId.DEBLOAT -> "Review"
    ReadinessId.PATCHES -> "Manage"
    ReadinessId.SIGNING -> "Configure"
    ReadinessId.PUBLICATION -> "Set up later"
}
