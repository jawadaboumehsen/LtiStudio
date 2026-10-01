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

import kotlin.test.Test
import kotlin.test.assertEquals

class CommandTokenizerTest {

    @Test
    fun testEmptyInput() {
        assertEquals(emptyList(), tokenizeCommandLine(""))
        assertEquals(emptyList(), tokenizeCommandLine("   "))
        assertEquals(emptyList(), tokenizeCommandLine("\t\n"))
    }

    @Test
    fun testPlainArgs() {
        assertEquals(listOf("echo", "hello", "world"), tokenizeCommandLine("echo hello world"))
        assertEquals(listOf("git", "status"), tokenizeCommandLine("   git    status   "))
        assertEquals(listOf("cmd", "arg1", "arg2", "arg3"), tokenizeCommandLine("cmd arg1 arg2 arg3"))
    }

    @Test
    fun testQuotedArgsWithSpaces() {
        assertEquals(
            listOf("echo", "hello world"),
            tokenizeCommandLine("echo \"hello world\""),
        )
        assertEquals(
            listOf("cd", "My Documents/Projects"),
            tokenizeCommandLine("cd 'My Documents/Projects'"),
        )
        assertEquals(
            listOf("cmd", "first arg", "second arg", "third"),
            tokenizeCommandLine("cmd \"first arg\" 'second arg' third"),
        )
    }

    @Test
    fun testEscapedQuotes() {
        assertEquals(
            listOf("echo", "hello \"world\""),
            tokenizeCommandLine("echo \"hello \\\"world\\\"\""),
        )
    }
}
