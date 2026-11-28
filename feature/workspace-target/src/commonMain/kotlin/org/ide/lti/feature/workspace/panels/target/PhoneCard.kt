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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.common.utils.formatBytes
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.workbench.FlashPlan
import org.ide.lti.core.domain.workbench.FlashPlanner
import org.ide.lti.core.domain.workbench.PhoneDeviceCheck
import org.ide.lti.core.domain.workbench.PhoneFacts
import org.ide.lti.core.domain.workbench.PhoneMode
import org.ide.lti.core.domain.workbench.PlanRow
import org.ide.lti.core.domain.workbench.PlanVerdict
import org.ide.lti.core.domain.workbench.ProjectImage
import org.ide.lti.core.model.target.TargetDevice

/**
 * The phone attached to WSL: what it says it is, its partitions, and where the ticked images would land with every
 * reason one would be refused. Nothing is written from here; flashing stays the user's.
 */
@Composable
internal fun PhoneCard(
    state: WorkspaceDeviceUiState,
    device: TargetDevice,
    vm: PhoneViewModel,
    onUsePhoneValue: ((TargetDevice) -> TargetDevice) -> Unit = {},
) {
    val phone by vm.state.collectAsStateWithLifecycle()
    val workspaceId = state.workspaceId ?: return
    LaunchedEffect(workspaceId) { vm.showFor(workspaceId) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("PhoneCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Title("Connected phone", Modifier.weight(1f))
            GlassButton(
                onClick = { vm.read(workspaceId) },
                variant = GlassButtonVariant.Secondary,
                loading = phone.busy != null,
                modifier = Modifier.testTag("ReadPhoneButton"),
            ) { Text(phone.busy ?: "Read the phone") }
        }
        phone.error?.let { Note(it, GlassTheme.diagnosticColors.error) }
        if (!phone.read) {
            Note(
                "Asks the phone over adb or fastboot what it is and how it is partitioned. Nothing is written.",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        if (phone.phones.isEmpty()) NoPhone()
        if (phone.phones.isNotEmpty() && phone.phone == null) {
            Note(
                "The selected phone is no longer connected. Choose a phone to continue.",
                GlassTheme.diagnosticColors.warning,
            )
        }
        Phones(phone, vm)
        if (phone.phones.any { it.mode == PhoneMode.NO_PERMISSION }) UsbPermissionHelp(workspaceId)
        phone.phone?.let { Facts(it, workspaceId, vm) }
        phone.phone?.let { MeasuredVsConfigured(it, device, enabled = !state.locked, onUsePhoneValue) }
        Images(phone, vm)
        val picked = phone.images.filter { it.path in phone.picked }
        Plan(FlashPlanner.plan(phone.phone, device, picked))
    }
}

@Composable
private fun NoPhone() {
    Note(
        "No phone found. WSL sees USB only through usbipd-win: in an administrator PowerShell, `usbipd list`, then " +
            "`usbipd bind --busid <id>` once and `usbipd attach --wsl --busid <id>` (again whenever the phone " +
            "reboots into another mode).",
        GlassTheme.diagnosticColors.warning,
    )
}

@Composable
private fun UsbPermissionHelp(workspaceId: String) {
    var expanded by remember(workspaceId) { mutableStateOf(false) }
    Note("WSL detected the phone but denied USB access.", GlassTheme.diagnosticColors.warning)
    GlassButton(onClick = { expanded = !expanded }, variant = GlassButtonVariant.Text) {
        Text(if (expanded) "Hide USB permission steps" else "How to fix USB access in WSL")
    }
    if (!expanded) return
    val textColor = MaterialTheme.colorScheme.onSurface
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.fillMaxWidth()) {
            Text("1. Open the WSL distro Studio uses. Check that its Linux user belongs to plugdev.", color = textColor)
            Code("id -nG")
            Text("If plugdev is missing, run these commands and reopen the WSL session.", color = textColor)
            Code("sudo groupadd -f plugdev\nsudo usermod -aG plugdev \"\$(id -un)\"")
            Text(
                "2. Find the phone's four-digit ID_VENDOR_ID and ID_MODEL_ID. " +
                    "Run lsusb if installed, or use this command.",
                color = textColor,
            )
            Code(
                "for node in /dev/bus/usb/*/*; do echo \"\$node\"; " +
                    "udevadm info --query=property --name=\"\$node\" | grep -E '^ID_(MODEL|MODEL_ID|VENDOR_ID)='; done",
            )
            Text(
                "3. Run the command below to open the rules file. " +
                    "Add this line with your phone's vendor and product IDs.",
                color = textColor,
            )
            Code("sudoedit /etc/udev/rules.d/70-ltirom-phone.rules")
            Code(
                "SUBSYSTEM==\"usb\", ENV{DEVTYPE}==\"usb_device\", ATTR{idVendor}==\"VENDOR\", " +
                    "ATTR{idProduct}==\"PRODUCT\", GROUP=\"plugdev\", MODE=\"0660\"",
            )
            Text(
                "4. Reload the rules and apply them to the phone. Use the same IDs in this command.",
                color = textColor,
            )
            Code(
                "sudo udevadm control --reload-rules\n" +
                    "sudo udevadm trigger --action=add --subsystem-match=usb " +
                    "--attr-match=idVendor=VENDOR --attr-match=idProduct=PRODUCT\nudevadm settle",
            )
            Text(
                "If Studio still reports the old permission error, detach and reattach the phone with usbipd, " +
                    "then select Read the phone. Unlock the phone and allow USB debugging if prompted.",
                color = textColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Phones(state: PhoneUiState, vm: PhoneViewModel) {
    if (state.phones.isEmpty() || (state.phones.size == 1 && state.phone != null)) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        state.phones.forEach { p ->
            GlassChip(
                label = "${p.serial} (${p.mode.words})",
                selected = p.serial == state.phone?.serial,
                onClick = { vm.select(p.serial) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(phone: PhoneFacts, workspaceId: String, vm: PhoneViewModel) {
    var all by remember(phone) { mutableStateOf(false) }
    phone.readError?.let { Note("Its answers are incomplete: $it", GlassTheme.diagnosticColors.warning) }
    factLines(phone).forEach { (label, value) -> Fact(label, value) }
    Note("Every value above is the phone's own answer (measured).", MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        listOf(PhoneMode.BOOTLOADER, PhoneMode.FASTBOOTD, PhoneMode.RECOVERY, PhoneMode.ANDROID)
            .filter { it != phone.mode }
            .forEach { mode ->
                GlassButton(onClick = { vm.reboot(workspaceId, mode) }, variant = GlassButtonVariant.Standard) {
                    Text("Reboot to ${mode.words.substringBefore(" (").substringBefore(",")}")
                }
            }
        if (phone.values.isNotEmpty()) {
            GlassButton(onClick = { all = !all }, variant = GlassButtonVariant.Text) {
                Text(if (all) "Hide its answers" else "All its answers (${phone.values.size})")
            }
        }
    }
    if (all) phone.values.entries.sortedBy { it.key }.forEach { (k, v) -> Code("$k: $v") }
    Partitions(phone)
}

private fun factLines(phone: PhoneFacts): List<Pair<String, String>> = listOfNotNull(
    "Serial" to phone.serial,
    "Mode" to phone.mode.words,
    phone.product?.let { "Product" to it },
    phone.model?.let { "Model" to it },
    phone.currentSlot?.let { "Running slot" to (it + (phone.slotCount?.let { n -> " of $n" } ?: "")) },
    phone.unlocked?.let { "Bootloader" to if (it) "unlocked" else "locked" },
    phone.snapshotStatus?.let { "Virtual A/B" to it },
    phone.superName?.let { "Super" to it },
    phone.maxDownloadBytes?.let { "One transfer" to formatBytes(it) },
)

@Composable
private fun Partitions(phone: PhoneFacts) {
    if (phone.partitions.isEmpty()) return
    Title("Partitions (${phone.partitions.size})")
    phone.partitions.forEach { p ->
        Code("${p.name}  ${p.sizeBytes?.let(::formatBytes) ?: "?"}${if (p.logical) "  logical" else ""}")
    }
}

/** The device configuration against what the phone measured, each difference with "Use phone value". */
@Composable
private fun MeasuredVsConfigured(
    phone: PhoneFacts,
    device: TargetDevice,
    enabled: Boolean,
    onUse: ((TargetDevice) -> TargetDevice) -> Unit,
) {
    val facts = PhoneDeviceCheck.facts(phone, device)
    if (facts.isEmpty()) return
    Title("Compared with the device configuration")
    facts.forEach { fact ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("PhoneFact_${fact.label}")) {
            Fact(fact.label, if (fact.differs) "${fact.configured} → phone ${fact.measured}" else fact.measured)
            if (fact.differs) {
                GlassButton(
                    onClick = { onUse(fact.apply) },
                    variant = GlassButtonVariant.Secondary,
                    enabled = enabled,
                ) {
                    Text("Use phone value")
                }
            }
        }
    }
    val differing = facts.count { it.differs }
    val matches = differing == 0
    Note(
        if (matches) "The configuration matches the phone." else "$differing value(s) differ; the phone measured them.",
        if (matches) MaterialTheme.colorScheme.onSurfaceVariant else GlassTheme.diagnosticColors.warning,
    )
}

@Composable
private fun Images(state: PhoneUiState, vm: PhoneViewModel) {
    val (built, stock) = state.images.partition { it.built }
    Title("Built by the last build (${built.size})")
    if (built.isEmpty()) Note("Nothing built yet.", MaterialTheme.colorScheme.onSurfaceVariant)
    built.forEach { ImageRow(it, it.path in state.picked, vm) }
    Title("As the firmware shipped them (${stock.size})")
    stock.forEach { ImageRow(it, it.path in state.picked, vm) }
}

@Composable
private fun ImageRow(image: ProjectImage, picked: Boolean, vm: PhoneViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("PhoneImage_${image.path}")) {
        Checkbox(checked = picked, onCheckedChange = { vm.toggle(image.path) })
        Code(
            "${image.path}  ${formatBytes(image.bytes)}" +
                if (image.sparse) " sparse, ${formatBytes(image.fullBytes)} written out" else "",
        )
    }
}

@Composable
private fun Plan(plan: FlashPlan) {
    if (plan.rows.isEmpty()) return
    Title("Where each would land")
    plan.refusals.forEach { Note(it, GlassTheme.diagnosticColors.error) }
    plan.notes.forEach { Note(it, MaterialTheme.colorScheme.onSurfaceVariant) }
    plan.rows.forEach { PlanLine(it) }
    Note(
        if (plan.refused) "This set would be refused." else "Nothing is written from here: flashing stays yours.",
        if (plan.refused) GlassTheme.diagnosticColors.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlanLine(row: PlanRow) {
    val color = when (row.verdict) {
        PlanVerdict.REFUSED -> GlassTheme.diagnosticColors.error
        PlanVerdict.NAMED_CONFIRMATION -> GlassTheme.diagnosticColors.warning
        PlanVerdict.NOTE -> GlassTheme.diagnosticColors.info
        PlanVerdict.FITS -> MaterialTheme.colorScheme.onSurface
    }
    val where = row.destination?.let {
        "$it (${if (row.measured) "measured" else "assumed"}" +
            (row.limitBytes?.let { l -> ", holds ${formatBytes(l)}" } ?: "") + ")"
    } ?: "nowhere"
    Text(
        "${row.image.path} → $where" + row.reasons.joinToString("") { "; $it" },
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = codeFontFamily(),
        modifier = Modifier.testTag("PlanRow_${row.image.path}"),
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun Code(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = codeFontFamily(),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Note(text: String, color: Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}
