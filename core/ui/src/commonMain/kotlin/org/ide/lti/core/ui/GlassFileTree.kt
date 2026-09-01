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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType

/**
 * Represents a flattened file tree node with its nesting depth.
 */
@Immutable
internal data class FlattenedFileNode(
    val node: FileNode,
    val level: Int,
)

/**
 * Flattens the visible tree nodes based on current expanded directory state.
 */
internal fun flattenFileTree(
    rootNode: FileNode,
    expandedNodes: Set<String>,
): List<FlattenedFileNode> {
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
 * Lti file tree component for displaying hierarchical file structures.
 *
 * @param rootNode The root node of the file tree
 * @param expandedNodes Set of expanded node paths
 * @param onNodeClick Callback when a node is clicked
 * @param modifier Modifier to be applied to the file tree
 */
@Composable
fun GlassFileTree(
    rootNode: FileNode,
    expandedNodes: Set<String>,
    onNodeClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleNodes = remember(rootNode, expandedNodes) {
        flattenFileTree(rootNode, expandedNodes)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = visibleNodes,
            key = { it.node.path },
            contentType = { it.node.type },
        ) { item ->
            GlassFileTreeItem(
                node = item.node,
                level = item.level,
                expandedNodes = expandedNodes,
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
    expandedNodes: Set<String>,
    onNodeClick: (FileNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = { onNodeClick(node) },
            )
            .padding(
                start = Spacing.Medium * level + Spacing.Small,
                top = Spacing.ExtraSmall,
                bottom = Spacing.ExtraSmall,
                end = Spacing.Small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Expansion indicator for directories
        if (node.type == FileType.DIRECTORY) {
            Icon(
                painter = if (node.path in expandedNodes) {
                    AppIcons.ChevronDownPainterResource()
                } else {
                    AppIcons.ChevronRightPainterResource()
                },
                contentDescription = null,
                modifier = Modifier.size(IconSize.Medium),
                tint = glassColors.onGlassMuted,
            )
            Spacer(modifier = Modifier.width(Spacing.ExtraSmall))
        } else {
            Spacer(modifier = Modifier.width(Spacing.TabHorizontal))
        }

        // File/folder icon
        LtiFileIcon(
            fileType = node.type,
            isExpanded = node.path in expandedNodes,
        )

        Spacer(modifier = Modifier.width(Spacing.Small))

        // File/folder name
        Text(
            text = node.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (node.type == FileType.DIRECTORY) glassColors.onGlass else glassColors.onGlassSecondary,
            fontWeight = if (node.type == FileType.DIRECTORY) {
                FontWeight.Medium
            } else {
                FontWeight.Normal
            },
        )
    }
}

/**
 * File icon component that displays the appropriate icon for the file type.
 */
@Composable
private fun LtiFileIcon(
    fileType: FileType,
    isExpanded: Boolean = false,
) {
    val glassColors = GlassTheme.colors
    val icon: AppIcon = when (fileType) {
        FileType.DIRECTORY -> if (isExpanded) {
            AppIcon.Vector(AppIcons.FolderOpen)
        } else {
            AppIcon.Painted(AppIcons.FolderPainterResource)
        }
        FileType.FILE -> AppIcon.Painted(AppIcons.FilePainterResource)
        FileType.SYMLINK -> AppIcon.Vector(AppIcons.Link)
    }

    val tint = when (fileType) {
        FileType.DIRECTORY -> glassColors.glassAccent
        FileType.FILE -> glassColors.onGlassSecondary
        FileType.SYMLINK -> glassColors.onGlassMuted
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
