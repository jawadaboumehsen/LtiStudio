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
 * Strategy interface for tokenizing source code into syntax tokens.
 */
interface SyntaxTokenizer {
    /**
     * Tokenizes the given [text] into a list of non-overlapping [SyntaxToken]s.
     */
    fun tokenize(text: CharSequence): List<SyntaxToken>
}

/**
 * Plain text fallback tokenizer that yields no syntax tokens.
 */
object PlainTextSyntaxTokenizer : SyntaxTokenizer {
    override fun tokenize(text: CharSequence): List<SyntaxToken> = emptyList()
}
