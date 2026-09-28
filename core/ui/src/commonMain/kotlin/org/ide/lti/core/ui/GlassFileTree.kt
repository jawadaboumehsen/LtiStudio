/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType

/**
 * Represents a flattened file tree node with its nesting depth.
 */
@Immutable
internal data class FlattenedFileNode(val node: FileNode, val level: Int)

/**
 * Flattens the visible tree nodes based on current expanded directory state.
 */
internal fun flattenFileTree(rootNode: FileNode, expandedNodes: Set<String>): List<FlattenedFileNode> {
    val result = mutableListOf<FlattenedFileNode>()
    fun traverse(children: List<FileNode>?, level: Int) {
        if (children == null) return
        for (child in children) {
            result.add(FlattenedFileNode(child, level))
            if (child.type == FileType.DIRECTORY && child.path in expandedNodes) {
                traverse(child.children, level + 1)
            }
        }
    }
    traverse(rootNode.children, 0)
    return result
}

/**
 * Lti file tree component for displaying hierarchical file structures with
 * luminous selection and hover micro-interactions.
 *
 * @param rootNode The root node of the file tree
 * @param expandedNodes Set of expanded node paths
 * @param onNodeClick Callback when a node is clicked
 * @param modifier Modifier to be applied to the file tree
 * @param selectedPath Optional path of the currently selected/active file
 */
@Composable
fun GlassFileTree(
    rootNode: FileNode,
    expandedNodes: Set<String>,
    onNodeClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    selectedPath: String? = null,
) {
    val visibleNodes = remember(rootNode, expandedNodes) {
        flattenFileTree(rootNode, expandedNodes)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = Spacing.ExtraSmall,
            vertical = Spacing.ExtraSmall,
        ),
    ) {
        items(
            items = visibleNodes,
            key = { it.node.path },
            contentType = { it.node.type },
        ) { item ->
            GlassFileTreeItem(
                node = item.node,
                level = item.level,
                isExpanded = item.node.path in expandedNodes,
                isSelected = item.node.path == selectedPath,
                onNodeClick = onNodeClick,
            )
        }
    }
}

/**
 * File tree item component representing a single node in the tree.
 */
@Composable
private fun GlassFileTreeItem(
    node: FileNode,
    level: Int,
    isExpanded: Boolean,
    onNodeClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val itemShape = GlassShapes.Compact
    val colorScheme = MaterialTheme.colorScheme
    val (itemBackground, itemBorderColor) = resolveItemColors(isSelected, isHovered, colorScheme)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.ExtraExtraSmall)
            .clip(itemShape)
            .background(itemBackground)
            .then(
                if (isSelected || isHovered) {
                    Modifier.glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = itemBorderColor,
                        shape = itemShape,
                    )
                } else {
                    Modifier
                },
            )
            .hoverable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = { onNodeClick(node) },
            )
            .padding(
                start = Spacing.Medium * level + Spacing.Small,
                top = Spacing.Compact,
                bottom = Spacing.Compact,
                end = Spacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Expansion indicator for directories
        if (node.type == FileType.DIRECTORY) {
            DirectoryExpansionIndicator(isExpanded = isExpanded, isSelected = isSelected)
        } else {
            Spacer(modifier = Modifier.width(Spacing.TabHorizontal))
        }

        // File/folder icon
        LtiFileIcon(
            fileType = node.type,
            isExpanded = isExpanded,
        )

        Spacer(modifier = Modifier.width(Spacing.Small))

        // File/folder name
        val (textColor, textWeight) = resolveTextAttributes(
            isSelected = isSelected,
            isDir = node.type == FileType.DIRECTORY,
            colorScheme = colorScheme,
        )
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = textWeight,
        )
    }
}

/**
 * Resolves the background and border color for a file tree item.
 */
private fun resolveItemColors(isSelected: Boolean, isHovered: Boolean, colorScheme: ColorScheme): Pair<Color, Color> {
    val background = when {
        isSelected -> colorScheme.primary.copy(alpha = AlphaTokens.Hover)
        isHovered -> colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Subtle)
        else -> Color.Transparent
    }
    val border = when {
        isSelected -> colorScheme.primary.copy(alpha = AlphaTokens.Half)
        isHovered -> colorScheme.outline.copy(alpha = AlphaTokens.Faint)
        else -> Color.Transparent
    }
    return background to border
}

/**
 * Expansion indicator icon for directory nodes.
 */
@Composable
private fun DirectoryExpansionIndicator(isExpanded: Boolean, isSelected: Boolean) {
    Icon(
        painter = if (isExpanded) {
            AppIcons.ChevronDownPainterResource()
        } else {
            AppIcons.ChevronRightPainterResource()
        },
        contentDescription = null,
        modifier = Modifier.size(IconSize.Medium),
        tint = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    Spacer(modifier = Modifier.width(Spacing.ExtraSmall))
}

/**
 * Resolves text color and font weight for a file tree item.
 */
private fun resolveTextAttributes(
    isSelected: Boolean,
    isDir: Boolean,
    colorScheme: ColorScheme,
): Pair<Color, FontWeight> {
    val color = when {
        isSelected || isDir -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant
    }
    val weight = when {
        isSelected -> FontWeight.SemiBold
        isDir -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    return color to weight
}

/**
 * File icon component that displays the appropriate icon for the file type.
 */
@Composable
private fun LtiFileIcon(fileType: FileType, isExpanded: Boolean = false) {
    val icon: AppIcon = when (fileType) {
        FileType.DIRECTORY -> if (isExpanded) {
            AppIcon.Painted(AppIcons.FolderOpenPainterResource)
        } else {
            AppIcon.Painted(AppIcons.FolderPainterResource)
        }
        FileType.FILE -> AppIcon.Painted(AppIcons.FilePainterResource)
        FileType.SYMLINK -> AppIcon.Painted(AppIcons.LinkPainterResource)
    }

    val tint = when (fileType) {
        FileType.DIRECTORY -> MaterialTheme.colorScheme.primary
        FileType.FILE -> MaterialTheme.colorScheme.onSurfaceVariant
        FileType.SYMLINK -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    when (icon) {
        is AppIcon.Vector -> Icon(
            imageVector = icon.imageVector,
            contentDescription = null,
            modifier = Modifier.size(IconSize.Medium),
            tint = tint,
        )
        is AppIcon.Painted -> Icon(
            painter = icon.painter(),
            contentDescription = null,
            modifier = Modifier.size(IconSize.Medium),
            tint = tint,
        )
    }
}
