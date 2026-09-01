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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinSyntaxTokenizerTest {

    @Test
    fun testEmptyInput() {
        val tokens = KotlinSyntaxTokenizer.tokenize("")
        assertTrue(tokens.isEmpty(), "Empty text should produce 0 tokens")
    }

    @Test
    fun testKeywordsAndTypes() {
        val code = "package org.ide.lti\n\nclass GlassCodeEditor : BaseEditor"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val keywords = tokens.filter { it.kind == SyntaxTokenKind.KEYWORD }
        val types = tokens.filter { it.kind == SyntaxTokenKind.TYPE }

        assertEquals(2, keywords.size)
        assertEquals("package", code.substring(keywords[0].range))
        assertEquals("class", code.substring(keywords[1].range))

        assertEquals(2, types.size)
        assertEquals("GlassCodeEditor", code.substring(types[0].range))
        assertEquals("BaseEditor", code.substring(types[1].range))
    }

    @Test
    fun testFunctionCalls() {
        val code = "val x = remember { compute(42) }\nprintln(x)"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val functions = tokens.filter { it.kind == SyntaxTokenKind.FUNCTION }
        val functionNames = functions.map { code.substring(it.range) }

        assertTrue(functionNames.contains("compute"), "Should highlight compute() as function")
        assertTrue(functionNames.contains("println"), "Should highlight println() as function")

        val keywords = tokens.filter { it.kind == SyntaxTokenKind.KEYWORD }.map { code.substring(it.range) }
        assertTrue(keywords.contains("val"), "Should highlight val as keyword")
    }

    @Test
    fun testSingleLineComment() {
        val code = "// This is a single line comment\nval a = 1"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val comment = tokens.first { it.kind == SyntaxTokenKind.COMMENT }
        assertEquals("// This is a single line comment", code.substring(comment.range))
    }

    @Test
    fun testBlockComment() {
        val code = "/* multi-line\n   comment */\nfun test() = 1"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val comment = tokens.first { it.kind == SyntaxTokenKind.COMMENT }
        assertEquals("/* multi-line\n   comment */", code.substring(comment.range))
    }

    @Test
    fun testUnterminatedBlockComment() {
        val code = "/* unterminated block comment at EOF"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenKind.COMMENT, tokens[0].kind)
        assertEquals(code, code.substring(tokens[0].range))
    }

    @Test
    fun testUnterminatedString() {
        val code = "val str = \"unterminated string at EOF"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val strToken = tokens.first { it.kind == SyntaxTokenKind.STRING }
        assertEquals("\"unterminated string at EOF", code.substring(strToken.range))
    }

    @Test
    fun testCommentContainingQuoteCharacters() {
        val code = "// Comment with \"quotes\" and 'c'\nval valid = true"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val comment = tokens.first { it.kind == SyntaxTokenKind.COMMENT }
        assertEquals("// Comment with \"quotes\" and 'c'", code.substring(comment.range))

        val keyword = tokens.first { it.kind == SyntaxTokenKind.KEYWORD }
        assertEquals("val", code.substring(keyword.range))
    }

    @Test
    fun testStringContainingCommentDelimiters() {
        val code = "val url = \"https://example.com/api/*test*/\"\nval x = 10"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val stringTokens = tokens.filter { it.kind == SyntaxTokenKind.STRING }
        assertEquals(1, stringTokens.size)
        assertEquals("\"https://example.com/api/*test*/\"", code.substring(stringTokens[0].range))

        val comments = tokens.filter { it.kind == SyntaxTokenKind.COMMENT }
        assertTrue(comments.isEmpty(), "String content should not produce comment tokens")
    }

    @Test
    fun testTrailingTokenWithNoNewlineAtEof() {
        val code = "val total = 100"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        assertEquals(2, tokens.size)
        assertEquals(SyntaxTokenKind.KEYWORD, tokens[0].kind)
        assertEquals("val", code.substring(tokens[0].range))

        assertEquals(SyntaxTokenKind.NUMBER, tokens[1].kind)
        assertEquals("100", code.substring(tokens[1].range))
    }

    @Test
    fun testAnnotations() {
        val code = "@Composable\n@OptIn(ExperimentalTestApi::class)\nfun Content() {}"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val annotations = tokens.filter { it.kind == SyntaxTokenKind.ANNOTATION }.map { code.substring(it.range) }
        assertTrue(annotations.contains("@Composable"), "Should highlight @Composable")
        assertTrue(annotations.contains("@OptIn"), "Should highlight @OptIn")
    }

    @Test
    fun testNumberLiterals() {
        val code = "val hex = 0xFF\nval bin = 0b1010\nval floatVal = 3.14f\nval longVal = 1000L\nval range = 1..10"
        val tokens = KotlinSyntaxTokenizer.tokenize(code)
        assertTokensValid(code, tokens)

        val numbers = tokens.filter { it.kind == SyntaxTokenKind.NUMBER }.map { code.substring(it.range) }
        assertTrue(numbers.contains("0xFF"), "Hex literal")
        assertTrue(numbers.contains("0b1010"), "Binary literal")
        assertTrue(numbers.contains("3.14f"), "Float literal")
        assertTrue(numbers.contains("1000L"), "Long literal")
        assertTrue(numbers.contains("1"), "Range start")
        assertTrue(numbers.contains("10"), "Range end")
    }

    @Test
    fun testPlainTextLanguage() {
        val code = "fun test() { val x = 42 }"
        val tokens = SyntaxLanguage.PLAIN_TEXT.tokenizer.tokenize(code)
        assertTrue(tokens.isEmpty(), "Plain text tokenizer should yield 0 tokens")
    }

    @Test
    fun testLanguageDetection() {
        assertEquals(SyntaxLanguage.KOTLIN, SyntaxLanguage.fromFileName("GlassLayout.kt"))
        assertEquals(SyntaxLanguage.KOTLIN, SyntaxLanguage.fromFileName("build.gradle.kts"))
        assertEquals(SyntaxLanguage.JAVA, SyntaxLanguage.fromFileName("App.java"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, SyntaxLanguage.fromFileName("index.js"))
        assertEquals(SyntaxLanguage.JAVASCRIPT, SyntaxLanguage.fromFileName("Component.jsx"))
        assertEquals(SyntaxLanguage.TYPESCRIPT, SyntaxLanguage.fromFileName("index.ts"))
        assertEquals(SyntaxLanguage.TYPESCRIPT, SyntaxLanguage.fromFileName("Component.tsx"))
        assertEquals(SyntaxLanguage.PYTHON, SyntaxLanguage.fromFileName("script.py"))
        assertEquals(SyntaxLanguage.RUST, SyntaxLanguage.fromFileName("main.rs"))
        assertEquals(SyntaxLanguage.GO, SyntaxLanguage.fromFileName("server.go"))
        assertEquals(SyntaxLanguage.MARKDOWN, SyntaxLanguage.fromFileName("README.md"))
        assertEquals(SyntaxLanguage.JSON, SyntaxLanguage.fromFileName("config.json"))
        assertEquals(SyntaxLanguage.XML, SyntaxLanguage.fromFileName("pom.xml"))
        assertEquals(SyntaxLanguage.YAML, SyntaxLanguage.fromFileName("compose.yaml"))
        assertEquals(SyntaxLanguage.YAML, SyntaxLanguage.fromFileName("compose.yml"))
        assertEquals(SyntaxLanguage.HTML, SyntaxLanguage.fromFileName("index.html"))
        assertEquals(SyntaxLanguage.HTML, SyntaxLanguage.fromFileName("index.htm"))
        assertEquals(SyntaxLanguage.CSS, SyntaxLanguage.fromFileName("styles.css"))
        assertEquals(SyntaxLanguage.GRADLE, SyntaxLanguage.fromFileName("build.gradle"))
        assertEquals(SyntaxLanguage.PLAIN_TEXT, SyntaxLanguage.fromFileName("unknown.xyz"))
        assertEquals(SyntaxLanguage.PLAIN_TEXT, SyntaxLanguage.fromFileName("no_ext"))
    }

    @Test
    fun testNewLanguagesDefaultToPlainTextTokenizer() {
        val nonKotlinLanguages = SyntaxLanguage.entries.filter { it != SyntaxLanguage.KOTLIN }
        for (lang in nonKotlinLanguages) {
            assertEquals(
                PlainTextSyntaxTokenizer,
                lang.tokenizer,
                "${lang.name} should default to PlainTextSyntaxTokenizer",
            )
        }
        assertEquals(KotlinSyntaxTokenizer, SyntaxLanguage.KOTLIN.tokenizer)
    }

    private fun assertTokensValid(text: String, tokens: List<SyntaxToken>) {
        var lastEnd = -1
        for (token in tokens) {
            val start = token.range.first
            val end = token.range.last

            assertTrue(
                start >= 0 && end < text.length,
                "Token range $start..$end out of bounds for text length ${text.length}",
            )
            assertTrue(
                start <= end,
                "Invalid token range: start $start > end $end",
            )
            assertTrue(
                start > lastEnd,
                "Overlapping token range: start $start <= previous end $lastEnd",
            )
            lastEnd = end
        }
    }
}
