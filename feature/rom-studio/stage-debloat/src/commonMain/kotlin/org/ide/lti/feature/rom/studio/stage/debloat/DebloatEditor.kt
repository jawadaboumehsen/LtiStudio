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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.feature.rom.studio.api.formatBytes

/**
 * Debloat: pick apps of the assembled work tree to remove. The inventory is the real tree (run the build
 * through Assemble first); protected apps cannot be picked; the stage refuses picks that no longer exist.
 */
@Suppress("LongParameterList")
@Composable
public fun DebloatEditor(
    settings: DebloatSettings,
    facts: DebloatFacts,
    onChange: (DebloatSettings) -> Unit,
    onToggle: (DebloatCandidate) -> Unit,
    onUnpick: (String) -> Unit,
    onRefresh: () -> Unit,
    selectedObjectId: String = "inventory",
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { if (!facts.loaded) onRefresh() }
    var query by remember { mutableStateOf("") }
    val picked = settings.removeSelectors.toSet()

    Box(modifier = modifier.fillMaxSize().testTag("DebloatEditor"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            item { Header(settings, facts, onChange, onRefresh) }
            when (selectedObjectId.lowercase()) {
                "remove-selections", "remove_selections" -> {
                    item { Hint(if (picked.isEmpty()) "Nothing picked." else "${picked.size} app(s) will be removed.") }
                    items(settings.removeSelectors) { selector -> PickRow(selector, onUnpick) }
                }
                "keep-rules", "keep_rules" -> {
                    item { Hint("These apps are needed to boot, set up, call or install apps; they cannot be picked.") }
                    items(facts.inventory.filter(DebloatInventory::isProtected)) { Line(label(it)) }
                }
                "risk-review", "risk_review" -> {
                    val known = facts.inventory.map(DebloatViewModel::selectorOf).toSet()
                    val missing = settings.removeSelectors.filter { facts.loaded && it !in known }
                    item {
                        Hint(
                            if (missing.isEmpty()) {
                                "No problems: every pick exists in the assembled tree and none is protected."
                            } else {
                                "These picks are not apps of this firmware; the build stops until you remove them:"
                            },
                        )
                    }
                    items(missing) { selector -> PickRow(selector, onUnpick, isProblem = true) }
                }
                "results" -> {
                    val result = facts.lastResult
                    item {
                        Hint(
                            result?.let {
                                "The last run removed ${it.removals.size} app(s), " +
                                    "freeing ${formatBytes(it.logicalBytesFreed)}."
                            } ?: "Debloat has not removed anything yet.",
                        )
                    }
                    items(result?.removals.orEmpty()) { Line("${it.partition}:${it.relativePath}") }
                }
                else -> {
                    item {
                        GlassTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search apps",
                            modifier = Modifier.fillMaxWidth().testTag("DebloatSearch"),
                        )
                    }
                    val shown = facts.inventory.filter { it.relativePath.contains(query, ignoreCase = true) }
                    items(shown) { candidate ->
                        InventoryRow(candidate, DebloatViewModel.selectorOf(candidate) in picked, onToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    settings: DebloatSettings,
    facts: DebloatFacts,
    onChange: (DebloatSettings) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(
                checked = settings.enabled,
                onCheckedChange = { onChange(settings.copy(enabled = it)) },
                activeColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (settings.enabled) "Debloat on" else "Debloat off (nothing is removed)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(1f),
            )
            GlassButton(onClick = onRefresh, enabled = !facts.loading, variant = GlassButtonVariant.Standard) {
                Text(if (facts.loading) "Reading…" else "Refresh", fontSize = FontSize.BodySmall)
            }
        }
        if (facts.loaded && facts.inventory.isEmpty()) {
            Hint("No assembled work tree yet. In Run history, run through Assemble, then refresh.")
        } else if (facts.loaded) {
            Hint("${facts.inventory.size} apps in the assembled tree.")
        }
    }
}

@Composable
private fun InventoryRow(candidate: DebloatCandidate, picked: Boolean, onToggle: (DebloatCandidate) -> Unit) {
    val protected = DebloatInventory.isProtected(candidate)
    Row(
        modifier = Modifier.fillMaxWidth().testTag("DebloatRow"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = picked, onCheckedChange = { onToggle(candidate) }, enabled = !protected)
        Text(
            text = label(candidate),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(1f),
        )
        if (protected) GlassChip(label = "Protected", selected = false)
        Text(
            text = formatBytes(candidate.sizeBytes),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
        )
    }
}

@Composable
private fun PickRow(selector: String, onUnpick: (String) -> Unit, isProblem: Boolean = false) {
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
private fun Line(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

private fun label(candidate: DebloatCandidate) = "${candidate.partition}/${candidate.relativePath}"
