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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
 * Stage Build subobject pane for configuring EROFS filesystem compression.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun CompressionPane(
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
            .testTag("CompressionPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        ErofsSettingsCard(settings = settings, onChange = onChange, colors = colors)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ApplyToImagesCard(colors = colors)
            }
            Box(modifier = Modifier.weight(1f)) {
                AboutErofsCard(colors = colors)
            }
        }

        CompressionFootnoteCard(colors = colors)
    }
}

@Suppress("UnusedParameter")
@Composable
private fun ErofsSettingsCard(
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
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "EROFS compression",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Choose the compression algorithm and parameters for system and product images.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        CompressionParameterRow(
            label = "Algorithm",
            value = "lz4hc",
            source = "Source: Android build tools (mkfs.erofs)",
            explanation = "lz4hc offers a good balance of size and performance for modern devices.",
            colors = colors,
        )
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        CompressionParameterRow(
            label = "Compression level",
            value = "12",
            source = "Range: 1 - 12 (higher = better compression, slower build)",
            explanation = "Recommended: 12 for release builds",
            colors = colors,
        )
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        CompressionParameterRow(
            label = "Block size",
            value = "4 KiB (4096)",
            source = "Derived from: Android device configuration",
            explanation = "Using 4 KiB for broad device compatibility.",
            colors = colors,
        )
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        CompressionParameterRow(
            label = "Alignment",
            value = "4 KiB (4096)",
            source = "Derived from: device partition layout",
            explanation = "Keep alignment at 4 KiB to match target devices.",
            colors = colors,
        )
    }
}

@Composable
private fun CompressionParameterRow(
    label: String,
    value: String,
    source: String,
    explanation: String,
    colors: IdeShellColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.0f),
        )

        Box(
            modifier = Modifier
                .weight(1.3f)
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    color = colors.textMain,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        Spacer(Modifier.width(Spacing.Medium))

        Box(
            modifier = Modifier
                .weight(1.7f)
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Control)
                .padding(Spacing.Small),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = source,
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = explanation,
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun ApplyToImagesCard(colors: IdeShellColorScheme) {
    var systemChecked by remember { mutableStateOf(true) }
    var productChecked by remember { mutableStateOf(true) }
    var systemExtChecked by remember { mutableStateOf(true) }
    var vendorChecked by remember { mutableStateOf(true) }
    var odmChecked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
            Text(
                text = "Apply to images",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Select which partitions should use EROFS compression.",
                color = colors.textMuted,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        ChecklistItem("system", "Required (Android 14)", systemChecked, { systemChecked = it }, true, colors)
        ChecklistItem("product", "Recommended", productChecked, { productChecked = it }, false, colors)
        ChecklistItem("system_ext", "Recommended", systemExtChecked, { systemExtChecked = it }, false, colors)
        ChecklistItem("vendor", "If supported by device", vendorChecked, { vendorChecked = it }, false, colors)
        ChecklistItem("odm", "If supported by device", odmChecked, { odmChecked = it }, false, colors)
    }
}

@Composable
private fun ChecklistItem(
    name: String,
    badge: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    locked: Boolean,
    colors: IdeShellColorScheme,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (locked) null else onCheckedChange,
                enabled = !locked,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.accentBlue,
                    uncheckedColor = colors.textDim,
                ),
            )
            Text(
                text = name,
                color = colors.textMain,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.Medium,
                fontFamily = codeFontFamily(),
            )
        }
        Text(
            text = badge,
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun AboutErofsCard(colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(Icons.Default.Info, null, tint = colors.accentBlue, modifier = Modifier.size(IconSize.Small))
            Text(
                text = "About EROFS",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
        }
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        Text(
            text = "EROFS (Enhanced Read-Only File System) provides high compression ratios " +
                "and fast read performance for Android system images.",
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )

        Text(
            text = "Settings here apply to image build steps and are validated against the " +
                "Android build tools in your environment.",
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun CompressionFootnoteCard(colors: IdeShellColorScheme) {
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
            text = "Run validation to verify settings against your current configuration.",
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
