/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build.panes

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings

/**
 * Stage Build subobject pane for output naming template and directory configuration.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun OutputNamingPane(
    settings: BuildSettings,
    target: TargetDevice,
    onChange: (BuildSettings) -> Unit,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("OutputNamingPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        OutputNamingUnifiedCard(settings = settings, onChange = onChange, colors = colors)
        OutputNamingFootnoteCard(colors = colors)
    }
}

@Composable
private fun OutputNamingUnifiedCard(
    settings: BuildSettings,
    onChange: (BuildSettings) -> Unit,
    colors: IdeShellColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        FilenameTemplateSection(settings = settings, onChange = onChange, colors = colors)
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)
        FilenamePreviewSection(colors = colors)
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)
        InvalidCharacterCheckSection(colors = colors)
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)
        OutputFolderSection(colors = colors)
    }
}

@Composable
private fun FilenameTemplateSection(
    settings: BuildSettings,
    onChange: (BuildSettings) -> Unit,
    colors: IdeShellColorScheme,
) {
    var templateText by remember { mutableStateOf("{device}-{version}-{date}.zip") }
    val variables = listOf("{device}", "{version}", "{date}", "{variant}", "{buildtype}", "{revision}")

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Filename template",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Use variables to generate consistent, descriptive output filenames.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = templateText,
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
                Text(
                    text = "{}",
                    color = colors.textDim,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Insert variable:",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            variables.forEach { variable ->
                Box(
                    modifier = Modifier
                        .clip(IdeShellShapes.Control)
                        .background(colors.cardBackgroundMuted)
                        .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                        .clickable {
                            templateText = templateText.replace(".zip", "$variable.zip")
                            onChange(settings.copy(filenameTemplate = templateText))
                        }
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = variable,
                        color = colors.accentBlue,
                        fontSize = FontSize.Micro,
                        fontFamily = codeFontFamily(),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(Icons.Default.Info, null, tint = colors.accentBlue, modifier = Modifier.size(IconSize.ExtraSmall))
            Text(
                text = "Click a variable to insert it at the cursor position.",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun FilenamePreviewSection(colors: IdeShellColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Preview",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Example filename using current settings and workspace information.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "pixel_7-pro-14.0-20250312.zip",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Valid filename",
                        color = colors.accentGreen,
                        fontSize = FontSize.Micro,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidCharacterCheckSection(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Invalid character check",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Filenames cannot contain the following characters:",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
        ) {
            Text(
                text = "\\  /  :  *  ?  \"  <  >  |",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Text(
                text = "No invalid characters detected",
                color = colors.accentGreen,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OutputFolderSection(colors: IdeShellColorScheme) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Output folder",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Build artifacts will be saved to:",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(IdeShellShapes.Control)
                    .background(colors.cardBackgroundMuted)
                    .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            ) {
                Text(
                    text = "D:\\ROMs\\PQ84P01\\out\\pixel_7-pro",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
            }

            GlassButton(
                onClick = { },
                variant = GlassButtonVariant.Standard,
                shape = IdeShellShapes.Control,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.textMain,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Change...", color = colors.textMain, fontSize = FontSize.Micro)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(Icons.Default.Info, null, tint = colors.accentBlue, modifier = Modifier.size(IconSize.ExtraSmall))
            Text(
                text = "The output folder is derived from your workspace and device name.",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun OutputNamingFootnoteCard(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(Icons.Default.Info, null, tint = colors.textDim, modifier = Modifier.size(IconSize.Small))
        Text(
            text = "These settings apply to the next build only. They do not rename existing files.",
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
