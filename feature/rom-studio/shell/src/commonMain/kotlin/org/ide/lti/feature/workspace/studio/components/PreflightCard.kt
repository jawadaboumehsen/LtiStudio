/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.workspace.studio.PreflightUiState
import org.ide.lti.feature.workspace.studio.PreflightViewModel

/**
 * Will it build? Build & Sign's checks run against the current settings and work tree in seconds; each finding
 * comes with its fix: a setting changed here, or the screen that fixes it.
 */
@Composable
internal fun PreflightCard(vm: PreflightViewModel, onOpen: (StageId, String) -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium)
            .testTag("PreflightCard"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Will it build?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Runs Build & Sign's checks on the current settings and work tree, without building.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                )
            }
            GlassButton(
                onClick = vm::check,
                enabled = !state.checking,
                variant = GlassButtonVariant.Primary,
                modifier = Modifier.testTag("PreflightCheck"),
            ) { Text(if (state.checking) "Checking…" else "Check now", fontSize = FontSize.BodySmall) }
        }
        state.error?.let { Text(it, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.BodySmall) }
        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
            )
        }
        VintfResult(state)
        SamePhone(state, vm)
        state.findings?.let { findings ->
            val stops = findings.count { it.blocks }
            Text(
                when {
                    findings.isEmpty() -> "Nothing would stop the build."
                    stops == 0 -> "Nothing would stop the build; ${findings.size} note(s)."
                    else -> "$stops thing(s) would stop the build."
                },
                color = if (stops == 0) GlassTheme.diagnosticColors.success else GlassTheme.diagnosticColors.error,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("PreflightSummary"),
            )
            findings.forEach { FindingRow(it, vm, onOpen) }
        }
        NextBuildSection(state)
    }
}

