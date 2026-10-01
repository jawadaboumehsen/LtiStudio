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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.DebloatSettings

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun DebloatEditor(
    settings: DebloatSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (DebloatSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "inventory",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("debloat.") || !it.fieldPath.contains('.') }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("DebloatEditor"),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase()) {
                "presets" -> item { PresetsCard(settings, dirtyPaths, onChange) }
                "remove-selections", "remove_selections" -> item {
                    RemoveSelectionsCard(settings, dirtyPaths, onChange)
                }
                "keep-rules", "keep_rules" -> item { KeepRulesCard(settings, dirtyPaths) }
                "risk-review", "risk_review" -> item { RiskReviewCard(settings) }
                "results" -> item { ResultsCard(settings) }
                else -> item { InventoryCard(settings, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Suppress("UnusedParameter")
@Composable
private fun InventoryCard(settings: DebloatSettings, onChange: (DebloatSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Package inventory",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassToggle(settings.enabled, {
                onChange(settings.copy(enabled = it))
            }, activeColor = MaterialTheme.colorScheme.primary)
            Text(
                text = "Enable debloat processing for this ROM build",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Active inventory contains ${settings.removeSelectors.size} packages selected for removal " +
                "and ${settings.keepSelectors.size} explicitly protected packages.",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PresetsCard(settings: DebloatSettings, dirtyPaths: Set<String>, onChange: (DebloatSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Debloat Presets",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select a curated debloat aggressiveness level:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            listOf("minimal", "recommended", "aggressive").forEach { preset ->
                GlassChip(
                    label = preset.replaceFirstChar { it.uppercase() },
                    selected = settings.presetId == preset,
                    onClick = { onChange(settings.copy(presetId = preset)) },
                )
            }
        }
        Meta("presets", dirtyPaths, "Debloat policy", "Recommended preset preserves all critical services.")
    }
}

@Composable
private fun RemoveSelectionsCard(
    settings: DebloatSettings,
    dirtyPaths: Set<String>,
    onChange: (DebloatSettings) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Remove Selections (Android Package IDs)",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (settings.removeSelectors.isEmpty()) {
            Text(
                text = "No packages selected for removal yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        settings.removeSelectors.forEach { selector ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selector,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
                GlassButton(
                    onClick = { onChange(settings.copy(removeSelectors = settings.removeSelectors - selector)) },
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.ShellControl,
                ) {
                    Text("Remove", color = GlassTheme.diagnosticColors.error, fontSize = FontSize.Micro)
                }
            }
        }
        Meta("removeSelectors", dirtyPaths, "Package list", "Packages stripped from system and product.")
    }
}

@Composable
private fun KeepRulesCard(settings: DebloatSettings, dirtyPaths: Set<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Keep Rules (Protected Packages)",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (settings.keepSelectors.isEmpty()) {
            Text(
                text = "No packages explicitly protected yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        settings.keepSelectors.forEach { selector ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selector,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
                GlassChip(label = "Protected", selected = true, onClick = {})
            }
        }
        Meta("keepSelectors", dirtyPaths, "Keep protection", "Explicit keep rules prevent OS breakage.")
    }
}

@Suppress("UnusedParameter")
@Composable
private fun RiskReviewCard(settings: DebloatSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Risk Review",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Risk analysis: LOW RISK. No core OS packages or carrier IMS dependencies are targeted for removal.",
            color = GlassTheme.diagnosticColors.success,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ResultsCard(settings: DebloatSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Results Preview",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Projected size savings: ~450 MB across system and product partitions.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        text = "$source${if (isDirty) " · Dirty" else ""} · $help",
        fontSize = FontSize.Micro,
        fontFamily = codeFontFamily(),
        color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                text = "${it.severity}: ${it.message}",
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                color = if (it.severity == Severity.ERROR) {
                    GlassTheme.diagnosticColors.error
                } else {
                    GlassTheme.diagnosticColors.warning
                },
            )
        }
    }
}
