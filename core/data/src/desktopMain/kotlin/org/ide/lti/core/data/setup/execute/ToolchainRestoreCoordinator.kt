/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.RestoreCoordinatorPort
import org.ide.lti.core.domain.setup.RestoreOutcome
import org.ide.lti.core.domain.setup.RevertingGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import java.util.UUID

/**
 * Coordinates restoring a previous toolchain installation without rebuilding (T091, US2).
 */
public class ToolchainRestoreCoordinator(
    private val installationManager: InstallationManager,
    private val artifactStore: ArtifactStore,
    private val installationPort: ToolchainInstallationPort,
    private val selectionRepository: ToolchainSelectionRepository,
) : RestoreCoordinatorPort {

    override suspend fun getRevertSet(): List<RevertingGroup> {
        val state = installationPort.state()
        val activeId = state.activeInstallId?.value
        val prevId = state.previousInstallId?.value
        return if (activeId != null && prevId != null) {
            installationManager.computeRevertSet(activeId, prevId, artifactStore)
        } else {
            emptyList()
        }
    }

    override suspend fun restore(distro: String, expectedRevision: Long?): RestoreOutcome {
        val state = installationPort.state()
        val active = state.activeInstallId
        val prev = state.previousInstallId
        return if (active == null || prev == null) {
            RestoreOutcome.Failed("No active or previous install to restore")
        } else {
            executeRestore(distro, active, prev, expectedRevision)
        }
    }

    private suspend fun executeRestore(
        distro: String,
        active: InstallId,
        prev: InstallId,
        expectedRevision: Long?,
    ): RestoreOutcome {
        val revertSet = installationManager.computeRevertSet(active.value, prev.value, artifactStore)
        val request = ActivationRequest(
            requestId = UUID.randomUUID().toString(),
            expectedActiveInstallId = active,
            targetInstallId = prev,
        )
        val activationOutcome = installationPort.activate(request)
        if (activationOutcome !is ActivationOutcome.Committed) {
            return RestoreOutcome.Failed("Activation of previous install failed: $activationOutcome")
        }

        val desiredUpdated = tryUpdateDesired(distro, prev.value, expectedRevision)
        return RestoreOutcome.Succeeded(
            activeInstallId = prev,
            revertSet = revertSet,
            desiredUpdated = desiredUpdated,
        )
    }

    private suspend fun tryUpdateDesired(distro: String, installId: String, expectedRevision: Long?): Boolean {
        val currentSelections = selectionRepository.desiredSelections(distro)
        if (expectedRevision != null && currentSelections.revision != expectedRevision) {
            return false
        }
        val restoredSelections = buildRestoredSelections(installId)
        return restoredSelections.isNotEmpty() && selectionRepository.saveSelections(
            distro = distro,
            expectedRevision = currentSelections.revision,
            desired = restoredSelections,
        ).isSuccess
    }

    private fun buildRestoredSelections(installId: String): Map<ToolGroupId, ToolSelection> {
        val manifest = installationManager.getManifest(installId) ?: return emptyMap()
        return ToolGroupCatalog.DEFAULT_GROUPS.mapNotNull { group ->
            val artId = manifest.groups[group.id.value] ?: return@mapNotNull null
            val art = artifactStore.getManifest(group.id.value, artId) ?: return@mapNotNull null
            val source = art.source
            val ref = when (source.kind) {
                "git" -> if (source.version != null) {
                    ToolRef.Tag(source.version)
                } else {
                    ToolRef.Commit(source.commit ?: "0000000000000000000000000000000000000000")
                }
                "release" -> ToolRef.ReleaseVersion(source.version ?: "default")
                else -> ToolRef.Tag("unknown")
            }
            group.id to ToolSelection(
                group = group.id.value,
                repoUrl = source.repoUrl,
                ref = ref,
            )
        }.toMap()
    }
}
