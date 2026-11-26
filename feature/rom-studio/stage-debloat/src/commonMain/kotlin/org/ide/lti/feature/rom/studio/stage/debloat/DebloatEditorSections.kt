/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeHintText
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.debloat.AppRisk
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.feature.rom.studio.api.formatBytes

@Composable
internal fun Reasons(risk: AppRisk) {
    for (blocker in risk.blockers) {
        Reason(blocker, GlassTheme.diagnosticColors.error)
    }
    for (warning in risk.warnings) {
        Reason(warning, GlassTheme.diagnosticColors.warning)
    }
    for (note in risk.notes) {
        Reason(note, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Reason(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        modifier = Modifier.padding(start = Spacing.ExtraExtraLarge),
    )
}

/** Apps that can never be picked: the built-in list, and those the other apps' manifests show the device needs. */
internal fun LazyListScope.keepRules(facts: DebloatFacts, risks: Map<String, AppRisk>) {
    item { IdeHintText("These apps are needed to boot, set up, call or install apps; they cannot be picked.") }
    items(facts.inventory.filter(DebloatInventory::isProtected)) { Line(label(it)) }
    val needed = facts.inventory.filter { !risks[DebloatViewModel.selectorOf(it)]?.blockers.isNullOrEmpty() }
    if (needed.isNotEmpty()) {
        item { IdeHintText("Needed by the device, from what the apps' manifests say:") }
        items(needed) { candidate ->
            Column {
                Line(label(candidate))
                val blockers = risks[DebloatViewModel.selectorOf(candidate)]?.blockers.orEmpty()
                for (blocker in blockers) {
                    Reason(blocker, GlassTheme.diagnosticColors.error)
                }
            }
        }
    }
}

/** Picks the build will refuse (not in this firmware) and picks that remove a part of the system. */
internal fun LazyListScope.riskReview(
    settings: DebloatSettings,
    facts: DebloatFacts,
    risks: Map<String, AppRisk>,
    onUnpick: (String) -> Unit,
) {
    val known = facts.inventory.map(DebloatViewModel::selectorOf).toSet()
    val missing = settings.removeSelectors.filter { facts.loaded && it !in known }
    val risky = settings.removeSelectors.filter { !risks[it]?.warnings.isNullOrEmpty() }
    item {
        IdeHintText(
            if (missing.isEmpty() && risky.isEmpty()) {
                "No problems: every pick exists in the assembled tree and none is a part of the system."
            } else {
                "Review these picks before building."
            },
        )
    }
    if (missing.isNotEmpty()) {
        item { IdeHintText("Not apps of this firmware; the build stops until you remove them:") }
        items(missing) { selector -> PickRow(selector, onUnpick, isProblem = true) }
    }
    if (risky.isNotEmpty()) {
        item { IdeHintText("Parts of the system: remove them only on purpose.") }
        items(risky) { selector ->
            Column {
                PickRow(selector, onUnpick)
                val warnings = risks[selector]?.warnings.orEmpty()
                for (warning in warnings) {
                    Reason(warning, GlassTheme.diagnosticColors.warning)
                }
            }
        }
    }
}

@Composable
internal fun PickRow(selector: String, onUnpick: (String) -> Unit, isProblem: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = selector,
            color = if (isProblem) GlassTheme.diagnosticColors.error else MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(1f),
        )
        GlassButton(onClick = { onUnpick(selector) }, variant = GlassButtonVariant.Standard) {
            Text("Remove pick", fontSize = FontSize.Micro)
        }
    }
}

@Composable
internal fun Line(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}

internal fun label(candidate: DebloatCandidate) = "${candidate.partition}/${candidate.relativePath}"

internal fun LazyListScope.removeSelections(
    settings: DebloatSettings,
    facts: DebloatFacts,
    picked: Set<String>,
    actions: DebloatActions,
    onUnpick: (String) -> Unit,
) {
    item { IdeHintText(if (picked.isEmpty()) "Nothing picked." else "${picked.size} app(s) will be removed.") }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(onClick = actions.importList, variant = GlassButtonVariant.Standard) {
                Text("Import a removal list", fontSize = FontSize.BodySmall)
            }
            GlassButton(
                onClick = actions.exportList,
                enabled = picked.isNotEmpty(),
                variant = GlassButtonVariant.Standard,
            ) {
                Text("Save the picks as a list", fontSize = FontSize.BodySmall)
            }
        }
    }
    item { DebloatPresets(facts, picked.isNotEmpty(), actions) }
    facts.listResult?.let { match ->
        item { IdeHintText("The list matched ${match.effectiveRemovals.size} app(s).") }
        if (match.blockedProtected.isNotEmpty()) {
            item { IdeHintText("Protected or needed by the device, not picked:") }
            items(match.blockedProtected) { Line(it.id) }
        }
        if (match.unknown.isNotEmpty()) {
            item { IdeHintText("Not an app of this firmware, skipped:") }
            items(match.unknown) { Line(it) }
        }
    }
    items(settings.removeSelectors) { selector -> PickRow(selector, onUnpick) }
}

internal fun LazyListScope.results(facts: DebloatFacts, actions: DebloatActions) {
    val result = facts.lastResult
    item {
        IdeHintText(
            result?.let {
                "The last run removed ${it.removals.size} app(s), " +
                    "freeing ${formatBytes(it.logicalBytesFreed)}."
            } ?: "Debloat has not removed anything yet.",
        )
    }
    items(result?.removals.orEmpty()) { Line("${it.partition}:${it.relativePath}") }
    item {
        IdeHintText(
            if (facts.restorePoints.isEmpty()) {
                "No backups: the last run removed nothing."
            } else {
                "Backups taken before removal. Restoring puts the app back and un-picks it."
            },
        )
    }
    if (facts.restorePoints.isNotEmpty()) {
        item {
            GlassButton(onClick = actions.restoreAll, variant = GlassButtonVariant.Standard) {
                Text("Restore all", fontSize = FontSize.BodySmall)
            }
        }
    }
    items(facts.restorePoints, key = { it.id }) { point ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${point.itemPath}  (${point.state})",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = codeFontFamily(),
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                onClick = { actions.restore(point) },
                enabled = point.state == RestorePointState.Available,
                variant = GlassButtonVariant.Standard,
            ) {
                Text("Restore", fontSize = FontSize.Micro)
            }
        }
    }
}
