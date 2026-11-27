/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.session

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings

class ObservePipelineDraftSessionUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<PipelineDraftSession> = repository.observeSession(workspaceId)
}

class SavePipelineDraftUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, expectedRevision: Long): ConfigurationSnapshot =
        repository.save(workspaceId, expectedRevision)
}

class ValidatePipelineDraftUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): ValidationReport = repository.validate(workspaceId)
}

class DiscardPipelineDraftChangesUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String): PipelineDraftSession = repository.discardChanges(workspaceId)
}

// ---- Per-Stage Configuration Use Cases ----

class ObserveAcquisitionConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<AcquisitionSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.acquisition }.distinctUntilChanged()
}

class UpdateAcquisitionConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: AcquisitionSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(acquisition = settings) }
}

class ObserveExtractionConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<ExtractionSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.extraction }.distinctUntilChanged()
}

class UpdateExtractionConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: ExtractionSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(extraction = settings) }
}

class ObserveAssemblyConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<AssemblySettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.assembly }.distinctUntilChanged()
}

class UpdateAssemblyConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: AssemblySettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(assembly = settings) }
}

class ObserveDebloatConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<DebloatSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.debloat }.distinctUntilChanged()
}

class UpdateDebloatConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: DebloatSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(debloat = settings) }
}

class ObserveCustomizationConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<CustomizationSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.customization }.distinctUntilChanged()
}

class UpdateCustomizationConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: CustomizationSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(customization = settings) }
}

class ObserveBuildConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<BuildSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.build }.distinctUntilChanged()
}

class UpdateBuildConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: BuildSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(build = settings) }
}

class ObserveReleaseConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<ReleaseSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.release }.distinctUntilChanged()
}

class UpdateReleaseConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: ReleaseSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(release = settings) }
}

class ObservePublishConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    operator fun invoke(workspaceId: String): Flow<PublishSettings> =
        repository.observeSession(workspaceId).map { it.workingSnapshot.publish }.distinctUntilChanged()
}

class UpdatePublishConfigUseCase(private val repository: PipelineDraftSessionRepository) {
    suspend operator fun invoke(workspaceId: String, settings: PublishSettings): PipelineDraftSession =
        repository.updateWorkingSnapshot(workspaceId) { it.copy(publish = settings) }
}
