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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.GlassIcon
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.debloat.AppFacts
import org.ide.lti.core.domain.debloat.AppImpact
import org.ide.lti.core.domain.debloat.AppRisk
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.debloat.DebloatRisk
import org.ide.lti.core.domain.debloat.PackageMentions
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointState
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.ui.toolkit.RefusalView
import org.ide.lti.feature.rom.studio.api.formatBytes
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

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
    actions: DebloatActions = DebloatActions(),
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { if (!facts.loaded) onRefresh() }
    var query by remember { mutableStateOf("") }
    val picked = settings.removeSelectors.toSet()
    val risks = remember(facts.inventory, facts.apps, picked) { facts.risks(picked) }

    Box(modifier = modifier.fillMaxSize().testTag("DebloatEditor"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            item { Header(settings, facts, onChange, onRefresh) }
            item { Feedback(facts, actions) }
            when (selectedObjectId.lowercase()) {
                "remove-selections", "remove_selections" -> removeSelections(settings, facts, picked, actions, onUnpick)

                "keep-rules", "keep_rules" -> keepRules(facts, risks)

                "risk-review", "risk_review" -> riskReview(settings, facts, risks, onUnpick)

                "results" -> results(facts, actions)

                else -> {
                    item {
                        GlassTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search apps",
                            modifier = Modifier.fillMaxWidth().testTag("DebloatSearch"),
                        )
                    }
                    val shown = facts.inventory.filter { candidate ->
                        candidate.relativePath.contains(query, ignoreCase = true) ||
                            facts.apps[DebloatViewModel.selectorOf(candidate)]?.label
                                ?.contains(query, ignoreCase = true) == true
                    }
                    items(shown) { candidate ->
                        val selector = DebloatViewModel.selectorOf(candidate)
                        val details = remember(facts.apps, selector) { details(facts.apps[selector], selector, facts) }
                        InventoryRow(
                            candidate,
                            selector in picked,
                            risks[selector],
                            facts.app(selector),
                            details,
                            onToggle,
                        )
                    }
                }
            }
        }
    }
}

/** What the Debloat editor can ask for besides picking (see DebloatViewModel). */
public class DebloatActions(
    public val restore: (RestorePoint) -> Unit = {},
    public val restoreAll: () -> Unit = {},
    public val importList: () -> Unit = {},
    public val exportList: () -> Unit = {},
    public val savePreset: (String) -> Unit = {},
    public val applyPreset: (String) -> Unit = {},
    public val deletePreset: (String) -> Unit = {},
    public val dismiss: () -> Unit = {},
)

@Composable
private fun Feedback(facts: DebloatFacts, actions: DebloatActions) {
    facts.refusal?.let { RefusalView(refusal = it, actionLabel = "Dismiss", onAction = actions.dismiss) }
    facts.message?.let { GlassBanner(severity = GlassBannerSeverity.INFO, title = it, onDismiss = actions.dismiss) }
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
            val picked = settings.removeSelectors.toSet()
            val freed = facts.inventory.filter { DebloatViewModel.selectorOf(it) in picked }.sumOf { it.sizeBytes }
            val configs = PackageMentions.orphaned(
                facts.mentions,
                picked.mapNotNull { facts.apps[it]?.packageId }.toSet(),
            )
            Hint(
                "${facts.inventory.size} apps in the assembled tree." +
                    (if (picked.isNotEmpty()) " The ${picked.size} picked would free ${formatBytes(freed)}." else "") +
                    (
                        if (configs.isNotEmpty()) {
                            " ${configs.size} config file(s) naming only them go too: " +
                                configs.joinToString { it.substringAfterLast('/') } + "."
                        } else {
                            ""
                        }
                        ),
            )
        }
    }
}