@Composable
private fun VintfResult(state: PreflightUiState) {
    val result = state.vintf
    val status = result?.status ?: VintfStatus.NOT_RUN
    val colors = GlassTheme.diagnosticColors
    Text(
        text = if (state.checking) "VINTF · Checking…" else "VINTF · ${status.label}",
        color = when (status) {
            VintfStatus.COMPATIBLE -> colors.success
            VintfStatus.INCOMPATIBLE -> colors.error
            VintfStatus.INCOMPLETE -> colors.warning
            VintfStatus.NOT_RUN -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = FontWeight.SemiBold,
        fontSize = FontSize.BodySmall,
        modifier = Modifier.testTag("PreflightVintfStatus"),
    )
    result?.missingEvidence?.forEach { missing ->
        Text(missing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
    }
}

/** If you build now: what changed since the last build that worked, which stages run and why, time and disk. */
@Composable
private fun NextBuildSection(state: PreflightUiState) {
    state.nextError?.let { Text(it, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.BodySmall) }
    if (state.readingNext) {
        Text(
            "Reading what the next build would do…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
        )
    }
    val next = state.next ?: return
    Column(
        modifier = Modifier.fillMaxWidth().testTag("NextBuild"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            "If you build now",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        nextBuildLines(next).forEach { line ->
            Text(line, color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
        }
    }
}

/** The section's lines, in reading order. */
internal fun nextBuildLines(next: NextBuildFacts): List<String> = buildList {
    val good = next.lastGood
    val changes = next.changes
    when {
        good == null -> add("No build has made a package here yet.")

        changes == null -> add("The last build that worked has no settings snapshot: what changed is not known.")

        changes.isEmpty() -> add("No settings or mods changed since the last build that worked (${started(good)}).")

        else -> {
            add("Changed since the last build that worked (${started(good)}):")
            changes.take(CHANGES_SHOWN).forEach { add("• $it") }
            if (changes.size > CHANGES_SHOWN) add("…and ${changes.size - CHANGES_SHOWN} more.")
        }
    }
    val runs = next.stages.filter { it.runsBecause != null }
    val first = runs.firstOrNull()
    if (first == null) {
        add("Every stage comes from cache.")
    } else {
        add(
            "${runs.size} of ${next.stages.size} stages run, from " +
                "${PipelineStageSequence.label(first.stageId)}: ${first.runsBecause}",
        )
    }
    next.estimate?.let { add(it.describe()) }
}

private fun started(run: BuildRun): String = run.startedAt.toString().take(STARTED_SHOWN).replace('T', ' ')

private const val CHANGES_SHOWN = 8
private const val STARTED_SHOWN = 16

/** The other workspaces of this phone: their settings (not their firmware's) can be taken over in one click. */
@Composable
private fun SamePhone(state: PreflightUiState, vm: PreflightViewModel) {
    if (state.samePhone.isEmpty()) return
    // Replacing four settings groups cannot be undone from here: it is confirmed first.
    var asked by remember { mutableStateOf<Workspace?>(null) }
    Text(
        "Same phone: take the assembly, debloat, mods and build settings of another workspace (kernel overrides, " +
            "image-browser edits and kept partitions belong to its firmware and stay there).",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
    )
    state.samePhone.forEach { other ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                other.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                onClick = { vm.compareWith(other) },
                enabled = !state.comparing,
                variant = GlassButtonVariant.Text,
                modifier = Modifier.testTag("CompareWith_${other.id}"),
            ) {
                val busy = state.comparing && state.comparedWith?.id == other.id
                Text(if (busy) "Comparing…" else "Compare first", fontSize = FontSize.BodySmall)
            }
            GlassButton(
                onClick = { asked = other },
                variant = GlassButtonVariant.Standard,
                modifier = Modifier.testTag("CarryFrom_${other.id}"),
            ) { Text("Use its settings", fontSize = FontSize.BodySmall) }
        }
    }
    UpgradeReport(state)
    asked?.let { other ->
        GlassDialog(
            onDismissRequest = { asked = null },
            title = "Use the settings of ${other.name}?",
            confirmButton = {
                GlassButton(
                    onClick = {
                        asked = null
                        vm.carryFrom(other)
                    },
                    variant = GlassButtonVariant.Primary,
                    modifier = Modifier.testTag("CarryConfirm"),
                ) { Text("Replace them") }
            },
            dismissButton = {
                GlassButton(onClick = { asked = null }, variant = GlassButtonVariant.Text) { Text("Cancel") }
            },
        ) {
            Text(
                "This workspace's assembly, debloat, mods and build settings are replaced by those of ${other.name}. " +
                    "The current ones are not kept anywhere.",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
            )
        }
    }
}

@Composable
private fun FindingRow(finding: PreflightFinding, vm: PreflightViewModel, onOpen: (StageId, String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("PreflightFinding_${finding.code}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                finding.code,
                color = if (finding.blocks) GlassTheme.diagnosticColors.error else GlassTheme.diagnosticColors.warning,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.SemiBold,
            )
            Text(finding.message, color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
        }
        when (val fix = finding.fix) {
            is PreflightFix.Setting -> GlassButton(onClick = { vm.apply(fix) }, variant = GlassButtonVariant.Standard) {
                Text(fix.label, fontSize = FontSize.BodySmall)
            }

            is PreflightFix.Open -> GlassButton(
                onClick = { onOpen(fix.stage, fix.objectId) },
                variant = GlassButtonVariant.Text,
            ) { Text("${fix.label} →", fontSize = FontSize.BodySmall) }

            null -> Unit
        }
    }
}

/** The upgrade assistant's report: what changes from the compared workspace's firmware, and what still fits. */
@Composable
private fun UpgradeReport(state: PreflightUiState) {
    val sections = state.upgrade ?: return
    val from = state.comparedWith?.name ?: return
    Column(
        modifier = Modifier.fillMaxWidth().testTag("UpgradeReport"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            "From $from to this firmware",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        sections.forEach { section ->
            Text(
                section.title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.SemiBold,
            )
            section.lines.forEach { line ->
                Text(
                    line.text,
                    color = if (line.problem) {
                        GlassTheme.diagnosticColors.warning
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = FontSize.BodySmall,
                )
            }
        }
    }
}
