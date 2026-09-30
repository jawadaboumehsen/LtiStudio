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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Top application bar matching the approved Stitch Studio design.
 * Height is strictly 44 dp.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdeTopAppBar(
    modifier: Modifier = Modifier,
    applySurface: Boolean? = null,
    leadingContent: @Composable RowScope.() -> Unit = {},
    centerContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    windowControls: @Composable (() -> Unit)? = null,
) {
    val inFrame = LocalInIdeAppFrame.current
    val shouldApplySurface = applySurface ?: !inFrame
    val surfaceModifier = if (shouldApplySurface) {
        Modifier.ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassDimens.TopBarHeight)
            .then(surfaceModifier)
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
