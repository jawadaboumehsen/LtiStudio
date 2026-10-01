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

import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.UpdateAvailability
import org.ide.lti.core.model.setup.ToolSelection

/**
 * Result of querying remote references for a Git repository.
 */
data class RefListing(val tags: List<String> = emptyList(), val branches: List<String> = emptyList())

/**
 * Outcome of resolving a user's selection into an immutable frozen input.
 */
sealed interface ResolveOutcome {
    data class Resolved(val input: ResolvedInput) : ResolveOutcome
    data object NotFound : ResolveOutcome
    data class Unreachable(val reason: String) : ResolveOutcome
    data object AccessDenied : ResolveOutcome
}

/**
 * Domain port for querying and resolving remote Git repositories or release versions.
 */
interface SourceResolverPort {
    suspend fun listRefs(group: ToolGroupId, repoUrl: String?): RefListing
    suspend fun resolve(selection: ToolSelection): ResolveOutcome
    suspend fun checkLayout(input: ResolvedInput.Git): CompatibilityResult
    suspend fun updateAvailability(frozen: ResolvedInput.Git): UpdateAvailability
}
