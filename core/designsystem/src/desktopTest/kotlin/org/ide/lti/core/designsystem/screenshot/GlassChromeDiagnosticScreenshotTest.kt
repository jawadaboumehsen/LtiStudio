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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
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
import dev.chrisbanes.haze.hazeSource
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.display.GlassPanelHeaderAction
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.GlassLayout
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorItem
import org.ide.lti.core.designsystem.component.layout.IdeNavigatorPanel
import org.ide.lti.core.designsystem.component.layout.IdeStatusBar
import org.ide.lti.core.designsystem.component.layout.IdeTopAppBar
import org.ide.lti.core.designsystem.component.layout.rememberLtiLayoutState
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelRail
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTabStrip
import org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

@Suppress("LargeClass") // Diagnostic screenshot test suite covering multiple chrome configurations
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
        val colors = MaterialTheme.colorScheme
        val bgModifier = if (isSelected) {
            Modifier.background(
                colors.primary.copy(alpha = AlphaTokens.Hover),
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
                    colors.primary
                } else if (isSelected) {
                    colors.onSurface
                } else {
                    colors.outline
                },
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }

    // Realistic Editor Panel
    @Composable
    private fun MockEditorPanel() {
        val syntax = GlassTheme.syntaxColors
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
                    .background(MaterialTheme.colorScheme.surface)
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
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Half),
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
        val colors = MaterialTheme.colorScheme
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
                            .background(colors.surfaceContainerHigh)
                            .border(
                                StrokeWidth.Hairline,
                                colors.outlineVariant,
                                RoundedCornerShape(CornerRadius.MediumSmall),
                            )
                            .padding(Spacing.SmallMedium),
                    ) {
                        Text(
                            text = "Analyzing GlassLayout and chrome geometry. " +
                                "Top bar and status bar layout alignment ready for review.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.Compact))
                        .background(colors.surface)
                        .border(
                            StrokeWidth.Hairline,
                            colors.outlineVariant,
                            RoundedCornerShape(CornerRadius.Compact),
                        )
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ask AI Assistant...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colors.outline,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        painter = AppIcons.EditPainterResource(),
                        contentDescription = "Send",
                        tint = colors.primary,
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
            LtiTheme(appTheme = AppTheme.Dark) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Explorer",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop()

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftRail = {
                            GlassSidePanelRail(
                                tabs = leftTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Left,
                            )
                        },
                        leftPanel = { MockExplorerPanel() },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Right,
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
            LtiTheme(appTheme = AppTheme.Light) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Explorer",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop()

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftRail = {
                            GlassSidePanelRail(
                                tabs = leftTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Left,
                            )
                        },
                        leftPanel = { MockExplorerPanel() },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Right,
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
     * Diagnostic Integration Offscreen Render & Screenshot Regression Test for Blue Mode.
     * Composes real sapphire aura Canvas + LtiTheme + GlassLayout with default topBar & statusBar in blue theme.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testDiagnosticRealAppChromeAndBackdropBlue() = runDesktopComposeUiTest(
        width = TEST_WIDTH,
        height = TEST_HEIGHT,
    ) {
        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Explorer",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop()

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftRail = {
                            GlassSidePanelRail(
                                tabs = leftTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Left,
                            )
                        },
                        leftPanel = { MockExplorerPanel() },
                        mainContent = { MockEditorPanel() },
                        bottomPanel = {},
                        rightPanel = { MockAIAssistantPanel() },
                        rightRail = {
                            GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Right,
                            )
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_app_chrome_full_composition_blue", image)
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
            LtiTheme(appTheme = AppTheme.Dark) {
                val layoutState = rememberLtiLayoutState(
                    initialLeftWeight = 0.22f,
                    initialRightWeight = 0.26f,
                    initialShowLeft = true,
                    initialShowRight = true,
                    initialShowBottom = false,
                )

                val leftTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Folder,
                        label = "Files",
                    ) { MockExplorerPanel() },
                )

                val rightTabs = listOf(
                    GlassSidePanelTab(
                        icon = AppIcons.Sparkles,
                        label = "AI Assistant",
                    ) { MockAIAssistantPanel() },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    GlassBackdrop()

                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftPanel = {
                            Column(modifier = Modifier.fillMaxSize()) {
                                GlassSidePanelTabStrip(
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
                            GlassSidePanelRail(
                                tabs = rightTabs,
                                selectedIndex = 0,
                                onSelectedIndexChange = {},
                                railPosition = SidePanelRailPosition.Right,
                            )
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_app_chrome_with_side_panel_tabs", image)
    }

    /**
     * Verifies that IdeNavigatorPanel inside IdeAppFrame in Blue theme over a patterned backdrop:
     * 1. Lets light pass through (translucency delta > 0.02 vs flat backdrop).
     * 2. Blurs high-frequency backdrop pattern (smoothed adjacent pixel differences).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testNavigatorPanelOnBlueInIdeAppFrameShowsGlassOptics() {
        fun captureFrame(drawBackdrop: DrawScope.() -> Unit): ImageBitmap {
            lateinit var captured: ImageBitmap
            runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
                setContent {
                    GlassSceneHost(effectsEnabled = true) {
                        LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .hazeSource(GlassTheme.hazeState),
                                ) {
                                    drawBackdrop()
                                }
                                IdeAppFrame(
                                    topBar = { IdeTopAppBar() },
                                    navigator = {
                                        IdeNavigatorPanel(
                                            headerTitle = "EXPLORER",
                                            headerSubtitle = null,
                                            items = listOf(
                                                IdeNavigatorItem(id = "1", label = "Source"),
                                                IdeNavigatorItem(id = "2", label = "Build"),
                                            ),
                                            selectedItemId = "1",
                                            onSelectItem = {},
                                        )
                                    },
                                    statusBar = { IdeStatusBar() },
                                    content = { Box(Modifier.fillMaxSize()) },
                                )
                            }
                        }
                    }
                }
                captured = onRoot().captureToImage()
            }
            return captured
        }

        fun captureBareBackdrop(drawBackdrop: DrawScope.() -> Unit): ImageBitmap {
            lateinit var captured: ImageBitmap
            runDesktopComposeUiTest(width = TEST_WIDTH, height = TEST_HEIGHT) {
                setContent {
                    GlassSceneHost(effectsEnabled = true) {
                        LtiTheme(appTheme = AppTheme.Blue, effectsEnabled = true) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawBackdrop()
                                }
                            }
                        }
                    }
                }
                captured = onRoot().captureToImage()
            }
            return captured
        }

        val stripePattern: DrawScope.() -> Unit = {
            val stripeWidth = size.width / 20f
            for (i in 0 until 20) {
                val color = if (i % 2 == 0) PreviewSampleColors.Orange else PreviewSampleColors.Blue
                drawRect(
                    color = color,
                    topLeft = Offset(i * stripeWidth, 0f),
                    size = size.copy(width = stripeWidth),
                )
            }
        }

        val flatPattern: DrawScope.() -> Unit = {
            drawRect(PreviewSampleColors.Blue)
        }

        val patternedImage = captureFrame(stripePattern)
        val flatImage = captureFrame(flatPattern)
        val barePatternImage = captureBareBackdrop(stripePattern)

        val patternPixels = patternedImage.toPixelMap()
        val flatPixels = flatImage.toPixelMap()
        val barePixels = barePatternImage.toPixelMap()

        // 1. Light passes through: difference between patterned and flat backdrop under panel
        var diffSum = 0.0
        var pixelCount = 0
        for (y in 250 until 450 step 2) {
            for (x in 40 until 200 step 2) {
                val p = patternPixels[x, y]
                val f = flatPixels[x, y]
                val delta = (abs(p.red - f.red) + abs(p.green - f.green) + abs(p.blue - f.blue)) / 3.0
                diffSum += delta
                pixelCount++
            }
        }
        val avgTranslucencyDelta = diffSum / pixelCount

        // 2. Blur happens: compare adjacent horizontal pixel differences across stripe edges
        var maxBareAdjacentDiff = 0.0
        var maxPanelAdjacentDiff = 0.0
        val sampleY = 350
        for (x in 40 until 199) {
            val b1 = barePixels[x, sampleY]
            val b2 = barePixels[x + 1, sampleY]
            val bareDelta = abs(b1.red - b2.red).toDouble()
            if (bareDelta > maxBareAdjacentDiff) maxBareAdjacentDiff = bareDelta

            val p1 = patternPixels[x, sampleY]
            val p2 = patternPixels[x + 1, sampleY]
            val panelDelta = abs(p1.red - p2.red).toDouble()
            if (panelDelta > maxPanelAdjacentDiff) maxPanelAdjacentDiff = panelDelta
        }

        println("DIAGNOSTIC: avgTranslucencyDelta = $avgTranslucencyDelta")
        println("DIAGNOSTIC: maxBareAdjacentDiff = $maxBareAdjacentDiff, maxPanelAdjacentDiff = $maxPanelAdjacentDiff")

        assertTrue(
            avgTranslucencyDelta > 0.02,
            "Navigator panel on Blue is opaque slab: avgTranslucencyDelta=$avgTranslucencyDelta <= 0.02",
        )
        assertTrue(
            maxPanelAdjacentDiff < maxBareAdjacentDiff * 0.7,
            "Navigator panel on Blue does not blur backdrop: panelDelta=$maxPanelAdjacentDiff " +
                ">= 0.7 * bareDelta=$maxBareAdjacentDiff",
        )
    }
}
