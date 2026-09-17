/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDownload
import org.ide.lti.core.designsystem.generated.resources.ideGeneralExternalTools
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.ide.lti.core.designsystem.generated.resources.ideToolwindowsRepositories
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.StudioBackdropColors
import org.ide.lti.feature.plugins.PluginDestination
import org.jetbrains.compose.resources.painterResource

private data class StageItem(val label: String, val icon: @Composable () -> Painter)

private val romStages = listOf(
    StageItem("Acquire", AppIconsRomStages.acquire),
    StageItem("Extract", AppIconsRomStages.extract),
    StageItem("Assemble", AppIconsRomStages.assemble),
    StageItem("Debloat", AppIconsRomStages.debloat),
    StageItem("Patches", AppIconsRomStages.patches),
    StageItem("Build", AppIconsRomStages.build),
    StageItem("Metadata", AppIconsRomStages.releaseMetadata),
    StageItem("Publish", AppIconsRomStages.publish),
)

private val authorSubStages = listOf(
    StageItem("Build", AppIconsRomStages.build),
    StageItem("Metadata", AppIconsRomStages.releaseMetadata),
    StageItem("Publish", AppIconsRomStages.publish),
)

/**
 * Persistent IDE destination rail and sidebar for Plugin Manager.
 * Matches the dense blue-glass navigation hierarchy in concept mockups 12, 13, 14, 15.
 */
@Composable
public fun PluginSidebar(
    currentDestination: PluginDestination,
    onSelectDestination: (PluginDestination) -> Unit,
    onNavigateToWorkspace: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Column(
        modifier = modifier
            .width(ComponentSize.PluginSidebarWidth)
            .fillMaxHeight()
            .pluginSidebarBackground()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
    ) {
        when (currentDestination) {
            PluginDestination.INSTALLED -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    romStages.forEach { stage ->
                        SidebarNavItem(
                            label = stage.label,
                            icon = stage.icon,
                            selected = false,
                            onClick = onNavigateToWorkspace,
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    SidebarNavItem(
                        label = "Installed",
                        icon = { painterResource(Res.drawable.ideNodesPlugin) },
                        selected = true,
                        onClick = { onSelectDestination(PluginDestination.INSTALLED) },
                    )
                    SidebarNavItem(
                        label = "Marketplace",
                        icon = { painterResource(Res.drawable.ideToolwindowsRepositories) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.MARKETPLACE) },
                    )
                    SidebarNavItem(
                        label = "Import package",
                        icon = { painterResource(Res.drawable.ideGeneralDownload) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.IMPORT) },
                    )
                    SidebarNavItem(
                        label = "Author tools",
                        icon = { painterResource(Res.drawable.ideGeneralExternalTools) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.AUTHOR_TOOLS) },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
            PluginDestination.MARKETPLACE, PluginDestination.IMPORT -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    SidebarNavItem(
                        label = "Installed",
                        icon = { painterResource(Res.drawable.ideNodesPlugin) },
                        selected = currentDestination == PluginDestination.INSTALLED,
                        onClick = { onSelectDestination(PluginDestination.INSTALLED) },
                    )
                    SidebarNavItem(
                        label = "Marketplace",
                        icon = { painterResource(Res.drawable.ideToolwindowsRepositories) },
                        selected = currentDestination == PluginDestination.MARKETPLACE,
                        onClick = { onSelectDestination(PluginDestination.MARKETPLACE) },
                    )
                    SidebarNavItem(
                        label = "Import package",
                        icon = { painterResource(Res.drawable.ideGeneralDownload) },
                        selected = currentDestination == PluginDestination.IMPORT,
                        onClick = { onSelectDestination(PluginDestination.IMPORT) },
                    )
                    SidebarNavItem(
                        label = "Author tools",
                        icon = { painterResource(Res.drawable.ideGeneralExternalTools) },
                        selected = currentDestination == PluginDestination.AUTHOR_TOOLS,
                        onClick = { onSelectDestination(PluginDestination.AUTHOR_TOOLS) },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
            PluginDestination.AUTHOR_TOOLS -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    SidebarNavItem(
                        label = "Installed",
                        icon = { painterResource(Res.drawable.ideNodesPlugin) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.INSTALLED) },
                    )
                    SidebarNavItem(
                        label = "Marketplace",
                        icon = { painterResource(Res.drawable.ideToolwindowsRepositories) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.MARKETPLACE) },
                    )
                    SidebarNavItem(
                        label = "Import package",
                        icon = { painterResource(Res.drawable.ideGeneralDownload) },
                        selected = false,
                        onClick = { onSelectDestination(PluginDestination.IMPORT) },
                    )
                    SidebarNavItem(
                        label = "Author tools",
                        icon = { painterResource(Res.drawable.ideGeneralExternalTools) },
                        selected = true,
                        onClick = { onSelectDestination(PluginDestination.AUTHOR_TOOLS) },
                    )

                    Spacer(modifier = Modifier.height(Spacing.Small))

                    authorSubStages.forEach { stage ->
                        SidebarNavItem(
                            label = stage.label,
                            icon = stage.icon,
                            selected = false,
                            enabled = false,
                            onClick = null,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    SidebarNavItem(
                        label = "Help & feedback",
                        icon = AppIcons.InfoPainterResource,
                        selected = false,
                        enabled = false,
                        onClick = null,
                    )
                    SidebarNavItem(
                        label = "Settings",
                        icon = AppIcons.SettingsPainterResource,
                        selected = false,
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }
}

private fun Modifier.pluginSidebarBackground(): Modifier = background(
    brush = Brush.verticalGradient(
        colors = listOf(
            StudioBackdropColors.SidebarStart,
            StudioBackdropColors.SidebarCenter,
            StudioBackdropColors.SidebarEnd,
        ),
    ),
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SidebarNavItem(
    label: String,
    icon: @Composable () -> Painter,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    var isHovered by remember { mutableStateOf(false) }

    val bg = when {
        selected -> PluginThemeColors.ActiveTabBackground
        isHovered -> glassColors.onGlass.copy(alpha = AlphaTokens.UltraFaint)
        else -> Color.Transparent
    }

    val contentColor = when {
        !enabled -> glassColors.onGlassMuted.copy(alpha = AlphaTokens.Disabled)
        selected -> BrandColors.OnLogoBadge
        isHovered -> glassColors.onGlass
        else -> glassColors.onGlassSecondary
    }

    val shape = GlassShapes.MediumSmall

    val borderModifier = if (selected) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = PluginThemeColors.ActiveTabBorder,
            shape = shape,
        )
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.ButtonMinHeight)
            .clip(shape)
            .background(bg)
            .then(borderModifier)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(role = Role.Tab, onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                },
            )
            .semantics { this.selected = selected }
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            painter = icon(),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(IconSize.Large),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