@Composable
private fun InventoryRow(
    candidate: DebloatCandidate,
    picked: Boolean,
    risk: AppRisk?,
    app: ShownApp,
    details: List<Pair<String, String>>?,
    onToggle: (DebloatCandidate) -> Unit,
) {
    val protected = DebloatInventory.isProtected(candidate)
    val needed = !risk?.blockers.isNullOrEmpty()
    Column(modifier = Modifier.fillMaxWidth().testTag("DebloatRow")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = picked,
                onCheckedChange = { onToggle(candidate) },
                enabled = !protected && (picked || !needed),
            )
            AppIcon(app.iconFile)
            Column(modifier = Modifier.weight(1f)) {
                app.name?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = ideFontFamily(),
                    )
                }
                Text(
                    text = label(candidate),
                    color = if (app.name == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = if (app.name == null) FontSize.BodySmall else FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
            if (protected || needed) GlassChip(label = if (protected) "Protected" else "Needed", selected = false)
            Text(
                text = formatBytes(candidate.sizeBytes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }
        risk?.let { Reasons(it) }
        details?.let { Facts(it) }
    }
}

/**
 * The app in both directions, as labelled facts: who would notice it gone (version, signer, needed by, overlaid
 * by, looked for by, shared user id, config files naming it), then what it relies on (uses, overlays, looks for).
 */
private fun details(app: AppFacts?, selector: String, facts: DebloatFacts): List<Pair<String, String>>? {
    app ?: return null
    val impact = DebloatRisk.impact(selector, facts.apps)
    return listOfNotNull(
        app.version?.let {
            "Version" to listOfNotNull(
                it,
                app.targetSdk?.let { sdk -> "target SDK $sdk" },
            ).joinToString(", ")
        },
        signer(app, impact),
        impact.neededBy.labelled("Needed by"),
        impact.overlaidBy.labelled("Overlaid by"),
        impact.askedBy.labelled("Looked for by"),
        impact.sharesUidWith.takeIf {
            it.isNotEmpty()
        }?.let { "Shares user" to "${app.sharedUserId} (${it.size} apps)" },
        app.packageId?.let { pkg ->
            PackageMentions.namedIn(facts.mentions, pkg).map { it.substringAfterLast('/') }.labelled("Named in")
        },
        impact.uses.labelled("Uses"),
        impact.overlays.labelled("Overlays"),
        impact.looksFor.labelled("Looks for"),
        impact.looksForMissing.labelled("Looks for (missing)"),
    ).takeIf { it.isNotEmpty() }
}

/** Who signed [app]: the platform key (the framework's), or which key by its digest (subjects repeat). */
private fun signer(app: AppFacts, impact: AppImpact): Pair<String, String>? = app.signerName?.let { name ->
    "Signed by" to if (impact.platformSigned) "$name, platform key" else "$name, key ${app.signerDigest}"
}

/** `label` to `a, b, c +2`, or null when empty. */
private fun List<String>.labelled(label: String): Pair<String, String>? = takeIf { it.isNotEmpty() }?.let {
    label to take(DETAIL_NAMES).joinToString() + if (size > DETAIL_NAMES) " +${size - DETAIL_NAMES}" else ""
}

/** The app's facts as `Label value` pairs, flowing across the row under the app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(facts: List<Pair<String, String>>) {
    FlowRow(
        // Aligned with the risk lines above it.
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.ExtraExtraLarge, top = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        facts.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = value,
                    color = if (value.endsWith("platform key")) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
    }
}

private const val DETAIL_NAMES = 3

/** The app's name and icon file (as Windows reads it), when its APK has them. */
private data class ShownApp(val name: String?, val iconFile: String?)

private fun DebloatFacts.app(selector: String): ShownApp {
    val facts = apps[selector]
    val icon = facts?.icon?.let { name -> iconFolder?.let { "$it${File.separator}$name" } }
    return ShownApp(facts?.label, icon)
}

/** The app's own icon, read off the disk away from the UI thread; a plain file icon when it has none. */
@Composable
private fun AppIcon(file: String?) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = file?.let {
            withContext(Dispatchers.IO) {
                runCatching { SkiaImage.makeFromEncoded(File(it).readBytes()).toComposeImageBitmap() }.getOrNull()
            }
        }
    }
    val shown = bitmap
    if (shown != null) {
        Image(bitmap = shown, contentDescription = null, modifier = Modifier.size(IconSize.Large))
    } else {
        GlassIcon(icon = AppIcons.File, modifier = Modifier.size(IconSize.Large))
    }
}

