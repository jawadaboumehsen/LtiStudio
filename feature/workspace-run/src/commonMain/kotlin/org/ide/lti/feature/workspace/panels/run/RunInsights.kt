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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.pipeline.RunEstimate
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.workspace.BuildReportText
import org.ide.lti.core.model.run.StageId

/** "What will run?": each stage of the next run, reused from cache or why it runs again. */
@Composable
internal fun ImpactPreview(uiState: BuildRunUiState, onPreview: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("ImpactPreview")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "What will run? The cache decisions of the next run, read without building.",
                    style = GlassTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    onClick = onPreview,
                    enabled = !uiState.isPreviewing && uiState.currentWorkspace != null,
                    variant = GlassButtonVariant.Standard,
                    modifier = Modifier.testTag("ImpactPreviewButton"),
                ) { Text(if (uiState.isPreviewing) "Reading…" else "Preview") }
            }
            // Three lines at most: the artifact list below shares the panel's height.
            uiState.impact?.let { stages ->
                val (reused, runs) = stages.partition { it.runsBecause == null }
                Text(
                    if (runs.isEmpty()) {
                        "Every stage comes from cache."
                    } else {
                        "${runs.size} of ${stages.size} stages run."
                    },
                    style = GlassTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("ImpactSummary"),
                )
                if (reused.isNotEmpty()) {
                    Text(
                        "Reused: " + reused.joinToString { stageLabel(it.stageId) },
                        style = GlassTheme.typography.bodySmall,
                        color = GlassTheme.diagnosticColors.success,
                    )
                }
                runs.firstOrNull()?.let { first ->
                    Text(
                        "${stageLabel(first.stageId)} runs: ${first.runsBecause}",
                        style = GlassTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("ImpactFirstRun"),
                    )
                }
                uiState.estimate?.let { estimate ->
                    Text(
                        estimateText(estimate),
                        style = GlassTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ImpactEstimate"),
                    )
                }
                runs.drop(1).takeIf { it.isNotEmpty() }?.let { after ->
                    Text(
                        "Then " + after.joinToString { stageLabel(it.stageId) } + " (they share work/)",
                        style = GlassTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Why the last run stopped: the refusal in words, the stage's last output, and the fix when there is one. */
@Composable
internal fun FailureDiagnosis(
    uiState: BuildRunUiState,
    onApplyFix: (PreflightFix.Setting) -> Unit,
    onOpen: (StageId, String) -> Unit,
) {
    val failure = uiState.failure ?: return
    val finding = uiState.diagnosis
    GlassCard(modifier = Modifier.fillMaxWidth().testTag("FailureDiagnosis")) {
        Column(
            modifier = Modifier.padding(Spacing.ExtraSmall),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                "Why the build stopped" + (finding?.let { " (${it.code})" } ?: ""),
                style = GlassTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassTheme.diagnosticColors.error,
            )
            Text(
                finding?.message ?: failure,
                style = GlassTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Output arrives in chunks of any size: the last lines, not the last chunks.
            val tail = uiState.logs.takeLast(LOG_LINES).joinToString("\n").lines().map { it.trimEnd() }
                .filter { it.isNotEmpty() }.takeLast(LOG_LINES)
            tail.takeIf { it.isNotEmpty() }?.let { lines ->
                Text(
                    lines.joinToString("\n"),
                    style = GlassTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (val fix = finding?.fix) {
                is PreflightFix.Setting -> GlassButton(
                    onClick = { onApplyFix(fix) },
                    variant = GlassButtonVariant.Standard,
                    modifier = Modifier.testTag("FailureFix"),
                ) { Text(fix.label) }

                is PreflightFix.Open -> GlassButton(
                    onClick = { onOpen(fix.stage, fix.objectId) },
                    variant = GlassButtonVariant.Text,
                    modifier = Modifier.testTag("FailureFix"),
                ) { Text("${fix.label} →") }

                null -> Unit
            }
            uiState.fixMessage?.let {
                Text(it, style = GlassTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** The post-build report, as the Markdown it is copied as. */
@Composable
internal fun BuildReportDialog(report: BuildReportText?, onClose: () -> Unit) {
    report ?: return
    val clipboard = LocalClipboardManager.current
    GlassDialog(
        onDismissRequest = onClose,
        title = "Build report",
        confirmButton = {
            GlassButton(
                onClick = { clipboard.setText(AnnotatedString(report.markdown)) },
                variant = GlassButtonVariant.Primary,
                modifier = Modifier.testTag("BuildReportCopy"),
            ) { Text("Copy") }
        },
        dismissButton = {
            GlassButton(onClick = onClose, variant = GlassButtonVariant.Text) { Text("Close") }
        },
    ) {
        Column(
            modifier = Modifier.heightIn(max = ComponentSize.MaxPanelHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            if (report.problems.isNotEmpty()) {
                Text(
                    "Needs a look: " + report.problems.joinToString("; "),
                    style = GlassTheme.typography.bodySmall,
                    color = GlassTheme.diagnosticColors.error,
                )
            }
            Text(
                report.markdown,
                style = GlassTheme.typography.bodySmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("BuildReportText"),
            )
        }
    }
}

/** "About 18 min (12-25 min), from up to 5 earlier runs here; Debloat never ran here: not counted." */
internal fun estimateText(estimate: RunEstimate): String {
    val parts = mutableListOf<String>()
    if (estimate.samples > 0) {
        val range = if (estimate.shortestMs == estimate.longestMs) {
            ""
        } else {
            " (${minutes(estimate.shortestMs)}-${minutes(estimate.longestMs)})"
        }
        val runs = if (estimate.samples == 1) "1 earlier run" else "${estimate.samples}+ earlier runs"
        parts += "Time: about ${minutes(estimate.typicalMs)}$range, from $runs here"
    }
    if (estimate.unknown.isNotEmpty()) {
        parts += estimate.unknown.joinToString { stageLabel(it) } + " never ran here: not counted"
    }
    return parts.joinToString("; ") + "."
}

private fun minutes(ms: Long): String {
    if (ms < MINUTE_MS) return "${ms / SECOND_MS} s"
    return "${(ms + MINUTE_MS / 2) / MINUTE_MS} min"
}

private const val SECOND_MS = 1000L
private const val MINUTE_MS = 60_000L

private const val LOG_LINES = 8
