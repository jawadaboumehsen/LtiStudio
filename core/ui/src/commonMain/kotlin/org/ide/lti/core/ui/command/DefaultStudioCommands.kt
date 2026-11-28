/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
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
        onToggleSessionDrawer: () -> Unit = {},
        onPopOutCopilot: () -> Unit = onOpenCopilot,
        onPopOutPartitionExplorer: () -> Unit = onOpenPartitionExplorer,
        onPopOutDiffInspector: () -> Unit = onOpenDiffViewer,
    ): CommandPaletteRegistry = CommandPaletteRegistry().apply {
        registerAll(
            navigationCommands(
                onNavigateSettings = onNavigateSettings,
                onToggleTerminal = onToggleTerminal,
                onToggleExplorer = onToggleExplorer,
                onToggleAiAssistant = onToggleAiAssistant,
                onToggleSessionDrawer = onToggleSessionDrawer,
                onPopOutCopilot = onPopOutCopilot,
                onPopOutPartitionExplorer = onPopOutPartitionExplorer,
                onPopOutDiffInspector = onPopOutDiffInspector,
            ),
        )
        registerAll(
            diagnosticAndStageCommands(
                onOpenCopilot = onOpenCopilot,
                onOpenSuperBudgeter = onOpenSuperBudgeter,
                onOpenDiffViewer = onOpenDiffViewer,
                onOpenPartitionExplorer = onOpenPartitionExplorer,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun navigationCommands(
        onNavigateSettings: () -> Unit,
        onToggleTerminal: () -> Unit,
        onToggleExplorer: () -> Unit,
        onToggleAiAssistant: () -> Unit,
        onToggleSessionDrawer: () -> Unit,
        onPopOutCopilot: () -> Unit,
        onPopOutPartitionExplorer: () -> Unit,
        onPopOutDiffInspector: () -> Unit,
    ): List<StudioCommand> = listOf(
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
        StudioCommand(
            id = "view.background.sessions",
            title = "Toggle Background Sessions & Notifications Tray",
            description = "Inspect active extractions, repacks, builds, and device reconnect alerts",
            category = CommandCategory.NAVIGATION,
            keywords = listOf("background", "tasks", "jobs", "notifications", "tray", "drawer"),
            action = onToggleSessionDrawer,
        ),
        StudioCommand(
            id = "window.popout.copilot",
            title = "Pop Out Live Logcat & Copilot Window",
            description = "Detach Logcat + Bootloop Copilot onto a secondary monitor window",
            category = CommandCategory.NAVIGATION,
            keywords = listOf("window", "detach", "popout", "monitor", "logcat", "copilot"),
            action = onPopOutCopilot,
        ),
        StudioCommand(
            id = "window.popout.partition",
            title = "Pop Out Partition Explorer Window",
            description = "Detach EROFS/EXT4 Partition Explorer onto its own monitor window",
            category = CommandCategory.NAVIGATION,
            keywords = listOf("window", "detach", "popout", "partition", "erofs", "ext4"),
            action = onPopOutPartitionExplorer,
        ),
        StudioCommand(
            id = "window.popout.diff",
            title = "Pop Out Diff & Provenance Inspector Window",
            description = "Detach Structural & Property Diff Inspector onto its own monitor window",
            category = CommandCategory.NAVIGATION,
            keywords = listOf("window", "detach", "popout", "diff", "sbom", "provenance"),
            action = onPopOutDiffInspector,
        ),
    )

    private fun diagnosticAndStageCommands(
        onOpenCopilot: () -> Unit,
        onOpenSuperBudgeter: () -> Unit,
        onOpenDiffViewer: () -> Unit,
        onOpenPartitionExplorer: () -> Unit,
    ): List<StudioCommand> = listOf(
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
    )
}
