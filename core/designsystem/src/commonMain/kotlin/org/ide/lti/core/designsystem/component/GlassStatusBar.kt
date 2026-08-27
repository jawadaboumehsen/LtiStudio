/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kashif_e.backdrop.effects.blur
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Line ending types for status bar display
 */
enum class LineEnding(val displayName: String) {
    LF("LF"),
    CRLF("CRLF"),
    CR("CR"),
}

/**
 * Modern IDE Liquid Glass Status Bar.
 */
@Composable
fun GlassStatusBar(
    branchName: String = "main",
    filePath: String? = null,
    module: String? = null,
    cursorPosition: Pair<Int, Int>? = null,
    encoding: String = "UTF-8",
    lineEnding: LineEnding = LineEnding.LF,
    fileType: String? = null,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.StatusBarHeightAlt)
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Medium))
            .border(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder.copy(alpha = AlphaTokens.Border),
                shape = RectangleShape
            )
            .padding(horizontal = Spacing.SmallMedium),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // LEFT SECTION: Git Branch & Workspace State
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Git Branch Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Five),
                    modifier = Modifier
                        .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
                        .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Elevated))
                        .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall)
                ) {
                    Box(
                        modifier = Modifier
                            .size(IconSize.Indicator)
                            .clip(CircleShape)
                            .background(glassColors.glassAccent)
                    )
                    Text(
                        text = branchName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = glassColors.onGlassSecondary
                    )
                }

                module?.let {
                    Text(
                        text = ".",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = glassColors.onGlassMuted
                    )
                }

                filePath?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = glassColors.onGlassSecondary
                    )
                }
            }

            // RIGHT SECTION: Editor & Encoding State
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Fourteen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cursorPosition?.let { (line, col) ->
                    Text(
                        text = "Ln , Col ",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = glassColors.onGlassSecondary
                    )
                }

                Text(
                    text = encoding,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = glassColors.onGlassSecondary
                )

                Text(
                    text = lineEnding.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = glassColors.onGlassMuted
                )

                fileType?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = glassColors.onGlassSecondary
                    )
                }
            }
        }
    }
}