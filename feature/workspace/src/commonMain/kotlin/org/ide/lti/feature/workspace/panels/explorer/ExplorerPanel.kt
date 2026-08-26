/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.explorer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.model.filesystem.FileType
import org.ide.lti.core.ui.GlassFileTree
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExplorerPanel(
    modifier: Modifier = Modifier,
    viewModel: ExplorerViewModel = koinViewModel(),
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
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = "Refresh",
                            tint = glassColors.onGlassSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
            )
        },
    ) {
        when (val state = uiState) {
            is ExplorerUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = glassColors.glassAccent,
                        modifier = Modifier.size(28.dp)
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
                        }
                    },
                )
            }

            is ExplorerUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.openFolder() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(glassColors.glassSurfaceElevated)
                                .border(0.5.dp, glassColors.glassBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = AppIcons.FolderOpen,
                                contentDescription = "Open Folder",
                                tint = glassColors.glassAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No Workspace Selected",
                            color = glassColors.onGlass,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Open a directory to explore files",
                            color = glassColors.onGlassMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        GlassButton(
                            onClick = { viewModel.openFolder() }
                        ) {
                            Text(text = "Open Folder")
                        }
                    }
                }
            }
        }
    }
}