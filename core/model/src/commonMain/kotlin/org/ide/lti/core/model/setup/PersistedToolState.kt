/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

import kotlinx.serialization.Serializable

/**
 * Persisted, per-tool compilation/verification record.
 */
@Serializable
public data class PersistedToolState(
    val id: String,
    val binaryName: String,
    val isCompiled: Boolean = false,
    val isVerified: Boolean = false,
    val binaryPath: String? = null,
    val version: String? = null,
    val lastVerifiedTimestamp: Long? = null,
)
