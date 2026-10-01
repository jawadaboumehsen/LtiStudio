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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Wireless-ADB pairing dialog: host, port, and the on-device pairing code.
 */
@Composable
fun GlassWifiPairingDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    onPairAndConnect: (host: String, port: Int, code: String) -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    GlassDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        title = "Pair Wireless Device",
        confirmButton = {
            GlassPrimaryButton(
                onClick = {
                    val portNumber = port.toIntOrNull() ?: return@GlassPrimaryButton
                    onPairAndConnect(host.trim(), portNumber, code.trim())
                },
            ) {
                Text("Pair & Connect")
            }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Text(
                text = "Enable wireless debugging on the device and enter the pairing details shown there.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassTextField(
                value = host,
                onValueChange = { host = it },
                label = "IP address",
                placeholder = "192.168.1.42",
                modifier = Modifier.fillMaxWidth(),
            )
            GlassTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit) },
                label = "Pairing port",
                placeholder = "37251",
                modifier = Modifier.fillMaxWidth(),
            )
            GlassTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit) },
                label = "Pairing code",
                placeholder = "123456",
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassTheme.diagnosticColors.error,
                )
            }
        }
    }
}
