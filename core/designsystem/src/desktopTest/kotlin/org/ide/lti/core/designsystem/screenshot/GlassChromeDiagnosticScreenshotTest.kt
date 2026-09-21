/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.display.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.GlassLayout
import org.ide.lti.core.designsystem.component.layout.rememberLtiLayoutState
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTabStrip
import org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.glass.rememberGlassSceneState
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test

class GlassChromeDiagnosticScreenshotTest {

    companion object {
        private const val TEST_WIDTH = 1200
        private const val TEST_HEIGHT = 750
    }

    // Realistic Explorer Panel
    @Composable
    private fun MockExplorerPanel() {
        GlassPanelContainer(
            header = {
                GlassPanelHeader(
                    title = "EXPLORER",
                    actions = {
                        GlassPanelHeaderAction(
                            icon = AppIcons.Refresh,
                            contentDescription = "Refresh",
                            onClick = {},
                        )
                    },
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                MockTreeItem(name = "LtiRomGui", isFolder = true, isOpen = true, depth = 0)
                MockTreeItem(name = "core", isFolder = true, isOpen = true, depth = 1)
                MockTreeItem(name = "designsystem", isFolder = true, isOpen = true, depth = 2)
                MockTreeItem(name = "GlassLayout.kt", isFolder = false, isOpen = false, depth = 3, isSelected = true)
                MockTreeItem(name = "GlassTopBar.kt", isFolder = false, isOpen = false, depth = 3)
                MockTreeItem(name = "GlassStatusBar.kt", isFolder = false, isOpen = false, depth = 3)
                MockTreeItem(name = "lti-shared", isFolder = true, isOpen = true, depth = 1)
                MockTreeItem(name = "LtiSharedApp.kt", isFolder = false, isOpen = false, depth = 2)
            }
        }
    }

    @Suppress("UnusedParameter")
    @Composable
    private fun MockTreeItem(
        name: String,
        isFolder: Boolean,
        isOpen: Boolean,
        depth: Int,
        isSelected: Boolean = false,
    ) {
        val glassColors = GlassTheme.colors
        val bgModifier = if (isSelected) {
            Modifier.background(
                glassColors.glassAccent.copy(alpha = AlphaTokens.Hover),
                RoundedCornerShape(CornerRadius.Compact),
            )
        } else {
            Modifier
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(bgModifier)
                .padding(
                    start = Spacing.Fourteen * depth,
                    top = Spacing.ExtraExtraSmall,
                    bottom = Spacing.ExtraExtraSmall,
                    end = Spacing.ExtraSmall,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
        ) {
            Icon(
                painter = if (isFolder) {
                    AppIcons.FolderPainterResource()
                } else {
                    AppIcons.FilePainterResource()
                },
                contentDescription = null,
                tint = if (isFolder) {
                    glassColors.glassAccent
                } else if (isSelected) {
                    glassColors.onGlass
                } else {
                    glassColors.onGlassMuted
                },
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isSelected) glassColors.onGlass else glassColors.onGlassSecondary,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }

    // Realistic Editor Panel
    @Composable
    private fun MockEditorPanel() {
        val glassColors = GlassTheme.colors
        val syntax = glassColors.syntax
        GlassPanelContainer(
            header = {
                GlassPanelHeader(
                    title = "GlassLayout.kt",
                    icon = AppIcons.File,
                    actions = {
                        GlassPanelHeaderAction(
                            icon = AppIcons.Check,
                            contentDescription = "Run",
                            onClick = {},
                        )
                    },
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassColors.canvasSurface)
                    .padding(Spacing.SmallMedium),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val codeLines = listOf(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.keyword)) { append("package ") }
                            withStyle(SpanStyle(color = syntax.plain)) { append("org.ide.lti.core.designsystem") }
                        },
                        buildAnnotatedString { },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.annotation)) { append("@Composable") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.keyword)) { append("fun ") }
                            withStyle(SpanStyle(color = syntax.function)) { append("GlassLayout") }
                            withStyle(SpanStyle(color = syntax.plain)) { append("(") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append("    state: ") }
                            withStyle(SpanStyle(color = syntax.type)) { append("LtiLayoutState") }
                            withStyle(SpanStyle(color = syntax.plain)) { append(" = ") }
                            withStyle(SpanStyle(color = syntax.function)) { append("rememberLtiLayoutState") }
                            withStyle(SpanStyle(color = syntax.plain)) { append("(),") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append("    version: ") }
                            withStyle(SpanStyle(color = syntax.type)) { append("Int") }
                            withStyle(SpanStyle(color = syntax.plain)) { append(" = ") }
                            withStyle(SpanStyle(color = syntax.number)) { append("42") }
                            withStyle(SpanStyle(color = syntax.plain)) { append(",") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append("    title: ") }
                            withStyle(SpanStyle(color = syntax.type)) { append("String") }
                            withStyle(SpanStyle(color = syntax.plain)) { append(" = ") }
                            withStyle(SpanStyle(color = syntax.string)) { append("\"Liquid Glass Studio\"") }
                            withStyle(SpanStyle(color = syntax.plain)) { append(",") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append(") {") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.comment)) {
                                append("    // Liquid Glass Morphism Container")
                            }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append("    ") }
                            withStyle(SpanStyle(color = syntax.function)) { append("GlassTopBar") }
                            withStyle(SpanStyle(color = syntax.plain)) { append("()") }
                        },
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = syntax.plain)) { append("}") }
                        },
                    )

                    codeLines.forEachIndexed { index, annotatedLine ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Hairline),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        ) {
                            Text(
                                text = "${index + 1}".padStart(3, ' '),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = glassColors.onGlassMuted.copy(alpha = AlphaTokens.Half),
                                ),
                            )
                            Text(
                                text = annotatedLine,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    color = syntax.plain,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    // Realistic AI Assistant Panel
    @Composable
    private fun MockAIAssistantPanel() {
        val glassColors = GlassTheme.colors
        GlassPanelContainer(
            header = {
                GlassPanelHeader(
                    title = "AI Assistant",
                    icon = AppIcons.Info,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.SmallMedium),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CornerRadius.MediumSmall))
                            .background(glassColors.glassSurfaceElevated)
                            .border(
                                StrokeWidth.Hairline,
                                glassColors.glassBorder,
                                RoundedCornerShape(CornerRadius.MediumSmall),
                            )
                            .padding(Spacing.SmallMedium),
                    ) {
                        Text(
                            text = "Analyzing GlassLayout and chrome geometry. " +
                                "Top bar and status bar layout alignment ready for review.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = glassColors.onGlassSecondary,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.Compact))
                        .background(glassColors.glassSurface)
                        .border(
                            StrokeWidth.Hairline,
                            glassColors.glassBorder,
                            RoundedCornerShape(CornerRadius.Compact),
                        )
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ask AI Assistant...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = glassColors.onGlassMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = AppIcons.EditPainterResource(),
                        contentDescription = "Send",
                        tint = glassColors.glassAccent,
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }
        }
    }

    /**
     * Diagnostic Integration Offscreen Render & Screenshot Regression Test for Dark Mode.
     * Composes real aurora Canvas + LtiTheme + GlassLayout with default topBar & statusBar.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDiagnosticRealAppChromeAndBackdrop() = runDesktopComposeUiTest(
        width = TEST_WIDTH,
        height = TEST_HEIGHT,
    ) {
        setContent {
            val backdrop = rememberGlassSceneState()

            LtiTheme(sharedScene = backdrop, darkTheme = true) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Explorer",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop(backdrop = backdrop)

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftRail = {
                            org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail(
                                tabs = leftTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition.Left,
                            )
                        },
                        leftPanel = { MockExplorerPanel() },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition.Right,
                            )
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_app_chrome_full_composition", image)
    }

    /**
     * Diagnostic Integration Offscreen Render & Screenshot Regression Test for Light Mode.
     * Composes real aurora Canvas + LtiTheme + GlassLayout with default topBar & statusBar in light theme.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDiagnosticRealAppChromeAndBackdropLight() = runDesktopComposeUiTest(
        width = TEST_WIDTH,
        height = TEST_HEIGHT,
    ) {
        setContent {
            val backdrop = rememberGlassSceneState()

            LtiTheme(sharedScene = backdrop, darkTheme = false) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Explorer",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop(backdrop = backdrop)

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftRail = {
                            org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail(
                                tabs = leftTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition.Left,
                            )
                        },
                        leftPanel = { MockExplorerPanel() },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition.Right,
                            )
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_app_chrome_full_composition_light", image)
    }

    /**
     * Diagnostic Integration Offscreen Render & Screenshot Regression Test for the continuous
     * frame combined with [org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTabStrip], matching
     * exactly how `WorkspaceScreen` wires the left/right panels in production.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDiagnosticRealAppChromeWithSidePanelTabs() = runDesktopComposeUiTest(
        width = TEST_WIDTH,
        height = TEST_HEIGHT,
    ) {
        setContent {
            val backdrop = rememberGlassSceneState()

            LtiTheme(sharedScene = backdrop, darkTheme = true) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Files",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop(backdrop = backdrop)

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftPanel = {
                            Column(modifier = Modifier.fillMaxSize()) {
                                org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTabStrip(
                                    tabs = leftTabs,
                                    selectedIndex = 0,
                                    onSelectedIndexChange = {},
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                ) {
                                    MockExplorerPanel()
                                }
                            }
                        },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition.Right,
                            )
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_app_chrome_with_side_panel_tabs", image)
    }
}
