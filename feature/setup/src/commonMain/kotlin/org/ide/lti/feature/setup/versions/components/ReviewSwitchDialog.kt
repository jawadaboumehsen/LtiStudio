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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.feature.setup.versions.ReviewGroupChange
import org.ide.lti.feature.setup.versions.ReviewSwitchModel

@Composable
public fun ReviewSwitchDialog(
    model: ReviewSwitchModel,
    onConfirmBuildAndSwitch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    GlassDialog(
        title = "Review Toolchain Switch",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassPrimaryButton(onClick = onConfirmBuildAndSwitch) {
                Text("Build & switch")
            }
        },
        dismissButton = {
            GlassSecondaryButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            }
            .semantics {
                contentDescription = "Review toolchain switch dialog"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
        ) {
            Text(
                text = "Your current tools stay active until the new toolchain is verified.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            if (model.unchangedGroups.isNotEmpty()) {
                Text(
                    text = "Unchanged groups are reused from your artifact cache.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.Small))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "Changed Groups (${model.changedGroups.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ComponentSize.DropdownMenuMinWidth),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(model.changedGroups) { change ->
                    ChangedGroupCard(change)
                }
            }

            if (model.requiresTrustConfirmation && model.untrustedRepoUrl != null) {
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Text(
                    text = "Note: Building from external fork runs code with your permissions: " +
                        model.untrustedRepoUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ChangedGroupCard(change: ReviewGroupChange) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = change.groupId.value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = change.currentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = codeFontFamily(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = change.newLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = codeFontFamily(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (change.affectedTools.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = "Affected tools: ${change.affectedTools.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
