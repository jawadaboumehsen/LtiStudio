/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassPasswordField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.BootParts
import org.ide.lti.core.domain.pipeline.toolkit.RootStatus
import org.ide.lti.core.domain.pipeline.toolkit.VbmetaFlag
import org.ide.lti.core.domain.workbench.RiskyFeature
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.toolkit.AcknowledgementDialog
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.api.formatBytes
import org.ide.lti.feature.rom.studio.stage.assemble.BootPaneActions
import org.ide.lti.feature.rom.studio.stage.assemble.BootPaneState

/**
 * Boot-chain workbench (US4, FR-020..FR-026): unpack, edit the ramdisk folder and device tree, repack into the
 * partition's override, vbmeta flags, root. Nothing here is shown as done until the job in WSL finished.
 */
@Composable
public fun BootPreparationPane(
    settings: AssemblySettings,
    target: TargetDevice,
    state: BootPaneState,
    actions: BootPaneActions,
    onChange: (AssemblySettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = state.busy == null
    // Edits made in Explorer are not seen by the app: look each time the pane opens.
    LaunchedEffect(Unit) { actions.checkUnbuilt() }
    Column(
        modifier = modifier.fillMaxWidth().testTag("BootPreparationPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (state.unbuilt.isNotEmpty()) {
            GlassBanner(
                severity = GlassBannerSeverity.WARNING,
                title = "Edited but not repacked: ${state.unbuilt.joinToString { "$it.img" }}. Those edits are not " +
                    "in the next build until you repack.",
                modifier = Modifier.testTag("UnbuiltBootEdits"),
            )
        }
        ImageCard(settings, target, state, actions, idle)
        state.parts?.let { parts ->
            ComponentsCard(parts, actions, idle)
            state.deviceTree?.let { DeviceTreeCard(it, state, actions, idle) }
            RepackCard(state, actions, idle)
        }
        KernelCard(state, actions, idle)
        RootCard(state, actions, idle)
        VbmetaCard(state, actions, idle)
        FooterCard(settings, onChange)
    }

    BootAckDialog(state, actions)
}

/** The acknowledgement a boot action waits for (FR-035); shown by every pane that starts one (Mount table too). */
@Composable
public fun BootAckDialog(state: BootPaneState, actions: BootPaneActions) {
    state.pendingAck?.let { feature ->
        AcknowledgementDialog(
            featureId = feature,
            warningMessage = ACK_TEXT[feature] ?: "This changes what the device boots.",
            onAcknowledge = actions.acknowledge,
            onDismiss = actions.dismissAck,
        )
    }
}

@Composable
private fun ImageCard(
    settings: AssemblySettings,
    target: TargetDevice,
    state: BootPaneState,
    actions: BootPaneActions,
    idle: Boolean,
) {
    Card("Boot-chain images") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            target.bootPartitions.forEach { partition ->
                GlassChip(
                    label = "$partition.img" + if (partition in settings.kernelOverrides) " (edited)" else "",
                    selected = state.image == partition,
                    onClick = { actions.selectImage(partition) },
                )
            }
        }
        Hint(
            "Unpacks the image the next build would use " +
                "(an earlier edit, else the extracted one).",
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(onClick = actions.unpack, enabled = idle, modifier = Modifier.testTag("UnpackBootButton")) {
                Text("Unpack ${state.image}.img", fontSize = FontSize.BodySmall)
            }
            if (state.image in settings.kernelOverrides) {
                GlassButton(
                    onClick = { actions.dropOverride(state.image) },
                    enabled = idle,
                    variant = GlassButtonVariant.Secondary,
                ) {
                    Text("Use the stock image again", fontSize = FontSize.BodySmall)
                }
            }
        }
        Status(state, actions)
    }
}

