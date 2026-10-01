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
 * Checkpoint stages reached during a toolchain switch operation.
 */
@Serializable
enum class SwitchCheckpoint {
    CANDIDATE_VERIFIED,
    ACTIVATION_REQUESTED,
    ACTIVATION_COMMITTED,
    INSTALLED_COMMITTED,
    OUTCOME_UNKNOWN,
}

/**
 * Record of an in-flight toolchain switch held in the client journal until full reconciliation.
 */
@Serializable
data class PendingSwitchRecord(
    val activationRequestId: String,
    val expectedActiveInstallId: String?,
    val targetInstallId: String,
    val selectionRevision: Long,
    val checkpoint: SwitchCheckpoint,
)
