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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatGoal
import org.ide.lti.core.domain.debloat.DebloatSuggestions
import org.ide.lti.feature.rom.studio.api.formatBytes

/**
 * Apps the ROM itself says can go (`DebloatSuggestions`): nothing it declares relies on them, nothing warns, no boot
 * start; the largest first. Picking one goes through the same checks as a pick in Inventory.
 */
internal fun LazyListScope.suggestions(
    facts: DebloatFacts,
    picked: Set<String>,
    onToggle: (DebloatCandidate) -> Unit,
    goalShort: @Composable (Long) -> Unit = {},
) {
    if (facts.apps.isEmpty()) return
    item { GoalCard(facts, picked, onToggle, goalShort) }
    val suggested = DebloatSuggestions.of(facts.inventory, facts.apps, picked)
    val total = formatBytes(suggested.sumOf { it.candidate.sizeBytes })
    item {
        Hint(
            if (suggested.isEmpty()) {
                "No suggestions: every app left is relied on, warned about or starts at boot."
            } else {
                "Suggested by the ROM (${suggested.size}, $total): nothing in the ROM relies on them or looks for " +
                    "them, they are not part of the system and do not start at boot. What they do at run time is " +
                    "not in any file."
            },
        )
    }
    items(suggested) { s ->
        Row(
            modifier = Modifier.fillMaxWidth().testTag("Suggestion_${s.packageId}"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Line("${s.label ?: s.packageId}  ${formatBytes(s.candidate.sizeBytes)}")
                if (s.label != null) Hint(s.packageId)
            }
            GlassButton(onClick = { onToggle(s.candidate) }, variant = GlassButtonVariant.Standard) {
                Text("Pick", fontSize = FontSize.BodySmall)
            }
        }
    }
}

/**
 * "Free about N MiB, keep these": a plan from the apps the ROM says can go ([DebloatGoal]), shown with why and what
 * stays unknown; picking it goes through Debloat's own checks, and the picks it made can be taken back.
 */
@Composable
private fun GoalCard(
    facts: DebloatFacts,
    picked: Set<String>,
    onToggle: (DebloatCandidate) -> Unit,
    goalShort: @Composable (Long) -> Unit,
) {
    var mib by rememberSaveable { mutableStateOf("") }
    var keep by rememberSaveable { mutableStateOf("") }
    var applied by remember { mutableStateOf<List<DebloatCandidate>>(emptyList()) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag("DebloatGoal"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Line("Goal: make the ROM smaller")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassTextField(
                value = mib,
                onValueChange = { mib = it.filter(Char::isDigit).take(MAX_DIGITS) },
                placeholder = "Free about ... MiB",
                modifier = Modifier.weight(1f).testTag("DebloatGoalMib"),
            )
            GlassTextField(
                value = keep,
                onValueChange = { keep = it },
                placeholder = "Keep apps named (e.g. bank, update)",
                modifier = Modifier.weight(2f).testTag("DebloatGoalKeep"),
            )
        }
        val target = mib.toLongOrNull()?.takeIf { it > 0 } ?: return@Column
        val plan = remember(facts, picked, target, keep) {
            val all = DebloatSuggestions.of(facts.inventory, facts.apps, picked, limit = Int.MAX_VALUE)
            DebloatGoal.plan(all, target * MIB, keep.split(','))
        }
        val short = if (plan.shortBytes > 0) ", ${formatBytes(plan.shortBytes)} short of the goal" else ""
        val kept = plan.kept.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "; kept as asked: ") { it.label ?: it.packageId }.orEmpty()
        Hint("Proposal: ${plan.picks.size} apps, ${formatBytes(plan.freedBytes)} of files$short$kept.")
        // The largest carry the goal; the long tail of small apps is one line.
        plan.picks.take(
            SHOWN,
        ).forEach { s -> Line("- ${s.label ?: s.packageId}  ${formatBytes(s.candidate.sizeBytes)}") }
        plan.picks.drop(SHOWN).takeIf { it.isNotEmpty() }?.let { rest ->
            Hint("+ ${rest.size} smaller apps, ${formatBytes(rest.sumOf { it.candidate.sizeBytes })}")
        }
        Hint(
            "Why: nothing in the ROM relies on or looks for these apps, none is part of the system or starts at " +
                "boot. Unknown: what they do at run time (intents, services) is in no file. The image shrinks by " +
                "about the files' size (APKs hardly compress)." +
                if (plan.shortBytes > 0) " For the rest, compression can be measured below." else "",
        )
        if (plan.shortBytes > 0) goalShort(plan.shortBytes)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = {
                    plan.picks.forEach { onToggle(it.candidate) }
                    applied = plan.picks.map { it.candidate }
                },
                enabled = plan.picks.isNotEmpty(),
                variant = GlassButtonVariant.Standard,
                modifier = Modifier.testTag("DebloatGoalPick"),
            ) { Text("Pick these ${plan.picks.size}", fontSize = FontSize.BodySmall) }
            // Undo: the picks this plan made that are still picked.
            val undo = applied.filter { "${it.partition}:${it.relativePath}" in picked }
            if (undo.isNotEmpty()) {
                GlassButton(
                    onClick = {
                        undo.forEach(onToggle)
                        applied = emptyList()
                    },
                    variant = GlassButtonVariant.Text,
                    modifier = Modifier.testTag("DebloatGoalUndo"),
                ) { Text("Undo these picks", fontSize = FontSize.BodySmall) }
            }
        }
    }
}

private const val MIB = 1024L * 1024L
private const val MAX_DIGITS = 6
private const val SHOWN = 10
