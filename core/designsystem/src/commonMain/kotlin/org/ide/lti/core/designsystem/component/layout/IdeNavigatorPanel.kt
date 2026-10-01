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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipPlacement
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdeNavigatorItem(
    val id: String,
    val label: String,
    val icon: String? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * Workspace navigator with section items and a contextual footer.
 *
 * Supports standard 215 dp layout and compact 44 dp [iconsOnly] rail with hover tooltips.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdeNavigatorPanel(
    headerTitle: String,
    headerSubtitle: String?,
    items: List<IdeNavigatorItem>,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    applySurface: Boolean? = null,
    footerContent: (@Composable () -> Unit)? = null,
    iconsOnly: Boolean = false,
) {
    val inFrame = LocalInIdeAppFrame.current
    val shouldApplySurface = applySurface ?: !inFrame
    val surfaceModifier = if (shouldApplySurface) {
        Modifier.ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false)
    } else {
        Modifier
    }

    val panelWidth = if (iconsOnly) {
        GlassDimens.CompactNavigatorWidth
    } else {
        GlassDimens.WorkspaceNavigatorWidth
    }

    Column(
        modifier = modifier
            .width(panelWidth)
            .fillMaxHeight()
            .then(surfaceModifier)
            .padding(vertical = Spacing.Small),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!iconsOnly) {
                NavigatorHeader(
                    headerTitle = headerTitle,
                    headerSubtitle = headerSubtitle,
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
            }

            // Navigation Items
            items.forEach { item ->
                NavigatorItemRow(
                    item = item,
                    isSelected = item.id == selectedItemId,
                    onSelectItem = onSelectItem,
                    iconsOnly = iconsOnly,
                )
            }
        }

        // Contextual Footer
        if (footerContent != null && !iconsOnly) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = GlassDimens.HairlineBorder,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.Small)) {
                    footerContent()
                }
            }
        }
    }
}

@Composable
private fun NavigatorHeader(headerTitle: String, headerSubtitle: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = headerTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.code(),
            letterSpacing = LetterSpacing.TrackingHeader,
        )
        if (headerSubtitle != null) {
            NavigatorHeaderBadge(headerSubtitle)
        }
    }
}

@Composable
private fun NavigatorHeaderBadge(subtitle: String) {
    Box(
        modifier = Modifier
            .clip(GlassShapes.ShellBadge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .glassOutlineBorder(
                width = GlassDimens.HairlineBorder,
                color = MaterialTheme.colorScheme.outline,
                shape = GlassShapes.ShellBadge,
            )
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline),
    ) {
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.code(),
        )
    }
}

@Composable
private fun NavigatorItemRow(
    item: IdeNavigatorItem,
    isSelected: Boolean,
    onSelectItem: (String) -> Unit,
    iconsOnly: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val visuals = resolveNavVisuals(item = item, isSelected = isSelected, isPressed = isPressed, isHovered = isHovered)

    val borderModifier = if (visuals.borderColor != null) {
        Modifier.glassOutlineBorder(
            width = GlassDimens.HairlineBorder,
            color = visuals.borderColor,
            shape = GlassShapes.ShellPill,
        )
    } else {
        Modifier
    }

    val itemContent = @Composable {
        val clickModifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (iconsOnly) Spacing.ExtraSmall else Spacing.Small,
                vertical = Spacing.Hairline,
            )
            .height(if (iconsOnly) GlassDimens.PipelineStageItemHeight else GlassDimens.NavigatorItemHeight)
            .background(visuals.bg, shape = GlassShapes.ShellPill)
            .clip(GlassShapes.ShellPill)
            .then(borderModifier)
            .testTag("NavigatorItem_${item.id}")
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = item.label
                if (!item.enabled) disabled()
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                onClick = { onSelectItem(item.id) },
            )

        if (iconsOnly) {
            Box(
                modifier = clickModifier,
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = resolveNavIcon(item.id),
                    contentDescription = null,
                    tint = visuals.contentColor,
                    modifier = Modifier.size(IconSize.SidePanelRail),
                )
            }
        } else {
            NavigatorFullItemRow(
                item = item,
                contentColor = visuals.contentColor,
                isSelected = isSelected,
                modifier = clickModifier,
            )
        }
    }

    if (iconsOnly) {
        val tooltip = item.label + (item.badge?.let { " ($it)" } ?: "")
        GlassTooltipArea(
            tooltipText = tooltip,
            tooltipPlacement = GlassTooltipPlacement.End,
        ) {
            itemContent()
        }
    } else {
        itemContent()
    }
}

private data class NavItemVisuals(val bg: Color, val contentColor: Color, val borderColor: Color?)

@Composable
private fun resolveNavVisuals(
    item: IdeNavigatorItem,
    isSelected: Boolean,
    isPressed: Boolean,
    isHovered: Boolean,
): NavItemVisuals {
    val colors = MaterialTheme.colorScheme
    val isBlue = GlassTheme.appTheme == AppTheme.Blue
    val effectsEnabled = GlassTheme.effectsEnabled

    return NavItemVisuals(
        bg = navItemBackground(item.enabled, isSelected, isPressed, isHovered, effectsEnabled, colors),
        contentColor = navItemContentColor(item.enabled, isSelected, isHovered, isBlue, effectsEnabled, colors),
        borderColor = if (isSelected) (if (isBlue) colors.outline else colors.primary) else null,
    )
}

