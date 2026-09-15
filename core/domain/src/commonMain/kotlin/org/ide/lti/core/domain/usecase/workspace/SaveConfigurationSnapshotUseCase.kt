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
import org.ide.lti.core.domain.ports.AdmissionResult
import org.ide.lti.core.domain.ports.MutationKind
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationDraft
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.Workspace
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
    private val admissionPort: WorkspaceAdmissionPort,
    private val materializerPort: SnapshotMaterializerPort,
) {
    suspend operator fun invoke(
        workspaceId: String,
        draft: ConfigurationDraft,
        packagePolicy: PackagePolicy? = null,
        expectedBaseSnapshotId: String? = null,
    ): Result<ConfigurationSnapshot> = runCatching {
        val workspace = requireNotNull(workspaceRepository.getWorkspace(workspaceId)) {
            "Workspace not found: $workspaceId"
        }
        val activeRun = runRepository.latestActive(workspaceId)
        check(activeRun == null || !activeRun.state.isActive) {
            "Cannot save configuration while a build run is active (${activeRun?.state})"
        }

        // expectedRevision/currentRevision are intentionally always equal here: the port's Int-typed
        // CAS compares snapshot identity via hashCode(), which risks a false-positive match on a
        // 32-bit hash collision between two different snapshot ids. The real base check below compares
        // the full String id directly instead, once the lock below has serialized concurrent writers -
        // this call to tryAcquire exists only for that serialization (Busy detection), not its own CAS.
        val admission = admissionPort.tryAcquire(
            workspaceId = workspaceId,
            kind = MutationKind.SAVE,
            operationId = UUID.randomUUID().toString(),
            expectedRevision = null,
            currentRevision = null,
        )

        when (admission) {
            is AdmissionResult.Busy -> error("Workspace is busy: ${admission.owner}")
            is AdmissionResult.RevisionMismatch -> error(
                "Revision mismatch: expected ${admission.expected}, actual ${admission.actual}",
            )
            is AdmissionResult.Admitted -> {
                try {
                    if (expectedBaseSnapshotId != null && expectedBaseSnapshotId != workspace.effectiveSnapshotId) {
                        error(
                            "Stale base: expected snapshot '$expectedBaseSnapshotId', workspace is " +
                                "currently at '${workspace.effectiveSnapshotId}'",
                        )
                    }

                    val snapshot = buildSnapshotToSave(workspace, workspaceId, draft, packagePolicy)

                    // 1. Persist immutable snapshot
                    snapshotRepository.saveSnapshot(snapshot)

                    // 2. Write intent marker
                    materializerPort.markMaterializing(workspaceId, snapshot.id)

                    // 3. Upload config.json to remote workspace
                    provisioningPort.writeConfig(workspace, snapshot)

                    // 4. Update workspace with effectiveSnapshotId
                    val updatedWorkspace = workspace.copy(effectiveSnapshotId = snapshot.id)
                    workspaceRepository.saveWorkspace(updatedWorkspace)

                    // 5. Clear intent marker
                    materializerPort.clearIntent(workspaceId, snapshot.id)

                    snapshot
                } finally {
                    admissionPort.release(admission.lease)
                }
            }
        }
    }

    /**
     * Builds the immutable snapshot to persist: fields the current (still-narrow) [ConfigurationDraft]
     * doesn't carry - extraction, debloat, customization, build (beyond packagePolicy) and publish -
     * fall forward from the workspace's previous snapshot rather than silently resetting to their bare
     * defaults, and the digest is recomputed over that final, preserved field set so it actually
     * matches the snapshot's real content.
     */
    private suspend fun buildSnapshotToSave(
        workspace: Workspace,
        workspaceId: String,
        draft: ConfigurationDraft,
        packagePolicy: PackagePolicy?,
    ): ConfigurationSnapshot {
        val previousSnapshot = workspace.effectiveSnapshotId?.let { snapshotRepository.getSnapshot(it) }

        val resolvedPackagePolicy = packagePolicy
            ?: previousSnapshot?.build?.packagePolicy
            ?: PackagePolicy()
        val resolvedExtraction = previousSnapshot?.extraction ?: ExtractionSettings()
        val resolvedDebloat = previousSnapshot?.debloat ?: DebloatSettings()
        val resolvedCustomization = previousSnapshot?.customization ?: CustomizationSettings()
        val resolvedBuild = previousSnapshot?.build?.copy(packagePolicy = resolvedPackagePolicy)
            ?: BuildSettings(packagePolicy = resolvedPackagePolicy)
        val resolvedPublish = previousSnapshot?.publish ?: PublishSettings()

        val profileRevision = workspace.targetBinding?.profileRevision ?: 1
        val base = draft.toSnapshot(
            id = UUID.randomUUID().toString(),
            workspaceId = workspaceId,
            profileRevision = profileRevision,
            packagePolicy = resolvedPackagePolicy,
            createdAt = Clock.System.now(),
        )
        val correctedDigest = ConfigurationSnapshot.computeDigest(
            workspaceId = workspaceId,
            profileRevision = profileRevision,
            acquisition = base.acquisition,
            extraction = resolvedExtraction,
            assembly = base.assembly,
            debloat = resolvedDebloat,
            customization = resolvedCustomization,
            build = resolvedBuild,
            release = base.release,
            publish = resolvedPublish,
        )
        return base.copy(
            extraction = resolvedExtraction,
            debloat = resolvedDebloat,
            customization = resolvedCustomization,
            build = resolvedBuild,
            publish = resolvedPublish,
            digest = correctedDigest,
        )
    }
}
