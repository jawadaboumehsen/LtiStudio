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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.workbench.FlashPlan
import org.ide.lti.core.domain.workbench.FlashPlanner
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
internal fun PhoneCard(state: WorkspaceDeviceUiState, device: TargetDevice, vm: PhoneViewModel) {
    val phone by vm.state.collectAsStateWithLifecycle()
    val workspaceId = state.workspaceId ?: return
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
                enabled = phone.busy == null,
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
        Phones(phone, vm)
        phone.phone?.let { Facts(it, workspaceId, vm) }
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
            "reboots into another mode). A phone listed with no permissions needs udev rules for this user.",
        GlassTheme.diagnosticColors.warning,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Phones(state: PhoneUiState, vm: PhoneViewModel) {
    if (state.phones.size < 2) return
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
    phone.maxDownloadBytes?.let { "One transfer" to formatSize(it) },
)

@Composable
private fun Partitions(phone: PhoneFacts) {
    if (phone.partitions.isEmpty()) return
    Title("Partitions (${phone.partitions.size})")
    phone.partitions.forEach { p ->
        Code("${p.name}  ${p.sizeBytes?.let(::formatSize) ?: "?"}${if (p.logical) "  logical" else ""}")
    }
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
            "${image.path}  ${formatSize(image.bytes)}" +
                if (image.sparse) " sparse, ${formatSize(image.fullBytes)} written out" else "",
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
            (row.limitBytes?.let { l -> ", holds ${formatSize(l)}" } ?: "") + ")"
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

/** Binary units with one decimal (`1.5 GiB`), in plain arithmetic: common code has no `String.format`. */
private fun formatSize(bytes: Long): String {
    val (unit, name) = listOf(GIB to "GiB", MIB to "MiB", KIB to "KiB").firstOrNull { bytes >= it.first } ?: (1L to "B")
    val tenths = bytes * TEN / unit
    return "${tenths / TEN}.${tenths % TEN} $name"
}

private const val KIB = 1024L
private const val MIB = KIB * KIB
private const val GIB = MIB * KIB
private const val TEN = 10L
