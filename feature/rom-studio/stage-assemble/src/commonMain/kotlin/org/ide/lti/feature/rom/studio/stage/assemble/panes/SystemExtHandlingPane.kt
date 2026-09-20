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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

/**
 * Stage Assemble subobject pane for configuring `system_ext` partition integration.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun SystemExtHandlingPane(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val isSeparate = settings.systemExtMode.equals("separate", ignoreCase = true) ||
        settings.systemExtMode.equals("auto", ignoreCase = true) ||
        settings.systemExtMode.equals("standalone", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("SystemExtHandlingPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        SystemExtTopInfoCard(colors = colors)

        SystemExtHandlingModeSection(
            isSeparate = isSeparate,
            settings = settings,
            onChange = onChange,
            colors = colors,
        )

        SystemExtPathsCard(colors = colors)

        SystemExtWarningBanner(colors = colors)
    }
}

@Composable
private fun SystemExtTopInfoCard(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(colors.infoBannerFill)
            .border(GlassDimens.HairlineBorder, colors.infoBannerBorder, GlassShapes.ShellCard)
            .padding(Spacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val infoText = "The system_ext partition contains device-specific framework extensions " +
                "provided by the vendor.\n" +
                "Choose how to handle system_ext for this build. Changing the mode may invalidate " +
                "downstream checkpoints\n" +
                "(Build, Metadata, Publish) which will need to be rerun."

            Text(
                text = infoText,
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(1f),
            )

            Row(
                modifier = Modifier
                    .clip(GlassShapes.ShellControl)
                    .clickable { }
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Help",
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
                Text(
                    text = "Help",
                    color = colors.accentBlue,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun SystemExtHandlingModeSection(
    isSeparate: Boolean,
    settings: AssemblySettings,
    onChange: (AssemblySettings) -> Unit,
    colors: IdeShellColorScheme,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Text(
            text = "Handling mode",
            color = colors.textMain,
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                HandlingModeCard(
                    title = "Separate (recommended)",
                    subtitle = "Keep system_ext as a separate partition",
                    description = "Preserves vendor compatibility and is required for the pinned target revision.",
                    isSelected = isSeparate,
                    isEnabled = true,
                    onSelect = { onChange(settings.copy(systemExtMode = "separate")) },
                    modifier = Modifier.weight(1f),
                    colors = colors,
                )

                HandlingModeCard(
                    title = "Fold into system",
                    subtitle = "Merge system_ext into system",
                    description = "Disabled by pinned target capability." +
                        " Target revision 4 requires separate system_ext.",
                    isSelected = !isSeparate,
                    isEnabled = false,
                    onSelect = { onChange(settings.copy(systemExtMode = "fold")) },
                    modifier = Modifier.weight(1f),
                    colors = colors,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Changing the handling mode will invalidate downstream checkpoints:" +
                        " Build, Metadata, Publish.",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun SystemExtPathsCard(colors: IdeShellColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        Text(
            text = "Paths and preview",
            color = colors.textMain,
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellCard)
                .ideCardSurface(colors, shape = GlassShapes.ShellCard)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            SystemExtFlowDiagram(colors = colors)

            HorizontalDivider(
                thickness = GlassDimens.HairlineBorder,
                color = colors.borderMuted,
            )

            SystemExtDerivedPaths(colors = colors)
        }
    }
}

@Composable
private fun SystemExtFlowDiagram(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Input directory",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBackgroundMuted, GlassShapes.ShellControl)
                    .border(GlassDimens.HairlineBorder, colors.borderMuted, GlassShapes.ShellControl)
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "D:\\ROM\\source\\extract\\system_ext",
                    color = colors.textMain,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    onClick = { },
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.ShellControl,
                ) {
                    Text(
                        text = "Browse...",
                        color = colors.textMain,
                        fontSize = FontSize.Micro,
                    )
                }
            }

            Text(
                text = "Extracted from vendor image during Extract stage.",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = colors.accentBlue,
            modifier = Modifier.size(IconSize.Medium),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Output directory (workspace)",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.cardBackgroundMuted, GlassShapes.ShellControl)
                    .border(GlassDimens.HairlineBorder, colors.borderMuted, GlassShapes.ShellControl)
                    .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Medium),
            ) {
                Text(
                    text = "D:\\ROM\\workspace\\out\\system_ext",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }

            Text(
                text = "Derived path (based on workspace and handling mode).",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun SystemExtDerivedPaths(colors: IdeShellColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = "Derived paths",
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            DerivedPathRow("Workspace root", "D:\\ROM\\workspace", colors)
            DerivedPathRow("system_ext (output)", "D:\\ROM\\workspace\\out\\system_ext", colors)
            DerivedPathRow("Image (build output)", "D:\\ROM\\workspace\\images\\system_ext.img", colors)
            DerivedPathRow("Mount point (build)", "out\\mount\\system_ext", colors)
        }
    }
}

@Composable
private fun HandlingModeCard(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme,
) {
    val borderColor = if (isSelected) colors.accentBlue else colors.borderMuted
    val bgColor = if (isSelected) {
        colors.accentBlue.copy(alpha = AlphaTokens.Subtle)
    } else {
        colors.cardBackground
    }

    Box(
        modifier = modifier
            .clip(GlassShapes.ShellCard)
            .background(bgColor)
            .border(GlassDimens.HairlineBorder, borderColor, GlassShapes.ShellCard)
            .clickable(enabled = isEnabled, onClick = onSelect)
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            RadioButton(
                selected = isSelected,
                onClick = if (isEnabled) onSelect else null,
                enabled = isEnabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.accentBlue,
                    unselectedColor = colors.textDim,
                    disabledSelectedColor = colors.textDim,
                    disabledUnselectedColor = colors.textDim.copy(alpha = AlphaTokens.Disabled),
                ),
            )

            val iconTint = when {
                isSelected -> colors.accentBlue
                isEnabled -> colors.textMuted
                else -> colors.textDim
            }

            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(IconSize.Large),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = title,
                    color = if (isEnabled) colors.textMain else colors.textMuted,
                    fontSize = FontSize.BodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = subtitle,
                    color = if (isEnabled) colors.textMuted else colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = description,
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun DerivedPathRow(label: String, value: String, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.width(GlassDimens.AcquirePolicyLabelWidth),
        )
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SystemExtWarningBanner(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellControl)
            .background(colors.cardBackground)
            .border(
                width = GlassDimens.HairlineBorder,
                color = colors.warningAmber.copy(alpha = AlphaTokens.Border),
                shape = GlassShapes.ShellControl,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(
                modifier = Modifier
                    .size(IconSize.Large)
                    .clip(GlassShapes.ShellPill)
                    .background(colors.warningAmber),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    color = colors.cardBackgroundMuted,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ideFontFamily(),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Downstream checkpoints will be invalidated",
                    color = colors.warningAmber,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "If you change the handling mode, the following stages must be rerun: " +
                        "Build, Metadata, Publish.",
                    color = colors.warningAmber,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}
