/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.ai.MessageRole
import org.ide.lti.core.designsystem.component.GlassChip
import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType
import org.ide.lti.core.ui.GlassChatInput
import org.ide.lti.core.ui.GlassCodeEditor
import org.ide.lti.core.ui.GlassFileTree
import org.ide.lti.core.ui.GlassMessageBubble
import org.ide.lti.core.ui.GlassScrollableTabRow
import org.ide.lti.core.ui.TabContent
import org.ide.lti.core.ui.syntax.SyntaxLanguage

@Composable
fun CoreUiTab() {
    val scrollState = rememberScrollState()

    var editorCode by remember {
        mutableStateOf(
            """// Kotlin Multiplatform Glass Design System
fun main() {
    val theme = GlassTheme.colors
    println("Active Accent: " + theme.glassAccent)
}"""
        )
    }

    val sampleFileTree = remember {
        FileNode(
            path = "/root",
            name = "LtiRomStudio",
            type = FileType.DIRECTORY,
            children = listOf(
                FileNode(
                    path = "/root/core",
                    name = "core",
                    type = FileType.DIRECTORY,
                    children = listOf(
                        FileNode(path = "/root/core/Color.kt", name = "Color.kt", type = FileType.FILE),
                        FileNode(path = "/root/core/Dimens.kt", name = "Dimens.kt", type = FileType.FILE),
                        FileNode(path = "/root/core/GlassButton.kt", name = "GlassButton.kt", type = FileType.FILE),
                    )
                ),
                FileNode(
                    path = "/root/feature",
                    name = "feature",
                    type = FileType.DIRECTORY,
                    children = listOf(
                        FileNode(path = "/root/feature/WorkspaceScreen.kt", name = "WorkspaceScreen.kt", type = FileType.FILE)
                    )
                )
            )
        )
    }

    var expandedFilePaths by remember { mutableStateOf(setOf("/root/core", "/root/feature")) }
    val pagerState = rememberPagerState { 3 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Glass Code Editor
        SectionContainer(
            title = "1. Glass Code Editor (GlassCodeEditor)",
            description = "Monospace syntax-highlighted code editor with line numbers and horizontal scrolling."
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.MinPanelHeight * 1.5f)
                    .clip(GlassShapes.Small)
                    .background(GlassTheme.colors.glassSurface)
            ) {
                GlassCodeEditor(
                    content = editorCode,
                    onContentChange = { editorCode = it },
                    modifier = Modifier.fillMaxSize(),
                    language = SyntaxLanguage.KOTLIN,
                )
            }
        }

        // Section 2: Glass File Explorer Tree
        SectionContainer(
            title = "2. Glass File Explorer Tree (GlassFileTree)",
            description = "Interactive hierarchical project explorer with expandable folder nodes."
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.MinPanelHeight * 1.8f)
                    .clip(GlassShapes.Small)
                    .background(GlassTheme.colors.glassSurface)
                    .padding(Spacing.Small)
            ) {
                GlassFileTree(
                    rootNode = sampleFileTree,
                    expandedNodes = expandedFilePaths,
                    onNodeClick = { node ->
                        if (node.type == FileType.DIRECTORY) {
                            expandedFilePaths = if (expandedFilePaths.contains(node.path)) {
                                expandedFilePaths - node.path
                            } else {
                                expandedFilePaths + node.path
                            }
                        }
                    }
                )
            }
        }

        // Section 3: Glass Chat Input & Message Bubbles
        SectionContainer(
            title = "3. AI Assistant Chat Components (GlassChatInput & GlassMessageBubble)",
            description = "AI chat interface widgets with role-based styling and copy actions."
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                GlassMessageBubble(
                    content = "How can I verify the WCAG contrast ratios of my color tokens?",
                    role = MessageRole.USER,
                    timestamp = 1772184000000L,
                    modifier = Modifier.fillMaxWidth()
                )

                GlassMessageBubble(
                    content = "Run `./gradlew :core:designsystem:test` to run GlassContrastTest and GlassComponentScreenshotTest.",
                    role = MessageRole.ASSISTANT,
                    timestamp = 1772184010000L,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.ExtraSmall))

                GlassChatInput(
                    onSendMessage = {},
                    placeholder = "Ask AI assistant about design tokens..."
                )
            }
        }

        // Section 4: Glass Chips & Tabs
        SectionContainer(
            title = "4. Action Chips & Scrollable Tab Rows (GlassChip & GlassScrollableTabRow)",
            description = "Compact hover-reactive metadata chips and horizontally scrollable tab rows."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                GlassChip(label = "Kotlin 2.1.0")
                GlassChip(label = "Compose 1.9.0")
                GlassChip(label = "Skiko GPU")
                GlassChip(label = "WCAG 2.1 AA")
            }

            Spacer(Modifier.height(Spacing.Small))

            GlassScrollableTabRow(
                tabContents = listOf(
                    TabContent("Tab 1") {
                        Text("Tab 1 content area", color = GlassTheme.colors.onGlassSecondary, modifier = Modifier.padding(Spacing.Medium))
                    },
                    TabContent("Tab 2") {
                        Text("Tab 2 content area", color = GlassTheme.colors.onGlassSecondary, modifier = Modifier.padding(Spacing.Medium))
                    },
                    TabContent("Tab 3") {
                        Text("Tab 3 content area", color = GlassTheme.colors.onGlassSecondary, modifier = Modifier.padding(Spacing.Medium))
                    }
                ),
                pagerState = pagerState,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
