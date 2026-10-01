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

import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment

public interface ServerArtifactPort {
    public suspend fun status(environment: SetupEnvironment): ServerArtifact

    /** Stage -> verify -> atomically activate; the previous version stays active on any failure. */
    public suspend fun ensureInstalled(environment: SetupEnvironment): ServerArtifact

    /**
     * Start, or restart onto, the active version if the running one differs and activeRuns == 0.
     * [javaHome] is PrerequisiteReport.javaHome: the single Java discovery result. The adapter never searches again.
     */
    public suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String): ServerArtifact
}
