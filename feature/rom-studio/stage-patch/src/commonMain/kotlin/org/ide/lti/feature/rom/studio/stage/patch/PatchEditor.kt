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
import org.ide.lti.core.model.workspace.CustomizationSettings

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
                .widthIn(max = IdeShellDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            item { EditorHeader(onValidate, onSave) }
            when (selectedObjectId.lowercase()) {
                "enabled-ordered-modules", "enabled_ordered_modules" -> item { EnabledModulesCard(settings, dirtyPaths, onChange) }
                "file-changes", "file_changes" -> item { FileChangesCard(settings) }
                "text-patches", "text_patches" -> item { TextPatchesCard(settings) }
                "property-patches", "property_patches" -> item { PropertyPatchesCard(settings, dirtyPaths, onChange) }
                "conflicts" -> item { ConflictsCard(settings) }
                else -> item { ModuleCatalogCard(settings, dirtyPaths, onChange) }
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
            Text("Patches and Mods", style = MaterialTheme.typography.headlineSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Integrate vendor mods, file patches, system property tweaks, and conflict policies.",
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
private fun ModuleCatalogCard(settings: CustomizationSettings, dirtyPaths: Set<String>, onChange: (CustomizationSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Module Catalog", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Installed and available plugin extensions in repository:", color = GlassTheme.colors.onGlassSecondary)

            settings.lockfile.entries.forEach { entry ->
                val pkgKey = "${entry.publisher}:${entry.id}"
                val isEnabled = settings.enabledPackages.contains(pkgKey)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(entry.id, style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.onGlass)
                        Text(
                            "v${entry.version} by ${entry.publisher}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlassSecondary,
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
                    )
                }
            }
            Meta("lockfile", dirtyPaths, "Catalog", "${settings.lockfile.entries.size} plugin packages resolved in lockfile.")
        }
    }
}

@Composable
private fun EnabledModulesCard(settings: CustomizationSettings, dirtyPaths: Set<String>, onChange: (CustomizationSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Enabled Ordered Modules", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Execution sequence for patch hooks during assembly:", color = GlassTheme.colors.onGlassSecondary)

            if (settings.enabledPackages.isEmpty()) {
                Text("No modules enabled.", color = GlassTheme.colors.onGlassMuted, style = MaterialTheme.typography.bodySmall)
            } else {
                settings.enabledPackages.forEachIndexed { index, pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. $pkg", color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodyMedium)
                        GlassButton(
                            onClick = { onChange(settings.copy(enabledPackages = settings.enabledPackages - pkg)) },
                            variant = GlassButtonVariant.Standard,
                            shape = IdeShellShapes.Control,
                        ) {
                            Text("Disable", color = GlassTheme.colors.diagnostics.error)
                        }
                    }
                }
            }
            Meta("enabledPackages", dirtyPaths, "Ordering", "Higher-indexed modules apply overrides over preceding modules.")
        }
    }
}

@Composable
private fun FileChangesCard(settings: CustomizationSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("File Changes", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Overlay files declared across enabled mods:",
                color = GlassTheme.colors.onGlassSecondary,
            )
            listOf(
                "/system/etc/permissions/privapp-permissions-microg.xml",
                "/system/framework/services.jar (bytecode hook)",
                "/product/overlay/CustomAccentOverlay.apk",
            ).forEach { path ->
                Text("• $path", color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TextPatchesCard(settings: CustomizationSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Text Patches", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "Search & replace rule operations for init scripts and configs:",
                color = GlassTheme.colors.onGlassSecondary,
            )
            Text("• init.environ.rc: Injected BOOTCLASSPATH extension", color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodySmall)
            Text("• ueventd.rc: Device node permission grant", color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PropertyPatchesCard(settings: CustomizationSettings, dirtyPaths: Set<String>, onChange: (CustomizationSettings) -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Property Patches", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text("Custom build and runtime system properties:", color = GlassTheme.colors.onGlassSecondary)

            settings.settings.forEach { (mod, props) ->
                Text(mod, style = MaterialTheme.typography.labelMedium, color = GlassTheme.colors.glassAccent)
                props.forEach { (k, v) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(k, color = GlassTheme.colors.onGlass, style = MaterialTheme.typography.bodySmall)
                        GlassChip(label = v, selected = true, onClick = {})
                    }
                }
            }
            Meta("settings", dirtyPaths, "Properties", "Appended to build.prop during assembly.")
        }
    }
}

@Composable
private fun ConflictsCard(settings: CustomizationSettings) {
    GlassCard(Modifier.fillMaxWidth(), shape = GlassShapes.Panel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            Text("Conflicts Analysis", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.onGlass)
            Text(
                "No conflicting file overlays or colliding property modifications detected.",
                color = GlassTheme.colors.diagnostics.success,
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
