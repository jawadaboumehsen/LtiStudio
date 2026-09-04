/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.actions

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
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
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import org.ide.lti.core.designsystem.utils.applyInteractiveHighlightTransform

/**
 * Button glass effect stack for compact icon buttons.
 * Delegated to canonical [GlassEffects.Button].
 */
val GlassButtonEffects: BackdropEffectScope.() -> Unit = GlassEffects.Button

/**
 * Pill-button glass effect stack for text-labeled action buttons.
 * Delegated to canonical [GlassEffects.ButtonPill].
 */
val GlassButtonPillEffects: BackdropEffectScope.() -> Unit = GlassEffects.ButtonPill

/**
 * Type-safe button style hierarchy representing visual variants.
 */
sealed interface GlassButtonStyle {
    data object Primary : GlassButtonStyle
    data object Secondary : GlassButtonStyle
    data object Standard : GlassButtonStyle
    data object Text : GlassButtonStyle
    data class Custom(
        val surfaceColor: Color,
        val contentColor: Color,
        val borderColor: Color = Color.Transparent,
    ) : GlassButtonStyle
}

/**
 * Button style variants.
 */
enum class GlassButtonVariant {
    Primary,
    Secondary,
    Standard,
    Text;

    fun toStyle(): GlassButtonStyle = when (this) {
        Primary -> GlassButtonStyle.Primary
        Secondary -> GlassButtonStyle.Secondary
        Standard -> GlassButtonStyle.Standard
        Text -> GlassButtonStyle.Text
    }
}

/**
 * Default values for GlassButton.
 */
