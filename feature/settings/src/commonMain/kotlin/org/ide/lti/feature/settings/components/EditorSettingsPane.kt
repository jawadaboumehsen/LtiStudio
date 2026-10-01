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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily

/**
 * Editor Preferences Pane providing full parity with the target design mockup.
 *
 * Implements:
 * - Breadcrumb navigation (`Settings / Editor preferences`)
 * - Header with `Get help` contextual action card
 * - `General` card: File encoding, EOL mode, Trim trailing whitespace, Final newline
 * - `Editor appearance` card: Font text scale stepper + reset, density dropdown,
 *   focus indicator, line numbers, indent guides
 * - `Keyboard shortcuts` card: 2-column keymap reference table + View all shortcuts CTA
 * - `Draft recovery` card: Automatic backup toggle + Protected vs saved callout card
 * - Status bar footer: Recovery status indicator, connection dot, environment concept label
 */
@Composable
fun EditorSettingsPane(modifier: Modifier = Modifier) {
    var selectedEncoding by remember { mutableStateOf("UTF-8") }
    var selectedEol by remember { mutableStateOf("LF (Unix)") }
    var trimTrailingWhitespace by remember { mutableStateOf(true) }
    var ensureFinalNewline by remember { mutableStateOf(true) }

    var textScalePercent by remember { mutableIntStateOf(100) }
    var selectedDensity by remember { mutableStateOf("Comfortable") }
    var focusIndicatorEnabled by remember { mutableStateOf(true) }
    var showLineNumbers by remember { mutableStateOf(true) }
    var showIndentGuides by remember { mutableStateOf(true) }

    var draftRecoveryEnabled by remember { mutableStateOf(true) }

    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val cardBorder = MaterialTheme.colorScheme.outline
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val textDim = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().testTag("EditorSettingsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        EditorBreadcrumbs(
            textPrimary = textPrimary,
            textMuted = textMuted,
            textDim = textDim,
        )

        EditorHelpHeader(
            textPrimary = textPrimary,
            textMuted = textMuted,
            cardBg = cardBg,
            cardBorder = cardBorder,
        )

        GeneralSettingsCard(
            selectedEncoding = selectedEncoding,
            onSelectEncoding = { selectedEncoding = it },
            selectedEol = selectedEol,
            onSelectEol = { selectedEol = it },
            trimTrailingWhitespace = trimTrailingWhitespace,
            onToggleTrimWhitespace = { trimTrailingWhitespace = it },
            ensureFinalNewline = ensureFinalNewline,
            onToggleEnsureNewline = { ensureFinalNewline = it },
            cardBg = cardBg,
            textPrimary = textPrimary,
            textMuted = textMuted,
        )

        EditorAppearanceCard(
            textScalePercent = textScalePercent,
            onTextScaleChange = { textScalePercent = it },
            selectedDensity = selectedDensity,
            onSelectDensity = { selectedDensity = it },
            focusIndicatorEnabled = focusIndicatorEnabled,
            onToggleFocusIndicator = { focusIndicatorEnabled = it },
            showLineNumbers = showLineNumbers,
            onToggleLineNumbers = { showLineNumbers = it },
            showIndentGuides = showIndentGuides,
            onToggleIndentGuides = { showIndentGuides = it },
            cardBg = cardBg,
            textPrimary = textPrimary,
            textMuted = textMuted,
        )

        KeyboardShortcutsCard(
            cardBg = cardBg,
            textPrimary = textPrimary,
            textMuted = textMuted,
        )

        DraftRecoveryCard(
            draftRecoveryEnabled = draftRecoveryEnabled,
            onToggleDraftRecovery = { draftRecoveryEnabled = it },
            cardBg = cardBg,
            textPrimary = textPrimary,
            textMuted = textMuted,
        )

        EditorStatusBar(
            textPrimary = textPrimary,
            textMuted = textMuted,
            textDim = textDim,
        )
    }
}

