/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.ThemePreviewCard
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.ui.settings.AppSettingsState

/**
 * High-fidelity Appearance Settings Pane matching the Stitch & Fleet design standards.
 *
 * Implements:
 * - App style selection (Light, Dark, Blue Glass) with miniature window illustrations & check badges
 * - Accent color dropdown with live preview color dot
 * - Interface density segmented toggle (Comfortable vs Compact)
 * - Text size scaling dropdown (100%, 110%, 125%)
 * - Glass & motion switches (Glass effects, Fluid animations, Focus indicators)
 * - Live interactive Studio Preview Card reacting dynamically to theme, density, and transparency
 * - Action footer bar (Restore defaults, Cancel, Apply changes)
 */
// LongMethod suppressed on @Composable screen pane for appearance settings.
@Suppress("LongMethod")
@Composable
fun AppearanceSettingsPane(
    appSettingsState: AppSettingsState,
    onRestoreDefaults: () -> Unit,
    onApplyChanges: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceTransparency = !appSettingsState.effectsEnabled
    var showFocusIndicators by remember { mutableStateOf(true) }
    var isComfortable by remember { mutableStateOf(true) }
    var selectedAccentName by remember { mutableStateOf("Cobalt Blue") }
    var selectedTextSize by remember { mutableStateOf("100%") }

    val currentTheme = AppTheme.fromId(appSettingsState.theme)

    Column(
        modifier = modifier.fillMaxWidth().testTag("AppearanceSettingsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        // Page Title & Subtitle Header
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Appearance",
                fontSize = FontSize.HeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Make the studio comfortable for long sessions.",
                fontSize = FontSize.BodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Two-Column Split Layout: Controls on Left (weight 1.1f), Live Preview on Right (weight 0.9f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
        ) {
            // Left Controls Column
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Card 1: App style
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentPadding = Spacing.CardPadding,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                            Text(
                                text = "App style",
                                fontSize = FontSize.TitleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Choose how LtiRom Studio looks on your screen.",
                                fontSize = FontSize.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Theme Preview Cards Row (Light, Dark, Blue Glass)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        ) {
                            AppTheme.allThemes.forEach { theme ->
                                val isSelected = appSettingsState.theme == theme.id
                                ThemePreviewCard(
                                    theme = theme,
                                    isSelected = isSelected,
                                    onClick = { appSettingsState.theme = theme.id },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        GlassHorizontalDivider()

                        // Accent Color Row
                        AccentColorRow(
                            selectedAccent = selectedAccentName,
                            onSelectAccent = { selectedAccentName = it },
                        )

                        GlassHorizontalDivider()

                        // Interface Density Row
                        InterfaceDensityRow(
                            isComfortable = isComfortable,
                            onDensityChange = { isComfortable = it },
                        )

                        GlassHorizontalDivider()

                        // Text Size Row
                        TextSizeRow(
                            selectedSize = selectedTextSize,
                            onSelectSize = { selectedTextSize = it },
                        )
                    }
                }

                // Card 2: Glass & motion
                GlassCard(
                    modifier = Modifier.fillMaxWidth().testTag("GlassCard_glass_and_motion"),
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentPadding = Spacing.CardPadding,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                            Text(
                                text = "Glass & motion",
                                fontSize = FontSize.TitleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Navigation stays expressive. Editors stay readable.",
                                fontSize = FontSize.BodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Glass Effects Switch
                        SettingsSwitchRow(
                            title = "Glass effects",
                            description = "Use translucent glass and optical refraction.",
                            checked = appSettingsState.effectsEnabled,
                            onCheckedChange = { appSettingsState.effectsEnabled = it },
                            toggleTestTag = "ReduceTransparencySwitch",
                        )

                        GlassHorizontalDivider()

                        // Fluid Animations Switch
                        SettingsSwitchRow(
                            title = "Fluid animations",
                            description = "Enable spring physics, fluid stretching, and motion.",
                            checked = appSettingsState.fluidAnimations,
                            onCheckedChange = { appSettingsState.fluidAnimations = it },
                            toggleTestTag = "FluidAnimationsSwitch",
                        )

                        GlassHorizontalDivider()

                        // Focus Indicators Switch
                        SettingsSwitchRow(
                            title = "Focus indicators",
                            description = "Show keyboard focus ring for navigation visibility.",
                            checked = showFocusIndicators,
                            onCheckedChange = { showFocusIndicators = it },
                            toggleTestTag = "FocusIndicatorsSwitch",
                        )
                    }
                }
            }

            // Right Preview Column
            Column(
                modifier = Modifier.weight(0.85f),
                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                    Text(
                        text = "Preview",
                        fontSize = FontSize.TitleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "A sample of the current look and feel.",
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LiveStudioPreviewCard(
                    theme = currentTheme,
                    reduceTransparency = reduceTransparency,
                    isComfortable = isComfortable,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Small))

        // Bottom Action Footer Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Small)
                .testTag("AppearanceActionFooter"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Restore Defaults Button
            Box(
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.Small,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            showFocusIndicators = true
                            isComfortable = true
                            selectedAccentName = "Cobalt Blue"
                            selectedTextSize = "100%"
                            onRestoreDefaults()
                        },
                    )
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Restore defaults",
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Right Action Cluster: Hint + Cancel + Apply changes + Design concept
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                Text(
                    text = "Changes apply to this app only.",
                    fontSize = FontSize.LabelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Cancel Button
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = MaterialTheme.colorScheme.outline,
                            shape = GlassShapes.Small,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRestoreDefaults,
                        )
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = FontSize.BodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Apply Changes Button
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = MaterialTheme.colorScheme.primary,
                            shape = GlassShapes.Small,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onApplyChanges,
                        )
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Apply changes",
                        fontSize = FontSize.BodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandColors.OnLogoBadge,
                    )
                }

                Text(
                    text = "Design concept",
                    fontSize = FontSize.LabelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AccentColorRow(selectedAccent: String, onSelectAccent: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val accents = listOf("Cobalt Blue", "Cyan", "Emerald", "Purple", "Amber", "Crimson")

    val accentColor = when (selectedAccent) {
        "Cyan" -> AccentPresetColors.Cyan
        "Emerald" -> AccentPresetColors.Emerald
        "Purple" -> AccentPresetColors.Purple
        "Amber" -> AccentPresetColors.Amber
        "Crimson" -> AccentPresetColors.Crimson
        else -> AccentPresetColors.Blue
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Accent color",
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Used for highlights, selections and interactive elements.",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.Small,
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Box(
                    modifier = Modifier
                        .size(Spacing.SmallMedium)
                        .clip(GlassShapes.Circle)
                        .background(accentColor),
                )
                Text(
                    text = selectedAccent,
                    fontSize = FontSize.BodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                accents.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            onSelectAccent(name)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InterfaceDensityRow(isComfortable: Boolean, onDensityChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Interface density",
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Controls the spacing of UI elements.",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Segmented Toggle: [Comfortable | Compact]
        Row(
            modifier = Modifier
                .clip(GlassShapes.Small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = GlassDimens.HairlineBorder,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Small,
                )
                .padding(Spacing.ExtraExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DensitySegmentItem(
                label = "Comfortable",
                isSelected = isComfortable,
                onClick = { onDensityChange(true) },
            )
            DensitySegmentItem(
                label = "Compact",
                isSelected = !isComfortable,
                onClick = { onDensityChange(false) },
            )
        }
    }
}

@Composable
private fun DensitySegmentItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val activeBg = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
    val activeBorder = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .clip(GlassShapes.ExtraSmall)
            .background(if (isSelected) activeBg else Color.Transparent)
            .border(
                width = if (isSelected) GlassDimens.HairlineBorder else Spacing.None,
                color = if (isSelected) activeBorder else Color.Transparent,
                shape = GlassShapes.ExtraSmall,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = FontSize.BodySmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextSizeRow(selectedSize: String, onSelectSize: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sizes = listOf("90%", "100%", "110%", "125%")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Text size",
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Scales text throughout the app.",
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.Small,
                    )
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = selectedSize,
                    fontSize = FontSize.BodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                sizes.forEach { size ->
                    DropdownMenuItem(
                        text = { Text(size) },
                        onClick = {
                            onSelectSize(size)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    toggleTestTag: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = title,
                fontSize = FontSize.BodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontSize = FontSize.BodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GlassToggle(
            modifier = if (toggleTestTag != null) Modifier.testTag(toggleTestTag) else Modifier,
            checked = checked,
            onCheckedChange = onCheckedChange,
            activeColor = MaterialTheme.colorScheme.primary,
        )
    }
}
