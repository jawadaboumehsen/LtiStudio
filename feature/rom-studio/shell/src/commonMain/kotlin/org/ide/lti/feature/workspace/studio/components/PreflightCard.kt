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
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.stages.VintfEvidence
import org.ide.lti.core.domain.pipeline.stages.VintfEvidenceKind
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.workspace.studio.PreflightUiState
import org.ide.lti.feature.workspace.studio.PreflightViewModel
import org.ide.lti.feature.workspace.studio.ReadinessStageLink

/**
 * Will it build? Build & Sign's checks run against the current settings and work tree in seconds; each finding
 * comes with its fix: a setting changed here, or the screen that fixes it.
 */
@Composable
internal fun PreflightCard(
    vm: PreflightViewModel,
    onOpen: (StageId, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSetup: () -> Unit = {},
    resolveLink: (PreflightFix.Open) -> ReadinessStageLink = { ReadinessStageLink(it) },
) {
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
                    "Build plan and checks",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Preview stages and check the current settings and work tree.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                )
            }
            GlassButton(
                onClick = vm::check,
                loading = state.checking || state.readingNext,
                variant = GlassButtonVariant.Primary,
                modifier = Modifier.testTag("PreflightCheck"),
            ) {
                Text(if (state.checking || state.readingNext) "Checking…" else "Check now", fontSize = FontSize.BodySmall)
            }
        }
        state.error?.let { Text(it, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.BodySmall) }
        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
            )
        }
        BuildReadinessResults(state, onOpen, onOpenSetup, vm::check, vm::apply, resolveLink)
        SamePhone(state, vm)
    }
}

@Composable
internal fun BuildReadinessResults(
    state: PreflightUiState,
    onOpen: (StageId, String) -> Unit,
    onOpenSetup: () -> Unit,
    onRetry: () -> Unit,
    onApply: (PreflightFix.Setting) -> Unit,
    resolveLink: (PreflightFix.Open) -> ReadinessStageLink = { ReadinessStageLink(it) },
) {
    if (state.checking) {
        NextBuildSection(state)
        GlassLoadingPlaceholder("Checking build readiness and VINTF…", Modifier.testTag("PreflightChecksLoading"))
        return
    }
    val stageLink: @Composable (PreflightFix.Open) -> Unit = { requested ->
        val link = resolveLink(requested)
        Column {
            link.explanation?.let { ReadinessText(it) }
            EvidenceLink(link.fix.label) { onOpen(link.fix.stage, link.fix.objectId) }
        }
    }
    val blockers = state.findings.orEmpty().filter { it.blocks }
    if (state.inputsChanged) {
        ReadinessText("Workspace inputs changed. Run the check again for a current result.")
    }
    if (state.findings != null) {
        val complete = state.vintf?.status == VintfStatus.COMPATIBLE ||
            state.vintf?.status == VintfStatus.INCOMPATIBLE && state.vintf.evidence.isEmpty()
        Text(
            when {
                blockers.size == 1 -> "Blocked by 1 finding"
                blockers.isNotEmpty() -> "Blocked by ${blockers.size} findings"
                !complete -> "Checks incomplete"
                else -> "No blocking findings"
            },
            color = when {
                blockers.isNotEmpty() -> GlassTheme.diagnosticColors.error
                !complete -> GlassTheme.diagnosticColors.warning
                else -> GlassTheme.diagnosticColors.success
            },
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("PreflightSummary"),
        )
        blockers.forEach { FindingRow(it, onApply, stageLink) }
    }
    NextBuildSection(state)
    VintfResult(state, stageLink, onOpenSetup, onRetry)
    state.findings.orEmpty().filterNot { it.blocks }.forEach { FindingRow(it, onApply, stageLink) }
}

