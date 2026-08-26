/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.ide.lti.core.designsystem.icon.AppIcons

/**
 * Message role enum for chat messages.
 */
enum class LtiMessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
}

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
    role: LtiMessageRole,
    timestamp: Long,
    modifier: Modifier = Modifier,
    showCopyButton: Boolean = true,
) {
    val isUser = role == LtiMessageRole.USER
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
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .then(
                        if (showCopyButton) {
                            Modifier
                                .onPointerEvent(PointerEventType.Enter) { showCopy = true }
                                .onPointerEvent(PointerEventType.Exit) { showCopy = false }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Column {
                    // Message content with selection support
                    SelectionContainer {
                    }

                    // Copy button (visible on hover)
                    if (showCopyButton && showCopy) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(content))
                                },
                                modifier = Modifier.width(24.dp).height(24.dp),
                            ) {
                                Icon(
                                    imageVector = AppIcons.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.width(16.dp).height(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(timestamp),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * Message content component that handles code blocks and regular text.
 */
@Composable
private fun LtiMessageContent(
    content: String,
    textColor: Color,
) {
    // Simple code block detection using string splits
    if (content.contains("```")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val parts = content.split("```")
            parts.forEachIndexed { index, part ->
                if (index % 2 == 0) {
                    // Regular text
                    if (part.isNotBlank()) {
                        Text(
                            text = part.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor,
                        )
                    }
                } else {
                    // Code block
                    if (part.isNotBlank()) {
                        LtiCodeBlock(
                            code = part.trim(),
                            textColor = textColor,
                        )
                    }
                }
            }
        }
    } else {
        // Regular text
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

/**
 * Code block component for displaying code snippets.
 */
@Composable
private fun LtiCodeBlock(
    code: String,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
//            .background(
//                ideSurfaceElevated.copy(alpha = 0.5f),
//                RoundedCornerShape(8.dp),
//            )
            .padding(12.dp),
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            color = textColor,
        )
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
