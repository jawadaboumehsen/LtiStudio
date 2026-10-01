/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import java.awt.Cursor

/**
 * Position of the tab switcher icon rail relative to the panel content.
 */
enum class SidePanelRailPosition {
    /** Outer edge on the left (for left-docked panels). */
    Left,

    /** Outer edge on the right (for right-docked panels). */
    Right,
}

/**
 * Specification for a single tool window side panel tab.
 *
 * @param icon The [AppIcon] displayed in the tool window rail.
 * @param label The human-readable tab label/tooltip.
 * @param contentDescription Optional accessible description (defaults to [label]).
 * @param content Composable body rendered when this tab is active.
 */
@Immutable
data class GlassSidePanelTab(
    val icon: AppIcon,
    val label: String,
    val contentDescription: String? = label,
    val content: @Composable () -> Unit,
) {
    constructor(
        icon: ImageVector,
        label: String,
        contentDescription: String? = label,
        content: @Composable () -> Unit,
    ) : this(
        icon = AppIcon.Vector(icon),
        label = label,
        contentDescription = contentDescription,
        content = content,
    )
}

/**
 * Vertical icon rail component for IDE side panels (tool windows).
 *
 * Renders ONLY the icon buttons for the tabs in a vertical strip with an inner hairline border,
 * drawing no background of its own (allowing the surrounding chrome glass surface to show through).
 *
 * @param tabs List of [GlassSidePanelTab] items to display.
 * @param selectedIndex The index of the currently active tab.
 * @param onSelectedIndexChange Callback invoked when a tab icon is clicked.
 * @param modifier Modifier applied to the rail layout.
 * @param railPosition Whether the rail is docked on the [SidePanelRailPosition.Left]
 * or [SidePanelRailPosition.Right] edge.
 */
@Composable
fun GlassSidePanelRail(
    tabs: List<GlassSidePanelTab>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    railPosition: SidePanelRailPosition = SidePanelRailPosition.Left,
) {
    if (tabs.isEmpty()) return

    // The rail's inner edge (facing the divider) is followed by the divider hairline plus the
    // content area's own Spacing.Small gap before the panel's visible border, while the outer
    // edge (facing the window edge) has nothing after it - so a symmetric padding here would
    // leave the rail button visually closer to the window edge than to the panel. Widen the
    // outer-edge padding to match that combined inner-side gap (divider + content padding) so
    // the button reads as centered between the window edge and the panel, not just within its
    // own narrow column.
    val innerEdgePadding = Spacing.ExtraSmall
    val outerEdgePadding = Spacing.SmallMedium
    val railWidth = ComponentSize.SidePanelRailButtonSize + innerEdgePadding + outerEdgePadding

    val iconColumn = @Composable {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .padding(
                    start = if (railPosition == SidePanelRailPosition.Left) outerEdgePadding else innerEdgePadding,
                    end = if (railPosition == SidePanelRailPosition.Left) innerEdgePadding else outerEdgePadding,
                    top = Spacing.Small,
                    bottom = Spacing.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val desc = tab.contentDescription ?: tab.label
                GlassSidePanelRailButton(
                    onClick = { onSelectedIndexChange(index) },
                    isSelected = isSelected,
                    desc = desc,
                ) {
                    when (tab.icon) {
                        is AppIcon.Vector -> Icon(
                            imageVector = tab.icon.imageVector,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.SidePanelRail),
                        )
                        is AppIcon.Painted -> Icon(
                            painter = tab.icon.painter(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.SidePanelRail),
                        )
                    }
                }
            }
        }
    }

    val divider = @Composable {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(StrokeWidth.Hairline)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent),
                            MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Hover),
                            MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                        ),
                    ),
                ),
        )
    }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (railPosition) {
            SidePanelRailPosition.Left -> {
                iconColumn()
                divider()
            }
            SidePanelRailPosition.Right -> {
                divider()
                iconColumn()
            }
        }
    }
}

