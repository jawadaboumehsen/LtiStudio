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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.LineHeight
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/**
 * Bottom application status bar matching the 24 dp Stitch design specification.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdeStatusBar(
    modifier: Modifier = Modifier,
    applySurface: Boolean? = null,
    leadingContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val inFrame = LocalInIdeAppFrame.current
    val shouldApplySurface = applySurface ?: !inFrame
    val surfaceModifier = if (shouldApplySurface) {
        Modifier.ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false)
    } else {
        Modifier
    }

    ProvideTextStyle(
        MaterialTheme.typography.labelSmall.copy(
            fontSize = FontSize.Micro,
            lineHeight = LineHeight.LabelSmall,
            fontFamily = ideFontFamily(),
        ),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(GlassDimens.StatusBarHeight)
                .then(surfaceModifier)
                .padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                leadingContent()
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                trailingContent()
            }
        }
    }
}
