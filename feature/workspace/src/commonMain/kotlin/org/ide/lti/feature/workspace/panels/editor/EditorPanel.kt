/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.GlassTabBar
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.ui.GlassCodeEditor
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditorPanel(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    GlassPanelContainer(
        modifier = modifier,
    ) {
        when (val state = uiState) {
            is EditorUiState.Empty -> {
                EmptyEditorView()
            }

            is EditorUiState.Loaded -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Tab bar
                    GlassTabBar(
                        tabs = state.openFiles.map { file ->
                            if (file.isModified) " â€¢" else file.name
                        },
                        selectedIndex = state.activeFileIndex,
                        onTabSelected = { index ->
                            viewModel.setActiveFile(index)
                        },
                        onTabClosed = { index ->
                            viewModel.closeFile(index)
                        },
                    )

                    // File info header
                    val currentFile = state.openFiles[state.activeFileIndex]
                    GlassPanelHeader(
                        title = currentFile.path,
                        actions = {
                            if (currentFile.isModified) {
                                GlassPanelHeaderAction(
                                    icon = AppIcons.Save,
                                    contentDescription = "Save file",
                                    onClick = { viewModel.saveFile() },
                                )
                            }
                        },
                    )

                    // Editor content
                    GlassCodeEditor(
                        content = currentFile.content,
                        onContentChange = { newContent ->
                            viewModel.updateContent(newContent)
                        },
                        modifier = Modifier.fillMaxSize(),
                        showLineNumbers = true,
                    )
                }
            }

            is EditorUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun EmptyEditorView() {
    val glassColors = GlassTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(glassColors.glassSurfaceElevated)
                    .border(0.5.dp, glassColors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.File,
                    contentDescription = null,
                    tint = glassColors.glassAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No File Open",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glassColors.onGlass,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Select a file from the Explorer or press Ctrl+P to quick open",
                style = MaterialTheme.typography.bodySmall,
                color = glassColors.onGlassMuted,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Quick shortcuts help grid
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .width(320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(glassColors.glassSurfaceElevated.copy(alpha = 0.4f))
                    .border(0.5.dp, glassColors.glassBorder, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                ShortcutRow(action = "Search Files", keys = "Ctrl + P")
                ShortcutRow(action = "Show All Commands", keys = "Ctrl + Shift + P")
                ShortcutRow(action = "Toggle Sidebar", keys = "Ctrl + B")
            }
        }
    }
}

@Composable
private fun ShortcutRow(action: String, keys: String) {
    val glassColors = GlassTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = glassColors.onGlassSecondary
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(glassColors.glassSurfaceElevated)
                .border(0.5.dp, glassColors.glassBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                color = glassColors.glassAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}