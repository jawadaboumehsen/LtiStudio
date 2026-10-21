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

/** The saved accent colour id, compact density, and text size (percent) of the app. */
public data class AppearanceSettings(
    val accent: String = DEFAULT_ACCENT,
    val compact: Boolean = false,
    val textScalePercent: Int = DEFAULT_TEXT_SCALE,
) {
    public companion object {
        public const val DEFAULT_ACCENT: String = "default"
        public const val DEFAULT_TEXT_SCALE: Int = 100
    }
}
