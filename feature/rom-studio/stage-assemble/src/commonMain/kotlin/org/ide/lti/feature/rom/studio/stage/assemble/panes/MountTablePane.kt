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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.MountTable
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.feature.rom.studio.stage.assemble.BootPaneState

/**
 * The mount table (`fstab`) of the work tree. Turning off data encryption removes the encryption flags of the
 * `/data` line from every fstab that mounts it.
 */
@Composable
public fun MountTablePane(
    settings: AssemblySettings,
    onChange: (AssemblySettings) -> Unit,
    modifier: Modifier = Modifier,
    boot: BootPaneState = BootPaneState(),
    onAddMountLines: () -> Unit = {},
    onRemoveIntegrityFlags: () -> Unit = {},
    onReadDeviceTree: () -> Unit = {},
) {
    LaunchedEffect(Unit) { onReadDeviceTree() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("MountTablePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Mount table",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(
                checked = settings.disableDataEncryption,
                onCheckedChange = { onChange(settings.copy(disableDataEncryption = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("DisableDataEncryptionToggle"),
            )
            Text(
                text = "Turn off data encryption",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Removes fileencryption, metadata_encryption, keydirectory and the older forceencrypt flags " +
                "from the /data line of every fstab in work/ (vendor/etc/fstab.*). The build stops if no fstab " +
                "mounts /data.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(
                checked = settings.removeIntegrityFlags,
                onCheckedChange = { onChange(settings.copy(removeIntegrityFlags = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("RemoveIntegrityFlagsToggle"),
            )
            Text(
                text = "Remove AVB and verity flags",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Removes avb, avb_keys, verify and verifyatboot from every line of the fstabs in work/, so no " +
                "partition is mounted with dm-verity. The dynamic partitions mount from the first-stage fstab in " +
                "vendor_boot: remove them there too with the button (saved as a vendor_boot override).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        GlassButton(
            onClick = onRemoveIntegrityFlags,
            enabled = boot.busy == null,
            modifier = Modifier.testTag("RemoveIntegrityFlagsButton"),
        ) {
            Text(text = boot.busy ?: "Remove AVB and verity flags from vendor_boot", fontSize = FontSize.BodySmall)
        }
        Text(
            text = "Partitions built as ext4 or F2FS",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        Text(
            text = "The phone mounts each partition with a line of its fstab for that filesystem. Build & Sign adds " +
                "the missing lines to vendor/etc/fstab.* itself; the first-stage fstab is inside vendor_boot, so " +
                "the build stops until it has them. This adds an EROFS, an ext4 and an F2FS line for every dynamic " +
                "partition to it and saves vendor_boot as an override (stock lines stay first).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = ideFontFamily(),
        )
        GlassButton(
            onClick = onAddMountLines,
            enabled = boot.busy == null,
            modifier = Modifier.testTag("AddMountLinesButton"),
        ) {
            Text(text = boot.busy ?: "Add mount lines to vendor_boot", fontSize = FontSize.BodySmall)
        }
        boot.message?.let {
            Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
        }
        boot.refusal?.let {
            Text(
                text = "${it.cause} ${it.nextStep}",
                color = GlassTheme.diagnosticColors.error,
                fontSize = FontSize.BodySmall,
            )
        }
        DeviceTreeMounts(boot.dtMounts, onReadDeviceTree)
        if (settings.disableDataEncryption) {
            Text(
                text = "Format data in recovery when you first flash this ROM: the phone cannot read the data the " +
                    "old encryption wrote and will not boot until it is formatted. Afterwards your files are not " +
                    "protected by the screen lock.",
                color = GlassTheme.diagnosticColors.warning,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

/**
 * The device tree's fstab: `firmware/android/fstab` in the boot images' dtb and the
 * dtbo overlays. First-stage init reads it only when no ramdisk carries an fstab; it cannot be edited here.
 */
@Composable
private fun DeviceTreeMounts(tree: MountTable.DeviceTree?, onRead: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Device-tree mount table",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1f),
        )
        GlassButton(onClick = onRead, modifier = Modifier.testTag("ReadDeviceTreeButton")) {
            Text(text = "Read again", fontSize = FontSize.Micro)
        }
    }
    val note = when {
        tree == null -> "Reading the device trees of boot, vendor_boot and dtbo in work/kernel…"

        tree.mounts.isEmpty() ->
            "None: no device tree of the boot images declares a fstab. The phone mounts its " +
                "partitions from the first-stage fstab in the ramdisk."

        tree.inUse ->
            "The phone mounts these partitions from the device tree: the ramdisks carry no fstab. A " +
                "partition built as another filesystem than its line here cannot be mounted."

        else -> "Present but not used: a ramdisk carries a first-stage fstab, which first-stage init reads instead."
    }
    Text(
        text = note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
        modifier = Modifier.testTag("DeviceTreeMountsNote"),
    )
    tree?.mounts?.forEach { m ->
        Text(
            text = "${m.mountPoint}  ${m.type}  ${m.device}  ${m.mountFlags}  ${m.fsMgrFlags}  (${m.source})" +
                if (m.disabled) "  disabled" else "",
            color = if (m.disabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )
    }
}