/**
 * Horizontal tab strip component for IDE side panels (tool windows).
 *
 * Renders tab pills in a top-anchored horizontal row on its own raised [GlassSurface] —
 * elevated above the panel content below via a real drop [Shadow] (matching how
 * [GlassDialog] separates from its scrim) rather than a flat divider line, so the strip
 * reads as a distinct glass layer sitting in front of the panel header beneath it.
 *
 * Uses a real [ContinuousRoundedRectangle], not [RectangleShape]: the outer chrome frame
 * ([GlassLayout]'s own [ContinuousRoundedRectangle]) rounds its top corner right where this
 * strip sits, so a flat-cornered strip only *looks* rounded at the top (borrowing the
 * frame's corner behind it) while its own bottom edge - which has no frame corner to
 * borrow - reads as an abrupt clip. Giving the strip its own matching rounded shape on
 * every corner makes the rounding genuine on all sides, not an illusion at the top only.
 *
 * @param tabs List of [GlassSidePanelTab] items to display.
 * @param selectedIndex The index of the currently active tab.
 * @param onSelectedIndexChange Callback invoked when a tab is clicked.
 * @param modifier Modifier applied to the tab strip layout.
 */
@Composable
fun GlassSidePanelTabStrip(
    tabs: List<GlassSidePanelTab>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = GlassShapes.HazePanel,
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ComponentSize.TabBarHeight)
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val desc = tab.contentDescription ?: tab.label
                GlassButton(
                    onClick = { onSelectedIndexChange(index) },
                    variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                    shape = GlassShapes.HazeCompact,
                    minWidth = Spacing.None,
                    minHeight = ComponentSize.TopBarButtonSize,
                    contentPadding = PaddingValues(
                        horizontal = Spacing.Small,
                        vertical = Spacing.ExtraExtraSmall,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = desc
                        role = Role.Tab
                    },
                ) {
                    when (tab.icon) {
                        is AppIcon.Vector -> Icon(
                            imageVector = tab.icon.imageVector,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.SidePanelRail),
                        )
                        is AppIcon.Painted -> Icon(
                            painter = tab.icon.painter(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.SidePanelRail),
                        )
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Multi-tab "tool window" container for IDE side panels (convenience wrapper).
 *
 * Combines [GlassSidePanelRail] and the active tab's content into a single row.
 * For the two-surface IDE architecture where the rail sits in the chrome frame and content
 * sits in the inner content surface, use [GlassSidePanelRail] directly in the rail slot
 * and place the active tab's content inside the content surface.
 *
 * @param tabs List of [GlassSidePanelTab] items to display in the switcher rail.
 * @param modifier Modifier applied to the outer layout container.
 * @param railPosition Whether the icon rail is docked on the [SidePanelRailPosition.Left]
 * or [SidePanelRailPosition.Right] edge.
 * @param selectedIndex Optional hoisted index of the active tab. If null, managed internally via [rememberSaveable].
 * @param onSelectedIndexChange Optional callback invoked when the user selects a tab.
 */
@Composable
fun GlassSidePanelTabs(
    tabs: List<GlassSidePanelTab>,
    modifier: Modifier = Modifier,
    railPosition: SidePanelRailPosition = SidePanelRailPosition.Left,
    selectedIndex: Int? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    Box(modifier = modifier) {
        if (tabs.isEmpty()) return@Box

        var internalIndex by rememberSaveable { mutableStateOf(0) }
        val activeIndex = (selectedIndex ?: internalIndex).coerceIn(0, tabs.lastIndex)

        val onTabClick: (Int) -> Unit = { clickedIndex ->
            if (selectedIndex == null) {
                internalIndex = clickedIndex
            }
            onSelectedIndexChange?.invoke(clickedIndex)
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val rail = @Composable {
                GlassSidePanelRail(
                    tabs = tabs,
                    selectedIndex = activeIndex,
                    onSelectedIndexChange = onTabClick,
                    railPosition = railPosition,
                )
            }

            val contentArea = @Composable {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        tabs[activeIndex].content()
                    }
                }
            }

            when (railPosition) {
                SidePanelRailPosition.Left -> {
                    rail()
                    contentArea()
                }
                SidePanelRailPosition.Right -> {
                    contentArea()
                    rail()
                }
            }
        }
    }
}

/**
 * Sleek, professional Activity Rail Button.
 *
 * Replaces heavy solid-color fills with a luminous glass accent pill on selection,
 * subtle hover illumination, and muted peripheral resting state.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassSidePanelRailButton(
    onClick: () -> Unit,
    isSelected: Boolean,
    desc: String,
    content: @Composable () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }

    val shape = GlassShapes.Compact
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Subtle)
        else -> Color.Transparent
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Prominent)
        isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
        else -> Color.Transparent
    }
    val iconTint = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isHovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(ComponentSize.SidePanelRailButtonSize)
            .clip(shape)
            .background(backgroundColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = borderColor,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .semantics {
                contentDescription = desc
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            content()
        }
    }
}
