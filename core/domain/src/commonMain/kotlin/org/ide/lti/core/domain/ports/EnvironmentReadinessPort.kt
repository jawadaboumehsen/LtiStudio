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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.workspace.Workspace

/**
 * Port for observing and querying the execution environment readiness for pipeline operations.
 */
public interface EnvironmentReadinessPort {
    /**
     * Continuously observes the current environment readiness status.
     */
    public fun observe(): Flow<EnvironmentReadiness>

    /**
     * Performs a fresh diagnostic probe of WSL, the server daemon, and required tools.
     */
    public suspend fun refresh(): EnvironmentReadiness

    /**
     * Validates that the environment is ready AND that the specified [workspace] is valid.
     */
    public suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness
}
