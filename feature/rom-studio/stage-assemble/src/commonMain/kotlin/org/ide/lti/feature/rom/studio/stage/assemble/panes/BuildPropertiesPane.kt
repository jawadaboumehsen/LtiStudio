/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Properties written into `system/build.prop`: the values the stage always stamps (read-only here) and
 * your overrides, which replace a key's value or add it.
 */
@Suppress("UnusedParameter")
@Composable
public fun BuildPropertiesPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("BuildPropertiesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Card("Always stamped") {
            Hint("Set by the Assemble stage from your settings; change them in their own fields.")
            Prop("ro.build.type", "${settings.buildType}  (ROM configuration → Build type)")
            Prop("org.lti.build.version", "${settings.romVersion}  (ROM configuration → ROM version)")
            Prop("org.lti.build.device", target.id)
            Prop("org.lti.build.date", "the run date")
            Prop("org.lti.build.variant", settings.buildType)
        }
        Card("Overrides") {
            Hint("Each line replaces that key in build.prop, or adds it when missing.")
            settings.propertyOverrides.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$key=$value",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontFamily = codeFontFamily(),
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        onClick = { onChange(settings.copy(propertyOverrides = settings.propertyOverrides - key)) },
                        variant = GlassButtonVariant.Standard,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.Micro)
                    }
                }
            }
            if (settings.propertyOverrides.isEmpty()) Hint("No overrides.")
            AddOverride { key, value ->
                onChange(settings.copy(propertyOverrides = settings.propertyOverrides + (key to value)))
            }
        }
    }
}

@Composable
private fun AddOverride(onAdd: (String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    val error = overrideError(key, value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassTextField(
            value = key,
            onValueChange = { key = it.trim() },
            placeholder = "key",
            modifier = Modifier.weight(1f),
        )
        GlassTextField(
            value = value,
            onValueChange = { value = it },
            placeholder = "value",
            modifier = Modifier.weight(1f),
        )
        GlassButton(
            onClick = {
                onAdd(key, value)
                key = ""
                value = ""
            },
            enabled = key.isNotEmpty() && error == null,
            variant = GlassButtonVariant.Primary,
            shape = GlassShapes.ShellControl,
        ) {
            Text("Add", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
        }
    }
    if (key.isNotEmpty() && error != null) {
        Text(error, color = GlassTheme.diagnosticColors.error, fontSize = FontSize.Micro, fontFamily = ideFontFamily())
    }
}

/** The same rules the Assemble stage enforces, so an invalid override cannot be added. */
internal fun overrideError(key: String, value: String): String? = when {
    key.any { it.isWhitespace() || it == '=' } -> "A key cannot contain spaces or '='."
    '\n' in value || '\u0000' in value -> "A value cannot contain a newline."
    key in RESERVED_KEYS -> "$key comes from the firmware and cannot be overridden."
    else -> null
}

private val RESERVED_KEYS = setOf("ro.build.version.release", "ro.build.version.security_patch")

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        content()
    }
}

@Composable
private fun Prop(key: String, value: String) {
    Text(
        text = "$key = $value",
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
