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

import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import java.util.UUID

/**
 * Saves a new immutable [ConfigurationSnapshot] from a [ConfigurationDraft].
 *
 * Refuses execution if a build run is currently active in the workspace (FR-015).
 */
class SaveConfigurationSnapshotUseCase(
    private val workspaceRepository: WorkspaceRepository,
    private val snapshotRepository: SnapshotRepository,
    private val runRepository: RunRepository,
    private val provisioningPort: WorkspaceProvisioningPort,
) {
    suspend operator fun invoke(
        workspaceId: String,
        draft: ConfigurationDraft,
        packagePolicy: PackagePolicy? = null,
    ): Result<ConfigurationSnapshot> = runCatching {
        val workspace = requireNotNull(workspaceRepository.getWorkspace(workspaceId)) {
            "Workspace not found: $workspaceId"
        }
        val activeRun = runRepository.latestActive(workspaceId)
        check(activeRun == null || !activeRun.state.isActive) {
            "Cannot save configuration while a build run is active (${activeRun?.state})"
        }

        val previousSnapshot = workspace.effectiveSnapshotId?.let { snapshotRepository.getSnapshot(it) }
        val resolvedPolicy = packagePolicy
            ?: previousSnapshot?.build?.packagePolicy
            ?: PackagePolicy()

        val newSnapshotId = UUID.randomUUID().toString()
        val snapshot = draft.toSnapshot(
            id = newSnapshotId,
            workspaceId = workspaceId,
            profileRevision = workspace.targetBinding?.profileRevision ?: 1,
            packagePolicy = resolvedPolicy,
            createdAt = Clock.System.now(),
        )

        // 1. Persist immutable snapshot
        snapshotRepository.saveSnapshot(snapshot)

        // 2. Upload config.json to remote workspace
        provisioningPort.writeConfig(workspace, snapshot)

        // 3. Update workspace with effectiveSnapshotId
        val updatedWorkspace = workspace.copy(effectiveSnapshotId = snapshot.id)
        workspaceRepository.saveWorkspace(updatedWorkspace)

        snapshot
    }
}
