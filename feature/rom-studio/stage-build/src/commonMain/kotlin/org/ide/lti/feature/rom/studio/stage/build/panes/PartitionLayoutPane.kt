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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Stage Build subobject pane for partition layout and storage allocation.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun PartitionLayoutPane(
    settings: BuildSettings,
    target: TargetDevice,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PartitionLayoutPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        PartitionLayoutNoticeCard(colors = colors)
        SuperPartitionStorageCard(colors = colors)
        PartitionTableCard(colors = colors)
        AboutPartitionLayoutCard(colors = colors)
    }
}

@Composable
private fun PartitionLayoutNoticeCard(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .background(colors.infoBannerFill)
            .border(IdeShellDimens.HairlineBorder, colors.infoBannerBorder, IdeShellShapes.Card)
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(Icons.Default.Info, null, tint = colors.accentBlue, modifier = Modifier.size(IconSize.Small))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "Partition layout is read from the source images and cannot be modified here.",
                    color = colors.textMain,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "To change partition sizes, use a different target revision " +
                        "or rebind with a compatible source build.",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun SuperPartitionStorageCard(colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Super partition",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "13.62 GB total",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "11.43 GB / 13.62 GB",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "2.19 GB free",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        // Segmented Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Small)
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.839f)
                    .height(Spacing.Small)
                    .background(colors.accentBlue),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.Small)
                    .background(colors.border),
            )
        }

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Box(modifier = Modifier.size(Spacing.Small).clip(CircleShape).background(colors.accentBlue))
                Text(
                    text = "Used 11.43 GB (83.9%)",
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Box(modifier = Modifier.size(Spacing.Small).clip(CircleShape).background(colors.border))
                Text(
                    text = "Free 2.19 GB (16.1%)",
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

private data class PartitionDetail(
    val index: String,
    val name: String,
    val mountPoint: String,
    val type: String,
    val size: String,
    val used: String,
    val free: String,
    val source: String,
)

@Composable
private fun PartitionTableCard(colors: IdeShellColorScheme) {
    val items = listOf(
        PartitionDetail("1", "super", "—", "dynamic", "13.62 GB", "11.43 GB", "2.19 GB", "super.img"),
        PartitionDetail("2", "system", "/system", "dynamic", "4.00 GB", "3.21 GB", "788 MB", "super.img"),
        PartitionDetail("3", "vendor", "/vendor", "dynamic", "2.00 GB", "1.74 GB", "266 MB", "super.img"),
        PartitionDetail("4", "product", "/product", "dynamic", "1.50 GB", "1.21 GB", "297 MB", "super.img"),
        PartitionDetail("5", "system_ext", "/system_ext", "dynamic", "1.00 GB", "728 MB", "296 MB", "super.img"),
        PartitionDetail("6", "odm", "/odm", "dynamic", "512 MB", "355 MB", "157 MB", "super.img"),
        PartitionDetail("7", "vendor_dlkm", "/vendor_dlkm", "dynamic", "512 MB", "402 MB", "110 MB", "super.img"),
        PartitionDetail("8", "product_dlkm", "/product_dlkm", "dynamic", "256 MB", "198 MB", "58 MB", "super.img"),
        PartitionDetail("9", "boot", "/boot", "raw", "128 MB", "96 MB", "32 MB", "boot.img"),
        PartitionDetail("10", "dtbo", "/dtbo", "raw", "64 MB", "48 MB", "16 MB", "dtbo.img"),
        PartitionDetail("11", "vbmeta", "/vbmeta", "raw", "64 MB", "8 MB", "56 MB", "vbmeta.img"),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IdeShellShapes.Card)
            .ideCardSurface(colors, shape = IdeShellShapes.Card)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        PartitionTableHeader(colors)
        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)
        items.forEach { item ->
            PartitionTableRow(item = item, colors = colors)
        }
    }
}

@Composable
private fun PartitionTableHeader(colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.width(Spacing.Large))
        Text("Partition", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.3f))
        Text("Mount point", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.3f))
        Text("Type", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.0f))
        Text("Size", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.0f))
        Text("Used", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.0f))
        Text("Free", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.0f))
        Text("Image source", color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun PartitionTableRow(item: PartitionDetail, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.index, color = colors.textDim, fontSize = FontSize.Micro, modifier = Modifier.width(Spacing.Large))
        Text(
            text = item.name,
            color = colors.textMain,
            fontWeight = FontWeight.SemiBold,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.3f),
        )
        Text(item.mountPoint, color = colors.textMuted, fontSize = FontSize.Micro, modifier = Modifier.weight(1.3f))
        Box(modifier = Modifier.weight(1.0f)) {
            Box(
                modifier = Modifier
                    .clip(IdeShellShapes.Badge)
                    .background(colors.cardBackgroundMuted)
                    .border(IdeShellDimens.HairlineBorder, colors.borderMuted, IdeShellShapes.Badge)
                    .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline),
            ) {
                Text(
                    text = item.type,
                    color = colors.textMuted,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
        Text(
            text = item.size,
            color = colors.textMain,
            fontWeight = FontWeight.Medium,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.used,
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.free,
            color = colors.textDim,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.source,
            color = colors.textMuted,
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun AboutPartitionLayoutCard(colors: IdeShellColorScheme) {
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
                text = "About partition layout",
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "This layout is read from the selected source images for target revision 4. " +
                "To modify partition sizes, choose a different target revision and " +
                "rebind source images in the Acquire stage.",
            color = colors.textMuted,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
