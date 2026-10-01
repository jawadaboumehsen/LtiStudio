/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.feature.configuration.ConfigurationTab

@Composable
fun ConfigurationTabHeader(
    activeTab: ConfigurationTab,
    onTabSelect: (ConfigurationTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = ConfigurationTab.entries
    GlassTabBar(
        tabs = tabs.map { it.title },
        selectedIndex = activeTab.ordinal,
        onTabSelected = { index -> onTabSelect(tabs[index]) },
        modifier = modifier.fillMaxWidth(),
    )
}
