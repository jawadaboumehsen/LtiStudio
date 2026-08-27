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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Lti code editor component with line numbers and syntax highlighting support.
 *
 * @param content The code content to display and edit
 * @param onContentChange Callback when the content changes
 * @param modifier Modifier to be applied to the editor
 * @param readOnly Whether the editor is read-only
 * @param showLineNumbers Whether to show line numbers
 */
@Composable
fun GlassCodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val glassColors = GlassTheme.colors

    Box(
        modifier = modifier
            .padding(Spacing.Medium),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .padding(end = Spacing.SmallMedium)
                        .verticalScroll(verticalScrollState),
                ) {
                    val lineCount = content.count { it == '\n' } + 1
                    for (lineNumber in 1..lineCount) {
                        Text(
                            text = lineNumber.toString().padStart(4),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = glassColors.onGlassSecondary,
                            ),
                            modifier = Modifier.padding(vertical = Spacing.ExtraExtraSmall),
                        )
                    }
                }
            }

            // Editor text field
            BasicTextField(
                value = content,
                onValueChange = onContentChange,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = glassColors.onGlass,
                    lineHeight = 20.sp,
                ),
                cursorBrush = SolidColor(glassColors.glassAccent),
                readOnly = readOnly,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
                    .padding(vertical = Spacing.ExtraExtraSmall),
            )
        }
    }
}
