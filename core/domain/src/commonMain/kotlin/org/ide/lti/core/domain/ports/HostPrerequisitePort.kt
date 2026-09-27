package org.ide.lti.core.domain.ports

import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.SetupEnvironment

public interface HostPrerequisitePort {
    /** Lists and classifies distros. Never blocks on interactive input; every call has a timeout. */
    public suspend fun detect(): List<DistroStatus>

    /** Checks every catalog requirement in one round trip, without the build service. */
    public suspend fun probe(environment: SetupEnvironment): PrerequisiteReport
}