@Composable
private fun EditorBreadcrumbs(textPrimary: Color, textMuted: Color, textDim: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Settings",
            fontSize = FontSize.BodySmall,
            color = textMuted,
        )
        Text(
            text = "/",
            fontSize = FontSize.BodySmall,
            color = textDim,
        )
        Text(
            text = "Editor preferences",
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
        )
    }
}

@Composable
private fun EditorHelpHeader(textPrimary: Color, textMuted: Color, cardBg: Color, cardBorder: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = "Editor preferences",
                fontSize = FontSize.HeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
            )
            Text(
                text = "Customize the editing experience",
                fontSize = FontSize.BodyMedium,
                color = textMuted,
            )
        }

        Row(
            modifier = Modifier
                .clip(GlassShapes.Card)
                .background(cardBg)
                .border(
                    width = GlassDimens.HairlineBorder,
                    color = cardBorder,
                    shape = GlassShapes.Card,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Help action */ },
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = "Get help",
                tint = textPrimary,
                modifier = Modifier.size(IconSize.Large),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "Get help",
                    fontSize = FontSize.LabelMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Text(
                    text = "Editor settings and tips",
                    fontSize = FontSize.LabelSmall,
                    color = textMuted,
                )
            }
            Spacer(modifier = Modifier.width(Spacing.ExtraSmall))
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
private fun GeneralSettingsCard(
    selectedEncoding: String,
    onSelectEncoding: (String) -> Unit,
    selectedEol: String,
    onSelectEol: (String) -> Unit,
    trimTrailingWhitespace: Boolean,
    onToggleTrimWhitespace: (Boolean) -> Unit,
    ensureFinalNewline: Boolean,
    onToggleEnsureNewline: (Boolean) -> Unit,
    cardBg: Color,
    textPrimary: Color,
    textMuted: Color,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = cardBg,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            EditorCardHeader(
                icon = Icons.Default.Settings,
                title = "General",
                subtitle = "Core editing behavior and file handling",
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorDropdownRow(
                icon = Icons.Default.Description,
                title = "Default file encoding",
                subtitle = "Used for new files and when encoding is not detected",
                selectedOption = selectedEncoding,
                options = listOf("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII"),
                onSelect = onSelectEncoding,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorDropdownRow(
                icon = Icons.Default.Description,
                title = "End of line (EOL)",
                subtitle = "Line ending for new files",
                selectedOption = selectedEol,
                options = listOf("LF (Unix)", "CRLF (Windows)", "CR (Classic Mac)"),
                onSelect = onSelectEol,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorToggleRow(
                icon = Icons.Default.Edit,
                title = "Trim trailing whitespace on save",
                subtitle = "Remove unnecessary spaces at end of lines",
                checked = trimTrailingWhitespace,
                onCheckedChange = onToggleTrimWhitespace,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorToggleRow(
                icon = Icons.Default.Save,
                title = "Ensure final newline",
                subtitle = "Add newline at end of file when saving",
                checked = ensureFinalNewline,
                onCheckedChange = onToggleEnsureNewline,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )
        }
    }
}

@Composable
private fun EditorAppearanceCard(
    textScalePercent: Int,
    onTextScaleChange: (Int) -> Unit,
    selectedDensity: String,
    onSelectDensity: (String) -> Unit,
    focusIndicatorEnabled: Boolean,
    onToggleFocusIndicator: (Boolean) -> Unit,
    showLineNumbers: Boolean,
    onToggleLineNumbers: (Boolean) -> Unit,
    showIndentGuides: Boolean,
    onToggleIndentGuides: (Boolean) -> Unit,
    cardBg: Color,
    textPrimary: Color,
    textMuted: Color,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = cardBg,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            EditorCardHeader(
                icon = Icons.Default.Palette,
                title = "Editor appearance",
                subtitle = "Text size, layout density and focus",
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            TextScaleStepperRow(
                textScalePercent = textScalePercent,
                onTextScaleChange = onTextScaleChange,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorDropdownRow(
                icon = Icons.Default.Tune,
                title = "Editor density",
                subtitle = "Adjust spacing and line height",
                selectedOption = selectedDensity,
                options = listOf("Comfortable", "Compact", "Expanded"),
                onSelect = onSelectDensity,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorToggleRow(
                icon = Icons.Default.Build,
                title = "Focus indicator",
                subtitle = "Show a clear outline around the active editor",
                checked = focusIndicatorEnabled,
                onCheckedChange = onToggleFocusIndicator,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorToggleRow(
                icon = Icons.Default.Info,
                title = "Show line numbers",
                subtitle = "Display line numbers in the editor",
                checked = showLineNumbers,
                onCheckedChange = onToggleLineNumbers,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            EditorToggleRow(
                icon = Icons.Default.Code,
                title = "Show indent guides",
                subtitle = "Visualize code structure with indent guides",
                checked = showIndentGuides,
                onCheckedChange = onToggleIndentGuides,
                textPrimary = textPrimary,
                textMuted = textMuted,
            )
        }
    }
}

@Composable
private fun TextScaleStepperRow(
    textScalePercent: Int,
    onTextScaleChange: (Int) -> Unit,
    textPrimary: Color,
    textMuted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(IconSize.SidePanelRail),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "Text scale",
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                )
                Text(
                    text = "Scale font size up or down relative to 100%",
                    fontSize = FontSize.BodySmall,
                    color = textMuted,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Row(
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.Small,
                    )
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease text scale",
                    tint = textPrimary,
                    modifier = Modifier
                        .size(IconSize.Small)
                        .clickable {
                            if (textScalePercent > 70) {
                                onTextScaleChange(textScalePercent - 5)
                            }
                        },
                )
                Text(
                    text = "$textScalePercent%",
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.ExtraSmall),
                )
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase text scale",
                    tint = textPrimary,
                    modifier = Modifier
                        .size(IconSize.Small)
                        .clickable {
                            if (textScalePercent < 200) {
                                onTextScaleChange(textScalePercent + 5)
                            }
                        },
                )
            }

            Box(
                modifier = Modifier
                    .clip(GlassShapes.Small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = GlassDimens.HairlineBorder,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.Small,
                    )
                    .clickable { onTextScaleChange(100) }
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reset",
                    fontSize = FontSize.LabelMedium,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                )
            }
        }
    }
}

