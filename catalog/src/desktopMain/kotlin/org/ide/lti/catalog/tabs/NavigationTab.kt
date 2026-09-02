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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.GlassBottomTab
import org.ide.lti.core.designsystem.component.GlassBottomTabs
import org.ide.lti.core.designsystem.component.GlassNavigationBar
import org.ide.lti.core.designsystem.component.GlassNavigationBarItem
import org.ide.lti.core.designsystem.component.GlassNavigationRail
import org.ide.lti.core.designsystem.component.GlassNavigationRailItem
import org.ide.lti.core.designsystem.component.GlassTabBar
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun NavigationTab() {
    val scrollState = rememberScrollState()
    val backdrop = GlassTheme.backdrop

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedBottomTabIndex by remember { mutableIntStateOf(1) }
    var selectedRailIndex by remember { mutableIntStateOf(0) }
    var selectedBarIndex by remember { mutableIntStateOf(0) }

    val editorTabs = remember {
        mutableListOf("WorkspaceScreen.kt", "Color.kt", "GlassButton.kt", "CLAUDE.md")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Glass Tab Bar
        SectionContainer(
            title = "1. Editor / Panel Tab Bar (GlassTabBar)",
            description = "Horizontal tab row with active indicators and tab close actions."
        ) {
            GlassTabBar(
                tabs = editorTabs,
                selectedIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                onTabClosed = { idx ->
                    if (editorTabs.size > 1) {
                        editorTabs.removeAt(idx)
                        if (selectedTabIndex >= editorTabs.size) {
                            selectedTabIndex = editorTabs.size - 1
                        }
                    }
                }
            )
        }

        // Section 2: Glass Bottom Tabs (Liquid Spotlight)
        SectionContainer(
            title = "2. Liquid Spotlight Bottom Tabs (GlassBottomTabs)",
            description = "Apple-inspired three-layer bottom bar with interactive spotlight pill that refracts active tab."
        ) {
            GlassBottomTabs(
                selectedTabIndex = { selectedBottomTabIndex },
                tabsCount = 4,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.BottomTabsHeight),
                tabs = {
                    GlassBottomTab(onClick = { selectedBottomTabIndex = 0 }) {
                        Icon(painter = AppIcons.HomePainterResource(), contentDescription = "Home", modifier = Modifier.size(IconSize.Medium))
                        Text("Home", style = MaterialTheme.typography.labelSmall)
                    }
                    GlassBottomTab(onClick = { selectedBottomTabIndex = 1 }) {
                        Icon(painter = AppIcons.FolderPainterResource(), contentDescription = "Files", modifier = Modifier.size(IconSize.Medium))
                        Text("Explorer", style = MaterialTheme.typography.labelSmall)
                    }
                    GlassBottomTab(onClick = { selectedBottomTabIndex = 2 }) {
                        Icon(painter = AppIcons.SearchPainterResource(), contentDescription = "Search", modifier = Modifier.size(IconSize.Medium))
                        Text("Search", style = MaterialTheme.typography.labelSmall)
                    }
                    GlassBottomTab(onClick = { selectedBottomTabIndex = 3 }) {
                        Icon(painter = AppIcons.SettingsPainterResource(), contentDescription = "Settings", modifier = Modifier.size(IconSize.Medium))
                        Text("Settings", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }

        // Section 3: Glass Navigation Rail
        SectionContainer(
            title = "3. Vertical Navigation Rail (GlassNavigationRail)",
            description = "Material 3 vertical navigation rail with docked glass effect."
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.MinPanelHeight * 2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                GlassNavigationRail(
                    modifier = Modifier.width(ComponentSize.ButtonMinWidth * 1.2f)
                ) {
                    GlassNavigationRailItem(
                        selected = selectedRailIndex == 0,
                        onClick = { selectedRailIndex = 0 },
                        icon = { Icon(painter = AppIcons.HomePainterResource(), contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    GlassNavigationRailItem(
                        selected = selectedRailIndex == 1,
                        onClick = { selectedRailIndex = 1 },
                        icon = { Icon(painter = AppIcons.SettingsPainterResource(), contentDescription = "Finance") },
                        label = { Text("Finance") }
                    )
                    GlassNavigationRailItem(
                        selected = selectedRailIndex == 2,
                        onClick = { selectedRailIndex = 2 },
                        icon = { Icon(painter = AppIcons.UserPainterResource(), contentDescription = "Profile") },
                        label = { Text("Profile") }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(Spacing.Medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Active Rail Target: ${listOf("Home", "Finance", "Profile")[selectedRailIndex]}",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassTheme.colors.onGlass
                    )
                }
            }
        }

        // Section 4: Glass Navigation Bar
        SectionContainer(
            title = "4. Glass Navigation Bar (GlassNavigationBar)",
            description = "Material 3 glass bottom navigation bar for mobile and compact desktop windows."
        ) {
            GlassNavigationBar(modifier = Modifier.fillMaxWidth()) {
                GlassNavigationBarItem(
                    selected = selectedBarIndex == 0,
                    onClick = { selectedBarIndex = 0 },
                    icon = { Icon(painter = AppIcons.HomePainterResource(), contentDescription = "Home") },
                    label = { Text("Home") }
                )
                GlassNavigationBarItem(
                    selected = selectedBarIndex == 1,
                    onClick = { selectedBarIndex = 1 },
                    icon = { Icon(painter = AppIcons.SettingsPainterResource(), contentDescription = "Payment") },
                    label = { Text("Payment") }
                )
                GlassNavigationBarItem(
                    selected = selectedBarIndex == 2,
                    onClick = { selectedBarIndex = 2 },
                    icon = { Icon(painter = AppIcons.UserPainterResource(), contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    }
}
