/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.serialization.Serializable

@Serializable
enum class EnvironmentUse {
    BUILD_USE,
    MAINTENANCE,
}

@Serializable
data class EnvironmentLease(
    val environmentKey: String,
    val use: EnvironmentUse,
    val operationId: String,
    val acquiredAtEpochMs: Long,
)

sealed interface EnvironmentAdmission {
    data class Admitted(val lease: EnvironmentLease) : EnvironmentAdmission
    data class Busy(val owners: List<EnvironmentLease>) : EnvironmentAdmission
}

/**
 * Port for managing environment-level leases.
 *
 * Rules:
 * MAINTENANCE is exclusive and admitted only when no lease of either kind is active;
 * BUILD_USE is shared (many) and denied while MAINTENANCE is active.
 */
interface EnvironmentLeasePort {
    suspend fun tryAcquire(environmentKey: String, use: EnvironmentUse, operationId: String): EnvironmentAdmission

    suspend fun release(lease: EnvironmentLease)

    suspend fun activeLeases(environmentKey: String): List<EnvironmentLease>
}
