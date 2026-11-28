/*
 * Copyright 2026 Mifos Initiative
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuItem
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuTabBar
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuDivider
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownSubMenuItem
import org.ide.lti.core.designsystem.component.navigation.GlassTab
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassSceneHost
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SHOWCASE_WIDTH = 920
private const val SHOWCASE_HEIGHT = 420
private const val MENU_HOST_WIDTH = 560
private const val MENU_HOST_HEIGHT = 440

@OptIn(ExperimentalTestApi::class)
class MacOsSubmenuScreenshotAndBehaviorTest {

    @Test
    fun testSegmentedSubmenusRenderAndSwitchAcrossAllThemes() {
        val configs = listOf(
            Triple("macos_submenus_blue", AppTheme.Blue, true),
            Triple("macos_submenus_dark", AppTheme.Dark, true),
            Triple("macos_submenus_light", AppTheme.Light, true),
            Triple("macos_submenus_light_solid", AppTheme.Light, false),
        )

        configs.forEach { (name, theme, effectsEnabled) ->
            runDesktopComposeUiTest(width = SHOWCASE_WIDTH, height = SHOWCASE_HEIGHT) {
                var selectedTabId by mutableStateOf("general")
                var selectedNavId by mutableStateOf("overview")

                setContent {
                    GlassSceneHost(effectsEnabled = effectsEnabled) {
                        LtiTheme(
                            appTheme = theme,
                            effectsEnabled = effectsEnabled,
                            reducedMotion = false,
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                GlassBackdrop()
                                SubmenuShowcaseSurface(
                                    selectedNavId = selectedNavId,
                                    onSelectNav = { selectedNavId = it },
                                    selectedTabId = selectedTabId,
                                    onSelectTab = { selectedTabId = it },
                                )
                            }
                        }
                    }
                }

                onNodeWithTag("ShowcaseSubMenu_overview").assertIsSelected()
                onNodeWithTag("ShowcaseSubMenu_config").assertIsNotSelected()

                // Hover then click to switch segment
                onNodeWithTag("ShowcaseSubMenu_config").performMouseInput {
                    moveTo(center)
                }
                waitForIdle()
                onNodeWithTag("ShowcaseSubMenu_config").performClick()
                waitForIdle()

                assertEquals("config", selectedNavId)
                onNodeWithTag("ShowcaseSubMenu_config").assertIsSelected()
                onNodeWithTag("ShowcaseSubMenu_overview").assertIsNotSelected()

                // Verify wrapped bar flows onto a second row cleanly within compact width
                val wrappedFirst = onNodeWithTag("WrappedSubMenu_overview").fetchSemanticsNode()
                val wrappedLast = onNodeWithTag("WrappedSubMenu_storage").fetchSemanticsNode()
                assertTrue(
                    wrappedLast.positionInRoot.y > wrappedFirst.positionInRoot.y,
                    "Wrapped macOS submenu must wrap trailing items cleanly onto next row",
                )

                // Switch back to overview so captured visual matches clean default frame
                onNodeWithTag("ShowcaseSubMenu_overview").performClick()
                waitForIdle()

                val captured = onRoot().captureToImage()
                saveSubmenuReportPng(name, captured)
            }
        }
    }

    @Test
    fun testDropdownAndCascadingSubmenuRenderAndInteractions() {
        listOf(
            AppTheme.Blue to "macos_dropdown_blue",
            AppTheme.Light to "macos_dropdown_light",
        ).forEach { (theme, name) ->
            runDesktopComposeUiTest(width = MENU_HOST_WIDTH, height = MENU_HOST_HEIGHT) {
                var configureClicks = 0
                var submenuItemClicked by mutableStateOf(false)

                setContent {
                    GlassSceneHost(effectsEnabled = true) {
                        LtiTheme(appTheme = theme, effectsEnabled = true) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(Spacing.Medium),
                            ) {
                                GlassBackdrop()
                                Box(Modifier.padding(Spacing.Medium)) {
                                    Text(
                                        text = "Workspace Actions",
                                        fontSize = FontSize.BodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    GlassDropdownMenu(
                                        expanded = true,
                                        onDismissRequest = {},
                                        offset = IntOffset.Zero,
                                    ) {
                                        GlassDropdownMenuHeader(title = "Workspace Target")
                                        GlassDropdownMenuItem(
                                            text = "REDMAGIC Astra (NP05J_GB)",
                                            subtitle = "PQ84P01 • Global • SM8650",
                                            isSelected = true,
                                            shortcut = "⌘1",
                                            onClick = {},
                                        )
                                        GlassDropdownMenuItem(
                                            text = "Configure Workspace...",
                                            shortcut = "⌘,",
                                            leadingIcon = {
                                                Icon(
                                                    painter = AppIcons.SettingsPainterResource(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(IconSize.Small),
                                                )
                                            },
                                            onClick = { configureClicks++ },
                                        )
                                        GlassDropdownSubMenuItem(
                                            text = "Reboot Device",
                                            leadingIcon = {
                                                Icon(
                                                    painter = AppIcons.RefreshPainterResource(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(IconSize.Small),
                                                )
                                            },
                                        ) { closeSubmenu ->
                                            GlassDropdownMenuItem(
                                                text = "Bootloader",
                                                shortcut = "⌥B",
                                                onClick = {
                                                    submenuItemClicked = true
                                                    closeSubmenu()
                                                },
                                            )
                                            GlassDropdownMenuItem(
                                                text = "Recovery",
                                                shortcut = "⌥R",
                                                onClick = { closeSubmenu() },
                                            )
                                        }
                                        GlassDropdownMenuDivider()
                                        GlassDropdownMenuItem(
                                            text = "Remove from Recents",
                                            shortcut = "⌫",
                                            destructive = true,
                                            leadingIcon = {
                                                Icon(
                                                    painter = AppIcons.DeletePainterResource(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(IconSize.Small),
                                                )
                                            },
                                            onClick = {},
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                onNodeWithText("Workspace Target").assertIsDisplayed()
                onNodeWithText("⌘1").assertIsDisplayed()
                onNodeWithContentDescription("Selected").assertExists()
                onNodeWithContentDescription("Open submenu").assertExists()

                onNodeWithText("Configure Workspace...").performMouseInput { moveTo(center) }
                waitForIdle()

                val popupsBeforeSubmenu = onAllNodes(isPopup(), useUnmergedTree = false)
                    .fetchSemanticsNodes()
                assertTrue(popupsBeforeSubmenu.isNotEmpty(), "Expected popup root for GlassDropdownMenu")
                val popupCapture = onAllNodes(isPopup(), useUnmergedTree = false)[0].captureToImage()
                saveSubmenuReportPng(name, popupCapture)

                onNodeWithText("Configure Workspace...").performClick()
                assertEquals(1, configureClicks)

                onNodeWithText("Reboot Device").performClick()
                waitForIdle()
                onNodeWithText("Bootloader").assertIsDisplayed().performClick()
                assertTrue(submenuItemClicked, "Clicking cascading submenu item must invoke handler")
            }
        }
    }
}

@Composable
private fun SubmenuShowcaseSurface(
    selectedNavId: String,
    onSelectNav: (String) -> Unit,
    selectedTabId: String,
    onSelectTab: (String) -> Unit,
) {
    val subMenuItems = remember {
        listOf(
            IdeSubMenuItem(id = "overview", label = "Overview", icon = Icons.Default.Dashboard),
            IdeSubMenuItem(id = "config", label = "ROM configuration", icon = Icons.Default.Settings, badge = "4"),
            IdeSubMenuItem(id = "history", label = "Run history", icon = Icons.Default.History),
            IdeSubMenuItem(id = "artifacts", label = "Artifacts", icon = Icons.Default.Archive),
            IdeSubMenuItem(id = "storage", label = "Disk usage", icon = Icons.Default.Storage, enabled = false),
        )
    }
    val tabItems = remember {
        listOf(
            GlassTab(id = "general", title = "General"),
            GlassTab(id = "streams", title = "OS & Streams"),
            GlassTab(id = "hardware", title = "Hardware & Storage"),
            GlassTab(id = "partitions", title = "Partitions"),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)) {
                Text(
                    text = "Studio & Panel Submenus",
                    fontSize = FontSize.BodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IdeSubMenuTabBar(
                    items = subMenuItems,
                    selectedItemId = selectedNavId,
                    onSelectItem = onSelectNav,
                    tagPrefix = "ShowcaseSubMenu_",
                    wrap = false,
                )
                GlassTabBar(
                    tabs = tabItems,
                    selectedId = selectedTabId,
                    onTabSelected = onSelectTab,
                    modifier = Modifier.fillMaxWidth(),
                    actions = {
                        GlassIconButton(
                            icon = AppIcons.Add,
                            onClick = {},
                            size = ComponentSize.TopBarButtonSize,
                            contentDescription = "Add",
                        )
                    },
                )
                IdeSubMenuTabBar(
                    items = subMenuItems,
                    selectedItemId = selectedNavId,
                    onSelectItem = onSelectNav,
                    tagPrefix = "WrappedSubMenu_",
                    wrap = true,
                    modifier = Modifier.width(ComponentSize.PluginInspectorWidth),
                )
            }
        }
    }
}

private fun saveSubmenuReportPng(name: String, bitmap: ImageBitmap) {
    val reportsDir = File("build/reports/macos_submenus")
    if (!reportsDir.exists()) reportsDir.mkdirs()
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
    if (bytes != null) File(reportsDir, "$name.png").writeBytes(bytes)
}
