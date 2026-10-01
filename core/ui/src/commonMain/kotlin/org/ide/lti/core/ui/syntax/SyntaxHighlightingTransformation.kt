/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import org.ide.lti.core.designsystem.theme.SyntaxColorScheme

/**
 * VisualTransformation that applies syntax highlighting colors to text.
 *
 * Guarantees [OffsetMapping.Identity] to ensure zero cursor/selection distortion.
 */
class SyntaxHighlightingTransformation(
    private val syntaxColors: SyntaxColorScheme,
    private val tokens: List<SyntaxToken>,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (tokens.isEmpty() || text.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(text.text)
        val textLength = text.length

        for (token in tokens) {
            val start = token.range.first
            val endExclusive = token.range.last + 1
            if (start in 0 until textLength && endExclusive > start) {
                val clampedEnd = endExclusive.coerceAtMost(textLength)
                val color = when (token.kind) {
                    SyntaxTokenKind.KEYWORD -> syntaxColors.keyword
                    SyntaxTokenKind.FUNCTION -> syntaxColors.function
                    SyntaxTokenKind.STRING -> syntaxColors.string
                    SyntaxTokenKind.NUMBER -> syntaxColors.number
                    SyntaxTokenKind.COMMENT -> syntaxColors.comment
                    SyntaxTokenKind.TYPE -> syntaxColors.type
                    SyntaxTokenKind.ANNOTATION -> syntaxColors.annotation
                    SyntaxTokenKind.PLAIN -> syntaxColors.plain
                }
                builder.addStyle(SpanStyle(color = color), start, clampedEnd)
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}
