/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze

/**
 * Relative severity of a request to release cached rendering resources.
 *
 * @property severity Comparable severity used to order increasingly urgent requests.
 */
enum class TrimMemoryLevel(val severity: Int) {
    /** The UI is no longer visible, allowing UI-only cached resources to be released. */
    UI_HIDDEN(severity = 10),

    /** The application is in the background or equivalent memory pressure has been reported. */
    BACKGROUND(severity = 20),

    /** Moderate memory pressure warrants releasing reusable rendering resources. */
    MODERATE(severity = 40),

    /** Critical memory pressure warrants releasing every disposable rendering resource. */
    COMPLETE(severity = 80),
}
