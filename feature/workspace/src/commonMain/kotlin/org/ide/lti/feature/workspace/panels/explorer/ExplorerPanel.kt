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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.display.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme
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
    val selectedFile by viewModel.selectedFile.collectAsState()
    val glassColors = GlassTheme.colors

    val rootName = (uiState as? ExplorerUiState.Success)?.rootNode?.name
    val headerTitle = if (!rootName.isNullOrBlank()) rootName else "Explorer"

    GlassPanelContainer(
        modifier = modifier,
        header = {
            GlassPanelHeader(
                title = headerTitle,
                actions = {
                    GlassPanelHeaderAction(
                        icon = AppIcon.Painted(AppIcons.RefreshPainterResource),
                        contentDescription = "Refresh",
                        onClick = { viewModel.refresh() },
                    )
                },
            )
        },
        scrollEdgeFade = false,
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
                    selectedPath = selectedFile?.path,
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
                        .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassEmptyState(
                        icon = AppIcons.FolderOpen,
                        title = "No Folder Open",
                        description = "Open a folder to start browsing files",
                        descriptionColor = glassColors.onGlassMuted,
                        iconTint = glassColors.onGlassMuted,
                        action = {
                            Spacer(modifier = Modifier.height(Spacing.Medium))
                            val buttonShape = if (GlassTheme.appTheme == AppTheme.Dark) GlassShapes.MediumSmall else GlassShapes.Capsule
                            GlassSecondaryButton(
                                onClick = { viewModel.openFolder() },
                                shape = buttonShape,
                            ) {
                                Icon(
                                    painter = AppIcons.FolderOpenPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                                Spacer(modifier = Modifier.width(Spacing.Small))
                                Text(text = "Open Folder")
                            }
                        },
                    )
                }
            }
        }
    }
}
