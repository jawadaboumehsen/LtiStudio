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

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Standardized Liquid Glass Sidebar Navigation Item (e.g. for Setup, Drawer, Sidebar panels).
 *
 * @param label Human-readable navigation title.
 * @param icon The [AppIcon] to display.
 * @param selected Whether this destination is currently selected.
 * @param onClick Invoked when clicked.
 * @param modifier Optional modifier.
 * @param badge Optional trailing badge or counter string.
 */
@Composable
fun GlassSidebarItem(
    label: String,
    icon: AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val glassColors = GlassTheme.colors
    GlassButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        variant = if (selected) GlassButtonVariant.Primary else GlassButtonVariant.Text,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            when (icon) {
                is AppIcon.Vector -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                    tint = if (selected) glassColors.onGlass else glassColors.onGlassSecondary,
                    modifier = Modifier.size(IconSize.Medium),
                )
                is AppIcon.Painted -> Icon(
                    painter = icon.painter(),
                    contentDescription = null,
                    tint = if (selected) glassColors.onGlass else glassColors.onGlassSecondary,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) glassColors.onGlass else glassColors.onGlassSecondary,
                modifier = Modifier.weight(1f),
            )
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) glassColors.onGlass else glassColors.onGlassMuted,
                )
            }
        }
    }
}

/**
 * Convenience overload accepting an [ImageVector] icon directly.
 */
@Composable
fun GlassSidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) = GlassSidebarItem(
    label = label,
    icon = AppIcon.Vector(icon),
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    badge = badge,
)