object GlassButtonDefaults {
    val ContentPadding = PaddingValues(horizontal = Spacing.ExtraLarge, vertical = Spacing.SmallMedium)
    val MinWidth = ComponentSize.ButtonMinWidth
    val MinHeight = ComponentSize.ButtonMinHeight
    val TextStyle = TextStyle(
        fontSize = FontSize.TitleSmall,
        fontWeight = FontWeight.Medium,
        letterSpacing = LetterSpacing.Snug,
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
    shape: Shape = GlassShapes.Capsule,
    variant: GlassButtonVariant = GlassButtonVariant.Standard,
    effects: BackdropEffectScope.() -> Unit = GlassButtonPillEffects,
    backdrop: Backdrop? = null,
    minWidth: Dp = GlassButtonDefaults.MinWidth,
    minHeight: Dp = GlassButtonDefaults.MinHeight,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    interactive: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    var isHovered by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

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

    val showFocusRing = isFocused && !GlassTheme.focusRingSuppressedByPointer
    val targetSurfaceColor = resolveHoverSurfaceColor(isHovered, variant, baseSurfaceColor, glassColors)
    val targetBorderColor = resolveTargetBorderColor(showFocusRing, isHovered, baseBorderColor, glassColors)
    val targetBorderWidth = resolveTargetBorderWidth(showFocusRing)

    val surfaceColor by animateColorAsState(
        targetValue = targetSurfaceColor,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )
    val borderWidth by animateDpAsState(
        targetValue = targetBorderWidth,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
    )

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .then(
                if (variant != GlassButtonVariant.Text) {
                    if (effectsEnabled && actualBackdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = actualBackdrop,
                            shape = { shape },
                            effects = effects,
                            highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                            layerBlock = if (interactiveHighlight != null) {
                                {
                                    applyInteractiveHighlightTransform(interactiveHighlight)
                                }
                            } else {
                                null
                            },
                            onDrawSurface = {
                                drawButtonSurface(
                                    surfaceColor = surfaceColor,
                                    tint = tint,
                                    enabled = enabled,
                                    glassShadowColor = glassColors.glassShadow,
                                    vividTint = variant == GlassButtonVariant.Primary,
                                )
                            },
                        )
                    } else {
                        Modifier
                            .clip(shape)
                            .drawBehind {
                                drawButtonSurface(
                                    surfaceColor = surfaceColor,
                                    tint = tint,
                                    enabled = enabled,
                                    glassShadowColor = glassColors.glassShadow,
                                    vividTint = variant == GlassButtonVariant.Primary,
                                )
                            }
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.glassOutlineBorder(
                        color = borderColor,
                        width = borderWidth,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = if (interactiveHighlight != null) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (interactiveHighlight != null) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides finalContentColor) {
            ProvideTextStyle(GlassButtonDefaults.TextStyle) {
                content()
            }
        }
    }
}

private fun resolveTargetBorderColor(
    isFocused: Boolean,
    isHovered: Boolean,
    baseBorderColor: Color,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Color = if (isFocused) {
    glassColors.focusRing
} else {
    resolveHoverBorderColor(isHovered, baseBorderColor, glassColors)
}

private fun resolveTargetBorderWidth(isFocused: Boolean): Dp =
    if (isFocused) StrokeWidth.Focused else StrokeWidth.Hairline

private fun resolveButtonColors(
    variant: GlassButtonVariant,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Triple<Color, Color, Color> = when (variant) {
    GlassButtonVariant.Primary -> Triple(
        glassColors.glassSurfaceElevated,
        glassColors.glassAccent.copy(alpha = AlphaTokens.PrimaryButtonTint),
        glassColors.onGlass,
    )
    GlassButtonVariant.Secondary -> Triple(
        glassColors.glassSurface,
        glassColors.glassTintSecondary,
        glassColors.onGlass,
    )
    GlassButtonVariant.Standard -> Triple(
        glassColors.glassSurface,
        glassColors.glassTint,
        glassColors.onGlass,
    )
    GlassButtonVariant.Text -> Triple(
        Color.Transparent,
        Color.Transparent,
        glassColors.onGlass,
    )
}

private fun resolveButtonBorderColor(
    variant: GlassButtonVariant,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
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
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Color = if (isHovered && variant != GlassButtonVariant.Text) {
    glassColors.glassSurfaceElevated
} else {
    baseSurfaceColor
}

private fun resolveHoverBorderColor(
    isHovered: Boolean,
    baseBorderColor: Color,
    glassColors: org.ide.lti.core.designsystem.theme.GlassColorScheme,
): Color = if (isHovered && baseBorderColor != Color.Transparent) {
    glassColors.glassBorderHighlight
} else {
    baseBorderColor
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawButtonSurface(
    surfaceColor: Color,
    tint: Color,
    enabled: Boolean,
    glassShadowColor: Color,
    vividTint: Boolean = false,
) {
    if (surfaceColor.isSpecified && surfaceColor != Color.Transparent) {
        drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * AlphaTokens.ButtonFill))
    }
    if (tint.isSpecified && tint != Color.Transparent) {
        if (vividTint) {
            // Full-strength SrcOver instead of a Hue-only blend: the accent color reads as
            // itself (matching the brand logo badge) rather than being diluted by the dark
            // base surface underneath, which a Hue blend deliberately preserves the lightness of.
            drawRect(tint, blendMode = BlendMode.SrcOver)
        } else {
            drawRect(tint, blendMode = BlendMode.Hue)
            drawRect(tint.copy(alpha = tint.alpha * AlphaTokens.Half))
        }
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
    shape: Shape = GlassShapes.Capsule,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        variant = GlassButtonVariant.Primary,
        content = content,
    )
}

/**
 * Convenience composable for a secondary glass button.
 */
@Composable
fun GlassSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Capsule,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        variant = GlassButtonVariant.Secondary,
        content = content,
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
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        variant = GlassButtonVariant.Text,
        contentPadding = PaddingValues(horizontal = Spacing.Medium, vertical = Spacing.Small),
        content = content,
    )
}

/**
 * Glass icon button component optimized for toolbars.
 *
 * @param interactive Whether the button applies the squash-and-stretch press/drag transform
 * (see [InteractiveHighlight]). Defaults to `false` ΓÇö icon buttons are small, frequently-clicked
 * controls (toolbars, rails, panel headers) where that motion reads as jittery rather than fluid.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    shape: Shape = GlassShapes.Capsule,
    effects: BackdropEffectScope.() -> Unit = GlassButtonEffects,
    backdrop: Backdrop? = null,
    checked: Boolean? = null,
    selected: Boolean = checked ?: false,
    enabled: Boolean = true,
    interactive: Boolean = false,
    content: @Composable () -> Unit,
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
        interactive = interactive,
        variant = if (selected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
    ) {
        content()
    }
}

/**
 * Glass icon button component with [AppIcon].
 */
@Composable
fun GlassIconButton(
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    contentDescription: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        size = size,
        selected = selected,
        enabled = enabled,
    ) {
        GlassIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (selected) GlassTheme.colors.onGlass else GlassTheme.colors.onGlassSecondary,
            modifier = Modifier.size(IconSize.Medium),
        )
    }
}

/**
 * Glass icon toggle button with custom content slot.
 */
@Composable
fun GlassIconToggleButton(
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    shape: Shape = GlassShapes.Capsule,
    enabled: Boolean = true,
    interactive: Boolean = false,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = { onSelectedChange(!selected) },
        modifier = modifier,
        size = size,
        shape = shape,
        selected = selected,
        enabled = enabled,
        interactive = interactive,
        content = content,
    )
}

/**
 * Glass icon toggle button with [AppIcon].
 */
@Composable
fun GlassIconToggleButton(
    icon: AppIcon,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    GlassIconToggleButton(
        selected = checked,
        onSelectedChange = onCheckedChange,
        modifier = modifier,
        size = size,
        enabled = enabled,
    ) {
        GlassIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (checked) GlassTheme.colors.onGlass else GlassTheme.colors.onGlassSecondary,
            modifier = Modifier.size(IconSize.Medium),
        )
    }
}
