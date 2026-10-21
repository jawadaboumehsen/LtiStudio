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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice

/**
 * The device configuration of the current workspace: only the fields the build cannot start without. Each field
 * shows its own problem; while a run is active the whole section is read-only.
 */
@Composable
fun WorkspaceDeviceSection(viewModel: WorkspaceDeviceViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val device = state.draft
    val loadError = state.loadError
    if (loadError != null) {
        UnreadableDevice(loadError, viewModel, modifier)
        return
    }
    if (device == null) {
        NoDevice(state, viewModel, modifier)
        return
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        DeviceHeader(state)
        DeviceIdentity(state, device, viewModel)
        DeviceLayout(state, device, viewModel)
        DeviceSuperPartition(state, device, viewModel)
        DeviceBootChain(state, device, viewModel)
        DeviceActions(state, viewModel)
    }
}

@Composable
private fun UnreadableDevice(reason: String, viewModel: WorkspaceDeviceViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassBanner(
            severity = GlassBannerSeverity.ERROR,
            title = "Device configuration could not be read",
            description = "device.json in the workspace folder is unreadable or the build service is unavailable: " +
                reason,
        )
        GlassButton(onClick = viewModel::retry, variant = GlassButtonVariant.Secondary) {
            Text("Retry")
        }
    }
}

@Composable
private fun NoDevice(
    state: WorkspaceDeviceUiState,
    viewModel: WorkspaceDeviceViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassBanner(
            severity = GlassBannerSeverity.WARNING,
            title = "No device configuration",
            description = "This workspace folder has no device.json, so a build cannot start. Start from a " +
                "template; the workspace gets its own copy in its folder.",
        )
        state.templates.forEach { template ->
            GlassButton(onClick = { viewModel.startFromTemplate(template) }, variant = GlassButtonVariant.Secondary) {
                Text("Start from ${template.name}")
            }
        }
    }
}

@Composable
private fun DeviceHeader(state: WorkspaceDeviceUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Device",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GlassChip(
            label = when {
                state.deviceIssues.isNotEmpty() -> "${state.deviceIssues.size} to fix"
                state.isDirty -> "Modified"
                else -> "Ready"
            },
            selected = state.deviceIssues.isEmpty(),
        )
    }
    if (state.locked) {
        GlassBanner(
            severity = GlassBannerSeverity.INFO,
            title = "A build is running",
            description = "The device configuration is read-only until the run ends. Edits apply to the next run.",
        )
    }
    state.message?.let {
        GlassBanner(severity = GlassBannerSeverity.INFO, title = "Device", description = it)
    }
}

@Composable
private fun DeviceIdentity(state: WorkspaceDeviceUiState, device: TargetDevice, vm: WorkspaceDeviceViewModel) {
    Group("Identity") {
        TextField(state, "name", "Name", device.name) { v -> vm.update { it.copy(name = v) } }
        TextField(state, "codename", "Codename", device.codename) { v -> vm.update { it.copy(codename = v) } }
        TextField(state, "productName", "Product name", device.productName) { v ->
            vm.update { it.copy(productName = v) }
        }
        TextField(state, "fastbootProduct", "Fastboot product", device.fastbootProduct) { v ->
            vm.update { it.copy(fastbootProduct = v) }
        }
    }
}

@Composable
private fun DeviceLayout(state: WorkspaceDeviceUiState, device: TargetDevice, vm: WorkspaceDeviceViewModel) {
    Group("Layout") {
        TextField(state, "filesystemType", "Filesystem", device.filesystemType) { v ->
            vm.update { it.copy(filesystemType = v) }
        }
        ToggleRow("Hardware has a separate system_ext partition", device.hasStandaloneSystemExt, !state.locked) { v ->
            vm.update { it.copy(hasStandaloneSystemExt = v) }
        }
        ListField(state, "dynamicPartitions", "Dynamic partitions", device.dynamicPartitions) { v ->
            vm.update { it.copy(dynamicPartitions = v, packagePolicy = it.packagePolicy.copy(flashablePartitions = v)) }
        }
        ListField(state, "bootPartitions", "Boot partitions", device.bootPartitions) { v ->
            vm.update {
                it.copy(bootPartitions = v, packagePolicy = it.packagePolicy.copy(flashableBootPartitions = v))
            }
        }
        ListField(state, "packagePolicy.bootSlots", "Boot slots", device.packagePolicy.bootSlots) { v ->
            vm.update { it.copy(packagePolicy = it.packagePolicy.copy(bootSlots = v)) }
        }
        TextField(state, "activeSlotSuffix", "Active slot suffix", device.activeSlotSuffix) { v ->
            vm.update { it.copy(activeSlotSuffix = v) }
        }
    }
}

