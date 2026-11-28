/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.hazeGlass
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassControlColors
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.GlassMaterialStyles
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.InteractionColors
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.ide.lti.core.designsystem.theme.sceneHazeInput
import kotlin.math.abs

private const val WHEEL_STEP_PX: Float = 64f
private const val DIVIDER_CENTER_DIVISOR: Float = 2f
private const val SAME_ROW_TOLERANCE_PX: Int = 2

/**
 * Immutable item descriptor rendered by [MacOsSegmentedControl].
 */
@Immutable
internal data class MacOsSegmentItem(
    val id: String,
    val label: String,
    val icon: AppIcon? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
    val testTag: String? = null,
    val onClose: (() -> Unit)? = null,
)

private class SegmentRowGeometry {
    var bounds: List<IntRect> = emptyList()
}

@Immutable
private data class SegmentedPillThemeColors(
    val fill: Color,
    val content: Color,
    val iconTint: Color,
    val topRim: Color,
    val bottomRim: Color,
    val focusBorder: Color,
)

/**
 * Shared macOS Sequoia/Tahoe Liquid Glass segmented submenu control.
 *
 * Powers both IDE stage/workspace submenu switchers and panel/dialog tab bars so every submenu
 * presents one unified recessed translucent capsule track with an elevated active pill thumb,
 * top specular rim, and subtle vertical dividers between adjacent unselected segments.
 */
@Composable
internal fun MacOsSegmentedControl(
    items: List<MacOsSegmentItem>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    trailingActions: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty()) return

    var hoveredIndex by remember(items) { mutableStateOf<Int?>(null) }
    val onHoverChange: (Int, Boolean) -> Unit = { index, isHovered ->
        hoveredIndex = when {
            isHovered -> index
            hoveredIndex == index -> null
            else -> hoveredIndex
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val trackModifier = Modifier.weight(1f, fill = false)
        if (wrap) {
            MacOsWrappingSegmentedTrack(
                items = items,
                selectedId = selectedId,
                hoveredIndex = hoveredIndex,
                onHoveredIndexChange = onHoverChange,
                onSelect = onSelect,
                modifier = trackModifier,
            )
        } else {
            MacOsSingleRowSegmentedTrack(
                items = items,
                selectedId = selectedId,
                hoveredIndex = hoveredIndex,
                onHoveredIndexChange = onHoverChange,
                onSelect = onSelect,
                modifier = trackModifier,
            )
        }

        if (trailingActions != null) {
            Row(
                modifier = Modifier.padding(start = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                trailingActions()
            }
        }
    }
}

@Composable
private fun MacOsSingleRowSegmentedTrack(
    items: List<MacOsSegmentItem>,
    selectedId: String?,
    hoveredIndex: Int?,
    onHoveredIndexChange: (Int, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val reducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced

    Box(modifier = modifier.macOsTrackSurface(shape = GlassShapes.Pill)) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .wheelScrollsRow(scrollState)
                .padding(Spacing.ExtraExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    val left = items[index - 1]
                    val visible = left.id != selectedId &&
                        item.id != selectedId &&
                        hoveredIndex != index - 1 &&
                        hoveredIndex != index
                    MacOsSegmentDivider(visible = visible, reducedMotion = reducedMotion)
                }

                MacOsSegmentedPill(
                    item = item,
                    selected = item.id == selectedId,
                    shape = GlassShapes.Pill,
                    hazeShape = GlassShapes.HazeCapsule,
                    onHoverChanged = { hovered -> onHoveredIndexChange(index, hovered) },
                    onClick = { onSelect(item.id) },
                )
            }
        }
    }
}

