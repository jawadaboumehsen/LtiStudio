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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

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
 * @param icon The [ImageVector] displayed in the tool window rail.
 * @param label The human-readable tab label/tooltip.
 * @param contentDescription Optional accessible description (defaults to [label]).
 * @param content Composable body rendered when this tab is active.
 */
@Immutable
data class GlassSidePanelTab(
    val icon: ImageVector,
    val label: String,
    val contentDescription: String? = label,
    val content: @Composable () -> Unit,
)

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

    val glassColors = GlassTheme.colors
    val railWidth = ComponentSize.SidePanelRailButtonSize + Spacing.ExtraSmall * 2

    val iconColumn = @Composable {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .padding(
                    start = Spacing.ExtraSmall,
                    end = Spacing.ExtraSmall,
                    top = Spacing.Small,
                    bottom = Spacing.Small,
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                val desc = tab.contentDescription ?: tab.label
                GlassIconButton(
                    onClick = { onSelectedIndexChange(index) },
                    size = ComponentSize.SidePanelRailButtonSize,
                    shape = RoundedCornerShape(CornerRadius.Compact),
                    selected = isSelected,
                    modifier = Modifier.semantics {
                        contentDescription = desc
                        role = Role.Tab
                    },
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.SidePanelRail),
                    )
                }
            }
        }
    }

    val divider = @Composable {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(StrokeWidth.Hairline)
                .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
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

        val glassColors = GlassTheme.colors

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
                    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
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
