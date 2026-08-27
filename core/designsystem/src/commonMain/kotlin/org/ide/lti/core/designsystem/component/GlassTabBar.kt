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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun GlassTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    unselectedColor: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    Tab(
        text = { Text(text = text) },
        selected = selected,
        onClick = onClick,
        selectedContentColor = contentColorFor(selectedColor),
        unselectedContentColor = contentColorFor(unselectedColor),
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(if (selected) selectedColor else unselectedColor)
            .padding(horizontal = Spacing.TabHorizontal),
    )
}

/**
 * Tab item for panel tab bars.
 * Displays a single tab with optional close action.
 */
@Composable
fun GlassTabItem(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(ComponentSize.TabItemHeight)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onSelect)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(IconSize.Medium)
                    .padding(start = Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tab",
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }
    }
}

/**
 * Tab bar for panel content with scrollable tabs and optional actions slot.
 */
@Composable
fun GlassTabBar(
    tabs: List<String>,
    selectedIndex: Int = 0,
    selectedTabIndex: Int = selectedIndex,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onTabClosed: ((Int) -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val activeIndex = if (selectedIndex != 0) selectedIndex else selectedTabIndex
    Row(
        modifier = modifier
            .height(ComponentSize.TabBarHeight)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, title ->
                GlassTabItem(
                    title = title,
                    selected = index == activeIndex,
                    onSelect = { onTabSelected(index) },
                    onClose = onTabClosed?.let { { it(index) } },
                )
            }
        }
        actions?.invoke()
    }
}