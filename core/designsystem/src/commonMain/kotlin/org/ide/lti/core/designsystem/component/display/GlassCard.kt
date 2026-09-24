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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Glass card component with rounded corners and padding - built directly on the unified
 * [glassSurface] policy with [GlassRole.TextCard].
 *
 * @param modifier Modifier to be applied to the card
 * @param shape Shape of the card (default: [GlassShapes.HazeCard])
 * @param surfaceColor Optional surface color overlay (default: [MaterialTheme.colorScheme.surfaceContainerHigh])
 * @param tint Optional optical tint color
 * @param captureBacking Optional background color composited behind captured content before blur
 * @param contentPadding Padding inside the card (default: 16dp)
 * @param content Content to display inside the card
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazeCard,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    captureBacking: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.Medium,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    val surfaceModifier = Modifier.glassSurface(
        role = GlassRole.TextCard,
        shape = shape,
        surfaceColor = surfaceColor,
        tint = tint,
        captureBacking = captureBacking,
    )

    Box(
        modifier = modifier
            .then(surfaceModifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}
