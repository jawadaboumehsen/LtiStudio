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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Stage Assemble subobject pane for Boot preparation and inspection.
 * Faithfully matches media_1789783219884.jpg.
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
public fun BootPreparationPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    var bootSource by remember { mutableStateOf("From extracted build (recommended)") }
    var kernelRamdiskHandling by remember { mutableStateOf("Use extracted components") }
    var applyBootPatches by remember { mutableStateOf(true) }
    var avbHandling by remember { mutableStateOf("Preserve AVB configuration") }
    var bootFooterPolicyOption by remember { mutableStateOf("Use target policy (recommended)") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("BootPreparationPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AssembleHeader(
            title = "Boot preparation",
            subtitle = "Configure and verify the boot image for this build.",
            targetSpec = "${target.codename} / Development",
            targetRevision = "Target revision ${target.revision}",
            additionalContext = "Snapshot 12 ·",
            onValidate = onValidate,
            onSave = onSave,
            colors = colors,
        )

        // 2-Column Split: Left (Configuration), Right (Inspected & Affected)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // ==================== LEFT COLUMN: Configuration ====================
            GlassCard(
                modifier = Modifier.weight(1f),
                surfaceColor = colors.cardBackground,
                borderColor = colors.border,
                contentPadding = Spacing.Medium,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Text(
                        text = "Configuration",
                        color = colors.textMain,
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GlassFontFamily.ide(),
                    )
                    Text(
                        text = "Prepare the boot image using the target policy and selected components.",
                        color = colors.textMuted,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.ide(),
                    )

                    // Field 1: Boot source
                    BootSelectField(
                        label = "Boot source",
                        value = bootSource,
                        options = listOf(
                            "From extracted build (recommended)",
                            "From vendor firmware partition",
                            "Custom boot image file",
                        ),
                        onSelect = { bootSource = it },
                        helperText = "Uses boot image from the current workspace sources.",
                        hasInfoIcon = true,
                        colors = colors,
                    )

                    // Field 2: Kernel and ramdisk handling
                    BootSelectField(
                        label = "Kernel and ramdisk handling",
                        value = kernelRamdiskHandling,
                        options = listOf(
                            "Use extracted components",
                            "Recompile from source tree",
                            "Preserve without repack",
                        ),
                        onSelect = { kernelRamdiskHandling = it },
                        helperText = "Keep kernel and ramdisk from source unless patches modify them.",
                        colors = colors,
                    )

                    // Field 3: Patch integration (Checkbox)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                        Text(
                            text = "Patch integration",
                            color = colors.textDim,
                            fontSize = FontSize.Micro,
                            fontFamily = GlassFontFamily.ide(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { applyBootPatches = !applyBootPatches },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Checkbox(
                                checked = applyBootPatches,
                                onCheckedChange = { applyBootPatches = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accentBlue,
                                    uncheckedColor = colors.textDim,
                                ),
                            )
                            Text(
                                text = "Apply compatible boot patches",
                                color = colors.textMain,
                                fontSize = FontSize.BodySmall,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }
                        Text(
                            text = "Includes kernel modules and ramdisk modifications from the Patches stage.",
                            color = colors.textMuted,
                            fontSize = FontSize.Micro,
                            fontFamily = GlassFontFamily.ide(),
                            modifier = Modifier.padding(start = Spacing.Large),
                        )
                    }

                    // Field 4: AVB handling
                    BootSelectField(
                        label = "AVB handling",
                        value = avbHandling,
                        options = listOf(
                            "Preserve AVB configuration",
                            "Disable AVB verification",
                            "Custom AVB signing key",
                        ),
                        onSelect = { avbHandling = it },
                        helperText = "Maintain verified boot settings from source.",
                        colors = colors,
                    )

                    // Field 5: Boot footer policy
                    BootSelectField(
                        label = "Boot footer policy",
                        value = bootFooterPolicyOption,
                        options = listOf(
                            "Use target policy (recommended)",
                            "Preserve original footer",
                            "Strip footer completely",
                        ),
                        onSelect = {
                            bootFooterPolicyOption = it
                            val policy = if (it.contains("target")) "target_policy" else "preserve"
                            onChange(settings.copy(bootFooterPolicy = policy))
                        },
                        helperText = "Footer configuration will follow target revision 4 policy.",
                        hasInfoIcon = true,
                        colors = colors,
                    )
                }
            }

            // ==================== RIGHT COLUMN: Inspection & Preview ====================
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Card 1: Inspected boot image
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = colors.cardBackground,
                    borderColor = colors.border,
                    contentPadding = Spacing.Medium,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Inspected boot image",
                            color = colors.textMain,
                            fontSize = FontSize.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GlassFontFamily.ide(),
                        )

                        val clipboardManager = LocalClipboardManager.current
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                            InspectedRow("Source", "extracted/boot.img", colors)
                            InspectedRow("Device", target.codename, colors)
                            InspectedRow("Android version", "14", colors)
                            InspectedRow("Kernel version", "5.15.110-android14-g123abc", colors)
                            InspectedRow("Build date", "2024-11-02 10:24:17", colors)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Image hash (SHA-256)",
                                    color = colors.textMuted,
                                    fontSize = FontSize.Micro,
                                    fontFamily = GlassFontFamily.ide(),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    Text(
                                        text = "7e3f9a0c6b21...d4e8c2f1",
                                        color = colors.textMain,
                                        fontSize = FontSize.Micro,
                                        fontFamily = GlassFontFamily.code(),
                                    )
                                    GlassIconButton(
                                        onClick = {
                                            val hash = "7e3f9a0c6b2123456789abcdef0123456789" +
                                                "abcdef0123456789abcdefd4e8c2f1"
                                            clipboardManager.setText(AnnotatedString(hash))
                                        },
                                        modifier = Modifier.size(IconSize.Small),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy hash",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(IconSize.ExtraSmall),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 2: Footer verification
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = colors.cardBackground,
                    borderColor = colors.border,
                    contentPadding = Spacing.Medium,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                        Text(
                            text = "Footer verification",
                            color = colors.textMain,
                            fontSize = FontSize.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GlassFontFamily.ide(),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = "Valid",
                                color = colors.accentGreen,
                                fontSize = FontSize.BodySmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }

                        Text(
                            text = "Boot footer matches target policy (revision 4).",
                            color = colors.textMuted,
                            fontSize = FontSize.Micro,
                            fontFamily = GlassFontFamily.ide(),
                        )
                    }
                }

                // Card 3: Affected files preview
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    surfaceColor = colors.cardBackground,
                    borderColor = colors.border,
                    contentPadding = Spacing.Medium,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Text(
                                text = "Affected files preview",
                                color = colors.textMain,
                                fontSize = FontSize.BodySmall,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(IconSize.ExtraSmall),
                            )
                        }

                        // Table Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Path",
                                color = colors.textDim,
                                fontSize = FontSize.Micro,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                            Text(
                                text = "Action",
                                color = colors.textDim,
                                fontSize = FontSize.Micro,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }

                        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

                        // Rows
                        AffectedFileRow("boot.img", "Rebuild", IdeStatusSeverity.Warning, colors)
                        AffectedFileRow("kernel", "Included", IdeStatusSeverity.Ready, colors)
                        AffectedFileRow("ramdisk", "Included", IdeStatusSeverity.Ready, colors)
                        AffectedFileRow("kernel_modules/*", "Included", IdeStatusSeverity.Ready, colors)
                        AffectedFileRow("init_boot.img", "Unchanged", IdeStatusSeverity.Running, colors)

                        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

                        // Footnote Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(IconSize.ExtraSmall),
                            )
                            val filesNotice = "Files shown are relative to the output image. " +
                                "The final boot image will be generated during the Build stage."
                            Text(
                                text = filesNotice,
                                color = colors.textDim,
                                fontSize = FontSize.Micro,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BootSelectField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    helperText: String,
    hasInfoIcon: Boolean = false,
    colors: IdeShellColorScheme,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = label,
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
            )
            if (hasInfoIcon) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(IdeShellShapes.Control)
                    .background(colors.cardBackgroundMuted)
                    .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                    .clickable { expanded = true }
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = value,
                    color = colors.textMain,
                    fontSize = FontSize.Micro,
                    fontFamily = GlassFontFamily.ide(),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textDim,
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
                                color = if (option == value) colors.accentBlue else colors.textMain,
                                fontSize = FontSize.Micro,
                                fontFamily = GlassFontFamily.ide(),
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

        Text(
            text = helperText,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}

@Composable
private fun InspectedRow(label: String, value: String, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.ide(),
        )
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.Micro,
            fontFamily = GlassFontFamily.code(),
        )
    }
}

@Composable
private fun AffectedFileRow(path: String, action: String, severity: IdeStatusSeverity, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = path,
                color = colors.textMain,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.code(),
            )
        }

        IdeStatusBadge(
            label = action,
            severity = severity,
            useDot = true,
            colors = colors,
        )
    }
}
