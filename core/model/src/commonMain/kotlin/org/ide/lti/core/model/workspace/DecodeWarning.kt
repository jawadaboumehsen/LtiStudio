/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.serialization.Serializable

/**
 * Warning emitted when persisted preferences data fails to decode and is quarantined.
 */
@Serializable
data class DecodeWarning(
    val key: String,
    val quarantineKey: String,
    val message: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
)