@Composable
private fun MacOsWrappingSegmentedTrack(
    items: List<MacOsSegmentItem>,
    selectedId: String?,
    hoveredIndex: Int?,
    onHoveredIndexChange: (Int, Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isLight = GlassTheme.appTheme == AppTheme.Light
    val geometry = remember { SegmentRowGeometry() }
    val selectedIndex = items.indexOfFirst { it.id == selectedId }
    val dividerColor = colors.outline.copy(
        alpha = if (isLight) AlphaTokens.Prominent else AlphaTokens.Border,
    )

    Box(modifier = modifier.macOsTrackSurface(shape = GlassShapes.Medium)) {
        Layout(
            modifier = Modifier
                .padding(Spacing.ExtraExtraSmall)
                .drawSameRowDividers(
                    geometry = geometry,
                    selectedIndex = selectedIndex,
                    hoveredIndex = hoveredIndex,
                    dividerColor = dividerColor,
                ),
            content = {
                items.forEachIndexed { index, item ->
                    MacOsSegmentedPill(
                        item = item,
                        selected = item.id == selectedId,
                        shape = GlassShapes.MediumSmall,
                        hazeShape = GlassShapes.HazeMediumSmall,
                        onHoverChanged = { hovered -> onHoveredIndexChange(index, hovered) },
                        onClick = { onSelect(item.id) },
                    )
                }
            },
        ) { measurables, constraints ->
            val hGapPx = Spacing.Compact.roundToPx()
            val vGapPx = Spacing.ExtraSmall.roundToPx()
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(childConstraints) }
            val positions = mutableListOf<IntRect>()
            var x = 0
            var y = 0
            var rowHeight = 0
            var maxRowRight = 0
            val maxW = constraints.maxWidth

            for (placeable in placeables) {
                if (x > 0 && maxW != Int.MAX_VALUE && x + placeable.width > maxW) {
                    x = 0
                    y += rowHeight + vGapPx
                    rowHeight = 0
                }
                positions += IntRect(x, y, x + placeable.width, y + placeable.height)
                x += placeable.width
                if (x > maxRowRight) maxRowRight = x
                x += hGapPx
                if (placeable.height > rowHeight) rowHeight = placeable.height
            }

            geometry.bounds = positions
            val layoutWidth = if (constraints.hasBoundedWidth) {
                maxRowRight.coerceIn(constraints.minWidth, constraints.maxWidth)
            } else {
                maxRowRight.coerceAtLeast(constraints.minWidth)
            }
            val layoutHeight = (y + rowHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(layoutWidth, layoutHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val rect = positions[index]
                    placeable.placeRelative(rect.left, rect.top)
                }
            }
        }
    }
}

private fun Modifier.drawSameRowDividers(
    geometry: SegmentRowGeometry,
    selectedIndex: Int,
    hoveredIndex: Int?,
    dividerColor: Color,
): Modifier = drawWithCache {
    val strokePx = StrokeWidth.Standard.toPx()
    val dividerHeightPx = IconSize.Segmented.toPx()
    onDrawWithContent {
        drawContent()
        val rects = geometry.bounds
        for (index in 1 until rects.size) {
            val left = rects[index - 1]
            val right = rects[index]
            val sameRow = abs(left.top - right.top) <= SAME_ROW_TOLERANCE_PX
            val hidden = (index - 1 == selectedIndex) ||
                (index == selectedIndex) ||
                (index - 1 == hoveredIndex) ||
                (index == hoveredIndex)
            if (sameRow && !hidden) {
                val centerX = (left.right + right.left) / DIVIDER_CENTER_DIVISOR
                val rowHeight = maxOf(left.height, right.height).toFloat()
                val h = dividerHeightPx.coerceAtMost(rowHeight)
                val topY = left.top + (rowHeight - h) / DIVIDER_CENTER_DIVISOR
                drawLine(
                    color = dividerColor,
                    start = Offset(centerX, topY),
                    end = Offset(centerX, topY + h),
                    strokeWidth = strokePx,
                )
            }
        }
    }
}

@Composable
private fun MacOsSegmentDivider(visible: Boolean, reducedMotion: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) AlphaTokens.Prominent else 0f,
        animationSpec = resolveAnimationSpec(reducedMotion, MotionDuration.Quick),
        label = "macOsSegmentDividerAlpha",
    )
    Box(
        modifier = Modifier
            .width(StrokeWidth.Standard)
            .height(IconSize.Segmented)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = alpha)),
    )
}

