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
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("OutputNamingPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        OutputNamingUnifiedCard(settings = settings, onChange = onChange)
        OutputNamingFootnoteCard()
    }
}

@Composable
private fun OutputNamingUnifiedCard(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        FilenameTemplateSection(settings = settings, onChange = onChange)
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)
        FilenamePreviewSection()
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)
        InvalidCharacterCheckSection()
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)
        OutputFolderSection()
    }
}

@Composable
private fun FilenameTemplateSection(settings: BuildSettings, onChange: (BuildSettings) -> Unit) {
    var templateText by remember { mutableStateOf("{device}-{version}-{date}.zip") }
    val variables = listOf("{device}", "{version}", "{date}", "{variant}", "{buildtype}", "{revision}")

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Filename template",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Use variables to generate consistent, descriptive output filenames.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = templateText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
                Text(
                    text = "{}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            variables.forEach { variable ->
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.ShellControl)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(
                            GlassDimens.HairlineBorder,
                            MaterialTheme.colorScheme.outlineVariant,
                            GlassShapes.ShellControl,
                        )
                        .clickable {
                            templateText = templateText.replace(".zip", "$variable.zip")
                            onChange(settings.copy(filenameTemplate = templateText))
                        }
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = variable,
                        color = MaterialTheme.colorScheme.primary,
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
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Text(
                text = "Click a variable to insert it at the cursor position.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun FilenamePreviewSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Preview",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Example filename using current settings and workspace information.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "pixel_7-pro-14.0-20250312.zip",
                    color = MaterialTheme.colorScheme.onSurface,
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
                        tint = GlassTheme.diagnosticColors.success,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text(
                        text = "Valid filename",
                        color = GlassTheme.diagnosticColors.success,
                        fontSize = FontSize.Micro,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidCharacterCheckSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Invalid character check",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Filenames cannot contain the following characters:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Box(
            modifier = Modifier
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
        ) {
            Text(
                text = "\\  /  :  *  ?  \"  <  >  |",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = GlassTheme.diagnosticColors.success,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Text(
                text = "No invalid characters detected",
                color = GlassTheme.diagnosticColors.success,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun OutputFolderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Output folder",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Build artifacts will be saved to:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    .clip(GlassShapes.ShellControl)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        GlassDimens.HairlineBorder,
                        MaterialTheme.colorScheme.outlineVariant,
                        GlassShapes.ShellControl,
                    )
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            ) {
                Text(
                    text = "D:\\ROMs\\PQ84P01\\out\\pixel_7-pro",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontFamily = codeFontFamily(),
                )
            }

            GlassButton(
                onClick = { },
                variant = GlassButtonVariant.Standard,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Change...", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.Micro)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Text(
                text = "The output folder is derived from your workspace and device name.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun OutputNamingFootnoteCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            Icons.Default.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = "These settings apply to the next build only. They do not rename existing files.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
