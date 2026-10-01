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

import androidx.compose.foundation.BorderStroke
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
import org.ide.lti.core.designsystem.theme.StrokeWidth

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
    val borderColor = if (GlassTheme.effectsEnabled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Border)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = dpOffset,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = GlassShapes.Medium,
        border = BorderStroke(StrokeWidth.Hairline, borderColor),
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
public fun GlassDropdownMenuHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        enabled = enabled,
        colors = MenuDefaults.itemColors(
            textColor = MaterialTheme.colorScheme.onSurface,
            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

/**
 * A thin separator between item groups inside a [GlassDropdownMenu].
 */
@Composable
public fun GlassDropdownMenuDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = Spacing.ExtraSmall),
        color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Subtle),
    )
}
