/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.capsule.ContinuousCapsule
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Button glass effect stack — crisp/glossy, per the kmp-liquid-glass skill's guidance for
 * buttons, toggles, and tab bars: light blur + tight lens + vibrancy.
 */
val GlassButtonEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(BackdropBlur.Button.toPx())
    lens(LensRefraction.Button.height.toPx(), LensRefraction.Button.amount.toPx())
}

/**
 * Button style variants.
 */
enum class GlassButtonVariant {
    Primary,
    Secondary,
    Standard,
    Text
}

/**
 * Default values for GlassButton.
 */
object GlassButtonDefaults {
    val ContentPadding = PaddingValues(horizontal = Spacing.ExtraLarge, vertical = Spacing.SmallMedium)
    val MinWidth = ComponentSize.ButtonMinWidth
    val MinHeight = ComponentSize.ButtonMinHeight
    val TextStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp
    )
}

/**
 * Glass button component with interactive lighting and GPU-accelerated backdrop effects.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ContinuousCapsule,
    variant: GlassButtonVariant = GlassButtonVariant.Standard,
    effects: BackdropEffectScope.() -> Unit = GlassButtonEffects,
    backdrop: Backdrop? = null,
    minWidth: Dp = GlassButtonDefaults.MinWidth,
    minHeight: Dp = GlassButtonDefaults.MinHeight,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    interactive: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    var isHovered by remember { mutableStateOf(false) }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = if (interactive) {
        remember(animationScope) {
            InteractiveHighlight(animationScope)
        }
    } else {
        null
    }

    val (baseSurfaceColor, tint, contentColor) = resolveButtonColors(variant, glassColors)
    val finalContentColor = if (enabled) contentColor else glassColors.onGlassMuted
    val baseBorderColor = resolveButtonBorderColor(variant, glassColors)

    val surfaceColor by animateColorAsState(
        targetValue = resolveHoverSurfaceColor(isHovered, variant, baseSurfaceColor, glassColors),
        animationSpec = tween(MotionDuration.Fast)
    )
    val borderColor by animateColorAsState(
        targetValue = resolveHoverBorderColor(isHovered, baseBorderColor, glassColors),
        animationSpec = tween(MotionDuration.Fast)
    )

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .then(
                if (effectsEnabled && variant != GlassButtonVariant.Text && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { shape },
                        effects = effects,
                        highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                        layerBlock = if (interactiveHighlight != null) {
                            {
                                applyButtonInteractiveTransform(interactiveHighlight)
                            }
                        } else null,
                        onDrawSurface = {
                            drawButtonSurface(
                                surfaceColor = surfaceColor,
                                tint = tint,
                                enabled = enabled,
                                glassShadowColor = glassColors.glassShadow
                            )
                        }
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = StrokeWidth.Hairline, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (interactiveHighlight != null) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (interactiveHighlight != null) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides finalContentColor) {
            ProvideTextStyle(GlassButtonDefaults.TextStyle) {
                content()
            }
        }
    }
}

private fun resolveButtonColors(
    variant: GlassButtonVariant,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): Triple<Color, Color, Color> = when (variant) {
    GlassButtonVariant.Primary -> Triple(
        glassColors.glassSurfaceElevated,
        glassColors.glassAccent.copy(alpha = AlphaTokens.Hover),
        glassColors.onGlass
    )
    GlassButtonVariant.Secondary -> Triple(
        glassColors.glassSurface,
        glassColors.glassTintSecondary,
        glassColors.onGlass
    )
    GlassButtonVariant.Standard -> Triple(
        glassColors.glassSurface,
        glassColors.glassTint,
        glassColors.onGlass
    )
    GlassButtonVariant.Text -> Triple(
        Color.Transparent,
        Color.Transparent,
        glassColors.onGlass
    )
}

private fun resolveButtonBorderColor(
    variant: GlassButtonVariant,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): Color = when (variant) {
    GlassButtonVariant.Primary -> glassColors.glassBorderHighlight
    GlassButtonVariant.Secondary -> glassColors.glassBorder
    GlassButtonVariant.Standard -> glassColors.glassBorder
    GlassButtonVariant.Text -> Color.Transparent
}

private fun resolveHoverSurfaceColor(
    isHovered: Boolean,
    variant: GlassButtonVariant,
    baseSurfaceColor: Color,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): Color = if (isHovered && variant != GlassButtonVariant.Text) {
    glassColors.glassSurfaceElevated
} else {
    baseSurfaceColor
}

private fun resolveHoverBorderColor(
    isHovered: Boolean,
    baseBorderColor: Color,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme
): Color = if (isHovered && baseBorderColor != Color.Transparent) {
    glassColors.glassBorderHighlight
} else {
    baseBorderColor
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyButtonInteractiveTransform(
    interactiveHighlight: InteractiveHighlight
) {
    val width = size.width
    val height = size.height

    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + Spacing.ExtraSmall.toPx() / size.height, progress)

    val maxOffset = size.minDimension
    val initialDerivative = 0.05f
    val offset = interactiveHighlight.offset
    val tx = maxOffset * tanh((initialDerivative * offset.x / maxOffset).toDouble())
    val ty = maxOffset * tanh((initialDerivative * offset.y / maxOffset).toDouble())
    translationX = tx.toFloat()
    translationY = ty.toFloat()

    val maxDragScale = Spacing.ExtraSmall.toPx() / size.height
    val offsetAngle = atan2(offset.y.toDouble(), offset.x.toDouble()).toFloat()
    scaleX = scale +
        maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
        (width / height).fastCoerceAtMost(1f)
    scaleY = scale +
        maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
        (height / width).fastCoerceAtMost(1f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawButtonSurface(
    surfaceColor: Color,
    tint: Color,
    enabled: Boolean,
    glassShadowColor: Color
) {
    if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
        drawRect(surfaceColor)
    }
    if (tint.isSpecified && tint != Color.Transparent) {
        drawRect(tint, blendMode = BlendMode.Hue)
        drawRect(tint.copy(alpha = tint.alpha * AlphaTokens.Half))
    }
    if (!enabled) {
        drawRect(glassShadowColor.copy(alpha = AlphaTokens.Disabled))
    }
}

/**
 * Convenience composable for a primary glass button.
 */
@Composable
fun GlassPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = GlassButtonVariant.Primary,
        content = content
    )
}

/**
 * Convenience composable for a text-only glass button.
 */
@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = GlassButtonVariant.Text,
        contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = Spacing.Small),
        content = content
    )
}

/**
 * Glass icon button component optimized for toolbars.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    shape: Shape = ContinuousCapsule,
    effects: BackdropEffectScope.() -> Unit = GlassButtonEffects,
    backdrop: Backdrop? = null,
    checked: Boolean? = null,
    selected: Boolean = checked ?: false,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = shape,
        effects = effects,
        backdrop = backdrop,
        minWidth = size,
        minHeight = size,
        contentPadding = PaddingValues(Spacing.None),
        enabled = enabled,
        variant = if (selected) GlassButtonVariant.Primary else GlassButtonVariant.Standard
    ) {
        content()
    }
}

/**
 * Glass icon toggle button.
 */
@Composable
fun GlassIconToggleButton(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    shape: Shape = ContinuousCapsule,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    GlassIconButton(
        onClick = { onSelectedChange(!selected) },
        modifier = modifier,
        size = size,
        shape = shape,
        selected = selected,
        enabled = enabled,
        content = content
    )
}
