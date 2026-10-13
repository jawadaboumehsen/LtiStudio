/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily

/**
 * The running change, in place of the "Install & use" button: title, step, a real percentage, the latest line of
 * output and a Cancel. Everything else about the group is frozen while it shows (see `ToolsPageState`).
 */
@Composable
public fun OperationProgressCard(
    progress: OperationProgressPresentation,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = GlassShapes.HazeCard) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.SmallMedium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                progress.percentLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            progress.stepLabel?.let {
                // Announced when the step changes, not on every percent.
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            GlassProgressBar(
                progress = progress.percent?.let { it / PERCENT_SCALE },
                contentDescription = "${progress.title} ${progress.percentLabel.orEmpty()}".trim(),
            )
            progress.latestLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val note = if (progress.canCancel) CANCEL_NOTE else SWITCH_NOTE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (progress.canCancel) {
                    var cancelling by remember(progress.title) { mutableStateOf(false) }
                    GlassSecondaryButton(
                        onClick = {
                            cancelling = true
                            onCancel()
                        },
                        enabled = !cancelling,
                        minWidth = Spacing.None,
                        contentPadding = PaddingValues(
                            horizontal = Spacing.Medium,
                            vertical = Spacing.Small,
                        ),
                    ) {
                        Text(
                            if (cancelling) "Cancelling…" else "Cancel",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

private const val PERCENT_SCALE = 100f
private const val CANCEL_NOTE = "Other versions unlock when this finishes or is cancelled."
private const val SWITCH_NOTE = "One moment…"
