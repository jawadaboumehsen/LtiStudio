/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassEmptyState
import org.ide.lti.core.designsystem.component.GlassPanelContainer
import org.ide.lti.core.designsystem.component.GlassPanelHeader
import org.ide.lti.core.designsystem.component.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.GlassTabBar
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.syntax.SyntaxLanguage
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditorPanel(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = koinViewModel(),
    onActiveFileChange: (path: String?, language: String?) -> Unit = { _, _ -> },
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWorkspaceOpen by viewModel.isWorkspaceOpen.collectAsState()

    GlassPanelContainer(
        modifier = modifier,
    ) {
        when (val state = uiState) {
            is EditorUiState.Empty -> {
                LaunchedEffect(Unit) {
                    onActiveFileChange(null, null)
                }
                if (isWorkspaceOpen) {
                    EmptyEditorView()
                } else {
                    NoWorkspaceEditorView()
                }
            }

            is EditorUiState.Loaded -> {
                val currentFile = state.openFiles.getOrNull(state.activeFileIndex)
                val activePath = currentFile?.path
                val activeLanguage = currentFile?.language

                LaunchedEffect(activePath, activeLanguage) {
                    onActiveFileChange(activePath, activeLanguage)
                }

                if (currentFile != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Tab bar
                        GlassTabBar(
                            tabs = state.openFiles.map { file ->
                                if (file.isModified) "${file.name} •" else file.name
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
                        GlassPanelHeader(
                            title = currentFile.path,
                            actions = {
                                if (currentFile.isModified) {
                                    GlassPanelHeaderAction(
                                        icon = AppIcon.Painted(AppIcons.SavePainterResource),
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
                            language = SyntaxLanguage.fromFileName(currentFile.name),
                            onCursorPositionChange = onCursorPositionChange,
                        )
                    }
                }
            }

            is EditorUiState.Error -> {
                LaunchedEffect(Unit) {
                    onActiveFileChange(null, null)
                }
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

@Composable
private fun NoWorkspaceEditorView() {
    val glassColors = GlassTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glassColors.canvasSurface),
        contentAlignment = Alignment.Center,
    ) {
        GlassEmptyState(
            icon = AppIcon.Painted(AppIcons.FilePainterResource),
            title = "Welcome to LtiRom",
            description = "Open a folder in the Explorer to begin editing",
            iconTint = glassColors.onGlassMuted,
            titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            titleColor = glassColors.onGlassSecondary,
            descriptionColor = glassColors.onGlassMuted,
            modifier = Modifier.padding(Spacing.Large),
        )
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
            verticalArrangement = Arrangement.Center,
        ) {
            GlassEmptyState(
                icon = AppIcon.Painted(AppIcons.FilePainterResource),
                title = "No File Open",
                description = "Select a file from the Explorer or press Ctrl+P to quick open",
                descriptionColor = glassColors.onGlassMuted,
            )

            Spacer(modifier = Modifier.height(Spacing.ExtraLarge))

            // Quick shortcuts help grid
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .width(ComponentSize.ShortcutHelpWidth)
                    .clip(RoundedCornerShape(CornerRadius.MediumSmall))
                    .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Disabled))
                    .border(StrokeWidth.Hairline, glassColors.glassBorder, RoundedCornerShape(CornerRadius.MediumSmall))
                    .padding(Spacing.Medium),
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge),
    ) {
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            color = glassColors.onGlassSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
                .background(glassColors.glassSurfaceElevated)
                .border(StrokeWidth.Hairline, glassColors.glassBorder, RoundedCornerShape(CornerRadius.ExtraSmall))
                .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall,
                color = glassColors.glassAccent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
