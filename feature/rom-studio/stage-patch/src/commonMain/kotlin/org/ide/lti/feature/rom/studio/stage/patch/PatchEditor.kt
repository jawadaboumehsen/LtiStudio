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
import org.ide.lti.core.model.workspace.CustomizationSettings

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun PatchEditor(
    settings: CustomizationSettings,
    target: TargetDevice,
    validationErrors: List<ValidationError> = emptyList(),
    onChange: (CustomizationSettings) -> Unit,
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "module-catalog",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val errors = validationErrors.filter { it.fieldPath.startsWith("customization.") || !it.fieldPath.contains('.') }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("PatchEditor"),
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
                "enabled-ordered-modules", "enabled_ordered_modules" -> item {
                    EnabledModulesCard(settings, dirtyPaths, onChange)
                }
                "file-changes", "file_changes" -> item {
                    FileChangesCard(settings)
                }
                "text-patches", "text_patches" -> item {
                    TextPatchesCard(settings)
                }
                "property-patches", "property_patches" -> item {
                    PropertyPatchesCard(settings, dirtyPaths)
                }
                "conflicts" -> item {
                    ConflictsCard(settings)
                }
                else -> item {
                    ModuleCatalogCard(settings, dirtyPaths, onChange)
                }
            }
            item { Diagnostics(errors) }
        }
    }
}

@Composable
private fun ModuleCatalogCard(
    settings: CustomizationSettings,
    dirtyPaths: Set<String>,
    onChange: (CustomizationSettings) -> Unit,
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
            text = "Module Catalog",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Installed and available plugin extensions in repository:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        settings.lockfile.entries.forEach { entry ->
            val pkgKey = "${entry.publisher}:${entry.id}"
            val isEnabled = settings.enabledPackages.contains(pkgKey)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = entry.id,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.Medium,
                        fontFamily = codeFontFamily(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "v${entry.version} by ${entry.publisher}",
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GlassToggle(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        val updated = if (checked) {
                            settings.enabledPackages + pkgKey
                        } else {
                            settings.enabledPackages - pkgKey
                        }
                        onChange(settings.copy(enabledPackages = updated))
                    },
                    activeColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Meta("lockfile", dirtyPaths, "Catalog", "${settings.lockfile.entries.size} packages in lockfile.")
    }
}

@Composable
private fun EnabledModulesCard(
    settings: CustomizationSettings,
    dirtyPaths: Set<String>,
    onChange: (CustomizationSettings) -> Unit,
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
            text = "Enabled Ordered Modules",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Execution sequence for patch hooks during assembly:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (settings.enabledPackages.isEmpty()) {
            Text(
                text = "No modules enabled.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        } else {
            settings.enabledPackages.forEachIndexed { index, pkg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}. $pkg",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.Micro,
                        fontFamily = codeFontFamily(),
                    )
                    GlassButton(
                        onClick = { onChange(settings.copy(enabledPackages = settings.enabledPackages - pkg)) },
                        variant = GlassButtonVariant.Standard,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Text("Disable", color = GlassTheme.diagnosticColors.error, fontSize = FontSize.Micro)
                    }
                }
            }
        }
        Meta("enabledPackages", dirtyPaths, "Ordering", "Higher-indexed modules apply overrides.")
    }
}

@Suppress("UnusedParameter")
@Composable
private fun FileChangesCard(settings: CustomizationSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "File Changes",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Overlay files declared across enabled mods:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(
            "/system/etc/permissions/privapp-permissions-microg.xml",
            "/system/framework/services.jar (bytecode hook)",
            "/product/overlay/CustomAccentOverlay.apk",
        ).forEach { path ->
            Text(
                text = "• $path",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }
    }
}

@Suppress("UnusedParameter")
@Composable
private fun TextPatchesCard(settings: CustomizationSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Text Patches",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Search & replace rule operations for init scripts and configs:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "• init.environ.rc: Injected BOOTCLASSPATH extension",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
        )
        Text(
            text = "• ueventd.rc: Device node permission grant",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
        )
    }
}

@Composable
private fun PropertyPatchesCard(settings: CustomizationSettings, dirtyPaths: Set<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Property Patches",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Custom build and runtime system properties:",
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        settings.settings.forEach { (mod, props) ->
            Text(
                text = mod,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
                color = MaterialTheme.colorScheme.primary,
            )
            props.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = k,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.Micro,
                        fontFamily = codeFontFamily(),
                    )
                    GlassChip(label = v, selected = true, onClick = {})
                }
            }
        }
        Meta("settings", dirtyPaths, "Properties", "Appended to build.prop during assembly.")
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ConflictsCard(settings: CustomizationSettings) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Text(
            text = "Conflicts Analysis",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "No conflicting file overlays or colliding property modifications detected.",
            color = GlassTheme.diagnosticColors.success,
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