@Composable
private fun Modifier.macOsTrackSurface(shape: Shape): Modifier {
    val colors = MaterialTheme.colorScheme
    val appTheme = GlassTheme.appTheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val isLight = appTheme == AppTheme.Light
    val trackFill = when {
        isLight && effectsEnabled -> GlassControlColors.TrackInactiveLight
        isLight -> colors.secondaryContainer
        effectsEnabled -> colors.surfaceContainerLowest.copy(alpha = AlphaTokens.Prominent)
        else -> colors.surfaceContainerLowest
    }
    val outerBorder = when {
        isLight -> colors.outline.copy(alpha = if (effectsEnabled) AlphaTokens.Prominent else AlphaTokens.Border)
        else -> colors.outline.copy(alpha = if (effectsEnabled) AlphaTokens.Hover else AlphaTokens.Half)
    }
    val bevelColor = GlassControlColors.InnerShadow.copy(
        alpha = if (isLight) AlphaTokens.Glow else AlphaTokens.Muted,
    )

    return this
        .glassContainer()
        .clip(shape)
        .background(trackFill, shape)
        .drawWithCache {
            val bevelHeight = Spacing.ExtraSmall.toPx()
            val bevelBrush = Brush.verticalGradient(
                colors = listOf(bevelColor, Color.Transparent),
                startY = 0f,
                endY = bevelHeight,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = bevelBrush, size = Size(size.width, bevelHeight))
            }
        }
        .glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = outerBorder,
            shape = shape,
        )
}

@Composable
internal fun MacOsSegmentedPill(
    item: MacOsSegmentItem,
    selected: Boolean,
    shape: Shape,
    hazeShape: HazeShape,
    onHoverChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(hovered, item.enabled) {
        onHoverChanged(hovered && item.enabled)
    }
    DisposableEffect(Unit) {
        onDispose { onHoverChanged(false) }
    }

    val showKeyboardFocus = focused && inputModeManager.inputMode == InputMode.Keyboard
    val pillColors = rememberPillColors(
        selected = selected,
        hovered = hovered,
        pressed = pressed,
        focused = showKeyboardFocus,
        enabled = item.enabled,
    )

    val shadowPx = if (selected) with(density) { Spacing.ExtraExtraSmall.toPx() } else 0f

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = ComponentSize.TabItemHeight)
            .then(item.testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .graphicsLayer {
                shadowElevation = shadowPx
                this.shape = shape
                clip = false
                spotShadowColor = GlassLayoutColors.PanelShadow
                ambientShadowColor = GlassLayoutColors.PanelShadow
            }
            .clip(shape)
            .pillSurfaceDecorations(
                selected = selected,
                focused = showKeyboardFocus,
                shape = shape,
                hazeShape = hazeShape,
                pillColors = pillColors,
            )
            .selectable(
                selected = selected,
                enabled = item.enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        SegmentedPillContent(
            item = item,
            selected = selected,
            contentColor = pillColors.content,
            iconTint = pillColors.iconTint,
            badgeMutedColor = colors.onSurfaceVariant,
            badgeBackgroundColor = colors.onSurface.copy(
                alpha = if (selected) AlphaTokens.GlassSecondaryTint else AlphaTokens.Faint,
            ),
        )
    }
}

