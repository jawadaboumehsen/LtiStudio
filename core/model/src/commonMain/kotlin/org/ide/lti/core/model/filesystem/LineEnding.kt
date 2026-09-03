/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.filesystem

import kotlinx.serialization.Serializable

/**
 * Line ending types for text files and status bar display.
 */
@Serializable
enum class LineEnding(val displayName: String) {
    LF("LF"),
    CRLF("CRLF"),
    CR("CR"),
}
