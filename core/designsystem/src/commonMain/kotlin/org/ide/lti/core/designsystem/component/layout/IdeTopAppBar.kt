/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Top application bar matching the approved Stitch Studio design.
 * Height is strictly 40 dp.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdeTopAppBar(
    modifier: Modifier = Modifier,
    leadingContent: @Composable RowScope.() -> Unit = {},
    centerContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
) {
    // Real glass chrome only for the Blue theme - Dark/Light always render flat.
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val chromeModifier = if (isBlueGlass) {
        Modifier.hazeGlass(
            input = HazeInput.Backdrop(GlassTheme.hazeState),
            style = remember(surfaceColor) {
                GlassStyle.regular.then {
                    shape(GlassShapes.HazeFlat)
                    backgroundColor(surfaceColor)
                }
            },
        )
    } else {
        Modifier.background(surfaceColor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassDimens.TopBarHeight)
            .then(chromeModifier)
            .padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Leading Brand & Global Navigation
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            leadingContent()
        }

        // Center Area (optional search or status)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            centerContent()
        }

        // Trailing Context, Actions & Window Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            trailingContent()
            windowControls?.invoke()
        }
    }
}
