/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.model

/** The Settings pages. Each one changes or shows something real (no placeholder categories). */
enum class SettingsCategory(val title: String, val subtitle: String) {
    Appearance(
        title = "Appearance",
        subtitle = "Theme, glass effects and motion",
    ),
    About(
        title = "About",
        subtitle = "Version, runtime, and where the app keeps its data and logs",
    ),
}
