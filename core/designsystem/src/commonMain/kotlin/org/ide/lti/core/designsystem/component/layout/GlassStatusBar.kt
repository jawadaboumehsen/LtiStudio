/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Modern IDE Liquid Glass Status Bar Primitive.
 *
 * Generic status bar scaffold providing standard glass chrome, border, and content slot alignment.
 * Domain-specific indicators (Git branch, Ln/Col caret metrics, encoding, file types) are projected
 * via the [leadingContent], [centerContent], and [trailingContent] slots.
 *
 * @param modifier Modifier applied to the status bar container.
 * @param leadingContent Optional content slot on the start/left edge.
 * @param centerContent Optional content slot centered in the status bar.
 * @param trailingContent Optional content slot on the end/right edge.
 * @param drawOwnChrome Whether to render internal glass background and borders (false when embedded).
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassStatusBar(
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    centerContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    drawOwnChrome: Boolean = true,
) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val chromeModifier = if (drawOwnChrome) {
        Modifier.ideShellSurface(
            shape = GlassShapes.HazeFlat,
            surfaceColor = surfaceColor,
            drawBorder = false,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.StatusBarHeightAlt)
            .then(chromeModifier)
            .padding(horizontal = Spacing.SmallMedium),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // LEADING SECTION
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingContent?.invoke(this)
                }

                // CENTER SECTION
                if (centerContent != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        centerContent.invoke(this)
                    }
                }

                // TRAILING SECTION
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Fourteen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailingContent?.invoke(this)
                }
            }
        }
    }
}
