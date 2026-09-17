/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.DesktopWindowControls
import org.ide.lti.core.designsystem.component.actions.LocalWindowControlActions
import org.ide.lti.core.designsystem.component.actions.WindowControlActions
import org.ide.lti.core.designsystem.component.actions.windowDragGesture
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.ideFontFamily

/** Global destinations rendered by the single application top-bar family. */
enum class GlobalAppDestination {
    Setup,
    Workspace,
    Settings,
}

/**
 * Canonical application top bar used by Setup, Workspace, Studio, and Settings.
 *
 * Geometry, brand, global navigation, interaction states, drag region, and native window controls
 * are owned here. Destinations provide only contextual text and actions through slots.
 */
@Suppress("LongParameterList")
@Composable
fun GlobalAppTopBar(
    selectedDestination: GlobalAppDestination,
    onSelectDestination: (GlobalAppDestination) -> Unit,
    contextLabel: String,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    contextIcon: AppIcon = destinationIcon(selectedDestination),
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
        colors = colors,
        leadingContent = {
            BrandIdentity(colors = colors, tag = "${tagPrefix}Brand")
            GlobalAppDestination.entries.forEach { destination ->
                DestinationTab(
                    destination = destination,
                    selected = destination == selectedDestination,
                    colors = colors,
                    tag = "${tagPrefix}_${destination.name}",
                    onClick = { onSelectDestination(destination) },
                )
            }
        },
        centerContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.testTag("GlobalTopBarContext"),
            ) {
                GlassIcon(
                    icon = contextIcon,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = contextLabel,
                    color = colors.textMain,
                    fontFamily = ideFontFamily(),
                    fontWeight = FontWeight.Medium,
                )
                centerContent()
            }
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
private fun BrandIdentity(colors: IdeShellColorScheme, tag: String = "GlobalTopBarBrand") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        modifier = Modifier.testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.Medium)
                .clip(IdeShellShapes.Control)
                .background(colors.accentBlue),
            contentAlignment = Alignment.Center,
        ) {
            GlassIcon(
                icon = AppIcons.Play,
                contentDescription = null,
                tint = colors.textMain,
                modifier = Modifier.size(IconSize.Small),
            )
        }
        Text(
            text = "LtiRom Studio",
            color = colors.textMain,
            fontFamily = ideFontFamily(),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DestinationTab(
    destination: GlobalAppDestination,
    selected: Boolean,
    colors: IdeShellColorScheme,
    tag: String = "GlobalTopBar_${destination.name}",
    onClick: () -> Unit,
) {
    val background = if (selected) colors.controlSelected else colors.topBarBackground
    val border = if (selected) {
        Modifier.border(StrokeWidth.Standard, colors.border, IdeShellShapes.Control)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .testTag(tag)
            .clip(IdeShellShapes.Control)
            .then(border)
            .background(background)
            .selectable(
                selected = selected,
                enabled = !selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        GlassIcon(
            icon = destinationIcon(destination),
            contentDescription = null,
            tint = if (selected) colors.textMain else colors.textMuted,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = destination.name,
            color = if (selected) colors.textMain else colors.textMuted,
            fontFamily = ideFontFamily(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun destinationIcon(destination: GlobalAppDestination): AppIcon = when (destination) {
    GlobalAppDestination.Setup -> AppIcons.Home
    GlobalAppDestination.Workspace -> AppIcons.FolderOpen
    GlobalAppDestination.Settings -> AppIcons.Settings
}
