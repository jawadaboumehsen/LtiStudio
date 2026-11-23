/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.core.ui.command

import org.ide.lti.core.domain.command.CommandCategory
import org.ide.lti.core.domain.command.CommandPaletteRegistry
import org.ide.lti.core.domain.command.StudioCommand

/**
 * Pre-configured registry factory providing standard intent-driven IDE commands
 * for navigation, firmware stages, device management, and intelligent diagnostics.
 */
object DefaultStudioCommands {

    fun createDefaultRegistry(
        onNavigateSettings: () -> Unit = {},
        onToggleTerminal: () -> Unit = {},
        onToggleExplorer: () -> Unit = {},
        onToggleAiAssistant: () -> Unit = {},
        onOpenCopilot: () -> Unit = {},
        onOpenSuperBudgeter: () -> Unit = {},
        onOpenDiffViewer: () -> Unit = {},
        onOpenPartitionExplorer: () -> Unit = {},
        onOpenMacroStudio: () -> Unit = {},
    ): CommandPaletteRegistry {
        val registry = CommandPaletteRegistry()
        registry.registerAll(
            listOf(
                // Preferences & Navigation
                StudioCommand(
                    id = "nav.settings",
                    title = "Open Settings",
                    description = "Appearance, layout, and performance preferences",
                    category = CommandCategory.PREFERENCES,
                    shortcut = "Ctrl+,",
                    keywords = listOf("settings", "preferences", "theme", "appearance", "dark mode"),
                    action = onNavigateSettings,
                ),
                StudioCommand(
                    id = "view.terminal",
                    title = "Toggle Integrated Terminal",
                    description = "Show or hide persistent workspace terminal",
                    category = CommandCategory.NAVIGATION,
                    shortcut = "Ctrl+`",
                    keywords = listOf("terminal", "console", "shell", "bash", "cli"),
                    action = onToggleTerminal,
                ),
                StudioCommand(
                    id = "view.explorer",
                    title = "Toggle Project Explorer",
                    description = "Show or hide file tree and ROM assets",
                    category = CommandCategory.NAVIGATION,
                    shortcut = "Ctrl+B",
                    keywords = listOf("explorer", "files", "tree", "sidebar"),
                    action = onToggleExplorer,
                ),
                StudioCommand(
                    id = "view.ai",
                    title = "Toggle AI Assistant",
                    description = "Open AI chat and firmware guidance",
                    category = CommandCategory.NAVIGATION,
                    shortcut = "Ctrl+Shift+A",
                    keywords = listOf("ai", "assistant", "chat", "copilot"),
                    action = onToggleAiAssistant,
                ),

                // Diagnostics
                StudioCommand(
                    id = "diag.copilot",
                    title = "Run Bootloop & Crash Copilot",
                    description = "Analyze Logcat, Dmesg, and Tombstones for bootloop causes",
                    category = CommandCategory.DIAGNOSTICS,
                    keywords = listOf("bootloop", "crash", "logcat", "dmesg", "tombstone", "selinux", "sigsegv"),
                    action = onOpenCopilot,
                ),
                StudioCommand(
                    id = "diag.super.budget",
                    title = "Inspect Dynamic Partitions Capacity (super.img)",
                    description = "Calculate AOSP lpmake block allocations and storage headroom",
                    category = CommandCategory.DIAGNOSTICS,
                    keywords = listOf("super", "partitions", "dynamic", "erofs", "ext4", "lpmake", "budget"),
                    action = onOpenSuperBudgeter,
                ),
                StudioCommand(
                    id = "diag.partition.explorer",
                    title = "Browse Partition Image Filesystem (EROFS / EXT4)",
                    description = "Explore directory trees, permissions, and SELinux contexts without loop-mounting",
                    category = CommandCategory.DIAGNOSTICS,
                    keywords = listOf("partition", "explorer", "erofs", "ext4", "fs_config", "files", "selinux"),
                    action = onOpenPartitionExplorer,
                ),
                StudioCommand(
                    id = "diag.config.diff",
                    title = "Inspect Property & Config Diff (build.prop)",
                    description = "Compare properties, detect security anomalies, and manage pre-flash snapshots",
                    category = CommandCategory.DIAGNOSTICS,
                    keywords = listOf("diff", "properties", "build.prop", "system.prop", "rollback", "snapshot"),
                    action = onOpenDiffViewer,
                ),
                StudioCommand(
                    id = "stage.macro.studio",
                    title = "Open 1-Click Macro Pipeline Studio",
                    description = "Run multi-stage automated build & flash recipes with retry mechanics",
                    category = CommandCategory.STAGE_ACTIONS,
                    keywords = listOf("macro", "pipeline", "studio", "recipe", "fastbootd", "flash", "rebuild"),
                    action = onOpenMacroStudio,
                ),

                // Stage Actions
                StudioCommand(
                    id = "stage.extract",
                    title = "Extract Firmware Partitions",
                    description = "Unpack payload.bin, super.img, or sparsechunk archives",
                    category = CommandCategory.STAGE_ACTIONS,
                    keywords = listOf("extract", "payload", "unpack", "sparse", "img"),
                    action = {},
                ),
                StudioCommand(
                    id = "stage.patch.root",
                    title = "Inject Root Patch (KernelSU / Magisk)",
                    description = "Patch boot.img with KernelSU GKI or Magisk ramdisk",
                    category = CommandCategory.STAGE_ACTIONS,
                    keywords = listOf("root", "kernelsu", "magisk", "boot", "patch"),
                    action = {},
                ),
                StudioCommand(
                    id = "stage.assemble.super",
                    title = "Assemble & Rebuild super.img",
                    description = "Rebuild dynamic partitions container with lpmake",
                    category = CommandCategory.STAGE_ACTIONS,
                    keywords = listOf("assemble", "super", "lpmake", "build"),
                    action = {},
                ),

                // Device Operations
                StudioCommand(
                    id = "device.reboot.bootloader",
                    title = "Reboot Device to Bootloader (Fastboot)",
                    description = "Send adb reboot bootloader to connected target",
                    category = CommandCategory.DEVICE_OPERATIONS,
                    keywords = listOf("reboot", "bootloader", "fastboot", "adb"),
                    action = {},
                ),
                StudioCommand(
                    id = "device.reboot.recovery",
                    title = "Reboot Device to Recovery",
                    description = "Send adb reboot recovery to connected target",
                    category = CommandCategory.DEVICE_OPERATIONS,
                    keywords = listOf("reboot", "recovery", "twrp", "orangefox"),
                    action = {},
                ),
            ),
        )
        return registry
    }
}
