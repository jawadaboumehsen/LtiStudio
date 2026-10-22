/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.workspace.CustomizationSettings

/**
 * Patches/Mods: the installed plugins (enable them here; dependencies come along) and what the enabled ones
 * change, read from their plans. Plugins are installed on the Plugins page.
 */
@Suppress("LongParameterList")
@Composable
public fun PatchEditor(
    settings: CustomizationSettings,
    facts: PatchFacts,
    onToggle: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenPlugins: () -> Unit = {},
    selectedObjectId: String = "module-catalog",
    modifier: Modifier = Modifier,
) {
    // Every time it is shown: plugins are installed or updated on the Plugins page in between.
    LaunchedEffect(Unit) { onRefresh() }
    val changes = facts.plans.flatMap { (mod, plan) -> plan.operations.map { mod to it } }

    Box(modifier = modifier.fillMaxSize().testTag("PatchEditor"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item {
                when (selectedObjectId.lowercase()) {
                    "enabled-ordered-modules", "enabled_ordered_modules" -> EnabledCard(settings, facts, onToggle)
                    "file-changes", "file_changes" -> ChangesCard(
                        "File changes",
                        changes.filter { (_, op) -> op !is Operation.TextPatch && op !is Operation.PropertyPatch },
                    )
                    "text-patches", "text_patches" ->
                        ChangesCard("Text patches", changes.filter { (_, op) -> op is Operation.TextPatch })
                    "property-patches", "property_patches" ->
                        ChangesCard("Property patches", changes.filter { (_, op) -> op is Operation.PropertyPatch })
                    "conflicts" -> ConflictsCard(changes)
                    else -> CatalogCard(settings, facts, onToggle, onOpenPlugins)
                }
            }
        }
    }
}

@Composable
private fun CatalogCard(
    settings: CustomizationSettings,
    facts: PatchFacts,
    onToggle: (String) -> Unit,
    onOpenPlugins: () -> Unit,
) {
    Card("Installed plugins", "PatchCatalog") {
        GlassButton(onClick = onOpenPlugins, variant = GlassButtonVariant.Standard) {
            Text("Install plugins…", fontSize = FontSize.BodySmall)
        }
        // Next to what was clicked, not under the list: a refused toggle says why where you can see it.
        facts.lockError?.let { Problem(it) }
        if (facts.loaded && facts.installed.isEmpty()) {
            Hint("No plugins installed. Install .lti-mod.zip packages on the Plugins page.")
        }
        facts.installed.forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.displayName.ifBlank { record.identity.id },
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ideFontFamily(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    record.summary.takeIf { it.isNotBlank() }?.let { Hint(it) }
                    facts.incompatible[record.key]?.let { Problem("This plugin $it") }
                    Hint(
                        "v${record.identity.version} by ${record.identity.publisher}" +
                            record.dependencies.takeIf { it.isNotEmpty() }
                                ?.joinToString(prefix = " · needs ") { it.id }.orEmpty(),
                    )
                }
                val enabled = record.key in settings.enabledPackages
                GlassToggle(
                    checked = enabled,
                    // An incompatible plugin can be switched off, never on.
                    enabled = enabled || record.key !in facts.incompatible,
                    onCheckedChange = { onToggle(record.key) },
                    activeColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun EnabledCard(settings: CustomizationSettings, facts: PatchFacts, onToggle: (String) -> Unit) {
    Card("Enabled plugins, in the order they apply", "PatchEnabled") {
        if (settings.enabledPackages.isEmpty()) Hint("No plugins enabled.")
        settings.enabledPackages.forEachIndexed { index, pkg ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val name = facts.installed.firstOrNull { it.key == pkg }?.displayName?.takeIf { it.isNotBlank() }
                Mono("${index + 1}. ${name ?: pkg}")
                GlassButton(onClick = { onToggle(pkg) }, variant = GlassButtonVariant.Standard) {
                    Text("Disable", fontSize = FontSize.Micro)
                }
            }
        }
        Hint("A plugin's dependencies always apply before it.")
    }
}

@Composable
private fun ChangesCard(title: String, changes: List<Pair<String, Operation>>) {
    Card(title, "PatchChanges") {
        if (changes.isEmpty()) Hint("The enabled plugins make no changes of this kind.")
        changes.forEach { (mod, op) ->
            Mono(describe(op) + if (op.condition == Condition.Always) "" else "  (when its condition holds)")
            Hint(mod.substringAfter(':') + " · " + op.id)
        }
    }
}

/** Whole-file changes (add, replace, delete, bytecode) to one path from two plugins: only the last one would win. */
@Composable
private fun ConflictsCard(changes: List<Pair<String, Operation>>) {
    val wholeFile = changes.filter { (_, op) -> op !is Operation.TextPatch && op !is Operation.PropertyPatch }
    val conflicts = wholeFile.groupBy { (_, op) -> targetOf(op) }
        .filterValues { ops -> ops.map { it.first }.distinct().size > 1 }
    Card("Conflicts", "PatchConflicts") {
        if (conflicts.isEmpty()) Hint("No two enabled plugins replace the same file.")
        conflicts.forEach { (path, ops) -> Problem("$path: ${ops.joinToString { it.first.substringAfter(':') }}") }
    }
}

private fun targetOf(op: Operation): String = when (op) {
    is Operation.Copy -> op.destination
    is Operation.Replace -> op.destination
    is Operation.Delete -> op.target
    is Operation.PropertyPatch -> op.target
    is Operation.TextPatch -> op.target
    is Operation.ApkJarPatch -> op.target
}.let { "${it.partition}/${it.relativePath}" }

private fun describe(op: Operation): String = when (op) {
    is Operation.Copy -> {
        val meta = op.metadata?.let { " (${it.mode}, ${it.label ?: "folder label"})" }
        "add ${targetOf(op)}${meta.orEmpty()}"
    }
    is Operation.Replace -> "replace ${targetOf(op)}"
    is Operation.Delete -> "delete ${targetOf(op)}"
    is Operation.PropertyPatch -> "${targetOf(op)}: ${op.key}=${valueOf(op.value)}"
    is Operation.TextPatch -> "${targetOf(op)}: at \"${op.context.take(CONTEXT_PREVIEW)}…\""
    is Operation.ApkJarPatch -> "smali ${targetOf(op)} (${op.edits.size} edits)"
}

private fun valueOf(value: ValueRef): String = when (value) {
    is ValueRef.Literal -> value.value
    is ValueRef.Setting -> "<setting ${value.settingId}>"
    is ValueRef.TargetFact -> "<${value.factId}>"
}

@Composable
private fun Card(title: String, tag: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Problem(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = GlassTheme.diagnosticColors.error,
    )
}

private const val CONTEXT_PREVIEW = 48
