/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.explorer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.GlassEmptyState
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.filesystem.FileType
import org.ide.lti.core.ui.GlassFileTree
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExplorerPanel(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinViewModel(),
    onFileClick: (path: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val expandedNodes by viewModel.expandedNodes.collectAsState()
    val glassColors = GlassTheme.colors

    GlassPanelContainer(
        modifier = modifier,
        header = {
            GlassPanelHeader(
                title = "Explorer",
                actions = {
                    GlassPanelHeaderAction(
                        icon = AppIcon.Painted(AppIcons.RefreshPainterResource),
                        contentDescription = "Refresh",
                        onClick = { viewModel.refresh() },
                    )
                },
            )
        },
        scrollEdgeFade = true,
    ) {
        when (val state = uiState) {
            is ExplorerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = glassColors.glassAccent,
                        modifier = Modifier.size(ComponentSize.SpinnerSize),
                    )
                }
            }

            is ExplorerUiState.Success -> {
                GlassFileTree(
                    rootNode = state.rootNode,
                    expandedNodes = expandedNodes,
                    onNodeClick = { node ->
                        if (node.type == FileType.DIRECTORY) {
                            viewModel.toggleNodeExpansion(node.path)
                        } else {
                            viewModel.onFileSelected(node)
                            onFileClick(node.path)
                        }
                    },
                )
            }

            is ExplorerUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassEmptyState(
                        icon = AppIcons.FolderOpen,
                        title = "No Workspace Selected",
                        description = "Open a directory to explore files",
                        descriptionColor = glassColors.onGlassMuted,
                        onIconClick = { viewModel.openFolder() },
                        action = {
                            GlassPrimaryButton(
                                onClick = { viewModel.openFolder() },
                            ) {
                                Text(text = "Open Folder")
                            }
                        },
                    )
                }
            }
        }
    }
}
