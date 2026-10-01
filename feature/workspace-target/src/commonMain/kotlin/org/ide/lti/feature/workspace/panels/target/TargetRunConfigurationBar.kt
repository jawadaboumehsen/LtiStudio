/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuDivider
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion
import java.awt.Cursor

/**
 * Resolves the official OS stream codename for a target device (e.g. NP05J_GB, NP05J_CN).
 */
fun TargetDevice.getStreamCode(region: TargetRegion = TargetRegion.GLOBAL): String = when {
    codename == "PQ84P01" -> if (region == TargetRegion.GLOBAL) "NP05J_GB" else "NP05J_CN"
    else -> "${codename}_${region.code}"
}

/**
 * Resolves a clean IDE display label for a hardware target device.
 */
fun TargetDevice.getShortDisplayName(): String = when (codename) {
    "PQ84P01" -> "REDMAGIC Astra"
    "NX789J" -> "REDMAGIC 10 Pro"
    "NX769J" -> "REDMAGIC 9 Pro"
    else -> name
}

/**
 * Android Studio-style interactive Target & Connected Device Capsule in the Top Bar.
 *
 * Pattern:
 * Segment 1: [ 📱 REDMAGIC Astra • Global OS (NP05J_GB) ▾ ]  (Target ROM / Build Configuration)
 * Segment 2: [ 🔌 REDMAGIC Astra (USB) [online] ▾ ]           (Connected Physical / ADB / Fastboot Device)
 */
@Composable
fun TargetRunConfigurationBar(
    selectedTarget: TargetDevice,
    region: TargetRegion,
    onOpenWorkspaceConfiguration: () -> Unit,
    onOpenEditConfiguration: () -> Unit,
    onOpenAddConfiguration: () -> Unit,
    onCloseWorkspace: () -> Unit = {},
    // Connected Device / ADB Parameters
    connectedDevices: List<ConnectedTargetDevice> = emptyList(),
    selectedConnectedDevice: ConnectedTargetDevice? = null,
    isRefreshingDevices: Boolean = false,
    onSelectConnectedDevice: (String?) -> Unit = {},
    onRefreshConnectedDevices: () -> Unit = {},
    onRebootConnectedDevice: (String, String) -> Unit = { _, _ -> },
    onOpenWifiPairing: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val capsuleShape = GlassShapes.Pill

    var isTargetMenuOpen by remember { mutableStateOf(false) }
    var isDeviceMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .clip(capsuleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Subtle))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                shape = capsuleShape,
            )
            .padding(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            TargetRomSegment(
                selectedTarget = selectedTarget,
                region = region,
                isOpen = isTargetMenuOpen,
                onToggleMenu = {
                    isTargetMenuOpen = !isTargetMenuOpen
                    isDeviceMenuOpen = false
                },
                onDismissMenu = { isTargetMenuOpen = false },
                onOpenWorkspaceConfiguration = onOpenWorkspaceConfiguration,
                onOpenEditConfiguration = onOpenEditConfiguration,
                onOpenAddConfiguration = onOpenAddConfiguration,
                onCloseWorkspace = onCloseWorkspace,
            )

            // Specular vertical hairline divider between segments
            GlassVerticalDivider(
                modifier = Modifier.height(ComponentSize.TopBarDividerHeight),
                specular = true,
            )

            ConnectedDeviceSegment(
                selectedConnectedDevice = selectedConnectedDevice,
                connectedDevices = connectedDevices,
                isRefreshingDevices = isRefreshingDevices,
                isOpen = isDeviceMenuOpen,
                onToggleMenu = {
                    isDeviceMenuOpen = !isDeviceMenuOpen
                    isTargetMenuOpen = false
                },
                onDismissMenu = { isDeviceMenuOpen = false },
                onSelectConnectedDevice = onSelectConnectedDevice,
                onRefreshConnectedDevices = onRefreshConnectedDevices,
                onRebootConnectedDevice = onRebootConnectedDevice,
                onOpenWifiPairing = onOpenWifiPairing,
            )
        }
    }
}

