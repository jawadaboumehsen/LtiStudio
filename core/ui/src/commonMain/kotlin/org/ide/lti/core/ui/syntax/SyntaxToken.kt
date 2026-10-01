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
 * Category of a syntax token.
 */
enum class SyntaxTokenKind {
    KEYWORD,
    FUNCTION,
    STRING,
    NUMBER,
    COMMENT,
    TYPE,
    ANNOTATION,
    PLAIN,
}

/**
 * Represents a single highlighted character range within the code editor.
 *
 * @property range The character index range (first to last, inclusive) in the source text.
 * @property kind The semantic token kind.
 */
@Immutable
data class SyntaxToken(val range: IntRange, val kind: SyntaxTokenKind)
