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
 * Persisted, per-submodule external-repository sync record.
 */
@Serializable
public data class PersistedSubmoduleState(
    val name: String,
    val isSynced: Boolean = false,
    val commit: String? = null,
)
