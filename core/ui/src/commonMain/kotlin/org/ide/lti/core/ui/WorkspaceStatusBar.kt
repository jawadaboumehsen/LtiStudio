/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.component.layout.GlassStatusBar
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.model.filesystem.LineEnding

/**
 * IDE Workspace status bar composite.
 *
 * Binds IDE domain state (Git branch, file path, caret coordinates, character encoding,
 * and line endings) into the [GlassStatusBar] design primitive.
 */
@Composable
fun WorkspaceStatusBar(
    modifier: Modifier = Modifier,
    branchName: String = "main",
    filePath: String? = null,
    module: String? = null,
    cursorPosition: Pair<Int, Int>? = null,
    encoding: String = "UTF-8",
    lineEnding: LineEnding = LineEnding.LF,
    fileType: String? = null,
    drawOwnChrome: Boolean = true,
) {
    GlassStatusBar(
        modifier = modifier,
        drawOwnChrome = drawOwnChrome,
        leadingContent = {
            // Git Branch Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Five),
                modifier = Modifier
                    .clip(GlassShapes.ExtraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated))
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = MaterialTheme.colorScheme.outline,
                        shape = GlassShapes.ExtraSmall,
                    )
                    .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
            ) {
                Box(
                    modifier = Modifier
                        .size(IconSize.Indicator)
                        .clip(GlassShapes.Capsule)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = branchName,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            module?.let {
                Text(
                    text = ".",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            filePath?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            cursorPosition?.let { (line, col) ->
                Text(
                    text = "Ln $line, Col $col",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = encoding,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = lineEnding.displayName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            fileType?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = FontSize.MicroAlt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
