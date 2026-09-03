/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.GlassTab
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

@Suppress("MultipleEmitters")
@Composable
fun GlassScrollableTabRow(
    tabContents: List<TabContent>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    containerColor: Color = GlassTheme.colors.glassSurface,
    selectedContentColor: Color = GlassTheme.colors.glassAccent,
    unselectedContentColor: Color = GlassTheme.colors.glassSurfaceElevated,
    edgePadding: Dp = Spacing.Small,
    backdrop: Backdrop? = null,
) {
    val scope = rememberCoroutineScope()
    val actualBackdrop = backdrop ?: GlassTheme.backdrop

    ScrollableTabRow(
        modifier = modifier,
        containerColor = containerColor,
        selectedTabIndex = pagerState.currentPage,
        edgePadding = edgePadding,
        indicator = {},
        divider = {},
    ) {
        tabContents.forEachIndexed { index, currentTab ->
            GlassTab(
                text = currentTab.tabName,
                selected = pagerState.currentPage == index,
                selectedColor = selectedContentColor,
                unselectedColor = unselectedContentColor,
                backdrop = actualBackdrop,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        key = { page -> if (page in tabContents.indices) tabContents[page].tabName else page },
    ) {
        tabContents[it].content.invoke()
    }
}

@Immutable
data class TabContent(
    val tabName: String,
    val content: @Composable () -> Unit,
)
