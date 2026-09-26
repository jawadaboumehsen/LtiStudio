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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Type-safe interaction mode for [GlassChip].
 */
sealed interface ChipMode {
    data class Toggle(val selected: Boolean, val onToggle: (Boolean) -> Unit) : ChipMode
    data class Action(val onClick: () -> Unit) : ChipMode
    data object ReadOnly : ChipMode
}

/**
 * Standardized Liquid Glass Chip component - built on Haze's real `Modifier.hazeGlass`, same
 * `hovered { }` interaction pattern as [org.ide.lti.core.designsystem.component.actions.GlassButton].
 *
 * @param label The text label of the chip.
 * @param modifier Optional modifier.
 * @param selected Whether this chip is selected (for filter/choice chips).
 * @param onClick Action callback when clicked.
 * @param onSelectedChange Toggle callback for selectable filter chips.
 * @param leadingIcon Optional leading icon slot.
 * @param trailingIcon Optional trailing icon slot.
 * @param enabled Whether interaction is enabled.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalHazeApi::class)
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazeCapsule,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    var isHovered by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    val targetBackground = when {
        selected && isHovered -> colors.primary.copy(alpha = AlphaTokens.Elevated)
        selected -> colors.primary.copy(alpha = AlphaTokens.Hover)
        isHovered -> colors.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
        else -> colors.surfaceContainer.copy(alpha = AlphaTokens.Elevated)
    }
    val targetBorder = if (selected || isHovered) {
        colors.primary.copy(alpha = AlphaTokens.Prominent)
    } else {
        colors.primary.copy(alpha = AlphaTokens.Hover)
    }
    val targetText = when {
        selected -> colors.primary
        isHovered -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

    val backgroundColor by animateColorAsState(targetBackground)
    val borderColor by animateColorAsState(targetBorder)
    val textColor by animateColorAsState(targetText)

    val effectiveClick: (() -> Unit)? = when {
        !enabled -> null
        onSelectedChange != null -> {
            { onSelectedChange(!selected) }
        }
        onClick != null -> onClick
        else -> null
    }

    val theme = GlassTheme.appTheme
    val chipModifier = if (GlassTheme.effectsEnabled) {
        Modifier.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme, shape) {
                org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                    theme = theme,
                ).then {
                    this.shape(shape)
                    hovered { lightingIntensity(0.35f) }
                    pressed { scale(0.96f) }
                }
            },
        )
    } else {
        Modifier.background(backgroundColor, shape = shape)
    }

    Box(
        modifier = modifier
            .then(chipModifier)
            .clip(shape)
            .glassOutlineBorder(color = borderColor, width = StrokeWidth.Hairline, shape = shape)
            .onPointerEvent(PointerEventType.Enter) { if (enabled) isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { if (enabled) isHovered = false }
            .then(
                if (effectiveClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = if (onSelectedChange != null) Role.Checkbox else Role.Button,
                        onClick = effectiveClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.Chip),
                color = textColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            trailingIcon?.invoke()
        }
    }
}
