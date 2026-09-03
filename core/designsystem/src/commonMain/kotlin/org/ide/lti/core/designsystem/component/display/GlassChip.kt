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

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

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
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec

/**
 * Type-safe interaction mode for [GlassChip].
 */
sealed interface ChipMode {
    data class Toggle(val selected: Boolean, val onToggle: (Boolean) -> Unit) : ChipMode
    data class Action(val onClick: () -> Unit) : ChipMode
    data object ReadOnly : ChipMode
}

/**
 * Standardized Liquid Glass Chip component supporting action and toggleable filter modes.
 *
 * @param label The text label of the chip.
 * @param modifier Optional modifier.
 * @param selected Whether this chip is selected (for filter/choice chips).
 * @param onClick Action callback when clicked.
 * @param onSelectedChange Toggle callback for selectable filter chips.
 * @param leadingIcon Optional leading icon slot.
 * @param trailingIcon Optional trailing icon slot.
 * @param enabled Whether interaction is enabled.
 * @param backdrop Optional custom backdrop instance.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val chipShape = GlassShapes.Capsule

    val targetBackground = when {
        selected && isHovered -> glassColors.glassAccent.copy(alpha = AlphaTokens.Elevated)
        selected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Hover)
        isHovered -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated)
        else -> glassColors.glassSurface.copy(alpha = AlphaTokens.Elevated)
    }

    val targetBorder = when {
        selected && isHovered -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        selected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Prominent)
        isHovered -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        else -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Hover)
    }

    val targetText = when {
        selected && isHovered -> glassColors.onGlass
        selected -> glassColors.glassAccent
        isHovered -> glassColors.onGlass
        else -> glassColors.onGlassSecondary
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val textColor by animateColorAsState(
        targetValue = targetText,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    val effectiveClick: (() -> Unit)? = when {
        !enabled -> null
        onSelectedChange != null -> { { onSelectedChange(!selected) } }
        onClick != null -> onClick
        else -> null
    }

    val clickableModifier = if (effectiveClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = if (onSelectedChange != null) Role.Checkbox else Role.Button,
            onClick = effectiveClick,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(chipShape)
            .then(
                if (effectsEnabled && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { chipShape },
                        effects = GlassEffects.Chip,
                        highlight = { if (isHovered || selected) Highlight.Ambient else Highlight.Plain },
                        onDrawSurface = {
                            drawRect(backgroundColor)
                        },
                    )
                } else {
                    Modifier.background(backgroundColor)
                },
            )
            .glassOutlineBorder(
                color = borderColor,
                width = StrokeWidth.Hairline,
                shape = chipShape,
            )
            .onPointerEvent(PointerEventType.Enter) { if (enabled) isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { if (enabled) isHovered = false }
            .then(clickableModifier)
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
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = FontSize.Chip,
                ),
                color = textColor,
            )
            trailingIcon?.invoke()
        }
    }
}
