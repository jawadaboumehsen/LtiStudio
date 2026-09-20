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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Glass-styled dropdown menu, wrapping Material3's [DropdownMenu] with token-based surface
 * styling. Use with [GlassDropdownMenuHeader], [GlassDropdownMenuItem], and
 * [GlassDropdownMenuDivider] as content.
 */
@Composable
public fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val dpOffset = with(density) { DpOffset(offset.x.toDp(), offset.y.toDp()) }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = dpOffset,
        containerColor = GlassTheme.colors.glassSurfaceElevated,
        shape = GlassShapes.Medium,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
            content = content,
        )
    }
}

/**
 * Section header row inside a [GlassDropdownMenu].
 */
@Composable
public fun GlassDropdownMenuHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = GlassTheme.colors.onGlassMuted,
        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
    )
}

/**
 * A single selectable row inside a [GlassDropdownMenu].
 */
@Composable
public fun GlassDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlass,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = GlassTheme.colors.onGlass,
            leadingIconColor = GlassTheme.colors.onGlassSecondary,
        ),
    )
}

/**
 * A thin separator between item groups inside a [GlassDropdownMenu].
 */
@Composable
public fun GlassDropdownMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
        color = GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Subtle),
    )
}
