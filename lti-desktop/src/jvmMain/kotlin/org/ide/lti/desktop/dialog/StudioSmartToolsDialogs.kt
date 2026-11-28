/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.desktop.dialog

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.domain.command.CommandPaletteRegistry
import org.ide.lti.core.ui.command.CommandPaletteModal
import org.ide.lti.desktop.window.LiveCopilotPanelBody
import org.ide.lti.desktop.window.LiveDiffInspectorBody
import org.ide.lti.desktop.window.LivePartitionExplorerBody
import org.ide.lti.desktop.window.LiveSuperLayoutBody


/**
 * Encapsulated overlays for LtiStudio cognitive copilot and developer tooling dialogs.
 */
@Composable
fun StudioSmartToolsDialogs(
    isCommandPaletteOpen: Boolean,
    onDismissCommandPalette: () -> Unit,
    commandRegistry: CommandPaletteRegistry,
    isCopilotOpen: Boolean,
    onDismissCopilot: () -> Unit,
    isSuperBudgeterOpen: Boolean,
    onDismissSuperBudgeter: () -> Unit,
    isDiffViewerOpen: Boolean,
    onDismissDiffViewer: () -> Unit,
    isPartitionExplorerOpen: Boolean,
    onDismissPartitionExplorer: () -> Unit,
    onPopOutCopilot: (() -> Unit)? = null,
    onPopOutDiffViewer: (() -> Unit)? = null,
    onPopOutPartitionExplorer: (() -> Unit)? = null,
) {
    if (isCommandPaletteOpen) {
        CommandPaletteModal(
            isOpen = isCommandPaletteOpen,
            registry = commandRegistry,
            onDismiss = onDismissCommandPalette,
        )
    }

    if (isCopilotOpen) {
        CopilotDialog(
            onDismiss = onDismissCopilot,
            onPopOut = onPopOutCopilot,
        )
    }

    if (isSuperBudgeterOpen) {
        SuperBudgeterDialog(onDismiss = onDismissSuperBudgeter)
    }

    if (isDiffViewerOpen) {
        DiffViewerDialog(
            onDismiss = onDismissDiffViewer,
            onPopOut = onPopOutDiffViewer,
        )
    }

    if (isPartitionExplorerOpen) {
        PartitionExplorerDialog(
            onDismiss = onDismissPartitionExplorer,
            onPopOut = onPopOutPartitionExplorer,
        )
    }
}

@Composable
private fun CopilotDialog(
    onDismiss: () -> Unit,
    onPopOut: (() -> Unit)?,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Bootloop & Crash Copilot",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = onPopOut?.let { pop ->
            {
                GlassTextButton(
                    onClick = {
                        onDismiss()
                        pop()
                    },
                ) {
                    Text("Pop Out Window")
                }
            }
        },
    ) {
        LiveCopilotPanelBody()
    }
}

@Composable
private fun SuperBudgeterDialog(onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Dynamic Partitions (super.img) Layout",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        LiveSuperLayoutBody()
    }
}

@Composable
private fun DiffViewerDialog(
    onDismiss: () -> Unit,
    onPopOut: (() -> Unit)?,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Property, Timeline & Provenance Diff Inspector",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = onPopOut?.let { pop ->
            {
                GlassTextButton(
                    onClick = {
                        onDismiss()
                        pop()
                    },
                ) {
                    Text("Pop Out Window")
                }
            }
        },
    ) {
        LiveDiffInspectorBody()
    }
}

@Composable
private fun PartitionExplorerDialog(
    onDismiss: () -> Unit,
    onPopOut: (() -> Unit)?,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Firmware Partition Explorer",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = onPopOut?.let { pop ->
            {
                GlassTextButton(
                    onClick = {
                        onDismiss()
                        pop()
                    },
                ) {
                    Text("Pop Out Window")
                }
            }
        },
    ) {
        LivePartitionExplorerBody()
    }
}
