/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

/**
 * Splits a command line into argv tokens, respecting single and double quotes
 * as well as backslash escapes.
 */
fun tokenizeCommandLine(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inSingleQuote = false
    var inDoubleQuote = false
    var i = 0

    while (i < line.length) {
        val c = line[i]
        if (isEscapedQuoteOrSlash(c, i, line, inSingleQuote, inDoubleQuote)) {
            i++
            current.append(line[i])
        } else if (c == '\'' && !inDoubleQuote) {
            inSingleQuote = !inSingleQuote
        } else if (c == '"' && !inSingleQuote) {
            inDoubleQuote = !inDoubleQuote
        } else if (c.isWhitespace() && !inSingleQuote && !inDoubleQuote) {
            flushToken(current, tokens)
        } else {
            current.append(c)
        }
        i++
    }
    flushToken(current, tokens)
    return tokens
}

private fun isEscapedQuoteOrSlash(
    c: Char,
    index: Int,
    line: String,
    inSingleQuote: Boolean,
    inDoubleQuote: Boolean,
): Boolean {
    if (c != '\\' || index + 1 >= line.length) return false
    val next = line[index + 1]
    val isEscapeTarget = next == '"' || next == '\'' || next == '\\'
    return isEscapeTarget && (inDoubleQuote || !inSingleQuote)
}

private fun flushToken(current: StringBuilder, tokens: MutableList<String>) {
    if (current.isNotEmpty()) {
        tokens.add(current.toString())
        current.clear()
    }
}
