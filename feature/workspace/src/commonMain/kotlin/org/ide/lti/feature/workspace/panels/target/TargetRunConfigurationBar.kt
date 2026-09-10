/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import java.awt.Cursor
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
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.resolveAnimationSpec
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceConnectionType
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion

/**
 * Resolves the official OS stream codename for a target device (e.g. NP05J_GB, NP05J_CN).
 */
fun TargetDevice.getStreamCode(region: TargetRegion = selectedRegion): String {
    return when {
        codename == "PQ84P01" -> if (region == TargetRegion.GLOBAL) "NP05J_GB" else "NP05J_CN"
        else -> "${codename}_${region.code}"
    }
}

/**
 * Resolves a clean IDE display label for a hardware target device.
 */
fun TargetDevice.getShortDisplayName(): String {
    return when (codename) {
        "PQ84P01" -> "REDMAGIC Astra"
        "NX789J" -> "REDMAGIC 10 Pro"
        "NX769J" -> "REDMAGIC 9 Pro"
        else -> name
    }
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
    availableTargets: List<TargetDevice>,
    onSelectTarget: (String) -> Unit,
    onSelectRegion: (TargetRegion) -> Unit,
    onSelectFirmware: (TargetFirmware) -> Unit,
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
    val glassColors = GlassTheme.colors
    val capsuleShape = GlassShapes.Pill

    var isTargetMenuOpen by remember { mutableStateOf(false) }
    var isDeviceMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .height(ComponentSize.SegmentedToggleHeight)
            .clip(capsuleShape)
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Subtle))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder.copy(alpha = AlphaTokens.Faint),
                shape = capsuleShape,
            )
            .padding(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            // =========================================================================
            // SEGMENT 1: Target ROM / Build Configuration Pill
            // e.g. [ 📱 REDMAGIC Astra • Global OS (NP05J_GB) ▾ ]
            // =========================================================================
            Box {
                val streamCode = selectedTarget.getStreamCode()
                val targetLabel = "${selectedTarget.getShortDisplayName()} • ${selectedTarget.selectedRegion.displayName} ($streamCode)"

                TargetPillButton(
                    emoji = "📱",
                    label = targetLabel,
                    isMenuOpen = isTargetMenuOpen,
                    tooltipText = "Target ROM Configuration: ${selectedTarget.name} - ${selectedTarget.selectedRegion.displayName} ($streamCode)",
                    onClick = {
                        isTargetMenuOpen = !isTargetMenuOpen
                        isDeviceMenuOpen = false
                    },
                    modifier = Modifier.widthIn(max = ComponentSize.DropdownMenuMinWidth),
                )

                GlassDropdownMenu(
                    expanded = isTargetMenuOpen,
                    onDismissRequest = { isTargetMenuOpen = false },
                    offset = IntOffset(x = 0, y = 32),
                ) {
                    GlassDropdownMenuHeader(title = "Target ROM Configurations")

                    availableTargets.forEach { target ->
                        target.availableRegions.forEach { region ->
                            val code = target.getStreamCode(region)
                            val isSelected = target.id == selectedTarget.id && selectedTarget.selectedRegion == region
                            val regionEmoji = if (region == TargetRegion.GLOBAL) "🌐" else "🇨🇳"

                            GlassDropdownMenuItem(
                                text = "${target.name} ($code)",
                                subtitle = "${target.codename} • ${region.displayName} • ${target.socPlatform}",
                                leadingIcon = {
                                    Text(
                                        text = regionEmoji,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                isSelected = isSelected,
                                onClick = {
                                    if (target.id != selectedTarget.id) {
                                        onSelectTarget(target.id)
                                    }
                                    onSelectRegion(region)
                                    isTargetMenuOpen = false
                                },
                            )
                        }
                    }

                    GlassDropdownMenuDivider()

                    GlassDropdownMenuItem(
                        text = "Edit Configurations...",
                        subtitle = "Open Target & Run Configurations Studio",
                        leadingIcon = {
                            Icon(
                                painter = AppIcons.EditPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                                tint = glassColors.glassAccent,
                            )
                        },
                        onClick = {
                            isTargetMenuOpen = false
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
                                tint = glassColors.glassAccent,
                            )
                        },
                        onClick = {
                            isTargetMenuOpen = false
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
                                tint = glassColors.onGlassSecondary,
                            )
                        },
                        onClick = {
                            isTargetMenuOpen = false
                            onCloseWorkspace()
                        },
                    )
                }
            }

            // Specular vertical hairline divider between segments
            GlassVerticalDivider(
                modifier = Modifier.height(ComponentSize.TopBarDividerHeight),
                specular = true,
            )

            // =========================================================================
            // SEGMENT 2: Connected Device / ADB & Fastboot Target Pill
            // e.g. [ 🔌 REDMAGIC Astra (USB) [online] ▾ ] or [ 🔌 No Devices Connected ▾ ]
            // =========================================================================
            Box {
                val deviceEmoji = selectedConnectedDevice?.connectionType?.iconEmoji ?: "🔌"
                val deviceLabel = selectedConnectedDevice?.let { dev ->
                    "${dev.shortDisplayName} [${dev.stateBadge}]"
                } ?: "No Devices Connected"

                val tooltipText = selectedConnectedDevice?.let { dev ->
                    "Active Deployment Target: ${dev.modelName} (${dev.serial}) via ${dev.connectionType.displayName} • Status: ${dev.state.displayName}"
                } ?: "No physical or emulated devices connected over USB/Wi-Fi"

                TargetPillButton(
                    emoji = deviceEmoji,
                    label = deviceLabel,
                    isMenuOpen = isDeviceMenuOpen,
                    tooltipText = tooltipText,
                    onClick = {
                        isDeviceMenuOpen = !isDeviceMenuOpen
                        isTargetMenuOpen = false
                    },
                    modifier = Modifier.widthIn(max = ComponentSize.DropdownMenuMinWidth),
                )

                GlassDropdownMenu(
                    expanded = isDeviceMenuOpen,
                    onDismissRequest = { isDeviceMenuOpen = false },
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
                            val statusNote = "${dev.serial} • ${dev.connectionType.displayName} • Android ${dev.androidVersion ?: "15"}"

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
                                    isDeviceMenuOpen = false
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
                                tint = glassColors.glassAccent,
                            )
                        },
                        onClick = {
                            onRefreshConnectedDevices()
                        },
                    )

                    // Actions when a device is selected
                    selectedConnectedDevice?.let { dev ->
                        val isFastboot = dev.state == AdbDeviceState.FASTBOOT || dev.state == AdbDeviceState.FASTBOOTD

                        if (isFastboot) {
                            GlassDropdownMenuItem(
                                text = "Reboot to System",
                                subtitle = "fastboot reboot",
                                leadingIcon = {
                                    Text(text = "📱", style = MaterialTheme.typography.bodySmall)
                                },
                                onClick = {
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
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
                                    isDeviceMenuOpen = false
                                    onRebootConnectedDevice(dev.serial, "system")
                                },
                            )
                        }
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
                            isDeviceMenuOpen = false
                            onOpenWifiPairing()
                        },
                    )
                }
            }
        }
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
    val glassColors = GlassTheme.colors
    var isHovered by remember { mutableStateOf(false) }

    val backgroundState = when {
        isMenuOpen -> glassColors.glassAccent.copy(alpha = AlphaTokens.Hover)
        isHovered -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Hover)
        else -> glassColors.glassSurface.copy(alpha = AlphaTokens.Faint)
    }

    val animatedBg by animateColorAsState(
        targetValue = backgroundState,
        animationSpec = resolveAnimationSpec(GlassTheme.reducedMotion),
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
                color = if (isMenuOpen || isHovered) glassColors.onGlass else glassColors.onGlassSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            Icon(
                painter = AppIcons.ChevronDownPainterResource(),
                contentDescription = "Dropdown",
                modifier = Modifier.size(IconSize.Small),
                tint = if (isMenuOpen || isHovered) glassColors.onGlass else glassColors.onGlassMuted,
            )
        }
    }
}
