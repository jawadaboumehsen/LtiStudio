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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings

/**
 * Stage Build subobject pane for AVB signing keys and partition verification.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun AvbKeysPane(
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
            .testTag("AvbKeysPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AvbNoticeCard(colors = colors)
        AvbKeysTableCard(colors = colors)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(modifier = Modifier.weight(1.2f)) {
                SelectedKeyDetailsCard(colors = colors)
            }
            Box(modifier = Modifier.weight(1f)) {
                KeyManagementCard(colors = colors)
            }
        }
    }
}

@Composable
private fun AvbNoticeCard(colors: IdeShellColorScheme) {
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
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
                    text = "AVB (Android Verified Boot) signs partitions to ensure integrity at boot. " +
                        "Select key material for each applicable partition. " +
                        "Private keys are stored securely and are never displayed.",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = "Learn more ↗",
                color = colors.accentBlue,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
                modifier = Modifier.clickable { },
            )
        }
    }
}

private data class AvbKeyRow(
    val partition: String,
    val keyToUse: String,
    val fingerprint: String,
    val isAvailable: Boolean,
    val targetPolicy: String,
    val isMissing: Boolean = false,
)

@Composable
private fun AvbKeysTableCard(colors: IdeShellColorScheme) {
    val rows = listOf(
        AvbKeyRow("boot", "release-avb", "SHA256: 7a3f9d1e4c2b8a6f...", true, "Enforce"),
        AvbKeyRow("vendor_boot", "release-avb", "SHA256: 2d8c7f6b9e1a0d4...", true, "Enforce"),
        AvbKeyRow("dtbo", "release-avb", "SHA256: 91e4b2a7c3d5f00...", true, "Enforce"),
        AvbKeyRow("vbmeta", "release-avb", "SHA256: 6f0a9d8c2b1e3f7...", true, "Enforce"),
        AvbKeyRow("vbmeta_system", "release-avb", "SHA256: 3c7e1a9d0b5f4c8...", true, "Enforce"),
        AvbKeyRow("vbmeta_vendor", "No key selected", "—", false, "Enforce", isMissing = true),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        AvbTableHeader(colors)
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)

        rows.forEach { row ->
            AvbTableRow(row = row, colors = colors)
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)
        AvbMissingKeyErrorBanner(colors)
    }
}

@Composable
private fun AvbTableHeader(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Partition", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.2f))
        Text("Key to use", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.5f))
        Text(
            text = "Public key fingerprint",
            color = colors.textDim,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.8f),
        )
        Text("Availability", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.2f))
        Text("Target policy", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.0f))
    }
}

@Composable
private fun AvbTableRow(row: AvbKeyRow, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.partition,
            color = colors.textMain,
            fontWeight = FontWeight.SemiBold,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.2f),
        )

        Box(
            modifier = Modifier
                .weight(1.5f)
                .clip(GlassShapes.ShellControl)
                .background(colors.cardBackgroundMuted)
                .border(
                    width = StrokeWidth.Standard,
                    color = if (row.isMissing) colors.errorRed else colors.borderMuted,
                    shape = GlassShapes.ShellControl,
                )
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.keyToUse,
                    color = if (row.isMissing) colors.errorRed else colors.textMain,
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (row.isMissing) colors.errorRed else colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        Spacer(Modifier.width(Spacing.Small))

        Row(
            modifier = Modifier.weight(1.8f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = row.fingerprint,
                color = colors.textDim,
                fontFamily = codeFontFamily(),
                fontSize = FontSize.Micro,
            )
            if (!row.isMissing) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        Box(modifier = Modifier.weight(1.2f)) {
            if (row.isMissing) {
                IdeStatusBadge(label = "Missing key", severity = IdeStatusSeverity.Failed, colors = colors)
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Box(modifier = Modifier.size(Spacing.ExtraSmall).clip(CircleShape).background(colors.accentGreen))
                    Text("Available", color = colors.accentGreen, fontSize = FontSize.Micro)
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1.0f)
                .clip(GlassShapes.ShellControl)
                .background(colors.cardBackgroundMuted)
                .border(GlassDimens.HairlineBorder, colors.borderMuted, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.targetPolicy, color = colors.textMuted, fontSize = FontSize.Micro)
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textDim,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }
    }
}

@Composable
private fun AvbMissingKeyErrorBanner(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellControl)
            .background(colors.errorRedBg)
            .border(GlassDimens.HairlineBorder, colors.errorRed, GlassShapes.ShellControl)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(Icons.Default.Close, null, tint = colors.errorRed, modifier = Modifier.size(IconSize.Small))
            Text(
                text = "Select a key for vbmeta_vendor to continue.",
                color = colors.errorRed,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.Medium,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Manage credentials ↗",
            color = colors.accentBlue,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.clickable { },
        )
    }
}

@Composable
private fun SelectedKeyDetailsCard(colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Selected key details",
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)

        KeyDetailRow("Key alias", "release-avb", isCode = true, colors = colors)
        KeyDetailRow("Algorithm", "RSA 4096 (AVB v2.0)", isCode = false, colors = colors)
        KeyDetailRow("Created", "2024-05-18", isCode = false, colors = colors)
        KeyDetailRow("Expires", "Never", isCode = false, colors = colors)
        KeyDetailRow(
            label = "Usage",
            value = "Boot, vendor_boot, dtbo, vbmeta, vbmeta_system",
            isCode = false,
            colors = colors,
        )
    }
}

@Composable
private fun KeyDetailRow(label: String, value: String, isCode: Boolean, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Hairline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textDim,
            fontSize = FontSize.Micro,
            modifier = Modifier.width(Spacing.ExtraExtraLarge + Spacing.ExtraExtraLarge),
        )
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.Micro,
            fontFamily = if (isCode) codeFontFamily() else ideFontFamily(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KeyManagementCard(colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(Icons.Default.Info, null, tint = colors.accentBlue, modifier = Modifier.size(IconSize.Small))
            Text(
                text = "Key management",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
        }
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = colors.borderMuted)

        Text(
            text = "Manage and import AVB keys in Settings > Credentials.",
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            modifier = Modifier.clickable { },
        ) {
            Text(
                text = "Open credentials ↗",
                color = colors.accentBlue,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}
