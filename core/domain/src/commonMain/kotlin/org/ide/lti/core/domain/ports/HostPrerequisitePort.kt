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

import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.SetupEnvironment

public interface HostPrerequisitePort {
    /** Lists and classifies distros. Never blocks on interactive input; every call has a timeout. */
    public suspend fun detect(): List<DistroStatus>

    /** Picks the environment to use from [detect] results, or null when the user must choose. */
    public suspend fun selectEnvironment(statuses: List<DistroStatus>): SetupEnvironment? =
        statuses.filterIsInstance<DistroStatus.Usable>().singleOrNull()?.environment

    /** Checks every catalog requirement in one round trip, without the build service. */
    public suspend fun probe(environment: SetupEnvironment): PrerequisiteReport
}
