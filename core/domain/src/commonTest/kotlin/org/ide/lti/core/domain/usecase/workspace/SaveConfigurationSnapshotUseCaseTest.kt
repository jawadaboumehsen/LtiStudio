/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.workspace

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.domain.ports.AdmissionResult
import org.ide.lti.core.domain.ports.LeaseReconciliation
import org.ide.lti.core.domain.ports.MutationKind
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.SnapshotMaterializationIntent
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.ports.WorkspaceWriteLease
import org.ide.lti.core.domain.repository.run.InMemoryRunRepository
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.setup.WorkspaceReadiness
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.DecodeWarning
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.ide.lti.core.model.workspace.WorkspaceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SaveConfigurationSnapshotUseCaseTest {

    private class FakeProvisioningPort(private val failWrite: Boolean = false) : WorkspaceProvisioningPort {
        val writtenConfigs = mutableListOf<Pair<Workspace, ConfigurationSnapshot>>()
        override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = emptyFlow()
        override suspend fun cancel(id: String) {}
        override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {
            if (failWrite) error("Simulated remote materialization failure")
            writtenConfigs.add(workspace to snapshot)
        }
    }

    private class FakeWorkspaceRepo : WorkspaceRepository {
        val workspaces = mutableMapOf<String, Workspace>()
        private val _flow = MutableStateFlow<List<Workspace>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = _flow.asStateFlow()
        override suspend fun getWorkspace(id: String): Workspace? = workspaces[id]
        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
            _flow.value = workspaces.values.toList()
        }
        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
            _flow.value = workspaces.values.toList()
        }
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override fun readinessOf(workspace: Workspace): WorkspaceReadiness = WorkspaceReadiness.READY
        override fun observeDecodeWarnings(): Flow<DecodeWarning> = flowOf()
    }

    private class FakeWorkspaceAdmissionPort : WorkspaceAdmissionPort {
        override suspend fun tryAcquire(
            workspaceId: String,
            kind: MutationKind,
            operationId: String,
            expectedRevision: Int?,
            currentRevision: Int?,
        ): AdmissionResult {
            if (expectedRevision != null && currentRevision != null && expectedRevision != currentRevision) {
                return AdmissionResult.RevisionMismatch(expected = expectedRevision, actual = currentRevision)
            }
            return AdmissionResult.Admitted(
                WorkspaceWriteLease(
                    workspaceId = workspaceId,
                    ownerKind = kind,
                    operationId = operationId,
                    expectedRevision = expectedRevision,
                    acquiredAtEpochMs = 0L,
                    durable = false,
                ),
            )
        }
        override suspend fun release(lease: WorkspaceWriteLease) {}
        override suspend fun currentOwner(workspaceId: String): WorkspaceWriteLease? = null
        override suspend fun unreconciledLeases(): List<WorkspaceWriteLease> = emptyList()
        override suspend fun reconcile(lease: WorkspaceWriteLease, outcome: LeaseReconciliation) {}
    }

    private class FakeSnapshotMaterializerPort : SnapshotMaterializerPort {
        val intents = mutableListOf<SnapshotMaterializationIntent>()
        override suspend fun markMaterializing(workspaceId: String, snapshotId: String) {
            intents.add(
                SnapshotMaterializationIntent(
                    workspaceId = workspaceId,
                    snapshotId = snapshotId,
                    status = "materializing",
                ),
            )
        }
        override suspend fun clearIntent(workspaceId: String, snapshotId: String) {
            intents.removeAll { it.workspaceId == workspaceId && it.snapshotId == snapshotId }
        }
        override suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent> = intents
    }

    private val firmwareGlobal = TargetFirmware(
        version = "10.5.15_GL",
        buildId = "PQ84P01:15",
        androidVersion = "15",
        securityPatch = "2026-02-01",
    )

    private val firmwareCn = TargetFirmware(
        version = "10.5.15_CN",
        buildId = "PQ84P01:15",
        androidVersion = "15",
        securityPatch = "2026-02-01",
    )

    @Test
    fun savingCreatesNewImmutableSnapshotAndUpdatesEffectiveId() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val admissionPort = FakeWorkspaceAdmissionPort()
        val materializerPort = FakeSnapshotMaterializerPort()
        val useCase = SaveConfigurationSnapshotUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            runRepository = runRepo,
            provisioningPort = provisioningPort,
            admissionPort = admissionPort,
            materializerPort = materializerPort,
        )

        // Initial snapshot

        val initialSnapshot = ConfigurationSnapshot.create(
            id = "snap-initial",
            workspaceId = "ws-1",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = TargetRegion.GLOBAL,
                firmware = firmwareGlobal,
            ),
            build = BuildSettings(
                packagePolicy = PackagePolicy(listOf("system"), listOf("boot")),
            ),
            createdAt = Clock.System.now(),
        )
        snapshotRepo.saveSnapshot(initialSnapshot)

        // Workspace 1
        val ws1 = Workspace(
            id = "ws-1",
            name = "Workspace 1",
            path = "/home/lti/LtiRomWorkDir/workspaces/ws-1",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-1",
            type = WorkspaceType.REMOTE_WSL,
            targetBinding = TargetBinding("PQ84P01", 1),
            layoutVersion = 1,
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws1)

        // Workspace 2 bound to same target
        val ws2 = Workspace(
            id = "ws-2",
            name = "Workspace 2",
            path = "/home/lti/LtiRomWorkDir/workspaces/ws-2",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/ws-2",
            type = WorkspaceType.REMOTE_WSL,
            targetBinding = TargetBinding("PQ84P01", 1),
            layoutVersion = 1,
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws2)

        // Draft for Workspace 1: switch to CN
        val draft = ConfigurationDraft(
            region = TargetRegion.CHINA,
            firmware = firmwareCn,
            acquisitionMode = AcquisitionMode.DOWNLOAD,
            buildType = "userdebug",
            romVersion = "1.1.0",
            otaBaseUrl = "https://ota.nubia.com/astra",
        )

        val result = useCase("ws-1", draft)
        assertTrue(result.isSuccess)
        val newSnapshot = result.getOrThrow()

        // 1. New snapshot created with new id and new digest
        assertNotEquals("snap-initial", newSnapshot.id)
        assertNotEquals(initialSnapshot.digest, newSnapshot.digest)
        assertEquals(TargetRegion.CHINA, newSnapshot.acquisition.region)
        assertEquals("1.1.0", newSnapshot.assembly.romVersion)

        // 2. Previous snapshot is untouched
        val retrievedInitial = snapshotRepo.getSnapshot("snap-initial")
        assertNotNull(retrievedInitial)
        assertEquals(TargetRegion.GLOBAL, retrievedInitial.acquisition.region)

        // 3. Workspace 1 effectiveSnapshotId updated
        val updatedWs1 = workspaceRepo.getWorkspace("ws-1")
        assertEquals(newSnapshot.id, updatedWs1?.effectiveSnapshotId)

        // 4. Workspace 2 is unaffected
        val updatedWs2 = workspaceRepo.getWorkspace("ws-2")
        assertEquals("snap-initial", updatedWs2?.effectiveSnapshotId)

        // 5. writeConfig was called once for ws-1
        assertEquals(1, provisioningPort.writtenConfigs.size)
        assertEquals(newSnapshot.id, provisioningPort.writtenConfigs.first().second.id)
    }

    @Test
    fun savingIsRefusedWhileRunIsActiveInWorkspace() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val admissionPort = FakeWorkspaceAdmissionPort()
        val materializerPort = FakeSnapshotMaterializerPort()
        val useCase = SaveConfigurationSnapshotUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            runRepository = runRepo,
            provisioningPort = provisioningPort,
            admissionPort = admissionPort,
            materializerPort = materializerPort,
        )

        val ws = Workspace(
            id = "ws-active",
            name = "Active Workspace",
            path = "/path",
            linuxPath = "/path",
            effectiveSnapshotId = "snap-1",
        )
        workspaceRepo.saveWorkspace(ws)

        // Active build run in ws-active
        val activeRun = BuildRun(
            id = "run-1",
            workspaceId = "ws-active",
            snapshotId = "snap-1",
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(1000L),
        )
        runRepo.upsert(activeRun)

        val draft = ConfigurationDraft(
            region = TargetRegion.GLOBAL,
            firmware = firmwareGlobal,
        )

        val result = useCase("ws-active", draft)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, provisioningPort.writtenConfigs.size)
    }

    @Test
    fun savingPreservesHiddenExtractionSettingsAndRecomputesDigestOverThem() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val admissionPort = FakeWorkspaceAdmissionPort()
        val materializerPort = FakeSnapshotMaterializerPort()
        val useCase = SaveConfigurationSnapshotUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            runRepository = runRepo,
            provisioningPort = provisioningPort,
            admissionPort = admissionPort,
            materializerPort = materializerPort,
        )

        // Previous snapshot carries extraction settings ConfigurationDraft does not expose at all.
        val customExtraction = ExtractionSettings(adapter = "lpunpack", dynamicPartitions = listOf("product"))
        val initialSnapshot = ConfigurationSnapshot.create(
            id = "snap-initial",
            workspaceId = "ws-1",
            profileRevision = 1,
            acquisition = AcquisitionSettings(region = TargetRegion.GLOBAL, firmware = firmwareGlobal),
            extraction = customExtraction,
            createdAt = Clock.System.now(),
        )
        snapshotRepo.saveSnapshot(initialSnapshot)

        val ws1 = Workspace(
            id = "ws-1",
            name = "Workspace 1",
            path = "/path",
            linuxPath = "/path",
            targetBinding = TargetBinding("PQ84P01", 1),
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws1)

        val draft = ConfigurationDraft(region = TargetRegion.GLOBAL, firmware = firmwareGlobal, romVersion = "1.2.0")

        val newSnapshot = useCase("ws-1", draft).getOrThrow()

        // Extraction settings survive a save the current thin draft never mentions them in.
        assertEquals(customExtraction, newSnapshot.extraction)

        // The persisted digest actually matches the snapshot's real (post-preservation) content -
        // recomputing it independently from the same field values must reproduce it exactly.
        val recomputed = ConfigurationSnapshot.computeDigest(
            workspaceId = newSnapshot.workspaceId,
            profileRevision = newSnapshot.profileRevision,
            acquisition = newSnapshot.acquisition,
            extraction = newSnapshot.extraction,
            assembly = newSnapshot.assembly,
            debloat = newSnapshot.debloat,
            customization = newSnapshot.customization,
            build = newSnapshot.build,
            release = newSnapshot.release,
            publish = newSnapshot.publish,
        )
        assertEquals(recomputed, newSnapshot.digest)
    }

    @Test
    fun savingRejectsAStaleExpectedBaseSnapshotId() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort()
        val admissionPort = FakeWorkspaceAdmissionPort()
        val materializerPort = FakeSnapshotMaterializerPort()
        val useCase = SaveConfigurationSnapshotUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            runRepository = runRepo,
            provisioningPort = provisioningPort,
            admissionPort = admissionPort,
            materializerPort = materializerPort,
        )

        val ws = Workspace(
            id = "ws-1",
            name = "Workspace 1",
            path = "/path",
            linuxPath = "/path",
            effectiveSnapshotId = "snap-current",
        )
        workspaceRepo.saveWorkspace(ws)

        val draft = ConfigurationDraft(region = TargetRegion.GLOBAL, firmware = firmwareGlobal)

        // Caller believes the base is "snap-stale", but the workspace has already moved to "snap-current".
        val result = useCase("ws-1", draft, expectedBaseSnapshotId = "snap-stale")

        assertTrue(result.isFailure)
        assertEquals(0, provisioningPort.writtenConfigs.size)
        // Never partially applied: no materialization intent left dangling from a rejected save.
        assertFalse(materializerPort.intents.any { it.workspaceId == "ws-1" })
    }

    @Test
    fun oldCommittedPointerRemainsSavedWhenRemoteMaterializationFails() = runTest {
        val workspaceRepo = FakeWorkspaceRepo()
        val snapshotRepo = InMemorySnapshotRepository()
        val runRepo = InMemoryRunRepository()
        val provisioningPort = FakeProvisioningPort(failWrite = true)
        val admissionPort = FakeWorkspaceAdmissionPort()
        val materializerPort = FakeSnapshotMaterializerPort()
        val useCase = SaveConfigurationSnapshotUseCase(
            workspaceRepository = workspaceRepo,
            snapshotRepository = snapshotRepo,
            runRepository = runRepo,
            provisioningPort = provisioningPort,
            admissionPort = admissionPort,
            materializerPort = materializerPort,
        )

        val initialSnapshot = ConfigurationSnapshot.create(
            id = "snap-initial",
            workspaceId = "ws-1",
            profileRevision = 1,
            acquisition = AcquisitionSettings(region = TargetRegion.GLOBAL, firmware = firmwareGlobal),
            createdAt = Clock.System.now(),
        )
        snapshotRepo.saveSnapshot(initialSnapshot)

        val ws = Workspace(
            id = "ws-1",
            name = "Workspace 1",
            path = "/path",
            linuxPath = "/path",
            effectiveSnapshotId = "snap-initial",
        )
        workspaceRepo.saveWorkspace(ws)

        val draft = ConfigurationDraft(region = TargetRegion.CHINA, firmware = firmwareCn)

        // 1. Persist immutable snapshot and 2. mark materializing both happen before the failing
        // 3. writeConfig call - so the new snapshot exists, but nothing points at it yet.
        val result = useCase("ws-1", draft)

        assertTrue(result.isFailure)

        // Only a COMMITTED pointer is shown as Saved: the old snapshot id is untouched.
        val stillWs = workspaceRepo.getWorkspace("ws-1")
        assertEquals("snap-initial", stillWs?.effectiveSnapshotId)

        // The interrupted materialization is detectable, not silently lost: an intent marker
        // was written before the failing writeConfig call and was never cleared, because the
        // pointer-commit step that would have cleared it never ran.
        assertEquals(1, materializerPort.intents.size)
        assertEquals("ws-1", materializerPort.intents.first().workspaceId)
    }
}
