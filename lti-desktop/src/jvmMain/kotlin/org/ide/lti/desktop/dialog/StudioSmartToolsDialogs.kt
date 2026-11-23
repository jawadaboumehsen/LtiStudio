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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.domain.command.CommandPaletteRegistry
import org.ide.lti.core.domain.copilot.BootloopCopilotAnalyzer
import org.ide.lti.core.domain.copilot.CrashSource
import org.ide.lti.core.domain.diff.ConfigDiffEngine
import org.ide.lti.core.domain.partition.CompressionAlgorithm
import org.ide.lti.core.domain.partition.FilesystemType
import org.ide.lti.core.domain.partition.PartitionSpec
import org.ide.lti.core.domain.partition.SuperPartitionCalculator
import org.ide.lti.core.domain.partition.explorer.PartitionTreeParser
import org.ide.lti.core.domain.pipeline.macro.MacroPipelineRunner
import org.ide.lti.core.domain.pipeline.macro.MacroStepStatus
import org.ide.lti.core.ui.command.CommandPaletteModal
import org.ide.lti.core.ui.copilot.BootloopCopilotSheet
import org.ide.lti.core.ui.diff.PropertyDiffViewer
import org.ide.lti.core.ui.partition.PartitionExplorerView
import org.ide.lti.core.ui.partition.SuperPartitionBudgetMeter
import org.ide.lti.core.ui.pipeline.MacroPipelineStudioSheet

private const val SUPER_CAPACITY_BYTES = 9_126_805_504L
private const val SYSTEM_SIZE_BYTES = 3_200_000_000L
private const val VENDOR_SIZE_BYTES = 1_100_000_000L
private const val PRODUCT_SIZE_BYTES = 2_400_000_000L
private const val SYSTEM_EXT_SIZE_BYTES = 650_000_000L
private const val ODM_SIZE_BYTES = 250_000_000L

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
    isMacroStudioOpen: Boolean,
    onDismissMacroStudio: () -> Unit,
) {
    if (isCommandPaletteOpen) {
        CommandPaletteModal(
            isOpen = isCommandPaletteOpen,
            registry = commandRegistry,
            onDismiss = onDismissCommandPalette,
        )
    }

    if (isCopilotOpen) {
        CopilotDialog(onDismiss = onDismissCopilot)
    }

    if (isSuperBudgeterOpen) {
        SuperBudgeterDialog(onDismiss = onDismissSuperBudgeter)
    }

    if (isDiffViewerOpen) {
        DiffViewerDialog(onDismiss = onDismissDiffViewer)
    }

    if (isPartitionExplorerOpen) {
        PartitionExplorerDialog(onDismiss = onDismissPartitionExplorer)
    }

    if (isMacroStudioOpen) {
        MacroStudioDialog(onDismiss = onDismissMacroStudio)
    }
}

@Composable
private fun CopilotDialog(onDismiss: () -> Unit) {
    val sampleLogs = remember {
        listOf(
            "03-25 12:00:02.345 1000 1000 F libc : Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x0 in tid 1000 (system_server)",
            "03-25 12:00:02.350 500 500 I DEBUG : pid: 1000, tid: 1000, name: system_server >>> system_server <<<",
            "type=1400 audit(1710000000.123:45): avc: denied { read open } for pid=1234 comm=\"cameraserver\" path=\"/dev/v4l-subdev0\" scontext=u:r:cameraserver:s0 tcontext=u:object_r:vendor_video_device:s0 tclass=chr_file permissive=0",
            "03-25 12:05:00.000 1000 1050 W Watchdog: *** WATCHDOG KILLING SYSTEM PROCESS: Blocked in handler on ActivityManager (ActivityManager)",
        )
    }
    val diagnoses = remember(sampleLogs) {
        BootloopCopilotAnalyzer.analyze(
            CrashSource.LOGCAT,
            sampleLogs,
        )
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Bootloop & Crash Copilot",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        BootloopCopilotSheet(diagnoses = diagnoses)
    }
}

@Composable
private fun SuperBudgeterDialog(onDismiss: () -> Unit) {
    val samplePartitions = remember {
        listOf(
            PartitionSpec("system", SYSTEM_SIZE_BYTES, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("vendor", VENDOR_SIZE_BYTES, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("product", PRODUCT_SIZE_BYTES, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("system_ext", SYSTEM_EXT_SIZE_BYTES, FilesystemType.EROFS, CompressionAlgorithm.LZ4HC),
            PartitionSpec("odm", ODM_SIZE_BYTES, FilesystemType.EXT4),
        )
    }
    val forecast = remember(samplePartitions) {
        SuperPartitionCalculator().calculateBudget(
            superCapacityBytes = SUPER_CAPACITY_BYTES,
            partitions = samplePartitions,
        )
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Dynamic Partitions (super.img) Capacity Forecast",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        SuperPartitionBudgetMeter(forecast = forecast)
    }
}

@Composable
private fun DiffViewerDialog(onDismiss: () -> Unit) {
    val originalProp = remember {
        "ro.product.model=Pixel 8\nro.build.type=user\nro.secure=1\nro.crypto.state=encrypted\n"
    }
    val modifiedProp = remember {
        "ro.product.model=Pixel 8 Pro\nro.build.type=userdebug\nro.secure=0\nro.crypto.state=unencrypted\npersist.sys.phh.root=1\n"
    }
    val diffResult = remember(originalProp, modifiedProp) {
        ConfigDiffEngine.computeDiff(originalProp, modifiedProp)
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Property & Config Diff Inspector",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        PropertyDiffViewer(
            diffResult = diffResult,
            snapshotName = "Pre-flash Snapshot (Slot B)",
            onRollback = onDismiss,
        )
    }
}

@Composable
private fun PartitionExplorerDialog(onDismiss: () -> Unit) {
    val sampleTreeLines = remember {
        listOf(
            "system/bin/sh 0755 u:object_r:shell_exec:s0 250000",
            "system/bin/app_process64 0755 u:object_r:zygote_exec:s0 148000",
            "system/etc/hosts 0644 u:object_r:system_file:s0 120",
            "system/framework/framework.jar 0644 u:object_r:system_file:s0 34000000",
            "system/bin/su 0755 u:object_r:su_exec:s0 50000 /system/xbin/su",
        )
    }
    val metadata = remember(sampleTreeLines) {
        PartitionTreeParser.parseManifest(
            "system",
            "EROFS",
            sampleTreeLines,
        )
    }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Firmware Partition Explorer",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        PartitionExplorerView(
            metadata = metadata,
            onExtractNode = {},
        )
    }
}

@Composable
private fun MacroStudioDialog(onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val defaultRecipe = remember {
        MacroPipelineRunner.createDefaultFlashRecipe()
    }
    var recipeState by remember { mutableStateOf(defaultRecipe) }
    var isRunningMacro by remember { mutableStateOf(false) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Macro Pipeline Studio",
        confirmButton = {
            GlassPrimaryButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    ) {
        MacroPipelineStudioSheet(
            recipe = recipeState,
            isRunning = isRunningMacro,
            onRunRecipe = {
                coroutineScope.launch {
                    isRunningMacro = true
                    val runner = MacroPipelineRunner(recipeState)
                    runner.run()
                    recipeState = runner.currentRecipe.value
                    isRunningMacro = false
                }
            },
            onCancelRecipe = { isRunningMacro = false },
            onRetryStep = { stepId ->
                recipeState = recipeState.copy(
                    steps = recipeState.steps.map {
                        if (it.id == stepId) it.copy(status = MacroStepStatus.PENDING) else it
                    },
                )
            },
        )
    }
}
