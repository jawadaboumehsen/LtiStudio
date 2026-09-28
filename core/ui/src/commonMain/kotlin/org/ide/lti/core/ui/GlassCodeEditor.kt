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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LineHeight
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.ui.syntax.SyntaxHighlightingTransformation
import org.ide.lti.core.ui.syntax.SyntaxLanguage

/**
 * Lti code editor component with line numbers, opaque canvas surface, theme-aware syntax highlighting,
 * and cursor position tracking.
 *
 * @param content The code content to display and edit
 * @param onContentChange Callback when the content changes
 * @param modifier Modifier to be applied to the editor
 * @param readOnly Whether the editor is read-only
 * @param showLineNumbers Whether to show line numbers
 * @param language The syntax language used to tokenize and highlight the code
 * @param onCursorPositionChange Optional callback invoked when the cursor/caret position changes
 * (1-based line, 1-based column)
 */
@Composable
fun GlassCodeEditor(
    content: String,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    showLineNumbers: Boolean = true,
    language: SyntaxLanguage = SyntaxLanguage.PLAIN_TEXT,
    onCursorPositionChange: ((line: Int, column: Int) -> Unit)? = null,
) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val codeFont = codeFontFamily()

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = content, selection = TextRange.Zero))
    }

    if (textFieldValue.text != content) {
        val maxLen = content.length
        val clampedStart = textFieldValue.selection.start.coerceIn(0, maxLen)
        val clampedEnd = textFieldValue.selection.end.coerceIn(0, maxLen)
        textFieldValue = textFieldValue.copy(
            text = content,
            selection = TextRange(clampedStart, clampedEnd),
        )
    }

    LaunchedEffect(content) {
        if (onCursorPositionChange != null) {
            val cursorOffset = textFieldValue.selection.start.coerceIn(0, content.length)
            val textBeforeCursor = content.substring(0, cursorOffset)
            val line = textBeforeCursor.count { it == '\n' } + 1
            val lastNewline = textBeforeCursor.lastIndexOf('\n')
            val col = if (lastNewline >= 0) cursorOffset - lastNewline else cursorOffset + 1
            onCursorPositionChange(line, col)
        }
    }

    // Memoize syntax tokenization keyed on content and language to avoid re-tokenizing during recomposition
    val tokens = remember(content, language) {
        language.tokenizer.tokenize(content)
    }

    // Memoize the VisualTransformation keyed on tokens and active syntax theme colors
    val syntaxColors = GlassTheme.syntaxColors
    val visualTransformation = remember(tokens, syntaxColors) {
        SyntaxHighlightingTransformation(syntaxColors, tokens)
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.Medium),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Line numbers
            if (showLineNumbers) {
                val lineCount = remember(content) { content.count { it == '\n' } + 1 }
                val lineNumberColor = MaterialTheme.colorScheme.onSurfaceVariant
                val lineStyle = remember(codeFont, lineNumberColor) {
                    TextStyle(
                        fontFamily = codeFont,
                        fontSize = FontSize.Code,
                        color = lineNumberColor,
                        lineHeight = LineHeight.Code,
                    )
                }
                val lineNumbersText = remember(lineCount) {
                    buildString(lineCount * 6) {
                        for (lineNumber in 1..lineCount) {
                            append(lineNumber.toString().padStart(4))
                            if (lineNumber < lineCount) append('\n')
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(end = Spacing.SmallMedium)
                        .verticalScroll(verticalScrollState)
                        .padding(vertical = Spacing.ExtraExtraSmall),
                ) {
                    Text(
                        text = lineNumbersText,
                        style = lineStyle,
                    )
                }
            }

            // Editor text field
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val oldText = textFieldValue.text
                    val oldSelection = textFieldValue.selection
                    textFieldValue = newValue
                    if (newValue.text != oldText) {
                        onContentChange(newValue.text)
                    }
                    val cursorMoved = newValue.selection != oldSelection || newValue.text != oldText
                    if (onCursorPositionChange != null && cursorMoved) {
                        val cursorOffset = newValue.selection.start.coerceIn(0, newValue.text.length)
                        val textBeforeCursor = newValue.text.substring(0, cursorOffset)
                        val line = textBeforeCursor.count { it == '\n' } + 1
                        val lastNewline = textBeforeCursor.lastIndexOf('\n')
                        val col = if (lastNewline >= 0) cursorOffset - lastNewline else cursorOffset + 1
                        onCursorPositionChange(line, col)
                    }
                },
                textStyle = TextStyle(
                    fontFamily = codeFont,
                    fontSize = FontSize.Code,
                    color = GlassTheme.syntaxColors.plain,
                    lineHeight = LineHeight.Code,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = visualTransformation,
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
