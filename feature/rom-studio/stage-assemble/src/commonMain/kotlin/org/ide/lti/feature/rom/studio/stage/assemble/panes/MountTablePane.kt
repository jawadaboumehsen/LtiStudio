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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.IdeTable
import org.ide.lti.core.designsystem.component.display.IdeTableHeader
import org.ide.lti.core.designsystem.component.display.IdeTableHeaderCell
import org.ide.lti.core.designsystem.component.display.IdeTableRow
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.MountTable
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.stage.assemble.BootPaneState

/**
 * The mount tables (`fstab`) the phone reads: the work tree's own (encryption and verity flags, set per build),
 * the first-stage one inside vendor_boot (changed by a vendor_boot override), and the one in the device trees.
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
    onReadMountTables: () -> Unit = {},
    onRemoveFirstStageFlag: (String, String) -> Unit = { _, _ -> },
) {
    LaunchedEffect(Unit) { onReadDeviceTree() }
    Column(
        modifier = modifier.fillMaxWidth().testTag("MountTablePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Section("Work-tree mount tables", "vendor/etc/fstab.* in work/, changed by every build.") {
            SettingRow(
                title = "Turn off data encryption",
                text = "Removes fileencryption, metadata_encryption, keydirectory and forceencrypt from the /data " +
                    "line. The build stops if no fstab mounts /data.",
            ) {
                GlassToggle(
                    checked = settings.disableDataEncryption,
                    onCheckedChange = { onChange(settings.copy(disableDataEncryption = it)) },
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("DisableDataEncryptionToggle"),
                )
            }
            if (settings.disableDataEncryption) {
                GlassBanner(
                    severity = GlassBannerSeverity.WARNING,
                    title = "Format data when you first flash this ROM",
                    description = "The phone cannot read what the old encryption wrote and will not boot until data " +
                        "is formatted in recovery. Afterwards your files are not protected by the screen lock.",
                )
            }
            SettingRow(
                title = "Remove AVB and verity flags",
                text = "Removes avb, avb_keys, verify and verifyatboot from every line, so no partition is mounted " +
                    "with dm-verity. Remove them from vendor_boot too (below).",
            ) {
                GlassToggle(
                    checked = settings.removeIntegrityFlags,
                    onCheckedChange = { onChange(settings.copy(removeIntegrityFlags = it)) },
                    activeColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("RemoveIntegrityFlagsToggle"),
                )
            }
        }
        Section(
            "First-stage mount table",
            "Inside vendor_boot; the dynamic partitions mount from it. Each action saves vendor_boot as an override.",
        ) {
            SettingRow(
                title = "AVB and verity flags",
                text = "Removes them from every line, as the setting above does for the work tree.",
            ) {
                GlassButton(
                    onClick = onRemoveIntegrityFlags,
                    enabled = boot.busy == null,
                    modifier = Modifier.testTag("RemoveIntegrityFlagsButton"),
                ) { Text(text = "Remove from vendor_boot", fontSize = FontSize.BodySmall) }
            }
            SettingRow(
                title = "Partitions built as ext4 or F2FS",
                text =
                "Adds an EROFS, an ext4 and an F2FS line for every dynamic partition (stock lines stay first). " +
                    "Without them the build stops when a partition is built as another filesystem.",
            ) {
                GlassButton(
                    onClick = onAddMountLines,
                    enabled = boot.busy == null,
                    modifier = Modifier.testTag("AddMountLinesButton"),
                ) { Text(text = "Add mount lines", fontSize = FontSize.BodySmall) }
            }
            boot.busy?.let { Muted("$it…") }
            boot.message?.let { GlassBanner(severity = GlassBannerSeverity.INFO, title = it) }
            boot.refusal?.let { RefusalView(refusal = it) }
        }
        Section(
            "Mount tables, line by line",
            "Each mount point and filesystem with its flags. A verity or encryption flag can be removed on its own: " +
                "from vendor_boot at once (saved as an override), from the work tree in every build.",
        ) {
            MountTableRows(
                tables = boot.mountTables,
                settings = settings,
                onChange = onChange,
                idle = boot.busy == null,
                onRead = onReadMountTables,
                onRemoveFirstStage = onRemoveFirstStageFlag,
            )
        }
        DeviceTreeMounts(boot.dtMounts, boot.dtUnread, onReadDeviceTree)
    }
}

/**
 * The device tree's fstab: `firmware/android/fstab` in the boot images' dtb and the dtbo overlays. First-stage init
 * reads it only when no ramdisk carries an fstab; it cannot be edited here.
 */
@Composable
private fun DeviceTreeMounts(tree: MountTable.DeviceTree?, unread: String?, onRead: () -> Unit) {
    val (badge, severity, note) = when {
        unread != null -> Triple("Not read", IdeStatusSeverity.Warning, unread)

        tree == null -> Triple(
            "Reading",
            IdeStatusSeverity.Running,
            "Reading boot, vendor_boot and dtbo in work/kernel…",
        )

        tree.mounts.isEmpty() -> Triple(
            "None",
            IdeStatusSeverity.Neutral,
            "No device tree declares a fstab: the phone mounts its partitions from the first-stage fstab.",
        )

        tree.inUse -> Triple(
            "In use",
            IdeStatusSeverity.Warning,
            "The ramdisks carry no fstab, so the phone mounts these from the device tree. A partition built as " +
                "another filesystem than its line here cannot be mounted.",
        )

        else -> Triple(
            "Not used",
            IdeStatusSeverity.Neutral,
            "A ramdisk carries a first-stage fstab, which first-stage init reads instead.",
        )
    }
    Section("Device-tree mount table", note, action = {
        IdeStatusBadge(badge, severity)
        GlassButton(
            onClick = onRead,
            variant = GlassButtonVariant.Secondary,
            modifier = Modifier.testTag("ReadDeviceTreeButton"),
        ) {
            Text(text = "Read again", fontSize = FontSize.Micro)
        }
    }, tag = "DeviceTreeMountsNote") {
        tree?.mounts?.takeIf { it.isNotEmpty() }?.let { mounts ->
            IdeTable {
                IdeTableHeader { DT_COLUMNS.forEach { (t, w) -> IdeTableHeaderCell(t, Modifier.weight(w)) } }
                mounts.forEach { m ->
                    IdeTableRow(modifier = Modifier.alpha(if (m.disabled) AlphaTokens.Disabled else 1f)) {
                        listOf(m.mountPoint, m.type, m.device, "${m.mountFlags} ${m.fsMgrFlags}".trim(), m.source)
                            .forEachIndexed { i, v -> Mono(v, Modifier.weight(DT_COLUMNS[i].second)) }
                    }
                }
            }
        }
    }
}

/** A titled card; [action] sits on the title's line (a status, a button). */
@Composable
private fun Section(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Muted(subtitle, if (tag != null) Modifier.testTag(tag) else Modifier)
            }
            action?.invoke()
        }
        content()
    }
}

/** A setting or action: what it is and does on the left, its control on the right. */
@Composable
private fun SettingRow(title: String, text: String, control: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Muted(text)
        }
        control()
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
        modifier = modifier,
    )
}

@Composable
private fun Mono(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
        maxLines = 1,
        modifier = modifier,
    )
}

private val DT_COLUMNS = listOf(
    "Mount point" to 1f,
    "Type" to 0.6f,
    "Device" to 1.2f,
    "Flags" to 2f,
    "Source" to 1f,
)