@Composable
private fun Modifier.pillSurfaceDecorations(
    selected: Boolean,
    focused: Boolean,
    shape: Shape,
    hazeShape: HazeShape,
    pillColors: SegmentedPillThemeColors,
): Modifier {
    val colors = MaterialTheme.colorScheme
    val appTheme = GlassTheme.appTheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val hazeState = GlassTheme.hazeStateOrNull
    val isLight = appTheme == AppTheme.Light
    val useBlueGlass = selected && appTheme == AppTheme.Blue && effectsEnabled && hazeState != null

    val fillModifier = if (useBlueGlass) {
        Modifier.hazeGlass(
            input = sceneHazeInput(hazeState),
            style = remember(appTheme, hazeShape, colors) {
                GlassMaterialStyles.clearStyle(
                    theme = appTheme,
                    opticalTint = colors.surfaceContainerHighest.copy(alpha = AlphaTokens.Heavy),
                    captureBacking = colors.surfaceContainerHighest,
                ).then {
                    this.shape(hazeShape)
                }
            },
        )
    } else {
        Modifier.background(pillColors.fill)
    }

    val specularModifier = if (selected) {
        Modifier.drawThumbSpecular(
            shape = shape,
            topRim = pillColors.topRim,
            bottomRim = pillColors.bottomRim,
            isLight = isLight,
            effectsEnabled = effectsEnabled,
        )
    } else {
        Modifier
    }

    val focusModifier = if (focused) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Focused,
            color = pillColors.focusBorder,
            shape = shape,
        )
    } else {
        Modifier
    }

    return this.then(fillModifier).then(specularModifier).then(focusModifier)
}

@Composable
private fun rememberPillColors(
    selected: Boolean,
    hovered: Boolean,
    pressed: Boolean,
    focused: Boolean,
    enabled: Boolean,
): SegmentedPillThemeColors {
    val colors = MaterialTheme.colorScheme
    val appTheme = GlassTheme.appTheme
    val effectsEnabled = GlassTheme.effectsEnabled
    val reducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val isLight = appTheme == AppTheme.Light
    val isBlue = appTheme == AppTheme.Blue

    val animatedFill by animateColorAsState(
        targetValue = resolveThumbFill(selected, pressed, hovered, enabled, isLight, effectsEnabled, colors),
        animationSpec = resolveAnimationSpec(reducedMotion, MotionDuration.Fast),
        label = "macOsSegmentedThumbFill",
    )
    val animatedContent by animateColorAsState(
        targetValue = resolveContentColor(selected, hovered, enabled, colors),
        animationSpec = resolveAnimationSpec(reducedMotion, MotionDuration.Fast),
        label = "macOsSegmentedContentColor",
    )
    val iconTint = if (selected) resolveSelectedIconTint(isLight, isBlue, colors) else animatedContent
    val (topRim, bottomRim) = resolveSpecularRim(selected, isLight, isBlue, effectsEnabled, colors)
    val focusBorder = if (focused) colors.primary else Color.Transparent

    return SegmentedPillThemeColors(
        fill = animatedFill,
        content = animatedContent,
        iconTint = iconTint,
        topRim = topRim,
        bottomRim = bottomRim,
        focusBorder = focusBorder,
    )
}

