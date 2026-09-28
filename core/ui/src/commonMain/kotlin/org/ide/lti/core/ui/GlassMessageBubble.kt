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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.ai.MessageRole

/**
 * Lti message bubble component for displaying chat messages.
 *
 * @param content The message content
 * @param role The role of the message sender (USER, ASSISTANT, SYSTEM)
 * @param timestamp The timestamp of the message in milliseconds
 * @param modifier Modifier to be applied to the message bubble
 * @param showCopyButton Whether to show the copy button on hover
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassMessageBubble(
    content: String,
    role: MessageRole,
    timestamp: Long,
    modifier: Modifier = Modifier,
    showCopyButton: Boolean = true,
) {
    val isUser = role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val clipboardManager = LocalClipboardManager.current
    var showCopy by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            // Message bubble with hover effect
            GlassSurface(
                modifier = Modifier
                    .then(
                        if (showCopyButton) {
                            Modifier
                                .onPointerEvent(PointerEventType.Enter) { showCopy = true }
                                .onPointerEvent(PointerEventType.Exit) { showCopy = false }
                        } else {
                            Modifier
                        },
                    ),
                shape = GlassShapes.HazeMedium,
                tint = if (isUser) MaterialTheme.colorScheme.primary else Color.Unspecified,
            ) {
                Box(modifier = Modifier.padding(Spacing.SmallMedium)) {
                    Column {
                        // Message content with selection support
                        SelectionContainer {
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Copy button (visible on hover)
                        if (showCopyButton && showCopy) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.Small),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                GlassIconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(content))
                                    },
                                    size = ComponentSize.PanelHeaderAction,
                                ) {
                                    Icon(
                                        painter = AppIcons.CopyPainterResource(),
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(IconSize.Medium),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp
            val formattedTime = remember(timestamp) { formatTimestamp(timestamp) }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
            )
        }
    }
}

/**
 * Format timestamp to HH:mm format.
 */
private fun formatTimestamp(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}
