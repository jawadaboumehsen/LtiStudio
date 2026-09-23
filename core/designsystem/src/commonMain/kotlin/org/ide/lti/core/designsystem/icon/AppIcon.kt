/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents an icon source that can be either an [ImageVector] or a [Painter].
 * Allows consuming components to own sizing, tinting, and layout while accepting
 * either vector or painter-backed icons.
 */
@Immutable
sealed interface AppIcon {
    data class Vector(val imageVector: ImageVector) : AppIcon
    data class Painted(val painter: @Composable () -> Painter) : AppIcon
}

/**
 * Standard icon renderer for [AppIcon] sources adhering to Glass design system tokens.
 */
@Composable
fun GlassIcon(
    icon: AppIcon,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    when (icon) {
        is AppIcon.Vector -> androidx.compose.material3.Icon(
            imageVector = icon.imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
        is AppIcon.Painted -> androidx.compose.material3.Icon(
            painter = icon.painter(),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier,
        )
    }
}
