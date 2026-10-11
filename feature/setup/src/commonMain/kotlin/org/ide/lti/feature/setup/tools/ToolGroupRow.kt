/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily

@Suppress("CyclomaticComplexMethod")
@Composable
public fun ToolGroupRow(
    row: ToolRowPresentation,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onOpenInspector: () -> Unit = {},
    onCloseInspector: () -> Unit = {},
) {
    val statusText =
        if (row.phaseLabel != null) {
            "${row.statusLabel} · ${row.phaseLabel}"
        } else {
            row.statusLabel
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    GlassCard(
        modifier =
        modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        StrokeWidth.Focused,
                        MaterialTheme.colorScheme.primary,
                        GlassShapes.Card,
                    )
                } else {
                    Modifier
                },
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionUp -> {
                            onMoveUp()
                            true
                        }
                        Key.DirectionDown -> {
                            onMoveDown()
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            onOpenInspector()
                            true
                        }
                        Key.Escape -> {
                            onCloseInspector()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clickable(onClick = {
                runCatching { focusRequester.requestFocus() }
                onClick()
            })
            .semantics(mergeDescendants = true) {
                contentDescription = "Tool group ${row.displayName}, in use ${row.inUseLabel}, status $statusText"
                selected = isSelected
            },
        contentPadding = Spacing.Small,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Group name
            Text(
                text = row.displayName,
                style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // In use
            Text(
                text = row.inUseLabel,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Status
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color =
                when (row.statusKind) {
                    ToolRowStatusKind.Ready -> MaterialTheme.colorScheme.primary
                    ToolRowStatusKind.Failed -> MaterialTheme.colorScheme.error
                    ToolRowStatusKind.NeedsRepair -> MaterialTheme.colorScheme.error
                    ToolRowStatusKind.Installing -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
