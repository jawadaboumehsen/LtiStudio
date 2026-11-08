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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Presets the app keeps across workspaces: apply one to add its apps to the picks (matched by package name, so it
 * works on another firmware), or save the current picks under a name.
 */
@Composable
internal fun DebloatPresets(facts: DebloatFacts, canSave: Boolean, actions: DebloatActions) {
    var name by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.testTag("DebloatPresets")) {
        Hint("Presets: saved in this app, for any workspace. Applying one adds its apps to the picks.")
        facts.presets.forEach { preset ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Line(
                    when {
                        preset.device != null -> "${preset.name} (${preset.entries.size} apps, kept for this phone)"

                        preset.opinions.isNotEmpty() ->
                            "${preset.name} (community list: ${preset.opinions.size} rated, " +
                                "${preset.entries.size} recommended)"

                        else -> "${preset.name} (${preset.entries.size} apps)"
                    },
                )
                GlassButton(onClick = { actions.applyPreset(preset.name) }, variant = GlassButtonVariant.Standard) {
                    // A community list picks only what it recommends, and only when asked.
                    Text(
                        if (preset.opinions.isNotEmpty()) "Pick what it recommends" else "Apply",
                        fontSize = FontSize.BodySmall,
                    )
                }
                GlassButton(onClick = { actions.deletePreset(preset.name) }, variant = GlassButtonVariant.Text) {
                    Text("Delete", fontSize = FontSize.BodySmall)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Preset name",
                modifier = Modifier.widthIn(max = GlassDimens.OverviewMaxContentWidth / 2).testTag("PresetName"),
            )
            GlassButton(
                onClick = {
                    actions.savePreset(name)
                    name = ""
                },
                enabled = canSave && name.isNotBlank(),
                variant = GlassButtonVariant.Standard,
            ) {
                Text("Save the picks as a preset", fontSize = FontSize.BodySmall)
            }
        }
    }
}
