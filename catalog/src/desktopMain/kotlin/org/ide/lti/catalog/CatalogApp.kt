/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.catalog.tabs.ButtonsTab
import org.ide.lti.catalog.tabs.CoreUiTab
import org.ide.lti.catalog.tabs.DiagnosticsTab
import org.ide.lti.catalog.tabs.InputsTab
import org.ide.lti.catalog.tabs.LayoutTab
import org.ide.lti.catalog.tabs.ModalsTab
import org.ide.lti.catalog.tabs.NavigationTab
import org.ide.lti.catalog.tabs.SurfacesTab
import org.ide.lti.catalog.tabs.TokensTab
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassIconToggleButton
import org.ide.lti.core.designsystem.component.layout.GlassTopBar
import org.ide.lti.core.designsystem.component.layout.TopBarNavigation
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.DarkGlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LightGlassColorScheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

enum class CatalogCategory(val title: String) {
    Tokens("Tokens & Colors"),
    Buttons("Buttons & Controls"),
    Inputs("Inputs & Sliders"),
    Surfaces("Surfaces & Panels"),
    Navigation("Navigation & Tabs"),
    Modals("Modals & Feedback"),
    Layout("Workspace Layout"),
    CoreUi("Core UI Widgets"),
    Diagnostics("Diagnostics & Progress"),
}

@Composable
fun CatalogApp() {
    var isDarkTheme by remember { mutableStateOf(true) }
    var reducedMotion by remember { mutableStateOf(false) }
    var effectsEnabled by remember { mutableStateOf(true) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    val backdrop = rememberLayerBackdrop()
    val activeGlassColors = if (isDarkTheme) DarkGlassColorScheme else LightGlassColorScheme

    LtiTheme(
        darkTheme = isDarkTheme,
        glassColors = activeGlassColors,
        effectsEnabled = effectsEnabled,
        reducedMotion = reducedMotion,
        sharedBackdrop = backdrop,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GlassBackdrop(backdrop = backdrop)

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Top App Bar / Catalog Header
                CatalogTopBar(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    reducedMotion = reducedMotion,
                    onToggleReducedMotion = { reducedMotion = !reducedMotion },
                    effectsEnabled = effectsEnabled,
                    onToggleEffects = { effectsEnabled = !effectsEnabled },
                )

                // Category Tabs Row
                CategoryNavBar(
                    categories = CatalogCategory.entries,
                    selectedIndex = selectedCategoryIndex,
                    onSelectCategory = { selectedCategoryIndex = it }
                )

                // Active Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (CatalogCategory.entries[selectedCategoryIndex]) {
                        CatalogCategory.Tokens -> TokensTab()
                        CatalogCategory.Buttons -> ButtonsTab()
                        CatalogCategory.Inputs -> InputsTab()
                        CatalogCategory.Surfaces -> SurfacesTab()
                        CatalogCategory.Navigation -> NavigationTab()
                        CatalogCategory.Modals -> ModalsTab()
                        CatalogCategory.Layout -> LayoutTab()
                        CatalogCategory.CoreUi -> CoreUiTab()
                        CatalogCategory.Diagnostics -> DiagnosticsTab()
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTopBar(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    reducedMotion: Boolean,
    onToggleReducedMotion: () -> Unit,
    effectsEnabled: Boolean,
    onToggleEffects: () -> Unit,
) {
    GlassTopBar(
        navigation = TopBarNavigation.Brand(
            title = "LtiRom Design System Catalog",
            workspaceName = null,
        ),
        search = null,
        panelToggles = null,
        windowControls = null,
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                // Theme Mode Toggle
                GlassChip(
                    label = if (isDarkTheme) "Dark Theme" else "Light Theme",
                    selected = isDarkTheme,
                    onClick = onToggleTheme,
                )

                // Reduced Motion Toggle
                GlassChip(
                    label = if (reducedMotion) "Reduced Motion: ON" else "Reduced Motion: OFF",
                    selected = reducedMotion,
                    onClick = onToggleReducedMotion,
                )

                // Effects Toggle
                GlassChip(
                    label = if (effectsEnabled) "GPU Effects: ON" else "GPU Effects: OFF",
                    selected = effectsEnabled,
                    onClick = onToggleEffects,
                )
            }
        },
    )
}

@Composable
private fun CategoryNavBar(
    categories: List<CatalogCategory>,
    selectedIndex: Int,
    onSelectCategory: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Half))
            .horizontalScroll(scrollState)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        categories.forEachIndexed { index, category ->
            GlassChip(
                label = category.title,
                selected = index == selectedIndex,
                onClick = { onSelectCategory(index) },
            )
        }
    }
}

