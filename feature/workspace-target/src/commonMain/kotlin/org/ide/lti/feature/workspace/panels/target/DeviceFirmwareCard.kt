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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.workbench.FirmwareFact

/**
 * The device configuration next to what the workspace's own firmware says. A boot-chain size
 * from the firmware is the size its images were signed for: check it with `fastboot getvar partition-size:<p>_a`
 * before trusting either side.
 */
@Composable
internal fun DeviceFirmwareCard(state: WorkspaceDeviceUiState, vm: WorkspaceDeviceViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("DeviceFirmwareCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Compared with the firmware",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                onClick = vm::checkFirmware,
                variant = GlassButtonVariant.Secondary,
                enabled = !state.firmwareChecking,
                modifier = Modifier.testTag("CheckFirmwareButton"),
            ) {
                Text(if (state.firmwareChecking) "Reading..." else "Check against the firmware")
            }
        }
        state.firmwareError?.let { Note(it, GlassTheme.diagnosticColors.error) }
        val facts = state.firmwareFacts
        if (state.firmware == null) {
            Note(
                "Reads the extracted firmware (run the build through Extract first).",
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val differing = facts.count { it.differs }
            if (differing == 0) {
                Note("The settings match the firmware.", MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Note("$differing setting(s) differ from the firmware.", GlassTheme.diagnosticColors.warning)
            }
            if (state.firmware.superLayout == null) {
                Note(
                    "This firmware has no super image (an OTA payload), so the super sizes are not checked.",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            facts.forEach { FactRow(it, enabled = !state.locked, onUse = { vm.useFirmwareValue(it) }) }
        }
    }
}

@Composable
private fun FactRow(fact: FirmwareFact, enabled: Boolean, onUse: () -> Unit) {
    val warning = GlassTheme.diagnosticColors.warning
    val valueColor = if (fact.differs) warning else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().testTag("FirmwareFact_${fact.label}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = fact.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (fact.differs) "${fact.configured} → firmware ${fact.firmware}" else fact.firmware,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = valueColor,
            modifier = Modifier.weight(2f),
        )
        if (fact.differs) {
            GlassButton(onClick = onUse, variant = GlassButtonVariant.Secondary, enabled = enabled) {
                Text("Use firmware value")
            }
        }
    }
}

@Composable
private fun Note(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}
