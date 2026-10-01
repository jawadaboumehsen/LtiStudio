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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

private data class PropertyEntry(
    val key: String,
    val value: String,
    val source: String = "Custom",
    val hasError: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Stage Assemble subobject pane for Build properties.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "LongMethod", "UnusedParameter")
@Composable
public fun BuildPropertiesPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var buildType by remember(settings.buildType) {
        mutableStateOf(if (settings.buildType.isNotBlank()) settings.buildType else "userdebug")
    }
    var romVersion by remember(settings.romVersion) {
        mutableStateOf(if (settings.romVersion.isNotBlank()) settings.romVersion else "1.0.0-dev")
    }

    val selectedIndices = remember { mutableStateListOf<Int>() }

    val properties = remember {
        mutableStateListOf(
            PropertyEntry("ro.rom.display_name", target.codename),
            PropertyEntry("ro.rom.version", "1.0.0-dev"),
            PropertyEntry("ro.build.description", "${target.codename}-userdebug 14 API34"),
            PropertyEntry("persist.sys.locale", "en-US"),
            PropertyEntry(
                key = "ro.build.notes",
                value = "Initial build\\Test\"",
                hasError = true,
                errorMessage = "Value cannot contain newline characters. Use a single line.",
            ),
            PropertyEntry("debug.sf.nobootanimation", "1"),
        )
    }

    var activeHelpTab by remember { mutableStateOf(0) }
    val helpTabs = listOf("Overview", "Fields", "Examples")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("BuildPropertiesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        // 2-Column Layout (Left ~65%, Right ~35%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // ==================== LEFT COLUMN ====================
            Column(
                modifier = Modifier.weight(1.8f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Section: General configuration
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.ShellCard)
                        .ideCardSurface(shape = GlassShapes.ShellCard)
                        .padding(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = "General configuration",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = ideFontFamily(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        // Build type dropdown
                        BuildTypeDropdown(
                            selected = buildType,
                            onSelect = {
                                buildType = it
                                onChange(settings.copy(buildType = it))
                            },
                            modifier = Modifier.weight(1f),
                        )

                        // ROM version field
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                Text(
                                    text = "ROM version",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = FontSize.Micro,
                                    fontFamily = ideFontFamily(),
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.ExtraSmall),
                                )
                            }

                            BasicTextField(
                                value = romVersion,
                                onValueChange = {
                                    romVersion = it
                                    onChange(settings.copy(romVersion = it))
                                },
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = FontSize.Micro,
                                    fontFamily = codeFontFamily(),
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                                    .border(
                                        width = GlassDimens.HairlineBorder,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        shape = GlassShapes.ShellControl,
                                    )
                                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
                            )
                        }
                    }

                    Text(
                        text = "Use semantic versioning (e.g. 1.0.0 or 1.0.0-dev).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                }

                // Section: Property overrides
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.ShellCard)
                        .ideCardSurface(shape = GlassShapes.ShellCard)
                        .padding(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    // Title + Actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                Text(
                                    text = "Property overrides",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = FontSize.BodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = ideFontFamily(),
                                )
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(IconSize.ExtraSmall),
                                )
                            }
                            Text(
                                text = "Add or override system properties for the build. " +
                                    "Invalid entries are highlighted.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = FontSize.Micro,
                                fontFamily = ideFontFamily(),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            GlassButton(
                                onClick = {
                                    properties.add(PropertyEntry("ro.custom.prop", "value"))
                                },
                                variant = GlassButtonVariant.Standard,
                                shape = GlassShapes.ShellControl,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(IconSize.ExtraSmall),
                                    )
                                    Text(
                                        "+ Add property",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = FontSize.Micro,
                                    )
                                }
                            }

                            GlassButton(
                                onClick = {
                                    val toRemove = selectedIndices.sortedDescending()
                                    toRemove.forEach { idx ->
                                        if (idx in properties.indices) {
                                            properties.removeAt(idx)
                                        }
                                    }
                                    selectedIndices.clear()
                                },
                                variant = GlassButtonVariant.Standard,
                                shape = GlassShapes.ShellControl,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(IconSize.ExtraSmall),
                                    )
                                    Text(
                                        "Remove selected",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = FontSize.Micro,
                                    )
                                }
                            }
                        }
                    }

                    // Properties Table Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                            modifier = Modifier.width(Spacing.Large),
                        )
                        Box(modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth)) {
                            Checkbox(
                                checked = selectedIndices.size == properties.size && properties.isNotEmpty(),
                                onCheckedChange = { checkAll ->
                                    selectedIndices.clear()
                                    if (checkAll) {
                                        selectedIndices.addAll(properties.indices)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                        Text(
                            text = "Property name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                            modifier = Modifier.weight(1.5f),
                        )
                        Text(
                            text = "Value",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                            modifier = Modifier.weight(2f),
                        )
                        Text(
                            text = "Source",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                            modifier = Modifier.weight(0.8f),
                        )
                        Spacer(Modifier.width(Spacing.Large))
                    }

                    HorizontalDivider(
                        thickness = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    // Properties Table Rows
                    properties.forEachIndexed { index, prop ->
                        val isChecked = index in selectedIndices
                        PropertyRowItem(
                            index = index + 1,
                            isChecked = isChecked,
                            onCheckedChange = { checked ->
                                if (checked) selectedIndices.add(index) else selectedIndices.remove(index)
                            },
                            entry = prop,
                        )
                    }
                }

                // Validation Alert Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.ShellCard)
                        .ideCardSurface(shape = GlassShapes.ShellCard)
                        .padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                            Text(
                                text = "Validation",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = FontSize.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = ideFontFamily(),
                            )
                            Text(
                                text = "Found 1 error. Fix the highlighted fields and run validation again.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = FontSize.Micro,
                                fontFamily = ideFontFamily(),
                            )
                        }
                    }

                    GlassButton(
                        onClick = onValidate,
                        variant = GlassButtonVariant.Standard,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(IconSize.ExtraSmall),
                            )
                            Text("Validate", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.Micro)
                        }
                    }
                }
            }

            // ==================== RIGHT COLUMN ====================
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Card 1: Reserved fields
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.ShellCard)
                        .ideCardSurface(shape = GlassShapes.ShellCard)
                        .padding(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(
                            text = "Reserved fields",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = FontSize.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                        )
                    }

                    Text(
                        text = "Device identifiers and security settings are managed by the build system.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )

                    ReservedFieldItem("Target device", target.codename)
                    ReservedFieldItem("AVB signing", "Use default keys")
                    ReservedFieldItem("Verified Boot", "Enforcing (default)")
                }

                // Card 2: Help
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.ShellCard)
                        .ideCardSurface(shape = GlassShapes.ShellCard)
                        .padding(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(
                            text = "Help",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = FontSize.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = ideFontFamily(),
                        )
                    }

                    // Help Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        helpTabs.forEachIndexed { index, tabName ->
                            val isSelected = index == activeHelpTab
                            Column(
                                modifier = Modifier
                                    .clickable { activeHelpTab = index }
                                    .padding(vertical = Spacing.Hairline),
                            ) {
                                Text(
                                    text = tabName,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontSize = FontSize.Micro,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = ideFontFamily(),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(Spacing.Large)
                                            .height(GlassDimens.HairlineBorder)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        thickness = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    val helpText = "Build properties allow you to override system properties that will " +
                        "be included in the generated system image.\n\n" +
                        "Use this page to set a custom ROM name, version and other properties. " +
                        "Reserved fields are locked to prevent configuration errors."
                    Text(
                        text = helpText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )

                    Row(
                        modifier = Modifier
                            .clip(GlassShapes.ShellControl)
                            .clickable { }
                            .padding(vertical = Spacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "Open build properties documentation",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = FontSize.Micro,
                            fontFamily = ideFontFamily(),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.ExtraSmall),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuildTypeDropdown(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("userdebug", "user", "eng")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "Build type",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.ShellControl)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        GlassDimens.HairlineBorder,
                        MaterialTheme.colorScheme.outlineVariant,
                        GlassShapes.ShellControl,
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = selected,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            GlassDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = if (option ==
                                    selected
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = FontSize.Micro,
                                fontFamily = codeFontFamily(),
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PropertyRowItem(index: Int, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, entry: PropertyEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
                modifier = Modifier.width(Spacing.Large),
            )

            Box(modifier = Modifier.width(GlassDimens.AcquireTableIconColWidth)) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = onCheckedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            // Property name
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .padding(end = Spacing.ExtraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                    .border(
                        GlassDimens.HairlineBorder,
                        if (entry.hasError) {
                            GlassTheme.diagnosticColors.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        GlassShapes.ShellControl,
                    )
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
            ) {
                Text(
                    text = entry.key,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }

            // Value (with error border and error icon if hasError)
            Box(
                modifier = Modifier
                    .weight(2f)
                    .padding(end = Spacing.ExtraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                    .border(
                        GlassDimens.HairlineBorder,
                        if (entry.hasError) {
                            GlassTheme.diagnosticColors.error
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        GlassShapes.ShellControl,
                    )
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = entry.value,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.Micro,
                        fontFamily = codeFontFamily(),
                        modifier = Modifier.weight(1f),
                    )
                    if (entry.hasError) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = GlassTheme.diagnosticColors.error,
                            modifier = Modifier.size(IconSize.ExtraSmall),
                        )
                    }
                }
            }

            // Source
            Text(
                text = entry.source,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(0.8f),
            )

            // Action
            GlassIconButton(
                onClick = { },
                modifier = Modifier.size(Spacing.Large),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        if (entry.hasError && entry.errorMessage != null) {
            Text(
                text = entry.errorMessage,
                color = GlassTheme.diagnosticColors.error,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.padding(
                    start = Spacing.Large + GlassDimens.AcquireTableIconColWidth,
                    top = Spacing.Hairline,
                ),
            )
        }
    }
}

@Composable
private fun ReservedFieldItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, GlassShapes.ShellControl)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
        }
    }
}
