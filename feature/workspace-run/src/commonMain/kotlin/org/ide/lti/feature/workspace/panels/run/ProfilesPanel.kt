/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.workspace.BuildProfile

/**
 * Run panel > Profiles: the workspace's settings saved under names; what using one would change; using it; and a
 * queue that builds several in order (each checked by Will it build? before its build starts).
 */
@Composable
internal fun ProfilesPanel(viewModel: ProfilesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        item {
            Note(
                "A profile is this workspace's settings, every stage's, saved under a name. Use one to make it the " +
                    "current settings, or queue several to build them one after another.",
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = state.nameInput,
                    onValueChange = viewModel::setName,
                    placeholder = "Profile name",
                    modifier = Modifier.weight(1f).testTag("ProfileName"),
                )
                GlassButton(
                    onClick = viewModel::saveCurrent,
                    enabled = state.nameInput.isNotBlank(),
                    modifier = Modifier.testTag("ProfileSave"),
                ) { Text("Save the current settings") }
            }
        }
        state.message?.let { item { Note(it) } }
        if (state.profiles.isEmpty()) item { Note("No profiles yet.") }
        items(state.profiles, key = { it.name }) { profile ->
            ProfileCard(
                profile = profile,
                differences = state.differences[profile.name],
                queuedAt = state.queued.indexOf(profile.name).takeIf { it >= 0 }?.plus(1),
                queueBusy = state.queue.running,
                viewModel = viewModel,
            )
        }
        item { QueueCard(state, viewModel) }
    }
}

@Composable
private fun ProfileCard(
    profile: BuildProfile,
    differences: List<String>?,
    queuedAt: Int?,
    queueBusy: Boolean,
    viewModel: ProfilesViewModel,
) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("Profile_${profile.name}")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(profile.name, style = GlassTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            val saved = profile.savedAt.toLocalDateTime(TimeZone.currentSystemDefault()).toString()
            Note("Saved ${saved.take(STAMP).replace('T', ' ')}")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassChip(
                    label = if (differences == null) "Differences" else "Hide differences",
                    onClick = { viewModel.toggleDifferences(profile) },
                )
                GlassChip(label = "Use it", onClick = { viewModel.use(profile) }, enabled = !queueBusy)
                GlassChip(
                    label = queuedAt?.let { "Queued #$it" } ?: "Add to queue",
                    selected = queuedAt != null,
                    onClick = { viewModel.toggleQueued(profile.name) },
                    enabled = !queueBusy,
                )
                GlassChip(label = "Delete", onClick = { viewModel.delete(profile.name) }, enabled = !queueBusy)
            }
            differences?.let { lines ->
                if (lines.isEmpty()) Note("Same as the current settings.")
                lines.forEach { Note("• $it") }
            }
        }
    }
}

@Composable
private fun QueueCard(state: ProfilesUiState, viewModel: ProfilesViewModel) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("BuildQueue")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text("Build queue", style = GlassTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Note(
                "Each profile in turn: made the current settings, checked by Will it build? (a blocker stops " +
                    "the queue with why), then built in full. A failed build stops it. The queue runs while " +
                    "the app is open.",
            )
            val shown = state.queue.builds.takeIf { it.isNotEmpty() }?.map { "${it.profile}: ${it.status}" }
                ?: state.queued.mapIndexed { i, name -> "${i + 1}. $name" }
            shown.forEach { Note(it) }
            if (state.queue.stopRequested) Note("Stopping the queue. No further builds will start.")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassButton(
                    onClick = viewModel::startQueue,
                    enabled = !state.queue.running && state.queued.isNotEmpty(),
                    modifier = Modifier.testTag("QueueStart"),
                ) { Text("Build them in order") }
                if (state.queue.running && !state.queue.stopRequested) {
                    GlassButton(onClick = viewModel::stopQueue, variant = GlassButtonVariant.Text) {
                        Text("Stop after this build")
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = GlassTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val STAMP = 16
