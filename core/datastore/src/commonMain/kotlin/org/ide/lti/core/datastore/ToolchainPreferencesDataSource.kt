/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupLogBounds
import org.ide.lti.core.model.setup.ToolSelection
import org.ide.lti.core.model.setup.ToolchainPersistenceState

/**
 * Thread-safe multiplatform persistent preferences data source for toolchain state and recovery journal.
 *
 * Backed by a [ToolchainJournalStorage] document and [Json] serialization. The preference [Settings]
 * key is only the legacy location read once when the durable document does not exist yet.
 *
 * Enforces:
 * - Acknowledged durable journal writes: returns [Result] for mutations so failures surface to callers.
 * - "Journal write failure prevents submission; an ambiguous receipt is reconciled or blocked,
 *   never blindly resubmitted."
 * - "Persisted lastSeq never advances beyond durably stored replay evidence."
 * - "Corrupt journal fails closed with recoveryBlocked = true."
 * - "Reset protection: reset cannot erase an active attempt without explicit authorization."
 */
@Suppress("TooManyFunctions") // one persistence surface: setup evidence plus the attempt journal (US3)
public class ToolchainPreferencesDataSource(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val storage: ToolchainJournalStorage = SettingsToolchainJournalStorage(settings),
) {
    private val mutex = Mutex()
    private val _toolchainState = MutableStateFlow(readCurrentStateSafe())

    /**
     * Continuous reactive stream of persisted toolchain state.
     */
    public val toolchainState: Flow<ToolchainPersistenceState> = _toolchainState.asStateFlow()

    /**
     * Synchronous snapshot of the current persisted toolchain state.
     */
    public val currentToolchainState: ToolchainPersistenceState get() = _toolchainState.value

    /**
     * Atomically saves or replaces the complete [ToolchainPersistenceState].
     */
    public suspend fun saveToolchainState(state: ToolchainPersistenceState): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val persistResult = persistStateInternal(state)
            if (persistResult.isSuccess) {
                _toolchainState.value = state
            }
            persistResult
        }
    }

    /**
     * Atomically mutates an individual tool's persisted state.
     */
    public suspend fun updateTool(
        toolId: String,
        transform: (PersistedToolState) -> PersistedToolState,
    ): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val existingTool = current.tools[toolId] ?: PersistedToolState(id = toolId, binaryName = toolId)
            val updatedTool = transform(existingTool)
            val updated = current.copy(
                tools = current.tools + (toolId to updatedTool),
                lastVerifiedTimestamp = updatedTool.lastVerifiedTimestamp ?: current.lastVerifiedTimestamp,
            )
            val persistResult = persistStateInternal(updated)
            if (persistResult.isSuccess) {
                _toolchainState.value = updated
            }
            persistResult
        }
    }

    /**
     * Atomically mutates an external git submodule's persisted state.
     */
    public suspend fun updateSubmodule(
        name: String,
        transform: (PersistedSubmoduleState) -> PersistedSubmoduleState,
    ): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val existing = current.submodules[name] ?: PersistedSubmoduleState(name = name)
            val updatedSub = transform(existing)
            val updated = current.copy(
                submodules = current.submodules + (name to updatedSub),
            )
            val persistResult = persistStateInternal(updated)
            if (persistResult.isSuccess) {
                _toolchainState.value = updated
            }
            persistResult
        }
    }

    /**
     * Records a setup stage as completed or pending.
     */
    public suspend fun markStageCompleted(stageName: String, completed: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _toolchainState.value
                val updatedStages = if (completed) {
                    current.completedStages + stageName
                } else {
                    current.completedStages - stageName
                }
                val updated = current.copy(completedStages = updatedStages)
                val persistResult = persistStateInternal(updated)
                if (persistResult.isSuccess) {
                    _toolchainState.value = updated
                }
                persistResult
            }
        }

    /**
     * Records overall toolchain setup completion status with timestamp.
     */
    public suspend fun markSetupCompleted(completed: Boolean, timestamp: Long? = null): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _toolchainState.value
                val updated = current.copy(
                    isSetupCompleted = completed,
                    lastCompletedTimestamp = if (completed) (timestamp ?: current.lastCompletedTimestamp) else null,
                )
                val persistResult = persistStateInternal(updated)
                if (persistResult.isSuccess) {
                    _toolchainState.value = updated
                }
                persistResult
            }
        }

    /**
     * Records passing doctor diagnostic item IDs.
     */
    public suspend fun updateDiagnosticsSnapshot(passedIds: Set<String>): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updated = current.copy(passedDiagnosticIds = passedIds)
            val persistResult = persistStateInternal(updated)
            if (persistResult.isSuccess) {
                _toolchainState.value = updated
            }
            persistResult
        }
    }

    /**
     * Records AVB 2.0 key provisioning status and path.
     */
    public suspend fun updateAvbKeyState(provisioned: Boolean, keyPath: String?): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _toolchainState.value
                val updated = current.copy(
                    isAvbKeyProvisioned = provisioned,
                    avbKeyPath = keyPath,
                )
                val persistResult = persistStateInternal(updated)
                if (persistResult.isSuccess) {
                    _toolchainState.value = updated
                }
                persistResult
            }
        }

    /**
     * Atomically clears persisted toolchain state and resets to the empty default.
     * Reset protection: an active attempt or a recovery block is never erased unless [force] is true,
     * which is the explicit, user-confirmed authorization to discard it.
     */
    public suspend fun clearToolchainState(force: Boolean = false): Result<Unit> = mutate { current ->
        val active = current.activeAttempt
        if (!force && active != null && active.isActive) {
            return@mutate Result.failure(
                IllegalStateException(
                    "Cannot reset toolchain state while attempt '${active.attemptId}' is active. " +
                        "Explicit authorization required.",
                ),
            )
        }
        if (!force && current.recoveryBlocked) {
            return@mutate Result.failure(
                IllegalStateException(
                    "Cannot reset toolchain state while recovery is blocked: " +
                        "${current.recoveryBlockReason ?: "unresolved prior attempt"}. Explicit authorization " +
                        "required.",
                ),
            )
        }
        Result.success(ToolchainPersistenceState())
    }

    // ---- Durable ledger and attempt operations (003 US3) ----

    /**
     * Journals an authorized attempt before any submission. At most one active attempt per environment.
     */
    public suspend fun recordAttemptAuthorized(attempt: SetupAttemptRecord): Result<Unit> = mutate { current ->
        if (current.recoveryBlocked) {
            return@mutate Result.failure(
                IllegalStateException("Recovery blocked: ${current.recoveryBlockReason ?: "unresolved prior attempt"}"),
            )
        }
        val active = current.activeAttempt
        if (active != null && active.isActive) {
            return@mutate Result.failure(
                IllegalStateException("Another attempt '${active.attemptId}' is already active."),
            )
        }
        val now = System.currentTimeMillis()
        // A reconciled-but-unarchived leftover is history, never silently dropped.
        val history = if (active != null) current.attemptHistory + active else current.attemptHistory
        Result.success(
            current.copy(
                activeAttempt = attempt.copy(
                    createdAtEpochMs = if (attempt.createdAtEpochMs > 0L) attempt.createdAtEpochMs else now,
                    updatedAtEpochMs = now,
                ),
                attemptHistory = history,
            ),
        )
    }

    public suspend fun updateAttemptStatus(attemptId: String, status: AttemptStatus): Result<Unit> =
        mutateActiveAttempt(attemptId) { active -> Result.success(active.copy(status = status)) }

    /**
     * Enforces "Persist child intent before startRun": the intent is durably appended and its index
     * returned before the caller may submit anything.
     */
    public suspend fun appendChildIntent(attemptId: String, intent: ChildIntentRecord): Result<Int> {
        var assignedIndex = -1
        val result = mutateActiveAttempt(attemptId) { active ->
            assignedIndex = active.orderedIntents.size
            val journaled = intent.copy(
                childIndex = assignedIndex,
                submissionTimestampEpochMs = if (intent.submissionTimestampEpochMs > 0L) {
                    intent.submissionTimestampEpochMs
                } else {
                    System.currentTimeMillis()
                },
            )
            Result.success(
                active.copy(
                    orderedIntents = active.orderedIntents + journaled,
                    activeChildIndex = assignedIndex,
                    status = AttemptStatus.RUNNING,
                ),
            )
        }
        return result.map { assignedIndex }
    }

    /**
     * Enforces "Persist runId before acknowledging attachment".
     */
    public suspend fun recordChildRunId(attemptId: String, childIndex: Int, runId: String): Result<Unit> =
        mutateChild(attemptId, childIndex) { intent -> intent.copy(runId = runId) }

    /**
     * Enforces "Persisted lastSeq never advances beyond durably stored replay evidence": the cursor and
     * the evidence line it proves are written in one durable commit, and the cursor never moves backwards.
     */
    public suspend fun commitChildCursorAndEvidence(
        attemptId: String,
        childIndex: Int,
        lastSeq: Long,
        evidenceLine: String? = null,
    ): Result<Unit> = mutateChild(attemptId, childIndex) { intent ->
        // Persisted replay evidence obeys the same line/byte bounds as the live buffers (FR-011); the
        // newest evidence is always retained, so the committed cursor never outruns stored evidence.
        val safeLine = evidenceLine?.let { SetupLogBounds.truncateToUtf8Bytes(it) }
        val updatedLogs = if (safeLine != null) {
            val total = intent.replayLogs + safeLine
            if (total.size > SetupLogBounds.MAX_BUFFER_LINES) total.takeLast(SetupLogBounds.MAX_BUFFER_LINES) else total
        } else {
            intent.replayLogs
        }
        intent.copy(
            lastSeq = maxOf(intent.lastSeq, lastSeq),
            replayLogs = updatedLogs,
        )
    }

    /**
     * Records an authoritative terminal state for one child run. When every possibly submitted child of
     * the active attempt is proven, a previously set recovery block is lifted.
     * Enforces "Fresh retry requires persisted authoritative terminal proof for every possibly submitted
     * child; cancellation acknowledgement alone never unlocks mutation."
     */
    public suspend fun recordChildTerminalProof(proof: ChildTerminalProof): Result<Unit> = mutate { current ->
        val active = current.activeAttempt
        val updatedActive = if (active != null && active.attemptId == proof.attemptId) {
            active.copy(
                orderedIntents = active.orderedIntents.map { intent ->
                    if (intent.runId == proof.runId) {
                        intent.copy(terminalStatus = proof.authoritativeTerminalStatus)
                    } else {
                        intent
                    }
                },
                terminalProofs = active.terminalProofs.filterNot { it.runId == proof.runId } + proof,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        } else {
            active
        }
        val unblock = current.recoveryBlocked && updatedActive != null && updatedActive.unprovenIntents.isEmpty()
        Result.success(
            current.copy(
                activeAttempt = updatedActive,
                recoveryBlocked = if (unblock) false else current.recoveryBlocked,
                recoveryBlockReason = if (unblock) null else current.recoveryBlockReason,
            ),
        )
    }

    /**
     * Closes a child the server never accepted. No runId exists, so no proof can be recorded; the explicit
     * terminal status is the authoritative record that nothing was submitted.
     */
    public suspend fun recordChildNotSubmitted(
        attemptId: String,
        childIndex: Int,
        terminalStatus: String,
    ): Result<Unit> = mutateChild(attemptId, childIndex) { intent -> intent.copy(terminalStatus = terminalStatus) }

    public suspend fun recordPlanActionCompleted(attemptId: String, actionId: String): Result<Unit> =
        mutateActiveAttempt(attemptId) { active ->
            Result.success(active.copy(completedActionIds = (active.completedActionIds + actionId).distinct()))
        }

    public suspend fun recordAttemptAwaitingUserAction(
        attemptId: String,
        handoff: PersistedUserHandoff,
        plan: PersistedSetupPlan,
    ): Result<Unit> = mutateActiveAttempt(attemptId) { active ->
        Result.success(
            active.copy(
                status = AttemptStatus.AWAITING_USER_ACTION,
                awaitingHandoff = handoff,
                persistedPlan = plan,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Archives the active attempt into [ToolchainPersistenceState.attemptHistory] with its terminal result.
     */
    public suspend fun recordAttemptTerminal(
        attemptId: String,
        status: AttemptStatus,
        outcomeName: String,
        outcomeReason: String? = null,
    ): Result<Unit> = mutate { current ->
        val active = current.activeAttempt
        if (active == null || active.attemptId != attemptId) {
            return@mutate Result.failure(IllegalStateException("No active attempt matching $attemptId"))
        }
        val terminal = active.copy(
            status = status,
            terminalOutcome = outcomeName,
            terminalReason = outcomeReason,
            activeChildIndex = null,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        // Archiving is the end of reconciliation: whatever block the attempt carried is resolved with it.
        Result.success(
            current.copy(
                activeAttempt = null,
                attemptHistory = current.attemptHistory + terminal,
                recoveryBlocked = false,
                recoveryBlockReason = null,
            ),
        )
    }

    /**
     * Durably sets or clears the environment recovery block.
     */
    public suspend fun setRecoveryBlocked(blocked: Boolean, reason: String?): Result<Unit> = mutate { current ->
        Result.success(
            current.copy(
                recoveryBlocked = blocked,
                recoveryBlockReason = if (blocked) reason else null,
                activeAttempt = current.activeAttempt?.let { attempt ->
                    attempt.copy(
                        recoveryBlocked = blocked,
                        status = if (blocked) AttemptStatus.INTERRUPTED else attempt.status,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                },
            ),
        )
    }

    public suspend fun desiredSelections(distro: String): DistroToolSelections =
        _toolchainState.value.toolSelections[distro] ?: DistroToolSelections()

    public suspend fun saveSelections(
        distro: String,
        expectedRevision: Long,
        desired: Map<String, ToolSelection>,
    ): Result<Long> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val currentDistroSelections = current.toolSelections[distro] ?: DistroToolSelections()
            if (currentDistroSelections.revision != expectedRevision) {
                return@withLock Result.failure(
                    IllegalStateException(
                        "Stale selection revision for distro $distro: expected $expectedRevision, " +
                            "current ${currentDistroSelections.revision}",
                    ),
                )
            }
            val nextRevision = expectedRevision + 1L
            val updatedSelections = current.toolSelections + (distro to DistroToolSelections(desired, nextRevision))
            val updated = current.copy(toolSelections = updatedSelections)
            val persistResult = persistStateInternal(updated)
            if (persistResult.isSuccess) {
                _toolchainState.value = updated
                Result.success(nextRevision)
            } else {
                val error = persistResult.exceptionOrNull() ?: IllegalStateException("Failed to persist state")
                Result.failure(error)
            }
        }
    }

    public suspend fun recordTrust(repoUrl: String): Result<Unit> = mutate { current ->
        val normalized = ToolSelection.normalizeRepoUrl(repoUrl)
        val updated = if (normalized in current.trustedRepositories) {
            current
        } else {
            current.copy(trustedRepositories = current.trustedRepositories + normalized)
        }
        Result.success(updated)
    }

    public suspend fun isTrusted(repoUrl: String): Boolean {
        val normalized = ToolSelection.normalizeRepoUrl(repoUrl)
        return normalized in _toolchainState.value.trustedRepositories
    }

    /**
     * Single durable-write path: computes the new state under the mutex, persists it, and only then
     * publishes it in memory. A failed persist leaves both the store and the in-memory state untouched.
     */
    private suspend fun mutate(
        transform: (ToolchainPersistenceState) -> Result<ToolchainPersistenceState>,
    ): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _toolchainState.value
            val updated = transform(current).getOrElse { return@withLock Result.failure(it) }
            val persistResult = persistStateInternal(updated)
            if (persistResult.isSuccess) {
                _toolchainState.value = updated
            }
            persistResult
        }
    }

    private suspend fun mutateActiveAttempt(
        attemptId: String,
        transform: (SetupAttemptRecord) -> Result<SetupAttemptRecord>,
    ): Result<Unit> = mutate { current ->
        val active = current.activeAttempt
        if (active == null || active.attemptId != attemptId) {
            return@mutate Result.failure(IllegalStateException("No active attempt matching $attemptId"))
        }
        transform(active).map { updated ->
            current.copy(activeAttempt = updated.copy(updatedAtEpochMs = System.currentTimeMillis()))
        }
    }

    private suspend fun mutateChild(
        attemptId: String,
        childIndex: Int,
        transform: (ChildIntentRecord) -> ChildIntentRecord,
    ): Result<Unit> = mutateActiveAttempt(attemptId) { active ->
        val intent = active.orderedIntents.getOrNull(childIndex)
            ?: return@mutateActiveAttempt Result.failure(
                IllegalStateException("Child index $childIndex not found in attempt"),
            )
        val updatedIntents = active.orderedIntents.toMutableList()
        updatedIntents[childIndex] = transform(intent)
        Result.success(active.copy(orderedIntents = updatedIntents))
    }

    private fun readCurrentStateSafe(): ToolchainPersistenceState {
        // Durable document first; the preference key is the pre-journal legacy location, read only
        // until the first durable write migrates it.
        val raw = storage.read()
            ?: settings.getStringOrNull(KEY_TOOLCHAIN_PERSISTENCE_STATE)
            ?: return ToolchainPersistenceState()
        return try {
            json.decodeFromString<ToolchainPersistenceState>(raw)
        } catch (e: Exception) {
            // Corrupt journal fails closed with recoveryBlocked = true
            ToolchainPersistenceState(
                recoveryBlocked = true,
                recoveryBlockReason = "Corrupt toolchain persistence journal: ${e.message}",
            )
        }
    }

    private fun persistStateInternal(state: ToolchainPersistenceState): Result<Unit> = runCatching {
        val encoded = json.encodeToString(state)
        storage.write(encoded)
    }

    public companion object {
        public const val KEY_TOOLCHAIN_PERSISTENCE_STATE: String = "lti_toolchain_persistence_state_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
