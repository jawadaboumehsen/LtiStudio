/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType

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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        // Render root node's children
        rootNode.children?.let { children ->
            items(children) { node ->
                GlassFileTreeItem(
                    node = node,
                    level = 0,
                    expandedNodes = expandedNodes,
                    onNodeClick = onNodeClick,
                )
            }
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
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNodeClick(node) }
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
                    imageVector = if (node.path in expandedNodes) {
                        AppIcons.KeyboardArrowDown
                    } else {
                        AppIcons.KeyboardArrowRight
                    },
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Medium),
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
                fontWeight = if (node.type == FileType.DIRECTORY) {
                    FontWeight.Medium
                } else {
                    FontWeight.Normal
                },
            )
        }

        // Render children if expanded
        if (node.type == FileType.DIRECTORY && node.path in expandedNodes) {
            node.children?.forEach { child ->
                GlassFileTreeItem(
                    node = child,
                    level = level + 1,
                    expandedNodes = expandedNodes,
                    onNodeClick = onNodeClick,
                )
            }
        }
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
    val icon = when (fileType) {
        FileType.DIRECTORY -> if (isExpanded) AppIcons.FolderOpen else AppIcons.Folder
        FileType.FILE -> AppIcons.File
        FileType.SYMLINK -> AppIcons.Link
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(IconSize.Medium),
    )
}
