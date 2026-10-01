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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("PartitionLayoutPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        PartitionLayoutNoticeCard()
        SuperPartitionStorageCard()
        PartitionTableCard()
        AboutPartitionLayoutCard()
    }
}

@Composable
private fun PartitionLayoutNoticeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle))
            .border(
                GlassDimens.HairlineBorder,
                MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Ambient),
                GlassShapes.ShellCard,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Small),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                Text(
                    text = "Partition layout is read from the source images and cannot be modified here.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "To change partition sizes, use a different target revision " +
                        "or rebind with a compatible source build.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun SuperPartitionStorageCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "13.62 GB total",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "11.43 GB / 13.62 GB",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "2.19 GB free",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.839f)
                    .height(Spacing.Small)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.Small)
                    .background(MaterialTheme.colorScheme.outline),
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
                Box(
                    modifier = Modifier.size(
                        Spacing.Small,
                    ).clip(GlassShapes.Circle).background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "Used 11.43 GB (83.9%)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Box(
                    modifier = Modifier.size(
                        Spacing.Small,
                    ).clip(GlassShapes.Circle).background(MaterialTheme.colorScheme.outline),
                )
                Text(
                    text = "Free 2.19 GB (16.1%)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun PartitionTableCard() {
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
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        PartitionTableHeader()
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)
        items.forEach { item ->
            PartitionTableRow(item = item)
        }
    }
}

@Composable
private fun PartitionTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.width(Spacing.Large),
        )
        Text(
            "Partition",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.3f),
        )
        Text(
            "Mount point",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.3f),
        )
        Text(
            "Type",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            "Size",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            "Used",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            "Free",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            "Image source",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun PartitionTableRow(item: PartitionDetail) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.index,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.width(Spacing.Large),
        )
        Text(
            text = item.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.3f),
        )
        Text(
            item.mountPoint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.3f),
        )
        Box(modifier = Modifier.weight(1.0f)) {
            Box(
                modifier = Modifier
                    .clip(GlassShapes.ShellBadge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        GlassDimens.HairlineBorder,
                        MaterialTheme.colorScheme.outlineVariant,
                        GlassShapes.ShellBadge,
                    )
                    .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline),
            ) {
                Text(
                    text = item.type,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                )
            }
        }
        Text(
            text = item.size,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.used,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.free,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = item.source,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun AboutPartitionLayoutCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                Icons.Default.Info,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "About partition layout",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "This layout is read from the selected source images for target revision 4. " +
                "To modify partition sizes, choose a different target revision and " +
                "rebind source images in the Acquire stage.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