@Composable
private fun SegmentedPillContent(
    item: MacOsSegmentItem,
    selected: Boolean,
    contentColor: Color,
    iconTint: Color,
    badgeMutedColor: Color,
    badgeBackgroundColor: Color,
) {
    when (val icon = item.icon) {
        is AppIcon.Vector -> Icon(
            imageVector = icon.imageVector,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(IconSize.Small),
        )
        is AppIcon.Painted -> Icon(
            painter = icon.painter(),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(IconSize.Small),
        )
        null -> Unit
    }
    Text(
        text = item.label,
        color = contentColor,
        fontSize = FontSize.BodySmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        fontFamily = GlassFontFamily.ide(),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
    if (item.badge != null) {
        Text(
            text = item.badge,
            color = if (selected) badgeMutedColor else contentColor,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.code(),
            modifier = Modifier
                .clip(GlassShapes.Pill)
                .background(badgeBackgroundColor)
                .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
        )
    }
    if (item.onClose != null) {
        Spacer(Modifier.width(Spacing.ExtraExtraSmall))
        Box(
            modifier = Modifier
                .size(IconSize.Medium)
                .clip(GlassShapes.Pill)
                .selectable(
                    selected = false,
                    enabled = item.enabled,
                    role = Role.Button,
                    onClick = item.onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = AppIcons.ClosePainterResource(),
                contentDescription = "Close ${item.label}",
                tint = contentColor,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
        }
    }
}

private fun resolveSelectedIconTint(isLight: Boolean, isBlue: Boolean, colors: ColorScheme): Color = when {
    isLight -> colors.primary
    isBlue -> colors.secondary
    else -> colors.onPrimaryContainer
}

private fun resolveSpecularRim(
    selected: Boolean,
    isLight: Boolean,
    isBlue: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Pair<Color, Color> = when {
    !selected -> Color.Transparent to Color.Transparent
    isLight -> {
        val top = colors.outline.copy(alpha = if (effectsEnabled) AlphaTokens.Hover else AlphaTokens.Half)
        val bottom = colors.outline.copy(alpha = AlphaTokens.Prominent)
        top to bottom
    }
    isBlue -> {
        BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Half) to
            colors.outlineVariant.copy(alpha = AlphaTokens.Half)
    }
    else -> {
        InteractionColors.PressHighlight.copy(alpha = AlphaTokens.Faded) to
            colors.outline.copy(alpha = AlphaTokens.Muted)
    }
}

private fun resolveThumbFill(
    selected: Boolean,
    pressed: Boolean,
    hovered: Boolean,
    enabled: Boolean,
    isLight: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    !enabled -> Color.Transparent
    selected -> when {
        isLight -> GlassControlColors.ThumbResting
        effectsEnabled -> colors.surfaceContainerHighest.copy(alpha = AlphaTokens.Heavy)
        else -> colors.surfaceContainerHighest
    }
    pressed -> colors.onSurface.copy(alpha = AlphaTokens.Subtle)
    hovered -> colors.onSurface.copy(alpha = AlphaTokens.Faint)
    else -> Color.Transparent
}

private fun resolveContentColor(selected: Boolean, hovered: Boolean, enabled: Boolean, colors: ColorScheme): Color =
    when {
        !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
        selected || hovered -> colors.onSurface
        else -> colors.onSurfaceVariant
    }

private fun Modifier.drawThumbSpecular(
    shape: Shape,
    topRim: Color,
    bottomRim: Color,
    isLight: Boolean,
    effectsEnabled: Boolean,
): Modifier = drawWithCache {
    val strokePx = StrokeWidth.Hairline.toPx().coerceAtLeast(1f)
    val innerW = size.width - strokePx
    val innerH = size.height - strokePx
    val sheenColor = if (isLight || !effectsEnabled) {
        Color.Transparent
    } else {
        InteractionColors.PressHighlight.copy(alpha = AlphaTokens.GlassSecondaryTint)
    }
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(sheenColor, Color.Transparent),
        startY = 0f,
        endY = size.height / DIVIDER_CENTER_DIVISOR,
    )
    val rimBrush = Brush.verticalGradient(
        colors = listOf(topRim, bottomRim),
        startY = 0f,
        endY = size.height,
    )
    val valid = innerW > 0f && innerH > 0f
    val outline = if (valid) shape.createOutline(Size(innerW, innerH), layoutDirection, this) else null
    val stroke = Stroke(width = strokePx)
    val halfStroke = strokePx / DIVIDER_CENTER_DIVISOR
    onDrawWithContent {
        drawContent()
        if (sheenColor.alpha > 0f) {
            drawRect(brush = sheenBrush)
        }
        if (outline != null) {
            translate(left = halfStroke, top = halfStroke) {
                drawOutline(outline = outline, brush = rimBrush, style = stroke)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.wheelScrollsRow(scrollState: androidx.compose.foundation.ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val delta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
        val deltaPx = (if (delta.y != 0f) delta.y else delta.x) * WHEEL_STEP_PX
        if (deltaPx != 0f) {
            scope.launch { scrollState.scrollBy(deltaPx) }
        }
    }
}
