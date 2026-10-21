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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState

/** What the workspace's successful builds produced, newest build first: the package, its manifest and checksums. */
@Composable
public fun ArtifactsPanel(runs: List<BuildRun>, modifier: Modifier = Modifier) {
    val typography = GlassTheme.typography
    val builds = runs.filter { it.state == RunState.SUCCEEDED && it.artifacts.isNotEmpty() }
        .sortedByDescending { it.startedAt }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(Spacing.Medium).testTag("ArtifactsPanel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        item {
            Text("Artifacts", style = typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (builds.isEmpty()) {
            item {
                Text(
                    text = "No build has produced a package yet. Run the whole build from Run history.",
                    style = typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        builds.forEach { run ->
            item(key = run.id) {
                Text(
                    text = "Build of ${run.startedAt.toString().substringBefore('.').replace('T', ' ')} UTC",
                    style = typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(run.artifacts, key = { "${run.id}/${it.linuxPath}" }) { ArtifactItem(it) }
        }
    }
}
