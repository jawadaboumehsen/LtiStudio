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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
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
 * Stage Build subobject pane for flashable members selection and packaging.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun FlashableMembersPane(
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
            .testTag("FlashableMembersPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(modifier = Modifier.weight(1.3f)) {
                TargetImageSelectionCard()
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    PackagePreviewCard()
                    CompatibilityWarningsCard()
                }
            }
        }

        FlashableFootnoteCard()
    }
}

private data class FlashableItem(
    val id: String,
    val label: String,
    val partitionName: String,
    val badge: String,
    val isRequired: Boolean,
    val isLocked: Boolean,
    val isInitiallyChecked: Boolean,
)

@Suppress("LongMethod")
private fun defaultFlashableItems(): List<FlashableItem> = listOf(
    FlashableItem(
        id = "boot",
        label = "Boot image",
        partitionName = "boot",
        badge = "Required",
        isRequired = true,
        isLocked = true,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "vendor_boot",
        label = "Vendors boot image",
        partitionName = "vendor_boot",
        badge = "Required",
        isRequired = true,
        isLocked = true,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "system",
        label = "System image",
        partitionName = "system",
        badge = "Required",
        isRequired = true,
        isLocked = true,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "vendor",
        label = "Vendor image",
        partitionName = "vendor",
        badge = "Required",
        isRequired = true,
        isLocked = true,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "product",
        label = "Product image",
        partitionName = "product",
        badge = "Recommended",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "system_ext",
        label = "System extensions",
        partitionName = "system_ext",
        badge = "Recommended",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "odm",
        label = "ODM image",
        partitionName = "odm",
        badge = "Optional",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "dtbo",
        label = "DTBO image",
        partitionName = "dtbo",
        badge = "Recommended",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "vbmeta",
        label = "VBMeta image",
        partitionName = "vbmeta",
        badge = "Required",
        isRequired = true,
        isLocked = false,
        isInitiallyChecked = true,
    ),
    FlashableItem(
        id = "recovery",
        label = "Recovery image",
        partitionName = "recovery",
        badge = "Optional",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = false,
    ),
    FlashableItem(
        id = "init_boot",
        label = "Init boot image",
        partitionName = "init_boot",
        badge = "Optional",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = false,
    ),
    FlashableItem(
        id = "super",
        label = "Super image",
        partitionName = "super",
        badge = "Optional",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = false,
    ),
    FlashableItem(
        id = "misc",
        label = "Misc files",
        partitionName = "misc",
        badge = "Optional",
        isRequired = false,
        isLocked = false,
        isInitiallyChecked = true,
    ),
)

@Composable
private fun TargetImageSelectionCard() {
    val items = remember { defaultFlashableItems() }

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
            Text(
                text = "Target image selection",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.clickable { },
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
                Text(
                    text = "Compatibility info",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
        Text(
            text = "Choose which target-supported images to include. Required images are locked.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Include",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.width(Spacing.ExtraLarge),
            )
            Text(
                text = "Image / Member",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(1.5f),
            )
            Text(
                text = "Partition",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(1.0f),
            )
            Text(
                text = "Status",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(0.8f),
            )
        }
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        items.forEach { item ->
            FlashableItemRow(item = item)
        }
    }
}

@Composable
private fun FlashableItemRow(item: FlashableItem) {
    var isChecked by remember { mutableStateOf(item.isInitiallyChecked) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(Spacing.ExtraLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = if (item.isLocked) null else { checked -> isChecked = checked },
                enabled = !item.isLocked,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            if (item.isLocked) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            } else {
                Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.Micro)
            }
        }
        Text(
            text = item.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = item.partitionName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = codeFontFamily(),
            modifier = Modifier.weight(1.0f),
        )
        val statusColor = when (item.badge) {
            "Required" -> GlassTheme.diagnosticColors.success
            "Recommended" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = item.badge,
            color = statusColor,
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
            modifier = Modifier.weight(0.8f),
        )
    }
}

private data class PackagePreviewItem(val name: String, val size: String, val partition: String)

@Composable
private fun PackagePreviewCard() {
    val previewItems = listOf(
        PackagePreviewItem("boot.img", "96 MB", "boot"),
        PackagePreviewItem("vendor_boot.img", "64 MB", "vendor_boot"),
        PackagePreviewItem("system.img", "1.2 GB", "system"),
        PackagePreviewItem("vendor.img", "432 MB", "vendor"),
        PackagePreviewItem("product.img", "128 MB", "product"),
        PackagePreviewItem("system_ext.img", "96 MB", "system_ext"),
        PackagePreviewItem("dtbo.img", "32 MB", "dtbo"),
        PackagePreviewItem("vbmeta.img", "8 MB", "vbmeta"),
    )

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
            Text(
                text = "Package preview",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "8 items · 2.1 GB",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Files to be included in the signed package",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "File name",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(1.3f),
            )
            Text(
                "Size",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(0.9f),
            )
            Text(
                "Partition",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(1.0f),
            )
        }
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        previewItems.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                    modifier = Modifier.weight(1.3f),
                )
                Text(
                    text = item.size,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                    modifier = Modifier.weight(0.9f),
                )
                Text(
                    text = item.partition,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = codeFontFamily(),
                    modifier = Modifier.weight(1.0f),
                )
            }
        }
    }
}

@Composable
private fun CompatibilityWarningsCard() {
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
            Text(
                text = "Compatibility & warnings",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            IdeStatusBadge(severity = IdeStatusSeverity.Warning, label = "2 warnings")
        }
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

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
                text = "All required images are included",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
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
                text = "Package layout is valid for target device (PQ84P01)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = GlassTheme.diagnosticColors.warning,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Column {
                Text(
                    text = "Recovery image not included (optional)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Device can still boot using stock recovery",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = GlassTheme.diagnosticColors.warning,
                modifier = Modifier.size(IconSize.ExtraSmall),
            )
            Column {
                Text(
                    text = "Super image not included",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Ensure device supports sparse images or dynamic partitions",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun FlashableFootnoteCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
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
                text = "Only include images that are supported by your target device. " +
                    "Including unnecessary images may increase package size.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = "Learn more ↗",
            color = MaterialTheme.colorScheme.primary,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
            modifier = Modifier.clickable { },
        )
    }
}
