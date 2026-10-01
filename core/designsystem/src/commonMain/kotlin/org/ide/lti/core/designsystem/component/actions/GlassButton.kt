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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Type-safe button style hierarchy representing visual variants.
 */
sealed interface GlassButtonStyle {
    data object Primary : GlassButtonStyle
    data object Secondary : GlassButtonStyle
    data object Standard : GlassButtonStyle
    data object Text : GlassButtonStyle
    data class Custom(val surfaceColor: Color, val contentColor: Color, val borderColor: Color = Color.Transparent) :
        GlassButtonStyle
}

/**
 * Button style variants.
 */
enum class GlassButtonVariant {
    Primary,
    Secondary,
    Standard,
    Text,
    ;

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
 * Glass button component - built on Haze's real `Modifier.hazeGlass`, same shape as Haze's own
 * sample `GlassButton` (haze/sample/shared/.../sample/components/GlassButton.kt): one
 * `GlassStyle.regular.then { }` per press/hover/focus state, no hand-rolled animation.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: HazeShape = GlassShapes.HazeCapsule,
    variant: GlassButtonVariant = GlassButtonVariant.Standard,
    minWidth: Dp = GlassButtonDefaults.MinWidth,
    minHeight: Dp = GlassButtonDefaults.MinHeight,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .then(
                if (variant != GlassButtonVariant.Text) {
                    val theme = GlassTheme.appTheme
                    val effectsEnabled = GlassTheme.effectsEnabled
                    val surfaceColor = backgroundColor(variant, colors)
                    val buttonTint = tint(variant, colors, enabled)
                    if (effectsEnabled) {
                        Modifier.hazeGlass(
                            input = sceneHazeInput(GlassTheme.hazeState),
                            style = remember(theme, shape, variant, enabled, colors) {
                                org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                                    theme = theme,
                                    opticalTint = buttonTint,
                                    captureBacking = surfaceColor,
                                ) then GlassStyle {
                                    this.shape(shape)
                                    hovered {
                                        lightingIntensity(0.35f)
                                        refractionMultiplier(1.02f)
                                    }
                                    focused {
                                        lightingIntensity(0.5f)
                                        whitePointDelta(0.03f)
                                    }
                                    pressed {
                                        lightingIntensity(0.9f)
                                        refractionMultiplier(1.08f)
                                        scale(0.98f)
                                    }
                                }
                            },
                            interactionSource = interactionSource,
                            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
                            interactionReducedMotionPolicy = GlassTheme.reducedMotionPolicy,
                        )
                    } else {
                        Modifier.background(surfaceColor, shape = shape)
                    }
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) contentColor(colors) else colors.onSurfaceVariant
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(GlassButtonDefaults.TextStyle) {
                content()
            }
        }
    }
}

private fun backgroundColor(variant: GlassButtonVariant, colors: ColorScheme): Color = when (variant) {
    GlassButtonVariant.Primary -> colors.surfaceContainerHigh
    GlassButtonVariant.Secondary, GlassButtonVariant.Standard -> colors.surfaceContainer
    GlassButtonVariant.Text -> Color.Transparent
}

private fun tint(variant: GlassButtonVariant, colors: ColorScheme, enabled: Boolean): Color {
    val alpha = if (enabled) 1f else 0.4f
    return when (variant) {
        GlassButtonVariant.Primary -> colors.primary.copy(alpha = AlphaTokens.Strong * alpha)
        GlassButtonVariant.Secondary -> colors.secondary.copy(alpha = AlphaTokens.Glow * alpha)
        GlassButtonVariant.Standard -> colors.primary.copy(alpha = AlphaTokens.Glow * alpha)
        GlassButtonVariant.Text -> Color.Transparent
    }
}

private fun contentColor(colors: ColorScheme): Color = colors.onSurface

/**
 * Convenience composable for a primary glass button.
 */
@Composable
fun GlassPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazeCapsule,
    enabled: Boolean = true,
    minWidth: Dp = GlassButtonDefaults.MinWidth,
    minHeight: Dp = GlassButtonDefaults.MinHeight,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        variant = GlassButtonVariant.Primary,
        minWidth = minWidth,
        minHeight = minHeight,
        contentPadding = contentPadding,
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
    shape: HazeShape = GlassShapes.HazeCapsule,
    enabled: Boolean = true,
    minWidth: Dp = GlassButtonDefaults.MinWidth,
    minHeight: Dp = GlassButtonDefaults.MinHeight,
    contentPadding: PaddingValues = GlassButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        variant = GlassButtonVariant.Secondary,
        minWidth = minWidth,
        minHeight = minHeight,
        contentPadding = contentPadding,
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
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = ComponentSize.IconButtonSize,
    shape: HazeShape = GlassShapes.HazeCapsule,
    checked: Boolean? = null,
    selected: Boolean = checked ?: false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = shape,
        minWidth = size,
        minHeight = size,
        contentPadding = PaddingValues(Spacing.None),
        enabled = enabled,
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
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
    shape: HazeShape = GlassShapes.HazeCapsule,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    GlassIconButton(
        onClick = { onSelectedChange(!selected) },
        modifier = modifier,
        size = size,
        shape = shape,
        selected = selected,
        enabled = enabled,
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
            tint = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Medium),
        )
    }
}