private fun navItemBackground(
    enabled: Boolean,
    selected: Boolean,
    pressed: Boolean,
    hovered: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    !enabled -> Color.Transparent
    selected -> if (!effectsEnabled) colors.primaryContainer else colors.primary.copy(alpha = AlphaTokens.Faded)
    pressed -> colors.surfaceContainerHigh
    hovered -> colors.surfaceContainerHigh.copy(alpha = AlphaTokens.Half)
    else -> Color.Transparent
}

private fun navItemContentColor(
    enabled: Boolean,
    selected: Boolean,
    hovered: Boolean,
    isBlue: Boolean,
    effectsEnabled: Boolean,
    colors: ColorScheme,
): Color = when {
    !enabled -> colors.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
    selected -> when {
        !effectsEnabled -> colors.onPrimaryContainer
        isBlue -> BrandColors.OnLogoBadge
        else -> colors.onSurface
    }
    hovered -> colors.onSurface
    else -> colors.onSurfaceVariant
}

@Composable
private fun NavigatorFullItemRow(
    item: IdeNavigatorItem,
    contentColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            val icon = resolveNavIcon(item.id)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = item.label,
                color = contentColor,
                fontSize = FontSize.BodySmall,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
            )
        }

        if (item.badge != null) {
            val isBlue = GlassTheme.appTheme == AppTheme.Blue
            Text(
                text = item.badge,
                color = if (isSelected) {
                    if (isBlue) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.code(),
            )
        }
    }
}

private val NAV_ICONS: Map<String, ImageVector> = mapOf(
    "overview" to Icons.Default.Dashboard,
    "romconfig" to Icons.Default.Settings,
    "rom_configuration" to Icons.Default.Settings,
    "rom config" to Icons.Default.Settings,
    "runhistory" to Icons.Default.History,
    "run_history" to Icons.Default.History,
    "run history" to Icons.Default.History,
    "artifacts" to Icons.Default.Archive,
    "targetprofile" to Icons.Default.PhoneAndroid,
    "target_profile" to Icons.Default.PhoneAndroid,
    "target profile" to Icons.Default.PhoneAndroid,
    "source" to Icons.Default.Storage,
    "firmware-baseline" to Icons.Default.Memory,
    "firmware_baseline" to Icons.Default.Memory,
    "baseline" to Icons.Default.Memory,
    "existing-archives" to Icons.Default.Folder,
    "existing_archives" to Icons.Default.Folder,
    "archives" to Icons.Default.Folder,
    "download-policy" to Icons.Default.Download,
    "download_policy" to Icons.Default.Download,
    "download" to Icons.Default.Download,
    "integrity" to Icons.Default.VerifiedUser,
    "verification" to Icons.Default.VerifiedUser,
    "work-trees" to Icons.Default.Folder,
    "work_trees" to Icons.Default.Folder,
    "worktrees" to Icons.Default.Folder,
    "system-ext-handling" to Icons.Default.Layers,
    "system_ext_handling" to Icons.Default.Layers,
    "system_ext" to Icons.Default.Layers,
    "boot-preparation" to Icons.Default.Memory,
    "boot_preparation" to Icons.Default.Memory,
    "boot" to Icons.Default.Memory,
    "aot-cleanup" to Icons.Default.Settings,
    "aot_cleanup" to Icons.Default.Settings,
    "aot" to Icons.Default.Settings,
    "build-properties" to Icons.Default.Description,
    "build_properties" to Icons.Default.Description,
    "properties" to Icons.Default.Description,
    "images" to Icons.Default.Description,
    "compression" to Icons.Default.Storage,
    "partition-layout" to Icons.Default.GridView,
    "partition_layout" to Icons.Default.GridView,
    "partition layout" to Icons.Default.GridView,
    "flashable-members" to Icons.AutoMirrored.Filled.ListAlt,
    "flashable_members" to Icons.AutoMirrored.Filled.ListAlt,
    "flashable members" to Icons.AutoMirrored.Filled.ListAlt,
    "avb-keys" to Icons.Default.VpnKey,
    "avb_keys" to Icons.Default.VpnKey,
    "avb keys" to Icons.Default.VpnKey,
    "package-signing" to Icons.Default.Shield,
    "package_signing" to Icons.Default.Shield,
    "package signing" to Icons.Default.Shield,
    "output-naming" to Icons.Default.Sell,
    "output_naming" to Icons.Default.Sell,
    "output naming" to Icons.Default.Sell,
    "all_tools" to Icons.Default.GridView,
    "all-tools" to Icons.Default.GridView,
    "firmware" to Icons.Default.Memory,
    "filesystems" to Icons.Default.Storage,
    "signing" to Icons.Default.Shield,
    "general" to Icons.Default.Tune,
    "appearance" to Icons.Default.Palette,
    "editor" to Icons.Default.Code,
    "target_profiles" to Icons.Default.PhoneAndroid,
    "target-profiles" to Icons.Default.PhoneAndroid,
    "build_environment" to Icons.Default.Build,
    "build-environment" to Icons.Default.Build,
    "plugin_sources" to Icons.Default.Widgets,
    "plugin-sources" to Icons.Default.Widgets,
    "credentials" to Icons.Default.VpnKey,
    "notifications" to Icons.Default.Notifications,
    "about" to Icons.Default.Info,
)

fun resolveNavIcon(id: String): ImageVector = NAV_ICONS[id.lowercase()] ?: Icons.Default.Dashboard
