package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.SetupEnvironment

public class WslHostPrerequisiteProbe(
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector()
) : HostPrerequisitePort {

    override suspend fun detect(): List<DistroStatus> {
        return detector.detectAll()
    }

    /**
     * Resolves the selected environment from detected distribution statuses:
     * - If exactly one distro is [DistroStatus.Usable], selects it.
     * - Else, if the WSL default distro is [DistroStatus.Usable], selects it.
     * - Else returns null, prompting user selection.
     */
    public fun selectEnvironment(statuses: List<DistroStatus>): SetupEnvironment? {
        val usable = statuses.filterIsInstance<DistroStatus.Usable>()
        if (usable.size == 1) {
            return usable.single().environment
        }
        val defaultDistro = detector.getDefaultDistro()
        if (defaultDistro != null) {
            val matchingDefault = usable.firstOrNull { it.environment.distro == defaultDistro }
            if (matchingDefault != null) {
                return matchingDefault.environment
            }
        }
        return null
    }

    override suspend fun probe(environment: SetupEnvironment): PrerequisiteReport {
        // Will be expanded in Phase 4 / T040
        return PrerequisiteReport(
            environment = environment,
            results = emptyMap(),
            missingPackages = emptyList(),
            installCommand = null,
            javaHome = null,
            packageListsFresh = false,
            checkedAt = System.currentTimeMillis()
        )
    }
}
