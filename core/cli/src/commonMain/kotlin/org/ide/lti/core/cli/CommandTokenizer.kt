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
