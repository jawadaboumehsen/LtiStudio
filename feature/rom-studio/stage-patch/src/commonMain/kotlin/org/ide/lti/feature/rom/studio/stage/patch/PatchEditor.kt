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
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.plugin.LabRow
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.plugin.shortMember
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
    lab: LabState = LabState(),
    onCheckTogether: (Boolean) -> Unit = {},
    onOpenPlugins: () -> Unit = {},
    selectedObjectId: String = "module-catalog",
    modifier: Modifier = Modifier,
    saveError: String? = null,
) {
    // Every time it is shown: plugins are installed or updated on the Plugins page in between.
    LaunchedEffect(Unit) { onRefresh() }
    val selected = selectedObjectId.lowercase().replace('_', '-')
    val changes = facts.plans.flatMap { (mod, plan) -> plan.operations.map { mod to it } }
    val missingPlans = settings.enabledPackages.filterNot(facts.plans::containsKey)

    Box(modifier = modifier.fillMaxSize().testTag("PatchEditor"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            saveError?.let { message -> item { PatchProblem("Changes could not be saved. $message") } }
            facts.lockError?.let { message -> item { PatchProblem(message) } }
            if (facts.loaded && selected in PLAN_PREVIEWS && missingPlans.isNotEmpty()) {
                item { PatchProblem("Locked plugin plans unavailable: ${missingPlans.joinToString()}.") }
            }
            item {
                when (selected) {
                    "enabled-ordered-modules" -> Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        // Above the list: with many mods enabled the list is long, the check is what is asked for.
                        if (settings.enabledPackages.isNotEmpty()) ModLabCard(lab, facts, onCheckTogether)
                        EnabledCard(settings, facts, onToggle)
                    }

                    "file-changes" -> ChangesCard(
                        "File changes",
                        changes.filter { (_, op) -> op !is Operation.TextPatch && op !is Operation.PropertyPatch },
                    )

                    "text-patches" ->
                        ChangesCard("Text patches", changes.filter { (_, op) -> op is Operation.TextPatch })

                    "property-patches" ->
                        ChangesCard("Property patches", changes.filter { (_, op) -> op is Operation.PropertyPatch })

                    "conflicts" -> ConflictsCard(changes)

                    else -> PatchCatalog(settings, facts, onToggle, onOpenPlugins, onRefresh)
                }
            }
        }
    }
}

private val PLAN_PREVIEWS = setOf("file-changes", "text-patches", "property-patches", "conflicts")

@Composable
private fun EnabledCard(settings: CustomizationSettings, facts: PatchFacts, onToggle: (String) -> Unit) {
    PatchCard("Enabled plugins, in the order they apply", "PatchEnabled") {
        if (settings.enabledPackages.isEmpty()) PatchHint("No plugins enabled.")
        settings.enabledPackages.forEachIndexed { index, pkg ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pin = settings.lockedEntry(pkg)
                val record = if (pin != null) {
                    facts.installedVersions.firstOrNull { it.matches(pin) }
                } else {
                    facts.installed.firstOrNull { it.key == pkg }
                }
                val name = record?.displayName?.takeIf { it.isNotBlank() }
                Mono("${index + 1}. ${name ?: pkg}")
                GlassButton(
                    onClick = { onToggle(pkg) },
                    variant = GlassButtonVariant.Standard,
                    enabled = facts.canChangePlugins,
                ) {
                    Text("Disable", fontSize = FontSize.Micro)
                }
            }
        }
        PatchHint("A plugin's dependencies always apply before it.")
    }
}

@Composable
private fun ChangesCard(title: String, changes: List<Pair<String, Operation>>) {
    PatchCard(title, "PatchChanges") {
        if (changes.isEmpty()) PatchHint("The enabled plugins make no changes of this kind.")
        changes.forEach { (mod, op) ->
            Mono(describe(op) + if (op.condition == Condition.Always) "" else "  (when its condition holds)")
            PatchHint(mod.substringAfter(':') + " · " + op.id)
        }
    }
}

/**
 * Whole-file changes (add, replace, delete) to one path from two plugins: only the last one would win. Text,
 * property and bytecode edits are not among them: each plugin's edits apply on top of the one before it.
 */
internal fun conflictsOf(changes: List<Pair<String, Operation>>): Map<String, List<Pair<String, Operation>>> = changes
    .filter { (_, op) -> op is Operation.Copy || op is Operation.Replace || op is Operation.Delete }
    .groupBy { (_, op) -> targetOf(op) }
    .filterValues { ops -> ops.map { it.first }.distinct().size > 1 }