/** Segment 1: Target ROM / Build Configuration Pill and dropdown. */
@Composable
private fun TargetRomSegment(
    selectedTarget: TargetDevice,
    region: TargetRegion,
    isOpen: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onOpenWorkspaceConfiguration: () -> Unit,
    onOpenEditConfiguration: () -> Unit,
    onOpenAddConfiguration: () -> Unit,
    onCloseWorkspace: () -> Unit,
) {
    Box {
        val streamCode = selectedTarget.getStreamCode(region)
        val targetLabel = "${selectedTarget.getShortDisplayName()} • ${region.displayName} ($streamCode)"

        TargetPillButton(
            emoji = "📱",
            label = targetLabel,
            isMenuOpen = isOpen,
            tooltipText = "Workspace target: ${selectedTarget.name} - ${region.displayName} ($streamCode)",
            onClick = onToggleMenu,
            modifier = Modifier.widthIn(max = ComponentSize.DropdownMenuMinWidth),
        )

        GlassDropdownMenu(
            expanded = isOpen,
            onDismissRequest = onDismissMenu,
            offset = IntOffset(x = 0, y = 32),
        ) {
            GlassDropdownMenuHeader(title = "Workspace Target")

            // A workspace is bound to exactly one profile; region/firmware live in its
            // configuration snapshot, so the bound target is shown, not switched, here.
            GlassDropdownMenuItem(
                text = "${selectedTarget.name} ($streamCode)",
                subtitle = "${selectedTarget.codename} • ${region.displayName} • " +
                    selectedTarget.socPlatform,
                leadingIcon = {
                    Text(
                        text = if (region == TargetRegion.GLOBAL) "🌐" else "🇨🇳",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                isSelected = true,
                onClick = onDismissMenu,
            )

            GlassDropdownMenuItem(
                text = "Configure Workspace...",
                subtitle = "Region, firmware, acquisition, signing and OTA settings",
                leadingIcon = {
                    Icon(
                        painter = AppIcons.SettingsPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    onDismissMenu()
                    onOpenWorkspaceConfiguration()
                },
            )

            GlassDropdownMenuDivider()

            GlassDropdownMenuItem(
                text = "Edit Configurations...",
                subtitle = "Open Target & Run Configurations Studio",
                leadingIcon = {
                    Icon(
                        painter = AppIcons.EditPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    onDismissMenu()
                    onOpenEditConfiguration()
                },
            )

            GlassDropdownMenuItem(
                text = "Add Configuration...",
                subtitle = "Scaffold a new target device & partition spec",
                leadingIcon = {
                    Icon(
                        painter = AppIcons.AddPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = {
                    onDismissMenu()
                    onOpenAddConfiguration()
                },
            )

            GlassDropdownMenuDivider()

            GlassDropdownMenuItem(
                text = "Switch Target / Close Workspace...",
                subtitle = "Return to Setup to pick a different target hardware device",
                leadingIcon = {
                    Icon(
                        painter = AppIcons.ClosePainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {
                    onDismissMenu()
                    onCloseWorkspace()
                },
            )
        }
    }
}

/** Segment 2: Connected Device / ADB & Fastboot Target Pill and dropdown. */
@Composable
private fun ConnectedDeviceSegment(
    selectedConnectedDevice: ConnectedTargetDevice?,
    connectedDevices: List<ConnectedTargetDevice>,
    isRefreshingDevices: Boolean,
    isOpen: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSelectConnectedDevice: (String?) -> Unit,
    onRefreshConnectedDevices: () -> Unit,
    onRebootConnectedDevice: (String, String) -> Unit,
    onOpenWifiPairing: () -> Unit,
) {
    Box {
        val deviceEmoji = selectedConnectedDevice?.connectionType?.iconEmoji ?: "🔌"
        val deviceLabel = selectedConnectedDevice?.let { dev ->
            "${dev.shortDisplayName} [${dev.stateBadge}]"
        } ?: "No Devices Connected"

        val tooltipText = selectedConnectedDevice?.let { dev ->
            "Active Deployment Target: ${dev.modelName} (${dev.serial}) via " +
                "${dev.connectionType.displayName} • Status: ${dev.state.displayName}"
        } ?: "No physical or emulated devices connected over USB/Wi-Fi"

        TargetPillButton(
            emoji = deviceEmoji,
            label = deviceLabel,
            isMenuOpen = isOpen,
            tooltipText = tooltipText,
            onClick = onToggleMenu,
            modifier = Modifier.widthIn(max = ComponentSize.DropdownMenuMinWidth),
        )

        GlassDropdownMenu(
            expanded = isOpen,
            onDismissRequest = onDismissMenu,
            offset = IntOffset(x = 0, y = 32),
        ) {
            GlassDropdownMenuHeader(title = "Connected ADB & Fastboot Devices")

            if (connectedDevices.isEmpty()) {
                GlassDropdownMenuItem(
                    text = "No Devices Detected",
                    subtitle = "Connect an Android device via USB cable or Wireless ADB",
                    leadingIcon = {
                        Text(text = "⚠️", style = MaterialTheme.typography.bodySmall)
                    },
                    isSelected = false,
                    onClick = {},
                )
            } else {
                connectedDevices.forEach { dev ->
                    val isSelected = dev.serial == selectedConnectedDevice?.serial
                    val statusNote = "${dev.serial} • ${dev.connectionType.displayName} • " +
                        "Android ${dev.androidVersion ?: "15"}"

                    GlassDropdownMenuItem(
                        text = "${dev.modelName} [${dev.stateBadge}]",
                        subtitle = statusNote,
                        leadingIcon = {
                            Text(
                                text = dev.connectionType.iconEmoji,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        isSelected = isSelected,
                        onClick = {
                            onSelectConnectedDevice(dev.serial)
                            onDismissMenu()
                        },
                    )
                }
            }

            GlassDropdownMenuDivider()

            // Action 1: Refresh Devices
            GlassDropdownMenuItem(
                text = if (isRefreshingDevices) "Scanning Devices..." else "Refresh Devices (adb devices)",
                subtitle = "Probe USB bus and WSL ADB daemon",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onRefreshConnectedDevices,
            )

            // Actions when a device is selected
            selectedConnectedDevice?.let { dev ->
                DeviceRebootMenuItems(
                    dev = dev,
                    onDismissMenu = onDismissMenu,
                    onRebootConnectedDevice = onRebootConnectedDevice,
                )
            }

            GlassDropdownMenuDivider()

            // Action: Wireless ADB Pairing
            GlassDropdownMenuItem(
                text = "Pair Device via Wi-Fi...",
                subtitle = "adb pair ip:port with 6-digit code",
                leadingIcon = {
                    Text(text = "📶", style = MaterialTheme.typography.bodySmall)
                },
                onClick = {
                    onDismissMenu()
                    onOpenWifiPairing()
                },
            )
        }
    }
}

/** Dropdown menu items for rebooting a connected Android device. */
@Composable
private fun DeviceRebootMenuItems(
    dev: ConnectedTargetDevice,
    onDismissMenu: () -> Unit,
    onRebootConnectedDevice: (String, String) -> Unit,
) {
    val isFastboot = dev.state == AdbDeviceState.FASTBOOT || dev.state == AdbDeviceState.FASTBOOTD

    if (isFastboot) {
        GlassDropdownMenuItem(
            text = "Reboot to System",
            subtitle = "fastboot reboot",
            leadingIcon = {
                Text(text = "📱", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "system")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot to Bootloader",
            subtitle = "fastboot reboot-bootloader",
            leadingIcon = {
                Text(text = "⚡", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "bootloader")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot to Userspace Fastbootd",
            subtitle = "fastboot reboot fastboot",
            leadingIcon = {
                Text(text = "🔧", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "fastbootd")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot to Recovery",
            subtitle = "fastboot reboot recovery",
            leadingIcon = {
                Text(text = "🔄", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "recovery")
            },
        )
    } else {
        GlassDropdownMenuItem(
            text = "Reboot to Bootloader (Fastboot)",
            subtitle = "adb reboot bootloader",
            leadingIcon = {
                Text(text = "⚡", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "bootloader")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot to Userspace Fastbootd",
            subtitle = "adb reboot fastboot",
            leadingIcon = {
                Text(text = "🔧", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "fastbootd")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot to Recovery (Sideload)",
            subtitle = "adb reboot recovery",
            leadingIcon = {
                Text(text = "🔄", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "recovery")
            },
        )

        GlassDropdownMenuItem(
            text = "Reboot System",
            subtitle = "adb reboot",
            leadingIcon = {
                Text(text = "📱", style = MaterialTheme.typography.bodySmall)
            },
            onClick = {
                onDismissMenu()
                onRebootConnectedDevice(dev.serial, "system")
            },
        )
    }
}

/**
 * Single interactive pill button inside [TargetRunConfigurationBar].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TargetPillButton(
    emoji: String,
    label: String,
    isMenuOpen: Boolean,
    tooltipText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isHovered by remember { mutableStateOf(false) }

    val backgroundState = when {
        isMenuOpen -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Hover)
        else -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Faint)
    }

    val animatedBg by animateColorAsState(
        targetValue = backgroundState,
        label = "pillBg",
    )

    GlassTooltipArea(tooltipText = tooltipText) {
        Row(
            modifier = modifier
                .height(ComponentSize.SegmentedButtonSize)
                .clip(GlassShapes.Compact)
                .background(animatedBg)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .padding(horizontal = Spacing.Compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.Micro),
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = FontSize.Chip,
                    fontWeight = FontWeight.Medium,
                    fontFamily = codeFontFamily(),
                ),
                color = if (isMenuOpen ||
                    isHovered
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            Icon(
                painter = AppIcons.ChevronDownPainterResource(),
                contentDescription = "Dropdown",
                modifier = Modifier.size(IconSize.Small),
                tint = if (isMenuOpen ||
                    isHovered
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
