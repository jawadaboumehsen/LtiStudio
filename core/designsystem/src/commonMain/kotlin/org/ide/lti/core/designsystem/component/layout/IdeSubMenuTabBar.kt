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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdeSubMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * Horizontal sub-menu pill tab bar displaying sub-objects or section navigation with icons and text.
 */
@Composable
fun IdeSubMenuTabBar(
    items: List<IdeSubMenuItem>,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "NavigatorItem_",
    wrap: Boolean = false,
) {
    val tabs: @Composable () -> Unit = {
        items.forEach { item ->
            SubMenuPillTab(
                item = item,
                selected = item.id == selectedItemId,
                tag = "$tagPrefix${item.id}",
                onClick = { onSelectItem(item.id) },
            )
        }
    }
    if (wrap) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
            content = { tabs() },
        )
        return
    }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (scrollState.canScrollBackward) {
            GlassIconButton(
                icon = AppIcons.ChevronLeft,
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollBy(-SCROLL_STEP)
                    }
                },
                size = ComponentSize.TopBarButtonSize,
                contentDescription = "Scroll tabs left",
                modifier = Modifier.padding(end = Spacing.ExtraSmall),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .wheelScrollsRow(scrollState)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            tabs()
        }

        if (scrollState.canScrollForward) {
            GlassIconButton(
                icon = AppIcons.ChevronRight,
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollBy(SCROLL_STEP)
                    }
                },
                size = ComponentSize.TopBarButtonSize,
                contentDescription = "Scroll tabs right",
                modifier = Modifier.padding(start = Spacing.ExtraSmall),
            )
        }
    }
}

private const val SCROLL_STEP = 180f

/** A plain mouse wheel moves the row: `horizontalScroll` alone answers only to Shift + wheel. */
private fun Modifier.wheelScrollsRow(state: ScrollState): Modifier = pointerInput(state) {
    val step = ComponentSize.ButtonMinWidth.toPx()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (event.type == PointerEventType.Scroll && change != null && change.scrollDelta.y != 0f) {
                state.dispatchRawDelta(change.scrollDelta.y * step)
                change.consume()
            }
        }
    }
}

@Composable
private fun SubMenuPillTab(item: IdeSubMenuItem, selected: Boolean, tag: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val colors = MaterialTheme.colorScheme
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val effectsEnabled = GlassTheme.effectsEnabled

    val background = subMenuTabBackground(
        selected = selected,
        isHoveredOrFocused = isHovered || isFocused,
        isPressed = isPressed,
        effectsEnabled = effectsEnabled,
        colors = colors,
    )
    val contentColor = subMenuTabContentColor(
        selected = selected,
        isHoveredOrFocused = isHovered || isFocused,
        isBlue = isBlue,
        effectsEnabled = effectsEnabled,
        colors = colors,
    )
    val borderModifier = if (isFocused) {
        Modifier.glassOutlineBorder(
            color = colors.primary,
            width = GlassDimens.HairlineBorder,
            shape = GlassShapes.Small,
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .testTag(tag)
            .background(background, shape = GlassShapes.Small)
            .clip(GlassShapes.Small)
            .then(borderModifier)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled && !selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (item.icon != null) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.Small),
            )
        }
        Text(
            text = item.label,
            color = contentColor,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (item.badge != null) {
            Text(
                text = item.badge,
                color = if (selected && isBlue) BrandColors.OnLogoBadge else colors.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.code(),
            )
        }
    }
}

private fun subMenuTabBackground(
    selected: Boolean,
    isHoveredOrFocused: Boolean,
    isPressed: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    selected -> if (!effectsEnabled) colors.primaryContainer else colors.primary.copy(alpha = AlphaTokens.Faded)
    isPressed -> colors.surfaceContainerHigh
    isHoveredOrFocused -> colors.surfaceContainerHigh.copy(alpha = AlphaTokens.Half)
    else -> Color.Transparent
}

private fun subMenuTabContentColor(
    selected: Boolean,
    isHoveredOrFocused: Boolean,
    isBlue: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    selected -> when {
        !effectsEnabled -> colors.onPrimaryContainer
        isBlue -> BrandColors.OnLogoBadge
        else -> colors.onSurface
    }

    isHoveredOrFocused -> colors.onSurface

    else -> colors.onSurfaceVariant
}