@Composable
private fun ConflictsCard(changes: List<Pair<String, Operation>>) {
    val conflicts = conflictsOf(changes)
    PatchCard("Conflicts", "PatchConflicts") {
        if (conflicts.isEmpty()) PatchHint("No two enabled plugins replace the same file.")
        conflicts.forEach { (path, ops) -> PatchProblem("$path: ${ops.joinToString { it.first.substringAfter(':') }}") }
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
internal fun PatchCard(title: String, tag: String, content: @Composable () -> Unit) {
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
internal fun PatchHint(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun PatchProblem(text: String) {
    Text(
        text = text,
        fontSize = FontSize.Micro,
        fontFamily = ideFontFamily(),
        color = GlassTheme.diagnosticColors.error,
    )
}

private const val CONTEXT_PREVIEW = 48

/** The mod lab: every operation of the enabled mods, in the order they apply, as the build would meet it. */
@Composable
private fun ModLabCard(lab: LabState, facts: PatchFacts, onCheck: (Boolean) -> Unit) {
    PatchCard("Check them together", "PatchModLab") {
        PatchHint(
            "Checks each operation against the assembled work tree, after the ones before it, as the build applies " +
                "them. Nothing is written.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(
                onClick = { onCheck(false) },
                enabled = !lab.checking,
                variant = GlassButtonVariant.Standard,
                modifier = Modifier.testTag("PatchModLabCheck"),
            ) { Text(if (lab.checking) "Checking…" else "Check against this firmware", fontSize = FontSize.Micro) }
            // Decoding takes about a minute per package: asked for, not done on every check.
            GlassButton(
                onClick = { onCheck(true) },
                enabled = !lab.checking,
                variant = GlassButtonVariant.Text,
                modifier = Modifier.testTag("PatchModLabDecode"),
            ) { Text("Also check APK/JAR edits (decodes each package)", fontSize = FontSize.Micro) }
        }
        lab.error?.let { PatchProblem(it) }
        val result = lab.result ?: return@PatchCard
        result.problems.forEach { PatchProblem(it) }
        val counts = result.rows.groupingBy { it.status }.eachCount()
        if (result.rows.isNotEmpty()) {
            PatchHint(
                LabStatus.entries.mapNotNull { s -> counts[s]?.let { "$it ${LAB_WORDS.getValue(s)}" } }
                    .joinToString(" · "),
            )
        }
        var byPackage by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            listOf(false to "By mod", true to "By package").forEach { (value, label) ->
                GlassChip(
                    label = label,
                    selected = byPackage == value,
                    onClick = { byPackage = value },
                    modifier = Modifier.testTag("PatchModLab_$label"),
                )
            }
        }
        if (byPackage) LabPackages(result.rows, facts) else LabMods(result.rows, facts)
    }
}

/** The lab by mod: each mod's operations in plan order, with what the check said. */
@Composable
private fun LabMods(rows: List<LabRow>, facts: PatchFacts) {
    rows.groupBy { it.mod }.forEach { (mod, group) ->
        Mono(modName(mod, facts))
        group.forEach { row ->
            LabLine(row, labOperation(row, facts)?.let(::describe) ?: row.operationId)
            if (row.detail.isNotEmpty()) BeforeAfter(row.detail)
        }
    }
}

/**
 * The lab by package: each file the mods change (those several mods touch first), then each mod and what it does
 * there, then for an APK or JAR the methods its edits change and the files inside it they touch.
 */
@Composable
private fun LabPackages(rows: List<LabRow>, facts: PatchFacts) {
    labByPackage(rows) { row -> labOperation(row, facts)?.let(::targetOf) }.forEach { pkg ->
        val mods = pkg.rows.map { it.mod }.distinct()
        Mono(pkg.target + if (mods.size > 1) "  (${mods.size} mods)" else "")
        pkg.rows.forEach { row ->
            // The package's methods are listed once below, not after each of its edits.
            val shown = row.copy(reason = row.reason.substringBefore("; the package's edits change"))
            LabLine(
                shown,
                "${modName(row.mod, facts)}: ${labOperation(row, facts)?.let(::describe) ?: row.operationId}",
            )
            if (row.detail.isNotEmpty()) BeforeAfter(row.detail)
        }
        if (pkg.methods.isNotEmpty()) {
            PatchHint("Methods it changes (${pkg.methods.size}):")
            pkg.methods.forEach { PatchHint("    ${shortMember(it)}   $it") }
        }
        if (pkg.files.isNotEmpty()) PatchHint("Files inside it: ${pkg.files.joinToString()}")
    }
}

@Composable
private fun LabLine(row: LabRow, what: String) {
    val reason = row.reason.takeIf { it.isNotEmpty() }?.let { " — $it" }.orEmpty()
    val text = "${LAB_MARKS.getValue(row.status)} $what$reason"
    if (row.status == LabStatus.REFUSED) PatchProblem(text) else PatchHint(text)
}

private fun labOperation(row: LabRow, facts: PatchFacts): Operation? =
    facts.plans[row.mod]?.operations?.firstOrNull { it.id == row.operationId }

private fun modName(mod: String, facts: PatchFacts): String =
    facts.installed.firstOrNull { it.key == mod }?.displayName?.takeIf { it.isNotBlank() } ?: mod

/** A package's edits before and after: `- ` removed, `+ ` added, `  ` unchanged lines, `=== member` headers. */
@Composable
private fun BeforeAfter(detail: String) {
    var open by remember { mutableStateOf(false) }
    GlassButton(
        onClick = { open = !open },
        variant = GlassButtonVariant.Text,
        modifier = Modifier.testTag("PatchModLabDiff"),
    ) { Text(if (open) "Hide changes" else "Show changes (before / after)", fontSize = FontSize.Micro) }
    if (!open) return
    detail.lines().forEach { line ->
        Text(
            text = line,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            color = when {
                line.startsWith("+ ") -> GlassTheme.diagnosticColors.success
                line.startsWith("- ") -> GlassTheme.diagnosticColors.error
                line.startsWith("===") || line.startsWith("@@") -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val LAB_WORDS = mapOf(
    LabStatus.CHECKED to "checked",
    LabStatus.REFUSED to "refused",
    LabStatus.UNCHECKED to "checked only by the build",
    LabStatus.NOT_APPLICABLE to "left out",
)

private val LAB_MARKS = mapOf(
    LabStatus.CHECKED to "✓",
    LabStatus.REFUSED to "✗",
    LabStatus.UNCHECKED to "?",
    LabStatus.NOT_APPLICABLE to "–",
)