/** Why an app is needed (red), risky (amber) or what else its manifest says (muted). */
@Composable
private fun Reasons(risk: AppRisk) {
    risk.blockers.forEach { Reason(it, GlassTheme.diagnosticColors.error) }
    risk.warnings.forEach { Reason(it, GlassTheme.diagnosticColors.warning) }
    risk.notes.forEach { Reason(it, MaterialTheme.colorScheme.onSurfaceVariant) }
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
private fun LazyListScope.keepRules(facts: DebloatFacts, risks: Map<String, AppRisk>) {
    item { Hint("These apps are needed to boot, set up, call or install apps; they cannot be picked.") }
    items(facts.inventory.filter(DebloatInventory::isProtected)) { Line(label(it)) }
    val needed = facts.inventory.filter { !risks[DebloatViewModel.selectorOf(it)]?.blockers.isNullOrEmpty() }
    if (needed.isNotEmpty()) {
        item { Hint("Needed by the device, from what the apps' manifests say:") }
        items(needed) { candidate ->
            Column {
                Line(label(candidate))
                risks[DebloatViewModel.selectorOf(candidate)]?.blockers?.forEach {
                    Reason(it, GlassTheme.diagnosticColors.error)
                }
            }
        }
    }
}

/** Picks the build will refuse (not in this firmware) and picks that remove a part of the system. */
private fun LazyListScope.riskReview(
    settings: DebloatSettings,
    facts: DebloatFacts,
    risks: Map<String, AppRisk>,
    onUnpick: (String) -> Unit,
) {
    val known = facts.inventory.map(DebloatViewModel::selectorOf).toSet()
    val missing = settings.removeSelectors.filter { facts.loaded && it !in known }
    val risky = settings.removeSelectors.filter { !risks[it]?.warnings.isNullOrEmpty() }
    item {
        Hint(
            if (missing.isEmpty() && risky.isEmpty()) {
                "No problems: every pick exists in the assembled tree and none is a part of the system."
            } else {
                "Review these picks before building."
            },
        )
    }
    if (missing.isNotEmpty()) {
        item { Hint("Not apps of this firmware; the build stops until you remove them:") }
        items(missing) { selector -> PickRow(selector, onUnpick, isProblem = true) }
    }
    if (risky.isNotEmpty()) {
        item { Hint("Parts of the system: remove them only on purpose.") }
        items(risky) { selector ->
            Column {
                PickRow(selector, onUnpick)
                risks[selector]?.warnings?.forEach { Reason(it, GlassTheme.diagnosticColors.warning) }
            }
        }
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
internal fun Line(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = codeFontFamily(),
    )
}

@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}

private fun label(candidate: DebloatCandidate) = "${candidate.partition}/${candidate.relativePath}"

private fun LazyListScope.removeSelections(
    settings: DebloatSettings,
    facts: DebloatFacts,
    picked: Set<String>,
    actions: DebloatActions,
    onUnpick: (String) -> Unit,
) {
    item { Hint(if (picked.isEmpty()) "Nothing picked." else "${picked.size} app(s) will be removed.") }
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
        item { Hint("The list matched ${match.effectiveRemovals.size} app(s).") }
        if (match.blockedProtected.isNotEmpty()) {
            item { Hint("Protected or needed by the device, not picked:") }
            items(match.blockedProtected) { Line(it.id) }
        }
        if (match.unknown.isNotEmpty()) {
            item { Hint("Not an app of this firmware, skipped:") }
            items(match.unknown) { Line(it) }
        }
    }
    items(settings.removeSelectors) { selector -> PickRow(selector, onUnpick) }
}

private fun LazyListScope.results(facts: DebloatFacts, actions: DebloatActions) {
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
    item {
        Hint(
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
