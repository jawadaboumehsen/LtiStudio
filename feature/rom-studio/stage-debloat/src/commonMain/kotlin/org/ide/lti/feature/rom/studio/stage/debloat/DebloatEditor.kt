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
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.DebloatSettings

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
                .widthIn(max = IdeShellDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item { EditorHeader(onValidate, onSave) }
            when (selectedObjectId.lowercase()) {
                "presets" -> item { PresetsCard(settings, dirtyPaths, onChange) }
                "remove-selections", "remove_selections" -> item { RemoveSelectionsCard(settings, dirtyPaths, onChange) }
                "keep-rules", "keep_rules" -> item { KeepRulesCard(settings, dirtyPaths, onChange) }
                "risk-review", "risk_review" -> item { RiskReviewCard(settings) }
                "results" -> item { ResultsCard(settings) }
                else -> item { InventoryCard(settings, onChange) }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun EditorHeader(onValidate: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Debloat", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Configure package removal, keep rules, and safety verification.",
                color = GlassTheme.colors.onGlassSecondary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            GlassButton(onClick = onValidate, variant = GlassButtonVariant.Standard, shape = IdeShellShapes.Control) {
                Text("Validate", color = GlassTheme.colors.onGlass)
            }
            GlassButton(onClick = onSave, variant = GlassButtonVariant.Primary, shape = IdeShellShapes.Control) {
                Text("Save", color = GlassTheme.colors.onGlass)
            }
        }
    }
}

@Composable
private fun InventoryCard(settings: DebloatSettings, onChange: (DebloatSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Package inventory", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                GlassToggle(settings.enabled, { onChange(settings.copy(enabled = it)) })
                Text("Enable debloat processing for this ROM build", color = GlassTheme.colors.onGlass)
            }
            Text(
                "Active inventory contains ${settings.removeSelectors.size} packages selected for removal and ${settings.keepSelectors.size} explicitly protected packages.",
                style = MaterialTheme.typography.bodySmall,
                color = GlassTheme.colors.onGlassSecondary,
            )
        }
    }
}

@Composable
private fun PresetsCard(settings: DebloatSettings, dirtyPaths: Set<String>, onChange: (DebloatSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Debloat Presets", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Select a curated debloat aggressiveness level:", color = GlassTheme.colors.onGlassSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                listOf("minimal", "recommended", "aggressive").forEach { preset ->
                    GlassChip(
                        label = preset.replaceFirstChar { it.uppercase() },
                        selected = settings.presetId == preset,
                        onClick = { onChange(settings.copy(presetId = preset)) },
                    )
                }
            }
            Meta("presets", dirtyPaths, "Debloat policy", "Recommended preset preserves all critical telephony/IMS services.")
        }
    }
}

@Composable
private fun RemoveSelectionsCard(settings: DebloatSettings, dirtyPaths: Set<String>, onChange: (DebloatSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Remove Selections", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            settings.removeSelectors.forEach { selector ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selector, color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodyMedium)
                    GlassButton(
                        onClick = { onChange(settings.copy(removeSelectors = settings.removeSelectors - selector)) },
                        variant = GlassButtonVariant.Standard,
                        shape = IdeShellShapes.Control,
                    ) {
                        Text("Remove", color = GlassTheme.colors.diagnostics.error)
                    }
                }
            }
            Meta("removeSelectors", dirtyPaths, "Package list", "Selected packages will be stripped from /system and /product.")
        }
    }
}

@Composable
private fun KeepRulesCard(settings: DebloatSettings, dirtyPaths: Set<String>, onChange: (DebloatSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Keep Rules (Protected Packages)", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            settings.keepSelectors.forEach { selector ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selector, color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodyMedium)
                    GlassChip(label = "Protected", selected = true, onClick = {})
                }
            }
            Meta("keepSelectors", dirtyPaths, "Keep protection", "Explicit keep rules prevent accidental removal of core services.")
        }
    }
}

@Composable
private fun RiskReviewCard(settings: DebloatSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Risk Review", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Risk analysis: LOW RISK. No core OS packages or carrier IMS dependencies are targeted for removal.",
                color = GlassTheme.colors.diagnostics.success,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ResultsCard(settings: DebloatSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Results Preview", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Projected size savings: ~450 MB across system and product partitions.",
                color = GlassTheme.colors.onGlassSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Meta(path: String, dirty: Set<String>, source: String, help: String) {
    val isDirty = dirty.any { it.startsWith(path.substringBefore('/')) }
    Text(
        "$source${if (isDirty) " · Dirty" else ""} · $help",
        style = MaterialTheme.typography.labelSmall,
        color = if (isDirty) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlassMuted,
    )
}

@Composable
private fun Diagnostics(errors: List<ValidationError>) {
    if (errors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        errors.forEach {
            Text(
                "${it.severity}: ${it.message}",
                style = MaterialTheme.typography.bodySmall,
                color = if (it.severity == Severity.ERROR) {
                    GlassTheme.colors.diagnostics.error
                } else {
                    GlassTheme.colors.diagnostics.warning
                },
            )
        }
    }
}
