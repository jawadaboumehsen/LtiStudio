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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes

/**
 * Single macOS-styled pill tab matching the unified Liquid Glass segmented submenu aesthetic.
 */
@Suppress("UnusedParameter")
@Composable
fun GlassTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.PrimaryButtonTint),
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    MacOsSegmentedPill(
        item = MacOsSegmentItem(
            id = text,
            label = text,
        ),
        selected = selected,
        shape = GlassShapes.Pill,
        hazeShape = GlassShapes.HazeCapsule,
        onHoverChanged = {},
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Tab item for panel tab bars rendered with the unified macOS segmented pill style and optional
 * close affordance.
 */
@Composable
fun GlassTabItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    MacOsSegmentedPill(
        item = MacOsSegmentItem(
            id = title,
            label = title,
            onClose = onClose,
        ),
        selected = selected,
        shape = GlassShapes.Pill,
        hazeShape = GlassShapes.HazeCapsule,
        onHoverChanged = {},
        onClick = onSelect,
        modifier = modifier,
    )
}

/**
 * Close and modification behavior for an individual [GlassTab].
 */
sealed interface TabCloseBehavior {
    data object Unclosable : TabCloseBehavior
    data class Closable(val isDirty: Boolean = false, val onClose: () -> Unit) : TabCloseBehavior
}

/**
 * Specification for an individual tab item in [GlassTabBar].
 */
@Immutable
data class GlassTab(
    val id: String,
    val title: String,
    val icon: AppIcon? = null,
    val closeBehavior: TabCloseBehavior = TabCloseBehavior.Unclosable,
)

/**
 * macOS-styled Liquid Glass segmented tab bar for panel content with strongly typed [GlassTab]
 * models.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selectedId: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    MacOsSegmentedControl(
        items = tabs.map { tab ->
            val closeAction = when (val cb = tab.closeBehavior) {
                is TabCloseBehavior.Closable -> cb.onClose
                TabCloseBehavior.Unclosable -> null
            }
            MacOsSegmentItem(
                id = tab.id,
                label = tab.title,
                icon = tab.icon,
                onClose = closeAction,
            )
        },
        selectedId = selectedId,
        onSelect = onTabSelected,
        modifier = modifier,
        wrap = false,
        trailingActions = actions,
    )
}

/**
 * macOS-styled Liquid Glass segmented tab bar for panel content with optional close callbacks and
 * trailing actions slot.
 */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    onTabClosed: ((Int) -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val selectedId = selectedIndex.takeIf { it in tabs.indices }?.toString()
    MacOsSegmentedControl(
        items = tabs.mapIndexed { index, title ->
            MacOsSegmentItem(
                id = index.toString(),
                label = title,
                onClose = onTabClosed?.let { close -> { close(index) } },
            )
        },
        selectedId = selectedId,
        onSelect = { id ->
            id.toIntOrNull()?.let(onTabSelected)
        },
        modifier = modifier,
        wrap = false,
        trailingActions = actions,
    )
}
