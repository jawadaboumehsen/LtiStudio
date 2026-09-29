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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.DesktopWindowControls
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.actions.windowDragGesture
import org.ide.lti.core.designsystem.component.display.LtiRomStudioMark
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/** Global destinations rendered by the single application top-bar family. */
enum class GlobalAppDestination {
    Setup,
    Workspace,
    Settings,
}

/**
 * Top-level global application bar shared between the Setup/Launchpad and Workspace views.
 *
 * Provides single source of truth for:
 * - Brand identity ("LtiRom Studio")
 * - Global destination navigation (Setup vs Workspace vs Settings)
 * - Window controls (Minimize / Maximize / Close)
 * - Contextual breadcrumb / active profile indicator
 */
@Composable
fun GlobalAppTopBar(
    selectedDestination: GlobalAppDestination,
    onSelectDestination: (GlobalAppDestination) -> Unit,
    modifier: Modifier = Modifier,
    contextLabel: String = "",
    contextIcon: AppIcon = AppIcons.FolderOpen,
    tagPrefix: String = "GlobalTopBar",
    centerContent: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowControlActions: WindowControlActions? = null,
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
) {
    val localWindowControls = LocalWindowControlActions.current
    val effectiveWindowControls = windowControlActions ?: localWindowControls

    IdeTopAppBar(
        modifier = modifier.testTag("GlobalAppTopBar"),
        leadingContent = {
            BrandIdentity(tag = "${tagPrefix}Brand")
        },
        centerContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.testTag("GlobalTopBarDestinations"),
            ) {
                GlobalAppDestination.entries.forEach { destination ->
                    DestinationTab(
                        destination = destination,
                        selected = destination == selectedDestination,
                        tag = "${tagPrefix}_${destination.name}",
                        onClick = { onSelectDestination(destination) },
                    )
                }
            }
            if (contextLabel.isNotBlank() && contextLabel != selectedDestination.name) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    modifier = Modifier.testTag("GlobalTopBarContext"),
                ) {
                    GlassIcon(
                        icon = contextIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text(
                        text = contextLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            centerContent()
        },
        trailingContent = {
            actions()
            Spacer(
                modifier = Modifier
                    .width(Spacing.Large)
                    .fillMaxHeight()
                    .testTag(if (tagPrefix == "TopBar") "TopBar_DragRegion" else "${tagPrefix}DragRegion")
                    .windowDragGesture(
                        onDrag = onDragWindow ?: effectiveWindowControls.onDragWindow,
                        onDragDelta = effectiveWindowControls.onDragDelta,
                    ),
            )
        },
        windowControls = {
            windowControls?.invoke() ?: DesktopWindowControls(
                onMinimize = effectiveWindowControls.onMinimize,
                onMaximize = effectiveWindowControls.onMaximize,
                onClose = effectiveWindowControls.onClose,
            )
        },
    )
}

@Composable
private fun BrandIdentity(tag: String = "GlobalTopBarBrand") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
        modifier = Modifier.testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.LogoBadgeSize)
                .clip(GlassShapes.Small)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ),
                )
                .padding(Spacing.Compact),
            contentAlignment = Alignment.Center,
        ) {
            LtiRomStudioMark()
        }
        Text(
            text = "LtiRom Studio",
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = ideFontFamily(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DestinationTab(
    destination: GlobalAppDestination,
    selected: Boolean,
    tag: String = "GlobalTopBar_${destination.name}",
    onClick: () -> Unit,
) {
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val background = when {
        selected && isBlue -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
        else -> Color.Transparent
    }
    val contentColor = when {
        selected && isBlue -> BrandColors.OnLogoBadge
        selected -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (selected) {
        Modifier.glassOutlineBorder(
            color = if (isBlue) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            width = GlassDimens.HairlineBorder,
            shape = GlassShapes.ShellPill,
        )
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .testTag(tag)
            .clip(GlassShapes.ShellPill)
            .then(border)
            .background(background)
            .selectable(
                selected = selected,
                enabled = !selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        GlassIcon(
            icon = destinationIcon(destination),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = destination.name,
            color = contentColor,
            fontFamily = ideFontFamily(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun destinationIcon(destination: GlobalAppDestination): AppIcon = when (destination) {
    GlobalAppDestination.Setup -> AppIcons.Settings
    GlobalAppDestination.Workspace -> AppIcons.FolderOpen
    GlobalAppDestination.Settings -> AppIcon.Vector(Icons.Default.Tune)
}
