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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.display.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.syntax.SyntaxLanguage
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EditorPanel(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = koinViewModel(),
    onOpenFolder: () -> Unit = {},
    onCommandPalette: () -> Unit = {},
    onActiveFileChange: (path: String?, language: String?) -> Unit = { _, _ -> },
    onCursorPositionChange: (line: Int, column: Int) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val isWorkspaceOpen by viewModel.isWorkspaceOpen.collectAsState()

    GlassPanelContainer(
        modifier = modifier,
        header = {
            if (uiState is EditorUiState.Empty) {
                GlassPanelHeader(
                    title = "Editor",
                )
            }
        },
    ) {
        when (val state = uiState) {
            is EditorUiState.Empty -> {
                LaunchedEffect(Unit) {
                    onActiveFileChange(null, null)
                }
                if (isWorkspaceOpen) {
                    EmptyEditorView()
                } else {
                    NoWorkspaceEditorView(
                        onOpenFolder = onOpenFolder,
                        onCommandPalette = onCommandPalette,
                    )
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
private fun NoWorkspaceEditorView(
    onOpenFolder: () -> Unit,
    onCommandPalette: () -> Unit,
) {
    val glassColors = GlassTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // LtiRom Studio Brand Mark
            Box(
                modifier = Modifier
                    .size(ComponentSize.EmptyStateBadgeSize)
                    .clip(GlassShapes.Medium)
                    .background(GlassTheme.branding.logoBadgeBrush)
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = glassColors.glassBorderHighlight,
                        shape = GlassShapes.Medium,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "L",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }

            // Title & Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "LtiRom Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = glassColors.onGlass,
                )
                Text(
                    text = "Lightweight, GPU-Accelerated Liquid Glass IDE",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassMuted,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassPrimaryButton(
                    onClick = onOpenFolder,
                ) {
                    Icon(
                        painter = AppIcons.FolderOpenPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    Text("Open Folder")
                }

                GlassSecondaryButton(
                    onClick = onCommandPalette,
                ) {
                    Icon(
                        painter = AppIcons.SearchPainterResource(),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    Text("Command Palette")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Keyboard shortcuts strip
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorShortcutHint(keys = "Ctrl P", label = "Files")
                EditorShortcutHint(keys = "Ctrl O", label = "Folder")
                EditorShortcutHint(keys = "Ctrl `", label = "Terminal")
            }
        }
    }
}

@Composable
private fun EditorShortcutHint(keys: String, label: String) {
    val glassColors = GlassTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Box(
            modifier = Modifier
                .clip(GlassShapes.ExtraSmall)
                .background(glassColors.glassSurfaceElevated)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = glassColors.glassBorderHighlight,
                    shape = GlassShapes.ExtraSmall,
                )
                .padding(
                    horizontal = Spacing.Small,
                    vertical = Spacing.ExtraExtraSmall,
                ),
        ) {
            Text(
                text = keys,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = FontSize.Micro,
                ),
                color = glassColors.onGlass,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = glassColors.onGlassMuted,
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
                    .clip(GlassShapes.MediumSmall)
                    .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Disabled))
                    .glassOutlineBorder(
                        color = glassColors.glassBorder,
                        width = StrokeWidth.Hairline,
                        shape = GlassShapes.MediumSmall,
                    )
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
                .clip(GlassShapes.ExtraSmall)
                .background(glassColors.glassSurfaceElevated)
                .glassOutlineBorder(
                    color = glassColors.glassBorder,
                    width = StrokeWidth.Hairline,
                    shape = GlassShapes.ExtraSmall,
                )
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
