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

import androidx.compose.runtime.Immutable

/**
 * Supported syntax languages for editor syntax highlighting.
 */
@Immutable
enum class SyntaxLanguage(val displayName: String, val extensions: Set<String>) {
    KOTLIN(
        displayName = "Kotlin",
        extensions = setOf("kt", "kts"),
    ),
    JAVA(
        displayName = "Java",
        extensions = setOf("java"),
    ),
    JAVASCRIPT(
        displayName = "JavaScript",
        extensions = setOf("js", "jsx"),
    ),
    TYPESCRIPT(
        displayName = "TypeScript",
        extensions = setOf("ts", "tsx"),
    ),
    PYTHON(
        displayName = "Python",
        extensions = setOf("py"),
    ),
    RUST(
        displayName = "Rust",
        extensions = setOf("rs"),
    ),
    GO(
        displayName = "Go",
        extensions = setOf("go"),
    ),
    MARKDOWN(
        displayName = "Markdown",
        extensions = setOf("md"),
    ),
    JSON(
        displayName = "JSON",
        extensions = setOf("json"),
    ),
    XML(
        displayName = "XML",
        extensions = setOf("xml"),
    ),
    YAML(
        displayName = "YAML",
        extensions = setOf("yaml", "yml"),
    ),
    HTML(
        displayName = "HTML",
        extensions = setOf("html", "htm"),
    ),
    CSS(
        displayName = "CSS",
        extensions = setOf("css"),
    ),
    GRADLE(
        displayName = "Gradle",
        extensions = setOf("gradle"),
    ),
    PLAIN_TEXT(
        displayName = "Plain Text",
        extensions = emptySet(),
    ),
    ;

    val tokenizer: SyntaxTokenizer
        get() = when (this) {
            KOTLIN -> KotlinSyntaxTokenizer
            else -> PlainTextSyntaxTokenizer
        }

    companion object {
        /**
         * Resolves a [SyntaxLanguage] from a file name or path.
         */
        fun fromFileName(fileName: String): SyntaxLanguage {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(extension) } ?: PLAIN_TEXT
        }

        /**
         * Resolves a [SyntaxLanguage] from a file extension.
         */
        fun fromExtension(extension: String): SyntaxLanguage {
            val ext = extension.removePrefix(".").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: PLAIN_TEXT
        }
    }
}
