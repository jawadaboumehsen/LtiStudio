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
