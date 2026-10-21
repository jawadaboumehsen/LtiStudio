/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.usecase.publish.PublishJournal
import org.ide.lti.core.domain.usecase.publish.PublishTransfer

/** Publish (or Resume), and Cancel while it runs: the journal keeps what is done, so publishing again resumes. */
@Composable
internal fun PublishButtons(
    state: PublishUiState,
    ready: Boolean,
    resumes: Boolean,
    onPublish: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(
            onClick = onPublish,
            enabled = ready,
            variant = GlassButtonVariant.Primary,
            modifier = Modifier.testTag("PublishButton"),
        ) {
            Text(
                text = when {
                    state.publishing -> "Publishing…"
                    resumes -> "Resume publishing"
                    else -> "Publish…"
                },
                fontSize = FontSize.BodySmall,
            )
        }
        if (state.publishing) {
            GlassButton(
                onClick = onCancel,
                enabled = !state.cancelling,
                variant = GlassButtonVariant.Standard,
                modifier = Modifier.testTag("CancelPublishButton"),
            ) {
                Text(if (state.cancelling) "Cancelling…" else "Cancel", fontSize = FontSize.BodySmall)
            }
        }
    }
}

/** The running step (n of m), a bar of its bytes (indeterminate when it has none), size, speed and time left. */
@Composable
internal fun PublishProgress(journal: PublishJournal, transfer: PublishTransfer?, bytesPerSecond: Double?) {
    val index = journal.steps.indexOfFirst { it.id == transfer?.stepId }
    val step = journal.steps.getOrNull(index)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Mono(if (step == null) "Starting…" else "Step ${index + 1} of ${journal.steps.size} · ${step.label}")
        val done = transfer?.done
        val total = transfer?.total?.takeIf { it > 0 }
        val fraction = if (done != null && total != null) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
        GlassProgressBar(
            progress = fraction,
            modifier = Modifier.testTag("PublishProgress"),
            contentDescription = "Publish progress",
        )
        if (done != null && total != null) Hint(transferLine(done, total, bytesPerSecond))
    }
}

/** `3.21 of 7.16 GB · 24.3 MB/s · about 4 min left`. */
internal fun transferLine(done: Long, total: Long, bytesPerSecond: Double?): String {
    val percent = done * PERCENT / total
    val parts = mutableListOf("${size(done)} of ${size(total)} ($percent%)")
    if (bytesPerSecond != null && bytesPerSecond > 0) {
        parts += "${decimals(bytesPerSecond / MB, 1)} MB/s"
        val seconds = ((total - done) / bytesPerSecond).toLong()
        parts += when {
            seconds < MINUTE -> "less than a minute left"
            seconds < HOUR -> "about ${(seconds + MINUTE / 2) / MINUTE} min left"
            else -> "about ${seconds / HOUR} h ${(seconds % HOUR) / MINUTE} min left"
        }
    }
    return parts.joinToString(" · ")
}

internal fun size(bytes: Long): String =
    if (bytes >= GB) "${decimals(bytes / GB, 2)} GB" else "${decimals(bytes / MB, 1)} MB"

private fun decimals(value: Double, places: Int): String {
    var scale = 1L
    repeat(places) { scale *= TEN }
    val scaled = kotlin.math.round(value * scale).toLong()
    val fraction = (scaled % scale).toString().padStart(places, '0')
    return if (places == 0) "${scaled / scale}" else "${scaled / scale}.$fraction"
}

private const val MB = 1_000_000.0
private const val GB = 1_000_000_000.0
private const val TEN = 10L
private const val PERCENT = 100
private const val MINUTE = 60L
private const val HOUR = 3_600L
