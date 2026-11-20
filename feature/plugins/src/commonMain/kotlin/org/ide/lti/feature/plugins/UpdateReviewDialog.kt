/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.plugin.ModUpdateReview
import org.ide.lti.core.domain.plugin.PluginWorkspaceUsage
import org.ide.lti.core.domain.workspace.PendingUpdate

/**
 * Before the workspaces move onto a newer mod version: what changes from each version they use (options, files it
 * edits, devices, dependencies), and per workspace the mod lab on the new versions. Moving is a separate choice.
 */
@Composable
internal fun UpdateReviewDialog(
    update: PendingUpdate,
    checks: Map<String, String>,
    onCheck: (PluginWorkspaceUsage.PendingMove) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val to = update.reviews.first().to
    GlassDialog(
        title = "Review the update to ${update.reviews.first().key} $to",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(onClick = onApply, modifier = Modifier.testTag("UpdateApply")) {
                Text("Move ${update.moves.size} workspace(s) to $to")
            }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Text) { Text("Not now") }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(ComponentSize.ActionCardHeight * 3).testTag("UpdateReview"),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            update.reviews.forEach { review -> item { ReviewSections(review) } }
            item { Heading("Does each workspace still check?") }
            update.moves.forEach { move ->
                item { WorkspaceCheck(move, checks[move.name], onCheck) }
            }
        }
    }
}

@Composable
private fun ReviewSections(review: ModUpdateReview) {
    Heading("From ${review.from}")
    if (review.unchanged) Line("Nothing a review shows changed (options, files, devices, dependencies).")
    review.sections.filterValues { it.isNotEmpty() }.forEach { (title, lines) ->
        Line(title, bold = true)
        lines.forEach { Line("• $it") }
    }
}

@Composable
private fun WorkspaceCheck(
    move: PluginWorkspaceUsage.PendingMove,
    answer: String?,
    onCheck: (PluginWorkspaceUsage.PendingMove) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
        Line(move.name + (answer?.let { ": $it" } ?: ""), modifier = Modifier.weight(1f))
        GlassButton(
            onClick = { onCheck(move) },
            enabled = answer != CHECKING,
            variant = GlassButtonVariant.Standard,
            modifier = Modifier.testTag("UpdateCheck_${move.workspaceId}"),
        ) { Text(if (answer == null) "Check it" else "Check again") }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun Line(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** What [UpdateReviewDialog] shows while a workspace's check runs. */
internal const val CHECKING = "Checking…"
