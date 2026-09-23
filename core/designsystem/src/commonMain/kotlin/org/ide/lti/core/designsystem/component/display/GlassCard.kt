/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import org.ide.lti.core.designsystem.theme.HazeShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Glass card component with rounded corners and padding - built directly on Haze's
 * `Modifier.hazeGlass`, same shape as Haze's own sample card
 * (haze/sample/shared/.../sample/CardSample.kt): `GlassStyle.regular.then { backgroundColor(...);
 * tint(...); shape(...) }`.
 *
 * @param modifier Modifier to be applied to the card
 * @param shape Shape of the card (default: [GlassShapes.ShellCard])
 * @param surfaceColor Optional surface color overlay (default: [MaterialTheme.colorScheme.surfaceContainerHigh])
 * @param tint Optional tint color
 * @param contentPadding Padding inside the card (default: 16dp)
 * @param content Content to display inside the card
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazeCard,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.Medium,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val resolvedSurface = if (surfaceColor.isSpecified) surfaceColor else colors.surfaceContainerHigh
    val resolvedTint = if (tint.isSpecified) tint else Color.Transparent

    Box(
        modifier = modifier
            .hazeGlass(
                input = HazeInput.Backdrop(GlassTheme.hazeState),
                style = remember(shape, resolvedSurface, resolvedTint) {
                    GlassStyle.regular.then {
                        backgroundColor(resolvedSurface)
                        tint(resolvedTint)
                        this.shape(shape)
                    }
                },
            )
            .clip(shape),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
