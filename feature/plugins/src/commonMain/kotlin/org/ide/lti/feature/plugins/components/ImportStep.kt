/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

public enum class ImportStep(public val stepNumber: Int, public val title: String, public val subtitle: String) {
    INSPECT(1, "Inspect", "Completed"),
    REVIEW(2, "Review", "Current"),
    INSTALL(3, "Install", "Pending"),
}
