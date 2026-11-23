/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.partition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.partition.explorer.PartitionImageMetadata
import org.ide.lti.core.domain.partition.explorer.PartitionNode
import org.ide.lti.core.domain.partition.explorer.PartitionNodeType

/**
 * Visual Partition & Firmware Explorer allowing developers to browse files,
 * permissions, and SELinux contexts inside compressed EROFS/EXT4 images without loop mounting.
 */
@Composable
fun PartitionExplorerView(
    metadata: PartitionImageMetadata,
    modifier: Modifier = Modifier,
    onExtractNode: ((PartitionNode) -> Unit)? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedNode by remember { mutableStateOf<PartitionNode?>(null) }

    val displayTree = remember(metadata, searchQuery) {
        if (searchQuery.isBlank()) metadata.rootNode else metadata.rootNode.filter(searchQuery)
    }

    Column(
        modifier = modifier
            .testTag("PartitionExplorerView")
            .glassSurface(role = GlassRole.TextCard, shape = GlassShapes.HazeMedium, drawBorder = true)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Partition Header Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "${metadata.partitionName}.img",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ideFontFamily(),
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "${metadata.filesystem} | ${metadata.totalFiles} files | ${formatBytes(metadata.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .clip(GlassShapes.ExtraSmall)
                    .background(AccentPresetColors.Cyan.copy(alpha = AlphaTokens.Hover))
                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
            ) {
                Text(
                    text = metadata.filesystem,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        fontWeight = FontWeight.Bold,
                        color = AccentPresetColors.Cyan,
                    ),
                )
            }
        }

        // Virtualized Files List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .glassContainer(),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            val children = displayTree?.children ?: emptyList()
            items(children, key = { it.path }) { node ->
                val isSelected = selectedNode?.path == node.path
                PartitionNodeRow(
                    node = node,
                    isSelected = isSelected,
                    onClick = { selectedNode = node },
                    onExtract = onExtractNode,
                )
            }
        }
    }
}

@Composable
private fun PartitionNodeRow(
    node: PartitionNode,
    isSelected: Boolean,
    onClick: () -> Unit,
    onExtract: ((PartitionNode) -> Unit)?,
) {
    val typeSymbol = when (node.type) {
        PartitionNodeType.DIRECTORY -> "[D]"
        PartitionNodeType.REGULAR_FILE -> "[F]"
        PartitionNodeType.SYMLINK -> "[L]"
    }

    val typeColor = when (node.type) {
        PartitionNodeType.DIRECTORY -> AccentPresetColors.Cobalt
        PartitionNodeType.REGULAR_FILE -> MaterialTheme.colorScheme.onSurface
        PartitionNodeType.SYMLINK -> AccentPresetColors.Purple
    }

    val rowBackground = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Half)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = typeSymbol,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = typeColor,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = node.path,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.LabelSmall,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Text(
                text = formatBytes(node.sizeBytes),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }

        Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = node.permissions,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                Text(
                    text = node.selinuxContext,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = AccentPresetColors.Amber,
                    ),
                )
                if (node.targetLink != null) {
                    Text(
                        text = "-> ${node.targetLink}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = codeFontFamily(),
                            fontSize = FontSize.Micro,
                            color = AccentPresetColors.Purple,
                        ),
                    )
                }
            }

            if (onExtract != null) {
                Box(
                    modifier = Modifier
                        .clip(GlassShapes.ExtraSmall)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = AlphaTokens.Hover))
                        .clickable { onExtract(node) }
                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = "Extract",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ideFontFamily(),
                            fontSize = FontSize.Micro,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        ),
                    )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${(kb * 10).toLong() / 10.0} KB"
    val mb = kb / 1024.0
    return "${(mb * 10).toLong() / 10.0} MB"
}
