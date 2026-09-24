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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.component.display.GlassPanelHeader
import org.ide.lti.core.designsystem.component.inputs.GlassSlider
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.layout.GlassLayout
import org.ide.lti.core.designsystem.component.layout.GlassStatusBar
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.LayoutPanel
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.component.layout.TopBarPanelToggles
import org.ide.lti.core.designsystem.component.layout.rememberLtiLayoutState
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTab
import org.ide.lti.core.designsystem.component.navigation.GlassSidePanelTabs
import org.ide.lti.core.designsystem.component.navigation.SidePanelRailPosition
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.testing.screenshot.GoldenImageAssert
import org.junit.Test

/**
 * Visual screenshot regression test suite for core glass design system components.
 *
 * Uses off-screen rendering via [runDesktopComposeUiTest] to verify visual fidelity
 * across builds against verified baselines.
 */
@Suppress("LargeClass")
class GlassComponentScreenshotTest {

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassButtonVariants() = runDesktopComposeUiTest(width = 480, height = 180) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                            GlassPrimaryButton(onClick = {}) {
                                Text("Primary Action")
                            }
                            GlassButton(onClick = {}, variant = GlassButtonVariant.Secondary) {
                                Text("Secondary")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                            GlassButton(onClick = {}, variant = GlassButtonVariant.Standard) {
                                Text("Standard Glass")
                            }
                            GlassTextButton(onClick = {}) {
                                Text("Text Button")
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_button_variants", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassCard() = runDesktopComposeUiTest(width = 400, height = 240) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = Spacing.Medium,
                    ) {
                        Column {
                            Text(
                                text = "Design System Card",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.ExtraSmall))
                            Text(
                                text = "GPU-accelerated backdrop blur and lens refraction container.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.SmallMedium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                GlassPrimaryButton(onClick = {}) {
                                    Text("Explore")
                                }
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_card", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassPanelContainer() = runDesktopComposeUiTest(width = 320, height = 260) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    // Mirrors the real production wrapping in GlassLayout.kt's GlassLeftPanel:
                    // a GlassPanelContainer nested inside a real glass GlassCard ancestor.
                    // Regression coverage for GlassPanelContainer's Surface() defaulting to an
                    // opaque theme color and painting over the ancestor's backdrop.
                    GlassCard(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = Spacing.None,
                    ) {
                        GlassPanelContainer(
                            modifier = Modifier.fillMaxSize(),
                            header = {
                                GlassPanelHeader(
                                    title = "EXPLORER",
                                )
                            },
                        ) {
                            Text(
                                text = "Panel content should stay translucent, not opaque.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.SmallMedium),
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_panel_container", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassPanelScrollEdgeFade() = runDesktopComposeUiTest(width = 320, height = 260) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = Spacing.None,
                    ) {
                        GlassPanelContainer(
                            modifier = Modifier.fillMaxSize(),
                            header = {
                                GlassPanelHeader(
                                    title = "EXPLORER",
                                )
                            },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = Spacing.SmallMedium),
                            ) {
                                repeat(12) { index ->
                                    Text(
                                        text = "Item row $index - file_item_name_$index.kt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = Spacing.ExtraSmall),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_panel_scroll_edge_fade", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassTextField() = runDesktopComposeUiTest(width = 400, height = 220) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        GlassTextField(
                            value = "workspace_branch",
                            onValueChange = {},
                            label = "Branch Name",
                            placeholder = "Enter git branch",
                            leading = TextFieldLeadingSlot.Custom {
                                Icon(
                                    painter = AppIcons.SearchPainterResource(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_text_field", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassDialogSurface() = runDesktopComposeUiTest(width = 440, height = 260) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = AlphaTokens.Scrim))
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.DialogPadding)) {
                            Text(
                                text = "Confirm Changes",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.Small))
                            Text(
                                text = "Are you sure you want to commit these workspace token updates?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlassTextButton(onClick = {}) {
                                    Text("Cancel")
                                }
                                Spacer(Modifier.width(Spacing.Small))
                                GlassPrimaryButton(onClick = {}) {
                                    Text("Confirm")
                                }
                            }
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_dialog_surface", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassToggleAndSlider() = runDesktopComposeUiTest(width = 420, height = 200) {
        setContent {
            var sliderValue by remember { mutableFloatStateOf(0.6f) }
            var toggleChecked by remember { mutableStateOf(true) }

            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Large),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Enable Fluid Lighting", color = MaterialTheme.colorScheme.onSurface)
                            GlassToggle(
                                checked = toggleChecked,
                                onCheckedChange = { toggleChecked = it },
                            )
                        }

                        GlassSlider(
                            value = { sliderValue },
                            onValueChange = { sliderValue = it },
                            valueRange = 0f..1f,
                        )
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_toggle_and_slider", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassColorSchemeSwatches() = runDesktopComposeUiTest(width = 480, height = 240) {
        setContent {
            LtiTheme(appTheme = AppTheme.Blue) {
                val scheme = MaterialTheme.colorScheme
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Color Tokens & Contrast Ratios",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            SwatchCard("onSurface", scheme.onSurface, scheme.surface)
                            SwatchCard("onSecondary", scheme.onSecondary, scheme.surface)
                            SwatchCard("onSurfaceVariant", scheme.onSurfaceVariant, scheme.surface)
                            SwatchCard("primary", scheme.primary, scheme.surface)
                            SwatchCard("outline", scheme.outline, scheme.surface)
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_color_scheme_swatches", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassTopBar() = runDesktopComposeUiTest(width = 800, height = 100) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassTopBar(
                        navigation = TopBarNavigation.Brand(
                            title = "LtiRom",
                            workspaceName = "Workspace",
                        ),
                        panelToggles = TopBarPanelToggles(
                            isLeftVisible = true,
                            isBottomVisible = true,
                            isRightVisible = false,
                        ),
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_top_bar", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassStatusBar() = runDesktopComposeUiTest(width = 640, height = 60) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassStatusBar(
                        leadingContent = { Text("main") },
                        centerContent = { Text("Ln 12, Col 4") },
                        trailingContent = { Text("UTF-8") },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_status_bar", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassSidePanelTabs() = runDesktopComposeUiTest(width = 680, height = 300) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        // Left-docked multi-tab panel
                        GlassCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = Spacing.None,
                        ) {
                            GlassSidePanelTabs(
                                tabs = listOf(
                                    GlassSidePanelTab(
                                        icon = AppIcons.Folder,
                                        label = "Explorer",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "EXPLORER") },
                                        ) {
                                            Text(
                                                text = "Explorer panel content",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                    GlassSidePanelTab(
                                        icon = AppIcons.Search,
                                        label = "Search",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "SEARCH") },
                                        ) {
                                            Text(
                                                text = "Search workspace",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                ),
                                railPosition = SidePanelRailPosition.Left,
                            )
                        }

                        // Right-docked multi-tab panel
                        GlassCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = Spacing.None,
                        ) {
                            GlassSidePanelTabs(
                                tabs = listOf(
                                    GlassSidePanelTab(
                                        icon = AppIcons.Sparkles,
                                        label = "AI Assistant",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "AI ASSISTANT") },
                                        ) {
                                            Text(
                                                text = "AI assistant ready",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                    GlassSidePanelTab(
                                        icon = AppIcons.Settings,
                                        label = "Settings",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "SETTINGS") },
                                        ) {
                                            Text(
                                                text = "Inspector settings",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                ),
                                railPosition = SidePanelRailPosition.Right,
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_side_panel_tabs", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassSidePanelTabsLight() = runDesktopComposeUiTest(width = 680, height = 300) {
        setContent {
            LtiTheme(appTheme = AppTheme.Light) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        // Left-docked multi-tab panel
                        GlassCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = Spacing.None,
                        ) {
                            GlassSidePanelTabs(
                                tabs = listOf(
                                    GlassSidePanelTab(
                                        icon = AppIcons.Folder,
                                        label = "Explorer",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "EXPLORER") },
                                        ) {
                                            Text(
                                                text = "Explorer panel content",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                    GlassSidePanelTab(
                                        icon = AppIcons.Search,
                                        label = "Search",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "SEARCH") },
                                        ) {
                                            Text(
                                                text = "Search workspace",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                ),
                                railPosition = SidePanelRailPosition.Left,
                            )
                        }

                        // Right-docked multi-tab panel
                        GlassCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding = Spacing.None,
                        ) {
                            GlassSidePanelTabs(
                                tabs = listOf(
                                    GlassSidePanelTab(
                                        icon = AppIcons.Sparkles,
                                        label = "AI Assistant",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "AI ASSISTANT") },
                                        ) {
                                            Text(
                                                text = "AI assistant ready",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                    GlassSidePanelTab(
                                        icon = AppIcons.Settings,
                                        label = "Settings",
                                    ) {
                                        GlassPanelContainer(
                                            header = { GlassPanelHeader(title = "SETTINGS") },
                                        ) {
                                            Text(
                                                text = "Inspector settings",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(Spacing.SmallMedium),
                                            )
                                        }
                                    },
                                ),
                                railPosition = SidePanelRailPosition.Right,
                            )
                        }
                    }
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_side_panel_tabs_light", image)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testGlassLayoutFocusedPanel() = runDesktopComposeUiTest(width = 600, height = 360) {
        setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                val layoutState = rememberLtiLayoutState().apply {
                    focusedPanel = LayoutPanel.Center
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    GlassLayout(
                        state = layoutState,
                        modifier = Modifier.fillMaxSize(),
                        leftPanel = {
                            Box(modifier = Modifier.fillMaxSize().padding(Spacing.Small)) {
                                Text("Explorer Panel", color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                        mainContent = {
                            Box(modifier = Modifier.fillMaxSize().padding(Spacing.Small)) {
                                Text("Focused Editor Panel", color = MaterialTheme.colorScheme.onSurface)
                            }
                        },
                    )
                }
            }
        }

        val image = onRoot().captureToImage()
        GoldenImageAssert.assertMatchesBaseline("glass_layout_focused_panel", image)
    }

    @Composable
    private fun SwatchCard(
        name: String,
        color: androidx.compose.ui.graphics.Color,
        surfaceColor: androidx.compose.ui.graphics.Color,
    ) {
        val contrast = ColorContrast.contrastRatio(color, surfaceColor)

        Column(
            modifier = Modifier
                .width(ComponentSize.ButtonMinWidth)
                .background(color.copy(alpha = AlphaTokens.Hover))
                .padding(Spacing.ExtraSmall),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.Medium)
                    .background(color),
            )
            Spacer(Modifier.height(Spacing.ExtraExtraSmall))
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = "${"%.1f".format(contrast)}:1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
