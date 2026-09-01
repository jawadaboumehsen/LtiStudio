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

/**
 * High-performance, self-contained syntax tokenizer for Kotlin source code.
 *
 * Correctly tokenizes keywords, types, functions, strings, numbers, annotations,
 * and comments (including nested/unterminated edge cases) with non-overlapping ranges.
 */
object KotlinSyntaxTokenizer : SyntaxTokenizer {

    private val KOTLIN_KEYWORDS = hashSetOf(
        "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
        "companion", "const", "constructor", "continue", "crossinline", "data", "delegate",
        "do", "dynamic", "else", "enum", "expect", "external", "false", "field", "file",
        "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
        "inline", "inner", "interface", "internal", "is", "lateinit", "noinline", "null",
        "object", "open", "operator", "out", "override", "package", "param", "private",
        "property", "protected", "public", "receiver", "reified", "return", "sealed", "set",
        "setparam", "super", "suspend", "tailrec", "this", "throw", "true", "try",
        "typealias", "val", "value", "var", "vararg", "when", "where", "while",
    )

    private val DECIMAL_SUFFIXES = "fFdDlLgGuU".toSet()
    private val HEX_SUFFIXES = "uUlL".toSet()

    override fun tokenize(text: CharSequence): List<SyntaxToken> {
        val length = text.length
        if (length == 0) return emptyList()

        val tokens = ArrayList<SyntaxToken>()
        var i = 0

        while (i < length) {
            val token = nextToken(text, i, length)
            if (token != null) {
                tokens.add(token)
                i = token.range.last + 1
            } else {
                i++
            }
        }

        return tokens
    }

    private fun nextToken(text: CharSequence, start: Int, length: Int): SyntaxToken? {
        val c = text[start]
        return when {
            c == '/' && start + 1 < length -> tokenizeComment(text, start, length)
            c == '"' && start + 2 < length && text[start + 1] == '"' && text[start + 2] == '"' ->
                tokenizeRawString(text, start, length)
            c == '"' -> tokenizeRegularString(text, start, length)
            c == '\'' -> tokenizeCharLiteral(text, start, length)
            c == '@' -> tokenizeAnnotation(text, start, length)
            c.isDigit() -> tokenizeNumber(text, start, length)
            c.isLetter() || c == '_' -> tokenizeIdentifier(text, start, length)
            else -> null
        }
    }

    private fun tokenizeComment(text: CharSequence, start: Int, length: Int): SyntaxToken? {
        val next = text[start + 1]
        return when (next) {
            '/' -> {
                var i = start + 2
                while (i < length && text[i] != '\n') {
                    i++
                }
                SyntaxToken(start until i, SyntaxTokenKind.COMMENT)
            }
            '*' -> {
                var i = start + 2
                var depth = 1
                while (i < length && depth > 0) {
                    if (text[i] == '/' && i + 1 < length && text[i + 1] == '*') {
                        depth++
                        i += 2
                    } else if (text[i] == '*' && i + 1 < length && text[i + 1] == '/') {
                        depth--
                        i += 2
                    } else {
                        i++
                    }
                }
                SyntaxToken(start until i, SyntaxTokenKind.COMMENT)
            }
            else -> null
        }
    }

    private fun tokenizeRawString(text: CharSequence, start: Int, length: Int): SyntaxToken {
        var i = start + 3
        var terminated = false
        while (i + 2 < length) {
            if (text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"') {
                i += 3
                terminated = true
                break
            }
            i++
        }
        if (!terminated) {
            i = length
        }
        return SyntaxToken(start until i, SyntaxTokenKind.STRING)
    }

    private fun tokenizeRegularString(text: CharSequence, start: Int, length: Int): SyntaxToken {
        var i = start + 1
        while (i < length && text[i] != '"' && text[i] != '\n') {
            if (text[i] == '\\') {
                i += 2
            } else {
                i++
            }
        }
        if (i < length && text[i] == '"') {
            i++
        }
        return SyntaxToken(start until i, SyntaxTokenKind.STRING)
    }

    private fun tokenizeCharLiteral(text: CharSequence, start: Int, length: Int): SyntaxToken {
        var i = start + 1
        while (i < length && text[i] != '\'' && text[i] != '\n') {
            if (text[i] == '\\') {
                i += 2
            } else {
                i++
            }
        }
        if (i < length && text[i] == '\'') {
            i++
        }
        return SyntaxToken(start until i, SyntaxTokenKind.STRING)
    }

