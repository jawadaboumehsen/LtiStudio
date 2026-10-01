/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup.ports

import org.ide.lti.core.domain.setup.ToolGroupId
import kotlin.jvm.JvmInline

/**
 * Type-safe identifier for a complete installed toolchain configuration.
 */
@JvmInline
value class InstallId(val value: String) {
    init {
        require(value.isNotBlank()) { "InstallId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Type-safe identifier for a published tool artifact instance.
 */
@JvmInline
value class ArtifactId(val value: String) {
    init {
        require(value.isNotBlank()) { "ArtifactId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Installed artifact metadata for an active tool group.
 */
data class InstalledGroupArtifact(
    val artifactId: String,
    val version: String? = null,
    val commit: String? = null,
    val repoUrl: String? = null,
    val isHealthy: Boolean = true,
)

/**
 * Snapshot of currently active and previous toolchain installations on the environment.
 */
data class InstalledToolchain(
    val activeInstallId: InstallId?,
    val previousInstallId: InstallId?,
    val activeGroups: Map<String, InstalledGroupArtifact> = emptyMap(),
)

/**
 * Transaction request to activate a target installation.
 */
data class ActivationRequest(
    val requestId: String,
    val expectedActiveInstallId: InstallId?,
    val targetInstallId: InstallId,
)

/**
 * Report of reclaimed storage from cleaning unreferenced installations and artifacts.
 */
data class CleanupReport(
    val deletedInstallCount: Int = 0,
    val deletedArtifactCount: Int = 0,
    val reclaimedBytes: Long = 0L,
)

/**
 * Explicit outcomes of an activation transaction.
 */
sealed interface ActivationOutcome {
    data class Committed(val activeInstallId: InstallId) : ActivationOutcome
    data class Blocked(val activeWork: Int) : ActivationOutcome
    data class Conflict(val actualActiveInstallId: InstallId?) : ActivationOutcome
    data object RequestMismatch : ActivationOutcome
    data class InvalidTarget(val reason: String) : ActivationOutcome
    data class VerifyFailedRestored(val reason: String, val activeInstallId: InstallId?) : ActivationOutcome
    data object MaintenanceFailed : ActivationOutcome
    data object InProgress : ActivationOutcome
    data object Unknown : ActivationOutcome
}

/**
 * Domain port for managing toolchain candidate assembly, verification, activation, and cleanup.
 */
interface ToolchainInstallationPort {
    suspend fun state(): InstalledToolchain
    suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId
    suspend fun activate(request: ActivationRequest): ActivationOutcome
    suspend fun activationOutcome(requestId: String): ActivationOutcome?
    suspend fun cleanup(references: Set<InstallId>): CleanupReport
}
