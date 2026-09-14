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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.DestinationExistsException
import org.ide.lti.core.domain.ports.EnvironmentNotReadyException
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceManifest
import org.ide.lti.core.model.workspace.WorkspaceType
import java.util.UUID

data class CreateWorkspaceParams(
    val name: String,
    val target: TargetDevice,
    /** Overrides the service-reported `~/LtiRomWorkDir`; normally left null. */
    val baseLinuxWorkDir: String? = null,
    val destinationLinuxPath: String? = null,
    val keySource: KeySource = KeySource.GENERATE,
)

/**
 * Domain Use Case to atomically create and provision a self-contained workspace directory in WSL.
 *
 * Enforces:
 * - Environment must be in READY state (FR-001).
 * - Destination must be unique without collision (FR-007).
 * - All-or-nothing atomicity: failure or cancellation registers nothing (FR-006).
 */
class CreateWorkspaceUseCase(
    private val readinessPort: EnvironmentReadinessPort,
    private val provisioningPort: WorkspaceProvisioningPort,
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val appVersion: String = "1.0.0",
) {
    operator fun invoke(params: CreateWorkspaceParams): Flow<ProvisioningEvent> = flow {
        require(params.name.isNotBlank()) { "Workspace name cannot be blank" }

        val readiness = readinessPort.refresh()
        if (readiness.state != EnvironmentReadinessState.READY) {
            val msg = readiness.failingCheck ?: "Environment not ready: ${readiness.state}"
            emit(ProvisioningEvent.Failed("readiness", msg))
            throw EnvironmentNotReadyException(msg)
        }

        val workDir = params.baseLinuxWorkDir ?: readiness.workDirLinuxPath
        if (workDir.isNullOrBlank()) {
            val msg = "Execution service did not report its work directory"
            emit(ProvisioningEvent.Failed("readiness", msg))
            throw EnvironmentNotReadyException(msg)
        }

        val slug = params.name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val destination = params.destinationLinuxPath
            ?: "${workDir.trimEnd('/')}/workspaces/$slug"

        val existingWorkspaces = workspaceRepository.getWorkspaces().first()
        if (existingWorkspaces.any { it.name.equals(params.name, ignoreCase = true) || it.linuxPath == destination }) {
            val msg = "Destination or workspace name already exists: $destination"
            emit(ProvisioningEvent.Failed("check_destination", msg))
            throw DestinationExistsException(msg)
        }

        val target = params.target
        val profileRevision = target.revision
        val workspaceId = UUID.randomUUID().toString()
        val snapshotId = UUID.randomUUID().toString()
        val snapshot = initialSnapshot(target, workspaceId, snapshotId)

        val manifest = WorkspaceManifest(
            id = workspaceId,
            layoutVersion = 1,
            targetBinding = TargetBinding(target.id, profileRevision),
            createdAt = Clock.System.now().toString(),
            appVersion = appVersion,
        )

        val spec = ProvisioningSpec(
            destinationLinuxPath = destination,
            target = target,
            snapshot = snapshot,
            manifest = manifest,
            keySource = params.keySource,
            workDirLinuxPath = workDir.trimEnd('/'),
        )

        provisioningPort.provision(spec).collect { event ->
            when (event) {
                is ProvisioningEvent.Step -> emit(event)
                is ProvisioningEvent.Output -> emit(event)
                is ProvisioningEvent.Failed -> emit(event)
                is ProvisioningEvent.Completed -> {
                    register(params.name, destination, target, workspaceId, snapshot)
                    emit(event)
                }
            }
        }
    }

    /** Profile defaults: recommended firmware for the default region, DOWNLOAD, profile package policy. */
    private fun initialSnapshot(target: TargetDevice, workspaceId: String, snapshotId: String): ConfigurationSnapshot {
        val region = target.availableRegions.firstOrNull() ?: TargetRegion.GLOBAL
        val firmwareList = target.availableFirmwares[region].orEmpty()
        val firmware = firmwareList.firstOrNull { it.isRecommended }
            ?: firmwareList.firstOrNull()
            ?: TargetFirmware(
                version = "1.0.0",
                buildId = "${target.codename}:15/AQ3A.240812.002/20260101.000000",
                isOfficial = false,
            )
        val packagePolicy = target.packagePolicy ?: PackagePolicy(
            flashablePartitions = target.dynamicPartitions,
            flashableBootPartitions = target.bootPartitions,
        )
        return ConfigurationSnapshot.create(
            id = snapshotId,
            workspaceId = workspaceId,
            profileRevision = target.revision,
            acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
                mode = AcquisitionMode.DOWNLOAD,
                region = region,
                firmware = firmware,
            ),
            build = org.ide.lti.core.model.workspace.BuildSettings(
                packagePolicy = packagePolicy,
            ),
            assembly = org.ide.lti.core.model.workspace.AssemblySettings(
                buildType = "user",
                romVersion = "1.0.0",
            ),
            release = org.ide.lti.core.model.workspace.ReleaseSettings(
                otaBaseUrl = "https://example.com/updates",
            ),
            createdAt = Clock.System.now(),
        )
    }

    /** Persists snapshot + workspace + recent project only once provisioning has completed (FR-006). */
    private suspend fun register(
        name: String,
        destination: String,
        target: TargetDevice,
        workspaceId: String,
        snapshot: ConfigurationSnapshot,
    ) {
        val binding = TargetBinding(target.id, target.revision)
        val now = Clock.System.now()
        snapshotRepository.saveSnapshot(snapshot)
        workspaceRepository.saveWorkspace(
            Workspace(
                id = workspaceId,
                name = name,
                path = destination,
                linuxPath = destination,
                type = WorkspaceType.REMOTE_WSL,
                targetBinding = binding,
                layoutVersion = 1,
                effectiveSnapshotId = snapshot.id,
                lastOpened = now,
            ),
        )
        workspaceRepository.addRecentProject(
            RecentProject(
                workspaceId = workspaceId,
                name = name,
                path = destination,
                lastOpened = now,
                type = WorkspaceType.REMOTE_WSL,
                targetBinding = binding,
                targetDisplayName = target.name,
            ),
        )
    }

    suspend fun cancel(id: String = "") {
        provisioningPort.cancel(id)
    }
}
