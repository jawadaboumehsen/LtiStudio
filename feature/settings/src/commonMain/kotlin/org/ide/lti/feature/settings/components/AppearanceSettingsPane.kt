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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.ThemePreviewCard
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.AccentColor
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.Appearance
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.ui.settings.AppSettingsState
import kotlin.math.roundToInt

/**
 * High-fidelity Appearance Settings Pane matching the Stitch & Fleet design standards.
 *
 * Every control changes the app at once and is saved: app style (theme), glass effects and fluid animations.
 * The preview reacts to them; Restore defaults puts them back.
 */
// LongMethod suppressed on @Composable screen pane for appearance settings.
@Suppress("LongMethod")
@Composable
fun AppearanceSettingsPane(
    appSettingsState: AppSettingsState,
    onRestoreDefaults: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceTransparency = !appSettingsState.effectsEnabled

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
                        AppearanceChoices(appSettingsState)
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
                    isComfortable = !appSettingsState.appearance.compact,
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
                        onClick = onRestoreDefaults,
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

            // Changes are live and saved; there is nothing to apply or cancel.
            Text(
                text = "Changes apply at once and are saved for this app.",
                fontSize = FontSize.LabelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/** Accent colour, density and text size: saved, and applied to every screen at once. */
@Composable
private fun AppearanceChoices(appSettingsState: AppSettingsState) {
    val appearance = appSettingsState.appearance
    ChoiceRow("Accent color", "Buttons, selections and highlights.") {
        AccentColor.entries.forEach { accent ->
            GlassChip(
                label = accent.label,
                selected = appearance.accent == accent.id,
                onClick = { appSettingsState.appearance = appearance.copy(accent = accent.id) },
                leadingIcon = accent.color?.let { color ->
                    { Box(Modifier.size(Spacing.Small).clip(GlassShapes.Circle).background(color)) }
                },
                modifier = Modifier.testTag("Accent_${accent.id}"),
            )
        }
    }
    GlassHorizontalDivider()
    ChoiceRow("Interface density", "Compact tightens spacing and controls so more fits; text keeps its size.") {
        listOf(false to "Comfortable", true to "Compact").forEach { (compact, label) ->
            GlassChip(
                label = label,
                selected = appearance.compact == compact,
                onClick = { appSettingsState.appearance = appearance.copy(compact = compact) },
                modifier = Modifier.testTag("Density_$label"),
            )
        }
    }
    GlassHorizontalDivider()
    ChoiceRow("Text size", "Scales text throughout the app.") {
        Appearance.TextScales.forEach { scale ->
            val percent = (scale * PERCENT).roundToInt()
            GlassChip(
                label = "$percent%",
                selected = appearance.textScalePercent == percent,
                onClick = { appSettingsState.appearance = appearance.copy(textScalePercent = percent) },
                modifier = Modifier.testTag("TextScale_$percent"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(title: String, description: String, choices: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = title,
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = description, fontSize = FontSize.BodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) { choices() }
    }
}

private const val PERCENT = 100f