@Composable
private fun KeyboardShortcutsCard(cardBg: Color, textPrimary: Color, textMuted: Color) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = cardBg,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            EditorCardHeader(
                icon = Icons.Default.Keyboard,
                title = "Keyboard shortcuts",
                subtitle = "Common shortcuts (Windows)",
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        ShortcutItem(
                            label = "Find",
                            keys = listOf("Ctrl", "+", "F"),
                            textPrimary = textPrimary,
                        )
                        ShortcutItem(
                            label = "Find next",
                            keys = listOf("F3"),
                            textPrimary = textPrimary,
                        )
                        ShortcutItem(
                            label = "Find previous",
                            keys = listOf("Shift", "+", "F3"),
                            textPrimary = textPrimary,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        ShortcutItem(
                            label = "Go to line",
                            keys = listOf("Ctrl", "+", "G"),
                            textPrimary = textPrimary,
                        )
                        ShortcutItem(
                            label = "Toggle comment",
                            keys = listOf("Ctrl", "+", "/"),
                            textPrimary = textPrimary,
                        )
                        ShortcutItem(
                            label = "Format document",
                            keys = listOf("Ctrl", "+", "Shift", "+", "F"),
                            textPrimary = textPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.Large))

                Row(
                    modifier = Modifier
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = MaterialTheme.colorScheme.outline,
                            shape = GlassShapes.Small,
                        )
                        .clickable { /* open shortcuts list */ }
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = textPrimary,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text(
                        text = "View all shortcuts",
                        fontSize = FontSize.LabelMedium,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftRecoveryCard(
    draftRecoveryEnabled: Boolean,
    onToggleDraftRecovery: (Boolean) -> Unit,
    cardBg: Color,
    textPrimary: Color,
    textMuted: Color,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        surfaceColor = cardBg,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            EditorCardHeader(
                icon = Icons.Default.History,
                title = "Draft recovery",
                subtitle = "Automatically protect your work",
                textPrimary = textPrimary,
                textMuted = textMuted,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1.1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = textMuted,
                            modifier = Modifier.size(IconSize.SidePanelRail),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                            Text(
                                text = "Enable draft recovery",
                                fontSize = FontSize.BodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary,
                            )
                            Text(
                                text = "Automatically save your unsaved changes at regular intervals",
                                fontSize = FontSize.BodySmall,
                                color = textMuted,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassToggle(
                            checked = draftRecoveryEnabled,
                            onCheckedChange = onToggleDraftRecovery,
                            activeColor = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (draftRecoveryEnabled) "Enabled" else "Disabled",
                            fontSize = FontSize.LabelMedium,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(0.9f)
                        .clip(GlassShapes.Card)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = GlassDimens.HairlineBorder,
                            color = MaterialTheme.colorScheme.outline,
                            shape = GlassShapes.Card,
                        )
                        .padding(Spacing.Medium),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Large),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                        Text(
                            text = "Protected vs saved",
                            fontSize = FontSize.LabelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                        )
                        Text(
                            text = "Protected drafts are temporary local backups used for recovery. " +
                                "They are not the same as saved files and won't modify your files " +
                                "unless you choose to restore them.",
                            fontSize = FontSize.LabelSmall,
                            color = textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorStatusBar(textPrimary: Color, textMuted: Color, textDim: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Draft recovery enabled • Unsaved changes are automatically protected",
                fontSize = FontSize.LabelSmall,
                color = textMuted,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Box(
                    modifier = Modifier
                        .size(IconSize.Indicator)
                        .clip(GlassShapes.Circle)
                        .background(GlassTheme.diagnosticColors.success),
                )
                Text(
                    text = "Connected",
                    fontSize = FontSize.LabelSmall,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                )
            }

            Text(
                text = "Design concept • Sample data",
                fontSize = FontSize.LabelSmall,
                color = textDim,
            )
        }
    }
}

@Composable
private fun EditorCardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textMuted: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textPrimary,
            modifier = Modifier.size(IconSize.SidePanelRail),
        )
        Text(
            text = title,
            fontSize = FontSize.TitleMedium,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
        )
        Text(
            text = subtitle,
            fontSize = FontSize.BodySmall,
            color = textMuted,
        )
    }
}