@Composable
private fun VintfResult(
    state: PreflightUiState,
    stageLink: @Composable (PreflightFix.Open) -> Unit,
    onOpenSetup: () -> Unit,
    onRetry: () -> Unit,
) {
    val result = state.vintf
    val status = result?.status ?: VintfStatus.NOT_RUN
    val colors = GlassTheme.diagnosticColors
    Text(
        text = "VINTF · ${status.label}",
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
    if (result == null) return
    val android = result.androidRelease?.let { "Android $it" }
    val firstApi = result.firstApiLevel?.let { "Vendor first API level $it" }
    listOfNotNull(android, firstApi).joinToString(" · ").takeIf(String::isNotEmpty)?.let { ReadinessText(it) }
    if (result.skuResults.isNotEmpty()) {
        ReadinessHeading("Checker results by SKU")
    }
    result.skuResults.forEach { sku ->
        val vendor = sku.vendorSku ?: "default"
        val odm = sku.odmSku ?: "default"
        val confidence = if (sku.provisional) " · Provisional" else ""
        ReadinessText("Vendor SKU $vendor · ODM SKU $odm · ${sku.status.label}$confidence")
    }
    if (result.skuResults.any { it.status == VintfStatus.INCOMPATIBLE }) {
        stageLink(PreflightFix.Open("Review firmware baseline", StageId.FIRMWARE_ACQUISITION, "firmware-baseline"))
    }
    if (result.evidence.isNotEmpty()) {
        ReadinessHeading("Missing VINTF evidence")
        result.evidence.forEach { VintfEvidenceRow(it, stageLink, onOpenSetup) }
        if (result.evidence.any { it.kind == VintfEvidenceKind.SCAN }) EvidenceLink("Retry the checks", onRetry)
    }
}

@Composable
private fun VintfEvidenceRow(
    evidence: VintfEvidence,
    stageLink: @Composable (PreflightFix.Open) -> Unit,
    onOpenSetup: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("VintfEvidence_${evidence.kind.name}")) {
        ReadinessText(evidence.message)
        val fix = when (evidence.kind) {
            VintfEvidenceKind.ASSEMBLY ->
                PreflightFix.Open("Open Assemble work trees", StageId.WORK_TREE_ASSEMBLY, "work-trees")

            VintfEvidenceKind.PARTITION ->
                PreflightFix.Open("Review extracted partitions", StageId.FIRMWARE_EXTRACTION, "dynamic-partitions")

            VintfEvidenceKind.FIRMWARE_METADATA, VintfEvidenceKind.APEX ->
                PreflightFix.Open("Inspect firmware files", StageId.WORK_TREE_ASSEMBLY, "image-browser")

            VintfEvidenceKind.KERNEL ->
                PreflightFix.Open("Open boot preparation", StageId.WORK_TREE_ASSEMBLY, "boot-preparation")

            VintfEvidenceKind.TOOL, VintfEvidenceKind.SCAN -> null
        }
        if (fix != null) stageLink(fix)
        if (evidence.kind == VintfEvidenceKind.TOOL) EvidenceLink("Open Setup for vintf-tools", onOpenSetup)
    }
}

@Composable
private fun ReadinessHeading(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EvidenceLink(label: String, onClick: () -> Unit) {
    GlassButton(onClick = onClick, variant = GlassButtonVariant.Text) {
        Text(label, fontSize = FontSize.BodySmall)
    }
}

@Composable
private fun ReadinessText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
}

/** If you build now: what changed since the last build that worked, which stages run and why, time and disk. */
@Composable
private fun NextBuildSection(state: PreflightUiState) {
    state.nextError?.let { Text(it, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.BodySmall) }
    if (state.readingNext || state.checking && state.next == null && state.nextError == null) {
        GlassLoadingPlaceholder("Reading the build plan…", Modifier.testTag("PreflightPlanLoading"))
        return
    }
    val next = state.next ?: return
    Column(
        modifier = Modifier.fillMaxWidth().testTag("NextBuild"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            "Planned stages",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        next.stages.forEach { stage ->
            val label = PipelineStageSequence.label(stage.stageId)
            val reason = stage.runsBecause
            ReadinessText(if (reason == null) "$label · Reuse cached output" else "$label · Run — $reason")
        }
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
                loading = state.comparing && state.comparedWith?.id == other.id,
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
            scrollContent = true,
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
private fun FindingRow(
    finding: PreflightFinding,
    onApply: (PreflightFix.Setting) -> Unit,
    stageLink: @Composable (PreflightFix.Open) -> Unit,
) {
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
            is PreflightFix.Setting -> GlassButton(onClick = { onApply(fix) }, variant = GlassButtonVariant.Standard) {
                Text(fix.label, fontSize = FontSize.BodySmall)
            }

            is PreflightFix.Open -> stageLink(fix.copy(label = "${fix.label} →"))

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
