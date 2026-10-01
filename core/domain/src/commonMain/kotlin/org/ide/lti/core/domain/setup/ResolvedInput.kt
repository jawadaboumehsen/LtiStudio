/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import org.ide.lti.core.model.setup.ToolRef

/**
 * An immutable, fully resolved input frozen at the moment the user confirms a switch.
 */
sealed interface ResolvedInput {
    val group: ToolGroupId

    data class Git(
        override val group: ToolGroupId,
        val repoUrl: String?,
        val ref: ToolRef,
        val commit: String,
        val submoduleCommits: Map<String, String> = emptyMap(),
        val resolvedAt: Long,
    ) : ResolvedInput {
        init {
            require(commit.matches(HEX_40_REGEX)) {
                "Resolved Git commit must be a 40-character lowercase hex SHA: $commit"
            }
        }

        private companion object {
            private val HEX_40_REGEX = Regex("^[0-9a-f]{40}$")
        }
    }

    data class Release(
        override val group: ToolGroupId,
        val version: String,
        val url: String,
        val archiveMember: String,
        val sha256: String,
        val platform: String,
    ) : ResolvedInput
}

/**
 * Result of checking a repository's source tree structure against the catalog layout requirements.
 */
sealed interface CompatibilityResult {
    data object LayoutCompatible : CompatibilityResult
    data class Unsupported(val missingPaths: List<String>) : CompatibilityResult
    data class Unreachable(val reason: String) : CompatibilityResult
    data object AccessDenied : CompatibilityResult
    data class NotFound(val ref: String) : CompatibilityResult
}

/**
 * Availability of new commits on a tracking branch.
 */
sealed interface UpdateAvailability {
    data object UpToDate : UpdateAvailability
    data class UpdateAvailable(val newCommit: String) : UpdateAvailability
}

/**
 * Health assessment of the installed toolchain across the four necessary operational facts.
 */
data class ToolchainHealth(
    val selectedInstallId: String?,
    val intact: Boolean,
    val serviceResolves: Boolean,
    val journalCommitted: Boolean,
) {
    val isHealthy: Boolean get() = selectedInstallId != null && intact && serviceResolves && journalCommitted
}
