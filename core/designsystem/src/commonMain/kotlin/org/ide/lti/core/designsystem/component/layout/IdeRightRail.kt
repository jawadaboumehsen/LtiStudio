/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipPlacement
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdeRightRailItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/**
 * Compact 44 dp right rail matching the left rail geometry and glass styling.
 * Used across Settings, Setup, and Workspace for unified layout symmetry.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdeRightRail(
    items: List<IdeRightRailItem> = emptyList(),
    modifier: Modifier = Modifier,
    applySurface: Boolean? = null,
) {
    val inFrame = LocalInIdeAppFrame.current
    val shouldApplySurface = applySurface ?: !inFrame
    val surfaceModifier = if (shouldApplySurface) {
        Modifier.ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .width(GlassDimens.CompactRailWidth)
            .fillMaxHeight()
            .then(surfaceModifier)
            .padding(vertical = Spacing.Small)
            .testTag("IdeRightRail"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items.forEach { item ->
            RightRailButton(item = item)
        }
    }
}

@Composable
private fun RightRailButton(item: IdeRightRailItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val visuals = resolveRightRailVisuals(
        item = item,
        isPressed = isPressed,
        isHoveredOrFocused = isHovered || isFocused,
    )

    val borderModifier = if (item.selected) {
        Modifier.glassOutlineBorder(
            width = GlassDimens.HairlineBorder,
            color = visuals.borderColor,
            shape = GlassShapes.ShellPill,
        )
    } else {
        Modifier
    }

    GlassTooltipArea(
        tooltipText = item.label,
        tooltipPlacement = GlassTooltipPlacement.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDimens.PipelineStageItemHeight)
                .padding(
                    horizontal = Spacing.ExtraSmall,
                    vertical = Spacing.Hairline,
                )
                .background(visuals.bg, shape = GlassShapes.ShellPill)
                .clip(GlassShapes.ShellPill)
                .then(borderModifier)
                .testTag("RightRailItem_${item.id}")
                .semantics {
                    role = Role.Tab
                    selected = item.selected
                    contentDescription = item.label
                    if (!item.enabled) {
                        disabled()
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = item.enabled,
                    onClick = item.onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = visuals.contentColor,
                modifier = Modifier.size(IconSize.SidePanelRail),
            )
        }
    }
}

private data class RightRailVisuals(val bg: Color, val contentColor: Color, val borderColor: Color)

@Composable
private fun resolveRightRailVisuals(
    item: IdeRightRailItem,
    isPressed: Boolean,
    isHoveredOrFocused: Boolean,
): RightRailVisuals {
    val colors = MaterialTheme.colorScheme
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val effectsEnabled = GlassTheme.effectsEnabled

    return RightRailVisuals(
        bg = rightRailBackground(item.enabled, item.selected, isPressed, isHoveredOrFocused, effectsEnabled, colors),
        contentColor = rightRailContentColor(
            item.enabled,
            item.selected,
            isHoveredOrFocused,
            isBlue,
            effectsEnabled,
            colors,
        ),
        borderColor = if (isBlue) colors.outline else colors.primary,
    )
}

private fun rightRailBackground(
    enabled: Boolean,
    selected: Boolean,
    pressed: Boolean,
    hoveredOrFocused: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    !enabled -> Color.Transparent
    selected -> if (!effectsEnabled) colors.primaryContainer else colors.primary.copy(alpha = AlphaTokens.Faded)
    pressed -> colors.surfaceContainerHigh
    hoveredOrFocused -> colors.surfaceContainerHigh.copy(alpha = AlphaTokens.Half)
    else -> Color.Transparent
}

private fun rightRailContentColor(
    enabled: Boolean,
    selected: Boolean,
    hoveredOrFocused: Boolean,
    isBlue: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
    selected -> when {
        !effectsEnabled -> colors.onPrimaryContainer
        isBlue -> BrandColors.OnLogoBadge
        else -> colors.onSurface
    }
    hoveredOrFocused -> colors.onSurface
    else -> colors.onSurfaceVariant
}