@Composable
private fun DeviceSuperPartition(state: WorkspaceDeviceUiState, device: TargetDevice, vm: WorkspaceDeviceViewModel) {
    Group("Super partition") {
        NumberField(state, "superPartitionBytes", "Super size (bytes)", device.superPartitionBytes) { v ->
            vm.update { it.copy(superPartitionBytes = v) }
        }
        TextField(state, "superGroupName", "Group name", device.superGroupName) { v ->
            vm.update { it.copy(superGroupName = v) }
        }
        NumberField(state, "superGroupBytes", "Group size (bytes)", device.superGroupBytes) { v ->
            vm.update { it.copy(superGroupBytes = v) }
        }
        NumberField(state, "superMetadataSlots", "Metadata slots", device.superMetadataSlots.toLong()) { v ->
            vm.update { it.copy(superMetadataSlots = v.toInt()) }
        }
        ToggleRow("Virtual A/B", device.virtualAb, !state.locked) { v -> vm.update { it.copy(virtualAb = v) } }
    }
}

@Composable
private fun DeviceBootChain(state: WorkspaceDeviceUiState, device: TargetDevice, vm: WorkspaceDeviceViewModel) {
    Group("Boot chain") {
        NumberField(state, "bootPartitionBytes", "boot (bytes)", device.bootPartitionBytes) { v ->
            vm.update { it.copy(bootPartitionBytes = v) }
        }
        NumberField(state, "vendorBootPartitionBytes", "vendor_boot (bytes)", device.vendorBootPartitionBytes) { v ->
            vm.update { it.copy(vendorBootPartitionBytes = v) }
        }
        NumberField(state, "initBootPartitionBytes", "init_boot (bytes)", device.initBootPartitionBytes) { v ->
            vm.update { it.copy(initBootPartitionBytes = v) }
        }
        NumberField(state, "dtboPartitionBytes", "dtbo (bytes)", device.dtboPartitionBytes) { v ->
            vm.update { it.copy(dtboPartitionBytes = v) }
        }
        TextField(state, "bootDevicePath", "Boot device path", device.bootDevicePath) { v ->
            vm.update { it.copy(bootDevicePath = v) }
        }
    }
}

@Composable
private fun DeviceActions(state: WorkspaceDeviceUiState, vm: WorkspaceDeviceViewModel) {
    val canAct = state.canSave
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(onClick = vm::save, variant = GlassButtonVariant.Primary, enabled = canAct) {
            Text(if (state.isSaving) "Saving..." else "Save device")
        }
        GlassButton(onClick = vm::discard, variant = GlassButtonVariant.Secondary, enabled = canAct) {
            Text("Discard")
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

private fun fieldState(state: WorkspaceDeviceUiState, field: String): TextFieldState =
    state.issuesFor(field).firstOrNull()?.let { TextFieldState.Error(it.message) } ?: TextFieldState.Default

@Composable
private fun TextField(
    state: WorkspaceDeviceUiState,
    field: String,
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    GlassTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        state = fieldState(state, field),
        enabled = !state.locked,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A number kept as typed text so a half-typed value is not rewritten under the cursor. */
@Composable
private fun NumberField(
    state: WorkspaceDeviceUiState,
    field: String,
    label: String,
    value: Long,
    onChange: (Long) -> Unit,
) {
    var text by remember(state.resetCount) { mutableStateOf(value.toString()) }
    GlassTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.trim().toLongOrNull()?.let(onChange)
        },
        label = label,
        state = if (text.trim().toLongOrNull() == null) {
            TextFieldState.Error("Enter a whole number")
        } else {
            fieldState(state, field)
        },
        enabled = !state.locked,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Comma separated names kept as typed text, parsed into a list on every change. */
@Composable
private fun ListField(
    state: WorkspaceDeviceUiState,
    field: String,
    label: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var text by remember(state.resetCount) { mutableStateOf(values.joinToString(", ")) }
    GlassTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            onChange(typed.split(',').map { it.trim() }.filter { it.isNotEmpty() })
        },
        label = label,
        state = fieldState(state, field),
        enabled = !state.locked,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        GlassToggle(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