    private fun tokenizeAnnotation(text: CharSequence, start: Int, length: Int): SyntaxToken? {
        if (start + 1 >= length || (!text[start + 1].isLetter() && text[start + 1] != '_')) {
            return null
        }
        var i = start + 1
        while (i < length && isAnnotationChar(text[i])) {
            i++
        }
        return SyntaxToken(start until i, SyntaxTokenKind.ANNOTATION)
    }

    private fun isAnnotationChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == ':'

    private fun tokenizeNumber(text: CharSequence, start: Int, length: Int): SyntaxToken {
        val c = text[start]
        val end = when {
            c == '0' && start + 1 < length && (text[start + 1] == 'x' || text[start + 1] == 'X') ->
                scanHexNumber(text, start + 2, length)
            c == '0' && start + 1 < length && (text[start + 1] == 'b' || text[start + 1] == 'B') ->
                scanBinaryNumber(text, start + 2, length)
            else -> scanDecimalNumber(text, start, length)
        }
        return SyntaxToken(start until end, SyntaxTokenKind.NUMBER)
    }

    private fun scanHexNumber(text: CharSequence, start: Int, length: Int): Int {
        var i = start
        while (i < length && isHexDigit(text[i])) {
            i++
        }
        if (i < length && HEX_SUFFIXES.contains(text[i])) {
            i++
        }
        return i
    }

    private fun scanBinaryNumber(text: CharSequence, start: Int, length: Int): Int {
        var i = start
        while (i < length && isBinaryDigit(text[i])) {
            i++
        }
        if (i < length && HEX_SUFFIXES.contains(text[i])) {
            i++
        }
        return i
    }

    private fun isBinaryDigit(c: Char): Boolean = c == '0' || c == '1' || c == '_'

    private fun scanDecimalNumber(text: CharSequence, start: Int, length: Int): Int {
        var i = scanDigits(text, start, length)
        if (i < length && text[i] == '.' && isFractionalDot(text, i, length)) {
            i = scanDigits(text, i + 1, length)
        }
        if (i < length && (text[i] == 'e' || text[i] == 'E')) {
            i = scanExponent(text, i + 1, length)
        }
        if (i < length && DECIMAL_SUFFIXES.contains(text[i])) {
            i++
        }
        return i
    }

    private fun scanDigits(text: CharSequence, start: Int, length: Int): Int {
        var i = start
        while (i < length && (text[i].isDigit() || text[i] == '_')) {
            i++
        }
        return i
    }

    private fun scanExponent(text: CharSequence, start: Int, length: Int): Int {
        var i = start
        if (i < length && (text[i] == '+' || text[i] == '-')) {
            i++
        }
        return scanDigits(text, i, length)
    }

    private fun isFractionalDot(text: CharSequence, dotIndex: Int, length: Int): Boolean {
        val hasNextDigit = dotIndex + 1 < length && text[dotIndex + 1].isDigit()
        val isNotDoubleDot = dotIndex + 2 >= length || text[dotIndex + 2] != '.'
        return hasNextDigit && isNotDoubleDot
    }

    private val HEX_DIGITS = "0123456789abcdefABCDEF_".toSet()

    private fun isHexDigit(c: Char): Boolean = HEX_DIGITS.contains(c)

    private fun tokenizeIdentifier(text: CharSequence, start: Int, length: Int): SyntaxToken? {
        var i = start
        while (i < length && (text[i].isLetterOrDigit() || text[i] == '_')) {
            i++
        }
        val word = text.subSequence(start, i).toString()

        val kind = when {
            KOTLIN_KEYWORDS.contains(word) -> SyntaxTokenKind.KEYWORD
            isFollowedByOpenParen(text, i, length) -> SyntaxTokenKind.FUNCTION
            word.isNotEmpty() && word[0].isUpperCase() -> SyntaxTokenKind.TYPE
            else -> null
        } ?: return null

        return SyntaxToken(start until i, kind)
    }

    private fun isFollowedByOpenParen(text: CharSequence, index: Int, length: Int): Boolean {
        var k = index
        while (k < length && text[k].isWhitespace() && text[k] != '\n') {
            k++
        }
        return k < length && text[k] == '('
    }
}
