/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.user

/**
 * User-configurable code editor preferences.
 */
public data class EditorSettings(
    val autoSave: Boolean = true,
    val lineNumbers: Boolean = true,
    val bracketMatching: Boolean = true,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val tabSize: Int = DEFAULT_TAB_SIZE,
) {
    public companion object {
        public const val DEFAULT_FONT_SIZE: Int = 14
        public const val DEFAULT_TAB_SIZE: Int = 4
    }
}
