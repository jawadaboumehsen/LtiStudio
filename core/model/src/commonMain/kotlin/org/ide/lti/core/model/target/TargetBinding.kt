/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.target

import kotlinx.serialization.Serializable

/**
 * Immutable value object binding a workspace to a specific target profile and revision.
 */
@Serializable
data class TargetBinding(
    val profileId: String,
    val profileRevision: Int,
) {
    init {
        require(profileRevision >= 1) { "profileRevision must be >= 1, but was $profileRevision" }
    }
}
