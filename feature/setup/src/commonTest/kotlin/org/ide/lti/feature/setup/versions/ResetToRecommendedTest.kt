/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResetToRecommendedTest {

    private lateinit var repository: FakeSelectionRepository
    private lateinit var fakeInstallationPort: FakeToolchainInstallationPort
    private lateinit var state: ToolVersionsState

    @BeforeTest
    fun setUp() {
        repository = FakeSelectionRepository()
        fakeInstallationPort = FakeToolchainInstallationPort()
    }

    @Test
    fun `reset to recommended returns desired to recommended with revision bump`() = runTest {
        val erofsGroup = ToolGroupId("erofs-utils")
        val initialSelections = mapOf(
            erofsGroup to ToolSelection(
                group = erofsGroup.value,
                repoUrl = "https://github.com/custom/erofs-utils",
                ref = ToolRef.Tag("v1.9.0"),
            ),
        )
        repository.saveSelections("Ubuntu", expectedRevision = 0L, desired = initialSelections)
        val initialRev = repository.desiredSelections("Ubuntu").revision

        state = ToolVersionsState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            repository = repository,
            sourceResolver = null,
            installationPort = fakeInstallationPort,
            activeDistroProvider = { "Ubuntu" },
        )

        // Pre-check: desired is custom
        val preDesired = repository.desiredSelections("Ubuntu").desired[erofsGroup.value]
        assertEquals("https://github.com/custom/erofs-utils", preDesired?.repoUrl)
        assertEquals("v1.9.0", (preDesired?.ref as? ToolRef.Tag)?.name)

        // Reset to recommended
        val res = state.resetToRecommended(erofsGroup, distro = "Ubuntu")
        assertTrue(res.isSuccess)

        val updatedSelections = repository.desiredSelections("Ubuntu")
        assertEquals(initialRev + 1, updatedSelections.revision)

        val postDesired = updatedSelections.desired[erofsGroup.value]
        // Recommended has repoUrl = null
        assertNull(postDesired?.repoUrl)
        val recTag = ToolGroupCatalog.group(erofsGroup)!!.source
        val expectedLabel = (recTag as org.ide.lti.core.domain.setup.ToolSource.Git).recommendedLabel
        assertEquals(expectedLabel, (postDesired?.ref as? ToolRef.Tag)?.name)

        // Row status reflects the reset
        val row = state.rows.value.first { it.groupId == erofsGroup.value }
        assertEquals(ToolGroupStatus.UP_TO_DATE, row.status)
    }

    @Test
    fun `nothing built or activated until review and build`() = runTest {
        val erofsGroup = ToolGroupId("erofs-utils")
        repository.saveSelections(
            "Ubuntu",
            expectedRevision = 0L,
            desired = mapOf(erofsGroup to ToolSelection(erofsGroup.value, null, ToolRef.Tag("v1.9.0"))),
        )

        state = ToolVersionsState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            repository = repository,
            sourceResolver = null,
            installationPort = fakeInstallationPort,
            activeDistroProvider = { "Ubuntu" },
        )

        state.resetToRecommended(erofsGroup, distro = "Ubuntu")

        assertFalse(fakeInstallationPort.activateCalled, "Activate must not be called during reset to recommended")
        assertFalse(fakeInstallationPort.assembleCalled, "Candidate must not be assembled during reset to recommended")
    }

    private class FakeSelectionRepository : ToolchainSelectionRepository {
        val distroSelections = mutableMapOf<String, DistroToolSelections>()

        override suspend fun desiredSelections(distro: String): DistroToolSelections =
            distroSelections[distro] ?: DistroToolSelections()

        override suspend fun saveSelections(
            distro: String,
            expectedRevision: Long,
            desired: Map<ToolGroupId, ToolSelection>,
        ): Result<Long> {
            val current = distroSelections[distro] ?: DistroToolSelections()
            if (current.revision != expectedRevision) {
                return Result.failure(IllegalStateException("CAS mismatch"))
            }
            val newRev = expectedRevision + 1
            distroSelections[distro] = DistroToolSelections(
                desired = desired.mapKeys { it.key.value },
                revision = newRev,
            )
            return Result.success(newRev)
        }

        override suspend fun recordTrust(repoUrl: String): Result<Unit> = Result.success(Unit)

        override suspend fun isTrusted(repoUrl: String): Boolean = false
    }

    private class FakeToolchainInstallationPort : ToolchainInstallationPort {
        var activateCalled = false
        var assembleCalled = false

        override suspend fun state(): InstalledToolchain =
            InstalledToolchain(activeInstallId = InstallId("active-1"), previousInstallId = null)

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId {
            assembleCalled = true
            return InstallId("candidate-1")
        }

        override suspend fun activate(request: ActivationRequest): ActivationOutcome {
            activateCalled = true
            return ActivationOutcome.Committed(request.targetInstallId)
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null
        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }
}
