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

@Serializable
enum class EnvironmentReadinessState {
    ENV_ABSENT,
    SERVICE_UNREACHABLE,
    SERVICE_UNHEALTHY,
    TOOLS_MISSING,
    TOOLS_INCOMPATIBLE,
    WORKSPACE_INVALID,
    READY,
}

@Serializable
enum class WorkspaceReadiness {
    NEEDS_TARGET,
    INVALID,
    READY,
    UNKNOWN,
}

@Serializable
data class EnvironmentReadiness(
    val state: EnvironmentReadinessState,
    val failingCheck: String? = null,
    val remediation: String? = null,
    val missingToolIds: Set<String> = emptySet(),
    val observedAt: Long = System.currentTimeMillis(),
    /** Absolute Linux path of `~/LtiRomWorkDir` on the execution service host; set only when READY. */
    val workDirLinuxPath: String? = null,
)
