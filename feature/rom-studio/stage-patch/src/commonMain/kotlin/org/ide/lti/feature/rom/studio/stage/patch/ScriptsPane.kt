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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.ProjectScript
import org.ide.lti.core.ui.GlassCodeEditor

/** Navigator item of the Patches/Mods stage for the user's shell scripts. */
public const val SCRIPTS: String = "scripts"

/**
 * The user's shell scripts, run by the Patches stage after the modules. Each runs as
 * `sh <script> <work dir> <workspace dir>` from inside work/; a script that fails stops the build.
 */
@Composable
public fun ScriptsPane(
    settings: CustomizationSettings,
    onChange: (CustomizationSettings) -> Unit,
    modifier: Modifier = Modifier,
    /** The last `sh -n` of each script by name: `ok` or the error. */
    checks: Map<String, String> = emptyMap(),
    onCheck: (String) -> Unit = {},
) {
    val scripts = settings.scripts
    var selected by remember { mutableStateOf(scripts.firstOrNull()?.name) }
    var newName by remember { mutableStateOf("") }
    fun update(list: List<ProjectScript>) = onChange(settings.copy(scripts = list))
    val errors = StageSettingsValidator.validateCustomization(settings).errors
        .filter { it.code == StageSettingsValidator.INVALID_SCRIPT }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = GlassDimens.OverviewMaxContentWidth)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium)
            .testTag("ScriptsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Card("Scripts") {
            Hint(
                "Shell scripts run on the work tree after the modules, top to bottom: \$1 is work/, \$2 the " +
                    "workspace. Files a script adds are labelled as Android's build would: the ROM's own SELinux " +
                    "rules and fs_config tables (else Android's defaults), then their folder. A script that exits " +
                    "with an error stops the build.",
            )
            scripts.forEachIndexed { index, script ->
                ScriptRow(
                    script = script,
                    selected = script.name == selected,
                    first = index == 0,
                    last = index == scripts.lastIndex,
                    onSelect = { selected = script.name },
                    onEnable = { on -> update(scripts.map { if (it === script) it.copy(enabled = on) else it }) },
                    onMove = { by -> update(scripts.toMutableList().apply { add(index + by, removeAt(index)) }) },
                    onDelete = { update(scripts - script) },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "New script name (letters, digits, _ and -)",
                    modifier = Modifier.weight(1f).testTag("NewScriptName"),
                )
                GlassButton(
                    onClick = {
                        update(scripts + ProjectScript(newName.trim(), "#!/bin/sh\nset -e\nwork=\"\$1\"\n"))
                        selected = newName.trim()
                        newName = ""
                    },
                    enabled = newName.isNotBlank() && scripts.none { it.name == newName.trim() },
                    variant = GlassButtonVariant.Standard,
                ) { Text("Add", fontSize = FontSize.BodySmall) }
            }
            errors.forEach { Hint(it.message, isError = true) }
        }
        scripts.firstOrNull { it.name == selected }?.let { script ->
            Card(script.name) {
                GlassCodeEditor(
                    content = script.content,
                    onContentChange = { text ->
                        update(
                            scripts.map { if (it === script) it.copy(content = text) else it },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(ComponentSize.MaxPanelHeight).testTag("ScriptEditor"),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassButton(
                        onClick = { onCheck(script.name) },
                        variant = GlassButtonVariant.Standard,
                        modifier = Modifier.testTag("CheckScriptButton"),
                    ) { Text("Check", fontSize = FontSize.BodySmall) }
                    checks[script.name]?.let { result ->
                        if (result == "ok") Hint("sh -n: no syntax error.") else Hint(result, isError = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptRow(
    script: ProjectScript,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onSelect: () -> Unit,
    onEnable: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("ScriptRow_${script.name}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassToggle(
            checked = script.enabled,
            onCheckedChange = onEnable,
            activeColor = MaterialTheme.colorScheme.primary,
        )
        GlassChip(label = script.name, selected = selected, onClick = onSelect, modifier = Modifier.weight(1f))
        GlassButton(onClick = { onMove(-1) }, enabled = !first, variant = GlassButtonVariant.Standard) {
            Text("Up", fontSize = FontSize.Micro)
        }
        GlassButton(onClick = { onMove(1) }, enabled = !last, variant = GlassButtonVariant.Standard) {
            Text("Down", fontSize = FontSize.Micro)
        }
        GlassButton(
            onClick = onDelete,
            variant = GlassButtonVariant.Standard,
        ) { Text("Delete", fontSize = FontSize.Micro) }
    }
}

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
private fun Hint(text: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) GlassTheme.diagnosticColors.error else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.BodySmall,
        fontFamily = ideFontFamily(),
    )
}