@Composable
private fun ComponentsCard(parts: BootParts, actions: BootPaneActions, idle: Boolean) {
    Card("Components (FR-020)") {
        val h = parts.header
        fun part(path: String?, size: Long) = path?.let { "$it (${formatBytes(size)})" } ?: "none in this image"
        val os = "${h.osVersion} ${h.osPatchLevel}".trim().ifEmpty { "-" }
        Mono("Header v${h.headerVersion}, page ${h.pageSize}, OS $os")
        // unpack_bootimg writes an empty kernel file for an image that has none, as init_boot.
        Mono("Kernel: ${part(parts.kernelPath.takeIf { h.kernelSize > 0 }, h.kernelSize)}")
        Mono("Ramdisk: ${part(parts.ramdiskFolderPath, h.ramdiskSize)}")
        Mono("Command line: ${h.cmdline.ifBlank { "-" }}")
        val footer = parts.footer?.let { "${it.partitionName} ${it.algorithmName}, ${formatBytes(it.imageSize)}" }
        Mono("AVB footer: ${footer ?: "none"}")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(onClick = actions.openRamdisk, enabled = idle && parts.hasRamdisk) {
                Text("Open the ramdisk folder", fontSize = FontSize.BodySmall)
            }
            GlassButton(onClick = actions.removeVerityKeys, enabled = idle && parts.hasRamdisk) {
                Text("Remove dm-verity keys", fontSize = FontSize.BodySmall)
            }
        }
    }
}

@Composable
private fun DeviceTreeCard(dts: String, state: BootPaneState, actions: BootPaneActions, idle: Boolean) {
    Card("Device tree (FR-022)") {
        GlassCodeEditor(
            content = dts,
            onContentChange = actions.editDeviceTree,
            modifier = Modifier.fillMaxWidth().height(ComponentSize.MaxPanelHeight),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(onClick = actions.previewDeviceTree, enabled = idle) {
                Text("Show changes", fontSize = FontSize.BodySmall)
            }
            GlassButton(onClick = actions.saveDeviceTree, enabled = idle && state.deviceTreeDiff?.hasChanges == true) {
                Text("Save", fontSize = FontSize.BodySmall)
            }
        }
        state.deviceTreeDiff?.let { diff ->
            if (diff.hasChanges) diff.differences.take(MAX_LINES).forEach { Mono(it) } else Hint("No changes.")
        }
    }
}

