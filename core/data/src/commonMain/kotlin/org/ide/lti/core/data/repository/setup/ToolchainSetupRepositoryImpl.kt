/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.setup

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Thin [ToolchainSetupRepository] wrapper over [ToolchainPreferencesDataSource].
 */
public class ToolchainSetupRepositoryImpl(
    private val preferencesDataSource: ToolchainPreferencesDataSource,
    private val selectionRepository: ToolchainSelectionRepository =
        ToolchainSelectionRepositoryImpl(preferencesDataSource),
) : ToolchainSetupRepository,
    ToolchainSelectionRepository by selectionRepository {
    override val toolchainState: Flow<ToolchainPersistenceState> = preferencesDataSource.toolchainState

    override val currentState: ToolchainPersistenceState get() = preferencesDataSource.currentToolchainState

    override suspend fun saveToolchainState(state: ToolchainPersistenceState): Result<Unit> =
        preferencesDataSource.saveToolchainState(state)

    override suspend fun updateTool(
        toolId: String,
        transform: (PersistedToolState) -> PersistedToolState,
    ): Result<Unit> = preferencesDataSource.updateTool(toolId, transform)

    override suspend fun updateSubmodule(
        name: String,
        transform: (PersistedSubmoduleState) -> PersistedSubmoduleState,
    ): Result<Unit> = preferencesDataSource.updateSubmodule(name, transform)

    override suspend fun markStageCompleted(stageName: String, completed: Boolean): Result<Unit> =
        preferencesDataSource.markStageCompleted(stageName, completed)

    override suspend fun markSetupCompleted(completed: Boolean, timestamp: Long?): Result<Unit> =
        preferencesDataSource.markSetupCompleted(completed, timestamp)

    override suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>): Result<Unit> =
        preferencesDataSource.updateDiagnosticsSnapshot(passedIds)

    override suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?): Result<Unit> =
        preferencesDataSource.updateAvbKeyState(provisioned, keyPath)

    override suspend fun clearToolchainState(force: Boolean): Result<Unit> =
        preferencesDataSource.clearToolchainState(force)

    override suspend fun recordAttemptAuthorized(attempt: SetupAttemptRecord): Result<Unit> =
        preferencesDataSource.recordAttemptAuthorized(attempt)

    override suspend fun updateAttemptStatus(attemptId: String, status: AttemptStatus): Result<Unit> =
        preferencesDataSource.updateAttemptStatus(attemptId, status)

    override suspend fun appendChildIntent(attemptId: String, intent: ChildIntentRecord): Result<Int> =
        preferencesDataSource.appendChildIntent(attemptId, intent)

    override suspend fun recordChildRunId(attemptId: String, childIndex: Int, runId: String): Result<Unit> =
        preferencesDataSource.recordChildRunId(attemptId, childIndex, runId)

    override suspend fun commitChildCursorAndEvidence(
        attemptId: String,
        childIndex: Int,
        lastSeq: Long,
        evidenceLine: String?,
    ): Result<Unit> = preferencesDataSource.commitChildCursorAndEvidence(attemptId, childIndex, lastSeq, evidenceLine)

    override suspend fun recordChildTerminalProof(proof: ChildTerminalProof): Result<Unit> =
        preferencesDataSource.recordChildTerminalProof(proof)

    override suspend fun recordChildNotSubmitted(
        attemptId: String,
        childIndex: Int,
        terminalStatus: String,
    ): Result<Unit> = preferencesDataSource.recordChildNotSubmitted(attemptId, childIndex, terminalStatus)

    override suspend fun recordPlanActionCompleted(attemptId: String, actionId: String): Result<Unit> =
        preferencesDataSource.recordPlanActionCompleted(attemptId, actionId)

    override suspend fun recordAttemptTerminal(
        attemptId: String,
        status: AttemptStatus,
        outcome: SetupOutcome,
    ): Result<Unit> = preferencesDataSource.recordAttemptTerminal(
        attemptId = attemptId,
        status = status,
        outcomeName = outcome::class.simpleName ?: "Unknown",
        outcomeReason = when (outcome) {
            is SetupOutcome.Failed -> outcome.reason
            is SetupOutcome.Interrupted -> outcome.reason
            is SetupOutcome.Busy -> outcome.ownerId
            else -> null
        },
    )

    override suspend fun recordAttemptAwaitingUserAction(
        attemptId: String,
        handoff: PersistedUserHandoff,
        plan: PersistedSetupPlan,
    ): Result<Unit> = preferencesDataSource.recordAttemptAwaitingUserAction(attemptId, handoff, plan)

    override suspend fun setRecoveryBlocked(blocked: Boolean, reason: String?): Result<Unit> =
        preferencesDataSource.setRecoveryBlocked(blocked, reason)
}
