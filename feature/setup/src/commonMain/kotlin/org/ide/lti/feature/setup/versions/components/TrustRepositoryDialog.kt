/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily

@Composable
public fun TrustRepositoryDialog(
    repoUrl: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassDialog(
        title = "Trust External Repository?",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassPrimaryButton(onClick = onConfirm) {
                Text("Trust and proceed")
            }
        },
        dismissButton = {
            GlassSecondaryButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Text(
                text = "Building this repository runs its code on your machine with your permissions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.Small))
            Text(
                text = repoUrl,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
