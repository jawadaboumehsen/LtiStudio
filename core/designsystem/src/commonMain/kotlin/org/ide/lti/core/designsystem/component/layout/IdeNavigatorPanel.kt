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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ListAlt
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import org.ide.lti.core.designsystem.component.primitives.glass
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme
import org.ide.lti.core.designsystem.theme.glass.GlassStyles

@Immutable
data class IdeNavigatorItem(
    val id: String,
    val label: String,
    val icon: String? = null,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * 230 dp wide workspace navigator with section items and a contextual footer.
 */
@Composable
fun IdeNavigatorPanel(
    headerTitle: String,
    headerSubtitle: String?,
    items: List<IdeNavigatorItem>,
    selectedItemId: String?,
    onSelectItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    footerContent: (@Composable () -> Unit)? = null,
) {
    // Real glass chrome only for the Blue theme - see IdeTopAppBar.
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val style = GlassStyles.navigator.then { copy(surface = colors.navigatorBackground) }

    Column(
        modifier = modifier
            .width(IdeShellDimens.WorkspaceNavigatorWidth)
            .fillMaxHeight()
            .glass(style, enabled = isBlueGlass)
            .padding(vertical = Spacing.Small),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = headerTitle,
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.code(),
                    letterSpacing = LetterSpacing.TrackingHeader,
                )
                if (headerSubtitle != null) {
                    Box(
                        modifier = Modifier
                            .clip(GlassShapes.ShellBadge)
                            .background(colors.cardBackgroundMuted)
                            .border(
                                width = IdeShellDimens.HairlineBorder,
                                color = colors.border,
                                shape = GlassShapes.ShellBadge,
                            )
                            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline),
                    ) {
                        Text(
                            text = headerSubtitle,
                            color = colors.textDim,
                            fontSize = FontSize.Micro,
                            fontFamily = GlassFontFamily.code(),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ExtraSmall))

            // Navigation Items
            items.forEach { item ->
                NavigatorItemRow(
                    item = item,
                    isSelected = item.id == selectedItemId,
                    onSelectItem = onSelectItem,
                    colors = colors,
                )
            }
        }

        // Contextual Footer
        if (footerContent != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(
                    thickness = IdeShellDimens.HairlineBorder,
                    color = colors.borderMuted,
                )
                Box(modifier = Modifier.fillMaxWidth().padding(Spacing.Small)) {
                    footerContent()
                }
            }
        }
    }
}

@Composable
private fun NavigatorItemRow(
    item: IdeNavigatorItem,
    isSelected: Boolean,
    onSelectItem: (String) -> Unit,
    colors: IdeShellColorScheme,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bg = when {
        !item.enabled -> Color.Transparent
        isSelected -> colors.accentBlue
        isPressed -> colors.controlHover
        isHovered -> colors.controlHover.copy(alpha = AlphaTokens.Prominent)
        else -> Color.Transparent
    }

    val contentColor = when {
        !item.enabled -> colors.textDim.copy(alpha = AlphaTokens.Disabled)
        isSelected -> colors.textMain
        isHovered -> colors.textMain
        else -> colors.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline)
            .height(IdeShellDimens.NavigatorItemHeight)
            .clip(GlassShapes.ShellControl)
            .background(bg)
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
            .padding(horizontal = Spacing.Small),
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
            Text(
                text = item.badge,
                color = if (isSelected) colors.textMain else colors.textDim,
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
    "flashable-members" to Icons.Default.ListAlt,
    "flashable_members" to Icons.Default.ListAlt,
    "flashable members" to Icons.Default.ListAlt,
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

private fun resolveNavIcon(id: String): ImageVector = NAV_ICONS[id.lowercase()] ?: Icons.Default.Dashboard