@Composable
private fun RepackCard(state: BootPaneState, actions: BootPaneActions, idle: Boolean) {
    Card("Repack into the build (FR-021, FR-024)") {
        Hint(
            "The ramdisk folder is packed with mkbootfs and read back; any difference stops the repack. " +
                "The result becomes this partition's override, which Assemble copies into the build.",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassToggle(checked = state.signWithWorkspaceKey, onCheckedChange = actions.setSign)
            Text(
                "Sign the AVB footer with the AVB key set in Build & Sign",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        GlassButton(onClick = actions.repack, enabled = idle, variant = GlassButtonVariant.Primary) {
            Text("Repack and use in the build", fontSize = FontSize.BodySmall)
        }
    }
}

/** The boot kernel's version and what it reads: an image built with anything else would not mount or boot. */
@Composable
private fun KernelCard(state: BootPaneState, actions: BootPaneActions, idle: Boolean) {
    Card("Kernel") {
        val kernel = state.kernel
        when {
            kernel != null -> {
                Mono("Version ${kernel.version ?: "unknown"}")
                Mono("EROFS can use: ${kernel.erofsCompressions?.joinToString().orEmpty().ifEmpty { "nothing" }}")
                Mono("Ramdisks can use: ${kernel.ramdiskFormats?.joinToString().orEmpty().ifEmpty { "nothing" }}")
                if (kernel.config != null) {
                    val dm = if (kernel.dmVerityFec == true) "yes, with error correction" else yesNo(kernel.dmVerity)
                    Mono("dm-verity: $dm · fs-verity: ${yesNo(kernel.fsVerity)}")
                }
                if (kernel.config == null) Hint("This kernel carries no config, so what it reads is not known.")
            }

            state.kernelRead -> Hint("boot.img carries no kernel.")

            else -> Hint("Reads the kernel of the boot image the next build uses, and its embedded config.")
        }
        GlassButton(onClick = actions.readKernel, enabled = idle, modifier = Modifier.testTag("ReadKernelButton")) {
            Text(if (state.kernelRead) "Read again" else "Read the kernel", fontSize = FontSize.BodySmall)
        }
    }
}

@Composable
private fun FooterCard(settings: AssemblySettings, onChange: (AssemblySettings) -> Unit) {
    Card("Stock AVB footer") {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            FOOTER_POLICIES.forEach { (value, label) ->
                GlassChip(
                    label = label,
                    selected = settings.bootFooterPolicy.equals(value, ignoreCase = true),
                    onClick = { onChange(settings.copy(bootFooterPolicy = value)) },
                )
            }
        }
        Hint(
            if (settings.bootFooterPolicy.equals("erase", ignoreCase = true)) {
                "The vendor footer is removed (avbtool erase_footer); Build & Sign adds a new one signed " +
                    "with the workspace AVB key. Recommended."
            } else {
                "The vendor footer is kept as extracted. Build & Sign still re-signs the images."
            },
        )
    }
}

@Composable
private fun RootCard(state: BootPaneState, actions: BootPaneActions, idle: Boolean) {
    var superKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    Card("Root (FR-026)") {
        Mono(
            when (val root = state.rootStatus) {
                is RootStatus.AlreadyPatched -> "Already patched with ${root.patcher} (${root.details})"
                RootStatus.NotPatched -> if (state.parts == null) "Unpack the image to check." else "Not patched"
            },
        )
        Hint("APatch patches the unpacked kernel with KernelPatch; repack afterwards to keep it.")
        GlassPasswordField(
            label = "APatch superkey (8 characters or more)",
            value = superKey,
            showPassword = showKey,
            showPasswordChange = { showKey = it },
            onValueChange = { superKey = it },
        )
        GlassButton(
            onClick = { actions.patchApatch(superKey) },
            enabled = idle && state.parts?.kernelPath != null && superKey.length >= MIN_SUPERKEY &&
                state.rootStatus == RootStatus.NotPatched,
        ) {
            Text("Patch with APatch", fontSize = FontSize.BodySmall)
        }
        Hint(
            "Magisk: no Linux magiskboot is published, so patch ${state.image}.img in the Magisk app on the phone " +
                "and import the patched file here; it becomes the override.",
        )
        GlassButton(onClick = actions.importPatched, enabled = idle) {
            Text("Import a patched ${state.image}.img", fontSize = FontSize.BodySmall)
        }
    }
}

@Composable
private fun VbmetaCard(state: BootPaneState, actions: BootPaneActions, idle: Boolean) {
    Card("vbmeta flags (FR-023)") {
        Hint("vbmeta is never packaged; the edited copy is written to out/toolkit/ for you to flash.")
        val flags = state.vbmetaEdited
        if (flags == null) {
            GlassButton(onClick = actions.loadVbmeta, enabled = idle) {
                Text("Read vbmeta.img", fontSize = FontSize.BodySmall)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                VbmetaFlag.entries.forEach { flag ->
                    GlassChip(
                        label = flag.flagName,
                        selected = (flags and flag.bitMask) != 0,
                        onClick = { actions.toggleVbmeta(flag) },
                    )
                }
            }
            state.vbmetaDiff?.takeIf { it.hasChanges }?.describe()?.forEach { Mono(it) }
            GlassButton(onClick = actions.writeVbmeta, enabled = idle && state.vbmetaDiff?.hasChanges == true) {
                Text("Write the edited vbmeta", fontSize = FontSize.BodySmall)
            }
        }
    }
}

@Composable
private fun Status(state: BootPaneState, actions: BootPaneActions) {
    // The pane is long: an outcome of a button further down must come into view here. Not while busy: a button
    // that shows its result in its own card would be left behind.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.refusal, state.message) {
        if (state.refusal != null || state.message != null) requester.bringIntoView()
    }
    Column(
        modifier = Modifier.bringIntoViewRequester(requester),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        state.busy?.let { busy ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Mono("$busy…")
                GlassButton(onClick = actions.cancel, variant = GlassButtonVariant.Secondary) {
                    Text("Cancel", fontSize = FontSize.BodySmall)
                }
            }
        }
        state.refusal?.let { RefusalView(refusal = it, actionLabel = "Dismiss", onAction = actions.dismiss) }
        state.message?.let {
            GlassBanner(severity = GlassBannerSeverity.INFO, title = it, onDismiss = actions.dismiss)
        }
    }
}

private val FOOTER_POLICIES = listOf("erase" to "Erase (recommended)", "preserve" to "Preserve")
private const val MAX_LINES = 50
private const val MIN_SUPERKEY = 8
private val ACK_TEXT = mapOf(
    RiskyFeature.ROOT_PATCH to "Root patching changes the boot image the device starts from. A wrong image can " +
        "leave the device unable to boot until a stock image is flashed.",
    RiskyFeature.KERNEL_OVERRIDE to "The repacked image replaces the stock one in every build of this workspace " +
        "until you switch back to the stock image.",
    RiskyFeature.VBMETA_FLAGS to "Disabling verification flags turns off verified boot checks on the device.",
)

private fun yesNo(on: Boolean?): String = if (on == true) "yes" else "no"

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}
