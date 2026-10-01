/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.navigation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.layout.ideShellSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Now in Android navigation bar item with icon and label content slots. Wraps Material 3
 * [NavigationBarItem].
 *
 * @param selected Whether this item is selected.
 * @param onClick The callback to be invoked when this item is selected.
 * @param icon The item icon content.
 * @param modifier Modifier to be applied to this item.
 * @param selectedIcon The item icon content when selected.
 * @param enabled controls the enabled state of this item. When `false`, this item will not be
 * clickable and will appear disabled to accessibility services.
 * @param label The item text label content.
 * @param alwaysShowLabel Whether to always show the label for this item. If false, the label will
 * only be shown when this item is selected.
 */
@Composable
fun RowScope.GlassNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    selectedIcon: @Composable () -> Unit = icon,
    label: @Composable (() -> Unit)? = null,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = if (selected) selectedIcon else icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
    )
}

/**
 * Now in Android navigation bar with content slot. Wraps Material 3 [NavigationBar].
 *
 * @param modifier Modifier to be applied to the navigation bar.
 * @param content Destinations inside the navigation bar. This should contain multiple
 * [NavigationBarItem]s.
 */
@Composable
fun GlassNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(
        // Docked flush to the bottom screen edge — flat (0dp) shape, not the module-wide capsule
        // (rounding here would leave gaps against the window's real edge).
        modifier = modifier.dockedGlassSurface(),
        containerColor = Color.Transparent,
        tonalElevation = Elevation.None,
        content = content,
    )
}

/**
 * Now in Android navigation rail item with icon and label content slots. Wraps Material 3
 * [NavigationRailItem].
 *
 * @param selected Whether this item is selected.
 * @param onClick The callback to be invoked when this item is selected.
 * @param icon The item icon content.
 * @param modifier Modifier to be applied to this item.
 * @param selectedIcon The item icon content when selected.
 * @param enabled controls the enabled state of this item. When `false`, this item will not be
 * clickable and will appear disabled to accessibility services.
 * @param label The item text label content.
 * @param alwaysShowLabel Whether to always show the label for this item. If false, the label will
 * only be shown when this item is selected.
 */
@Composable
fun GlassNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    alwaysShowLabel: Boolean = true,
    selectedIcon: @Composable () -> Unit = icon,
    label: @Composable (() -> Unit)? = null,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = if (selected) selectedIcon else icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
    )
}

/**
 * Now in Android navigation rail with header and content slots. Wraps Material 3 [NavigationRail].
 *
 * @param modifier Modifier to be applied to the navigation rail.
 * @param header Optional header that may hold a floating action button or a logo.
 * @param content Destinations inside the navigation rail. This should contain multiple
 * [NavigationRailItem]s.
 */
@Composable
fun GlassNavigationRail(
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    NavigationRail(
        // Docked flush to the side screen edge — flat shape for the same reason as the bar.
        modifier = modifier.dockedGlassSurface(),
        containerColor = Color.Transparent,
        contentColor = LtiNavigationDefaults.navigationContentColor(),
        header = header,
        content = content,
    )
}

@Composable
private fun Modifier.dockedGlassSurface(): Modifier = this.ideShellSurface(
    shape = GlassShapes.HazeFlat,
    drawBorder = false,
)

@Preview
@Composable
private fun GlassNavigationBarPreview() {
    val items = listOf("Explorer", "Search", "Settings", "Profile")
    val icons: List<@Composable () -> androidx.compose.ui.graphics.painter.Painter> =
        listOf(
            AppIcons.FolderPainterResource,
            AppIcons.SearchPainterResource,
            AppIcons.SettingsPainterResource,
            AppIcons.UserPainterResource,
        )

    LtiTheme {
        GlassNavigationBar {
            items.forEachIndexed { index, item ->
                GlassNavigationBarItem(
                    selected = index == 0,
                    onClick = { },
                    icon = {
                        Icon(
                            painter = icons[index](),
                            contentDescription = item,
                        )
                    },
                    selectedIcon = {
                        Icon(
                            painter = icons[index](),
                            contentDescription = item,
                        )
                    },
                    label = { Text(item) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun GlassNavigationRailPreview() {
    val items = listOf("Explorer", "Search", "Settings", "Profile")
    val icons: List<@Composable () -> androidx.compose.ui.graphics.painter.Painter> =
        listOf(
            AppIcons.FolderPainterResource,
            AppIcons.SearchPainterResource,
            AppIcons.SettingsPainterResource,
            AppIcons.UserPainterResource,
        )

    LtiTheme {
        GlassNavigationRail {
            items.forEachIndexed { index, item ->
                GlassNavigationRailItem(
                    selected = index == 0,
                    onClick = { },
                    icon = {
                        Icon(
                            painter = icons[index](),
                            contentDescription = item,
                        )
                    },
                    selectedIcon = {
                        Icon(
                            painter = icons[index](),
                            contentDescription = item,
                        )
                    },
                    label = { Text(item) },
                )
            }
        }
    }
}

/**
 * Now in Android navigation default values.
 */
object LtiNavigationDefaults {
    @Composable
    fun navigationContentColor() = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun navigationSelectedItemColor() = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    fun navigationIndicatorColor() = MaterialTheme.colorScheme.primaryContainer
}