@Composable
private fun EditorDropdownRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selectedOption: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    textPrimary: Color,
    textMuted: Color,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(IconSize.SidePanelRail),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = title,
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                )
                Text(
                    text = subtitle,
                    fontSize = FontSize.BodySmall,
                    color = textMuted,
                )
            }
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
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = selectedOption,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = textMuted,
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                color = textPrimary,
                                fontSize = FontSize.BodySmall,
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
private fun EditorToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textPrimary: Color,
    textMuted: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textMuted,
                modifier = Modifier.size(IconSize.SidePanelRail),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = title,
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                )
                Text(
                    text = subtitle,
                    fontSize = FontSize.BodySmall,
                    color = textMuted,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                activeColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (checked) "Enabled" else "Disabled",
                fontSize = FontSize.LabelMedium,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
            )
        }
    }
}

@Composable
private fun ShortcutItem(label: String, keys: List<String>, textPrimary: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = FontSize.BodySmall,
            color = textPrimary,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            keys.forEach { key ->
                if (key == "+") {
                    Text(
                        text = "+",
                        fontSize = FontSize.LabelSmall,
                        color = textPrimary,
                        modifier = Modifier.padding(horizontal = Spacing.Hairline),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(GlassShapes.ExtraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = GlassDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.outline,
                                shape = GlassShapes.ExtraSmall,
                            )
                            .padding(
                                horizontal = Spacing.Small,
                                vertical = Spacing.Hairline,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            fontFamily = codeFontFamily(),
                            fontSize = FontSize.LabelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                        )
                    }
                }
            }
        }
    }
}
