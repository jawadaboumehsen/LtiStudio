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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.component.progress.GlassProgressBar
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.PluginLockfileEntry

@Composable
internal fun PatchCatalog(
    settings: CustomizationSettings,
    facts: PatchFacts,
    onToggle: (String) -> Unit,
    onOpenPlugins: () -> Unit,
    onRefresh: () -> Unit,
) {
    val activePins = settings.enabledPackages.mapNotNull(settings::lockedEntry).associateBy { it.packageKey }
    PatchCard("Installed plugins", "PatchCatalog") {
        GlassButton(onClick = onOpenPlugins, variant = GlassButtonVariant.Standard) {
            Text("Install plugins…", fontSize = FontSize.BodySmall)
        }
        if (facts.installed.isEmpty() && (facts.loading || (!facts.loaded && facts.loadError == null))) {
            GlassLoadingPlaceholder("Loading installed plugins…")
        } else if (facts.loading) {
            GlassProgressBar(
                progress = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.ExtraSmall),
            )
        }
        facts.loadError?.let { message ->
            PatchProblem("Could not load installed plugins. $message")
            GlassButton(onClick = onRefresh, variant = GlassButtonVariant.Standard) {
                Text("Try again", fontSize = FontSize.BodySmall)
            }
        }
        if (facts.loaded && !facts.loading && facts.loadError == null && facts.installed.isEmpty()) {
            PatchHint("No plugins installed. Install .lti-mod.zip packages on the Plugins page.")
        }
        facts.installed.forEach { newest ->
            CatalogPluginRow(newest, activePins[newest.key], settings, facts, onToggle)
        }
    }
}

@Composable
private fun CatalogPluginRow(
    newest: InstalledRecord,
    pin: PluginLockfileEntry?,
    settings: CustomizationSettings,
    facts: PatchFacts,
    onToggle: (String) -> Unit,
) {
    val record = if (pin == null) newest else facts.installedVersions.firstOrNull { it.matches(pin) }
    val enabled = pin != null || newest.key in settings.enabledPackages
    val incompatibility = facts.incompatible[newest.key]
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record?.displayName?.takeIf { it.isNotBlank() } ?: newest.identity.id,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (record != null) {
                record.summary.takeIf { it.isNotBlank() }?.let { PatchHint(it) }
                incompatibility?.let { PatchProblem("This plugin $it") }
                PatchHint(
                    "v${record.identity.version} by ${record.identity.publisher}" +
                        record.dependencies.takeIf { it.isNotEmpty() }
                            ?.joinToString(prefix = " · needs ") { it.id }.orEmpty(),
                )
            } else if (pin != null) {
                PatchProblem("Locked version ${pin.version} is unavailable.")
            }
        }
        GlassToggle(
            checked = enabled,
            enabled = facts.canChangePlugins && (enabled || incompatibility == null),
            onCheckedChange = { onToggle(newest.key) },
            activeColor = MaterialTheme.colorScheme.primary,
        )
    }
}
