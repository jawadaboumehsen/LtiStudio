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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Immutable session state tracking working draft vs persisted baseline for a workspace.
 */
public data class PipelineDraftSession(
    val workspaceId: String,
    val persistedSnapshot: ConfigurationSnapshot,
    val workingSnapshot: ConfigurationSnapshot,
    val persistedRevision: Long,
    val workingRevision: Long,
    val target: TargetDevice? = null,
) {
    public val isDirty: Boolean get() = workingRevision != persistedRevision

    public companion object {
        public fun initial(
            workspaceId: String,
            baseline: ConfigurationSnapshot?,
            target: TargetDevice? = null,
        ): PipelineDraftSession {
            val defaultTarget = target ?: DefaultTargetCatalog.PQ84P01_DEFAULT
            val effectiveBaseline = baseline ?: ConfigurationSnapshot.create(
                id = "snapshot-$workspaceId-init",
                workspaceId = workspaceId,
                profileRevision = 1,
                acquisition = AcquisitionSettings(
                    region = defaultTarget.availableRegions.first(),
                    firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
                ),
                createdAt = Clock.System.now(),
            )
            return PipelineDraftSession(
                workspaceId = workspaceId,
                persistedSnapshot = effectiveBaseline,
                workingSnapshot = effectiveBaseline,
                persistedRevision = 1L,
                workingRevision = 1L,
                target = defaultTarget,
            )
        }
    }
}

public class OptimisticRevisionConflictException(
    public val workspaceId: String,
    public val expectedRevision: Long,
    public val actualRevision: Long,
) : IllegalStateException(
    "Save rejected for workspace $workspaceId: expected revision $expectedRevision but working " +
        "revision is $actualRevision",
)

/**
 * In-memory repository managing pipeline draft sessions across active workspaces.
 */
public class PipelineDraftSessionRepository {
    private val sessions = mutableMapOf<String, MutableStateFlow<PipelineDraftSession>>()
    private val mutex = Mutex()

    private fun getOrCreateFlow(
        workspaceId: String,
        baseline: ConfigurationSnapshot? = null,
    ): MutableStateFlow<PipelineDraftSession> = sessions.getOrPut(workspaceId) {
        MutableStateFlow(PipelineDraftSession.initial(workspaceId, baseline))
    }

    public fun observeSession(workspaceId: String): Flow<PipelineDraftSession> =
        getOrCreateFlow(workspaceId).asStateFlow()

    public suspend fun updateWorkingSnapshot(
        workspaceId: String,
        transform: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    ): PipelineDraftSession = mutex.withLock {
        val flow = getOrCreateFlow(workspaceId)
        val current = flow.value
        val updatedWorking = transform(current.workingSnapshot)
        val updatedSession = current.copy(
            workingSnapshot = updatedWorking,
            workingRevision = current.workingRevision + 1,
        )
        flow.value = updatedSession
        updatedSession
    }

    public suspend fun save(workspaceId: String, expectedRevision: Long): ConfigurationSnapshot = mutex.withLock {
        val flow = getOrCreateFlow(workspaceId)
        val current = flow.value
        if (current.workingRevision != expectedRevision) {
            throw OptimisticRevisionConflictException(
                workspaceId = workspaceId,
                expectedRevision = expectedRevision,
                actualRevision = current.workingRevision,
            )
        }
        val committed = current.workingSnapshot.copy(
            createdAt = Clock.System.now(),
        )
        val newRevision = current.workingRevision + 1
        flow.value = current.copy(
            persistedSnapshot = committed,
            workingSnapshot = committed,
            persistedRevision = newRevision,
            workingRevision = newRevision,
        )
        committed
    }

    public suspend fun discardChanges(workspaceId: String): PipelineDraftSession = mutex.withLock {
        val flow = getOrCreateFlow(workspaceId)
        val current = flow.value
        val newRevision = current.workingRevision + 1
        val resetSession = current.copy(
            workingSnapshot = current.persistedSnapshot,
            workingRevision = newRevision,
            persistedRevision = newRevision,
        )
        flow.value = resetSession
        resetSession
    }

    public fun validate(workspaceId: String): ValidationReport {
        val session = getOrCreateFlow(workspaceId).value
        val draft = session.workingSnapshot
        val target = session.target
        val reports = listOf(
            "acquisition" to StageSettingsValidator.validateAcquisition(draft.acquisition, target),
            "extraction" to StageSettingsValidator.validateExtraction(draft.extraction, target),
            "assembly" to StageSettingsValidator.validateAssembly(draft.assembly, target),
            "debloat" to StageSettingsValidator.validateDebloat(draft.debloat),
            "customization" to StageSettingsValidator.validateCustomization(draft.customization),
            "build" to StageSettingsValidator.validateBuild(draft.build, target),
            "release" to StageSettingsValidator.validateRelease(draft.release, target),
            "publish" to StageSettingsValidator.validatePublish(draft.publish),
        )
        return ValidationReport(
            errors = reports.flatMap { (prefix, report) ->
                report.errors.map { error -> error.copy(fieldPath = "$prefix.${error.fieldPath}") }
            },
        )
    }
}
