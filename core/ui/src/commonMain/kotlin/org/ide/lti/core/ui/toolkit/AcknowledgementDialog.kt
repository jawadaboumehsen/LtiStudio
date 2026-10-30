/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.toolkit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Dialog requiring explicit user acknowledgement before proceeding with risky build operations (FR-035).
 * Reuses the existing [GlassDialog] component per constitution.
 */
@Composable
public fun AcknowledgementDialog(
    featureId: String,
    warningMessage: String,
    onAcknowledge: (featureId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Explicit Acknowledgement Required",
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = title,
        modifier = modifier,
        confirmButton = {
            GlassButton(
                onClick = { onAcknowledge(featureId) },
                variant = GlassButtonVariant.Primary,
            ) {
                Text("Acknowledge & Proceed")
            }
        },
        dismissButton = {
            GlassButton(
                onClick = onDismiss,
                variant = GlassButtonVariant.Secondary,
            ) {
                Text("Cancel")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = warningMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Feature: $featureId",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
