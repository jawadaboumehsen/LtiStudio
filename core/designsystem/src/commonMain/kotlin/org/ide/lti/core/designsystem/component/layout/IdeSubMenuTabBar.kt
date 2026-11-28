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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.ide.lti.core.designsystem.component.navigation.MacOsSegmentItem
import org.ide.lti.core.designsystem.component.navigation.MacOsSegmentedControl
import org.ide.lti.core.designsystem.icon.AppIcon

@Immutable
data class IdeSubMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * macOS-styled horizontal Liquid Glass segmented submenu bar displaying sub-objects or section
 * navigation with icons, optional badges, and capsule pill segments.
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
    MacOsSegmentedControl(
        items = items.map { item ->
            MacOsSegmentItem(
                id = item.id,
                label = item.label,
                icon = item.icon?.let { AppIcon.Vector(it) },
                badge = item.badge,
                enabled = item.enabled,
                testTag = "$tagPrefix${item.id}",
            )
        },
        selectedId = selectedItemId,
        onSelect = onSelectItem,
        modifier = modifier,
        wrap = wrap,
    )
}
