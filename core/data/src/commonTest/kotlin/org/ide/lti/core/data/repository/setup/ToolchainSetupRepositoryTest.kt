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

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupLogBounds
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolchainSetupRepositoryTest {

    @Test
    fun testRepositoryDelegationAndStateFlow() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val initial = repository.toolchainState.first()
        assertFalse(initial.isSetupCompleted)

        repository.updateSubmodule("android-tools") {
            it.copy(isSynced = true, commit = "commit123")
        }
        assertEquals(true, repository.currentState.submodules["android-tools"]?.isSynced)

        repository.updateTool("adb") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/bin/adb", version = "1.0")
        }
        assertEquals(true, repository.currentState.tools["adb"]?.isVerified)
        assertEquals("/bin/adb", repository.currentState.tools["adb"]?.binaryPath)

        repository.markStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name, true)
        assertTrue(repository.currentState.isStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name))

        repository.updateAvbKeyState(provisioned = true, keyPath = "/security/avb/key.pem")
        assertTrue(repository.currentState.isAvbKeyProvisioned)
        assertEquals("/security/avb/key.pem", repository.currentState.avbKeyPath)

        repository.updateDiagnosticsSnapshot(setOf("diag1", "diag2"))
        assertEquals(setOf("diag1", "diag2"), repository.currentState.passedDiagnosticIds)

        repository.markSetupCompleted(true)
        assertTrue(repository.currentState.isSetupCompleted)

        repository.clearToolchainState(force = true)
        assertFalse(repository.currentState.isSetupCompleted)
        assertTrue(repository.currentState.tools.isEmpty())
    }

    @Test
    fun testLegacyRecordMigrationDecodesToNoActiveAttempt() = runTest {
        val settings = MapSettings()
        // Legacy JSON format without any ledger fields
        val legacyJson = """
            {
                "isSetupCompleted": true,
                "completedStages": ["WSL_DETECTION", "SERVER_CONNECTIVITY"],
                "tools": {},
                "submodules": {},
                "lastVerifiedTimestamp": 1700000000000,
                "isAvbKeyProvisioned": false
            }
        """.trimIndent()
        settings[ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE] = legacyJson

        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val state = repository.currentState
        assertTrue(state.isSetupCompleted)
        assertEquals(1700000000000L, state.lastVerifiedTimestamp)
        assertNull(state.activeAttempt, "Legacy record migration must yield no active attempt")
        assertTrue(state.attemptHistory.isEmpty(), "Legacy record migration must yield empty history")
        assertFalse(state.recoveryBlocked, "Legacy record without failure is not blocked")
    }

    private class FailingSettings(val delegate: MapSettings = MapSettings()) : Settings by delegate {
        var failWrites = false

        override fun putString(key: String, value: String) {
            if (failWrites) {
                throw RuntimeException("Simulated durable storage disk I/O failure")
            }
            delegate.putString(key, value)
        }
    }

    @Test
    fun testDurableWriteFailureSurfacesAndIsNotSwallowed() = runTest {
        val failingSettings = FailingSettings()
        val dataSource =
            ToolchainPreferencesDataSource(settings = failingSettings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        // Normal write succeeds
        val initialRes = repository.markStageCompleted("WSL_DETECTION", true)
        assertTrue(initialRes.isSuccess)

        // When disk write fails, failure must surface to caller
        failingSettings.failWrites = true
        val failedRes = repository.markStageCompleted("REPO_SYNCHRONIZATION", true)
        assertTrue(failedRes.isFailure, "Durable write failure must surface as Result.failure, not be swallowed")
    }

    @Test
    fun testAtomicEventAndCursorCommit_CursorNeverAdvancesWithoutEvidence() = runTest {
        val failingSettings = FailingSettings()
        val dataSource =
            ToolchainPreferencesDataSource(settings = failingSettings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val attempt = SetupAttemptRecord(
            attemptId = "attempt-atomic-1",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            status = AttemptStatus.RUNNING,
            orderedIntents = listOf(
                ChildIntentRecord(
                    childIndex = 0,
                    stage = "REPO_SYNCHRONIZATION",
                    actionId = "android-tools",
                    request = PersistedExecutionRequest(toolId = "git"),
                    idempotencyKey = "key-1",
                    workspaceLock = "lock-1",
                    lastSeq = 10L,
                    replayLogs = listOf("line 10"),
                ),
            ),
        )
        repository.recordAttemptAuthorized(attempt)
        assertEquals(10L, repository.currentState.activeAttempt?.orderedIntents?.first()?.lastSeq)

        // Attempt cursor advance to seq 11 with line 11 while writes fail
        failingSettings.failWrites = true
        val result = repository.commitChildCursorAndEvidence("attempt-atomic-1", 0, 11L, "line 11")
        assertTrue(result.isFailure, "Commit must fail when write fails")

        // Crucial invariant: Persisted lastSeq never advances beyond durably stored replay evidence
        val activeIntent = repository.currentState.activeAttempt?.orderedIntents?.first()
        assertEquals(10L, activeIntent?.lastSeq, "Persisted lastSeq must NOT advance when write fails")
        assertEquals(
            listOf("line 10"),
            activeIntent?.replayLogs,
            "Replay logs must not commit without durable persistence",
        )
    }

    @Test
    fun testPersistedReplayEvidenceObeysLineAndByteBounds() = runTest {
        val dataSource = ToolchainPreferencesDataSource(settings = MapSettings(), ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)
        val legacyHistory = List(SetupLogBounds.MAX_BUFFER_LINES) { "legacy $it" }
        repository.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = "attempt-bounded",
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
                status = AttemptStatus.RUNNING,
                orderedIntents = listOf(
                    ChildIntentRecord(
                        childIndex = 0,
                        stage = "REPO_SYNCHRONIZATION",
                        actionId = "android-tools",
                        request = PersistedExecutionRequest(toolId = "git"),
                        idempotencyKey = "key-1",
                        workspaceLock = "lock-1",
                        lastSeq = legacyHistory.size.toLong(),
                        replayLogs = legacyHistory,
                    ),
                ),
            ),
        ).getOrThrow()

        val oversized = "日".repeat(SetupLogBounds.MAX_LINE_BYTES) // 3-byte code points, ~48 KiB
        repository.commitChildCursorAndEvidence("attempt-bounded", 0, legacyHistory.size + 1L, oversized).getOrThrow()

        val intent = repository.currentState.activeAttempt?.orderedIntents?.single()
        val logs = intent?.replayLogs.orEmpty()
        assertEquals(SetupLogBounds.MAX_BUFFER_LINES, logs.size, "Journal keeps at most the newest 10,000 lines")
        assertEquals("legacy 1", logs.first(), "The oldest line is evicted, never the newest evidence")
        assertTrue(logs.last().encodeToByteArray().size <= SetupLogBounds.MAX_LINE_BYTES)
        assertTrue(logs.last().endsWith(SetupLogBounds.TRUNCATION_MARKER))
        assertEquals(legacyHistory.size + 1L, intent?.lastSeq, "Cursor advances with its (bounded) evidence")
    }

    @Test
    fun testCorruptJournalFailsClosedWithRecoveryBlocked() = runTest {
        val settings = MapSettings()
        // Corrupt/invalid JSON payload
        settings[ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE] = "{corrupt-non-json-content"

        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val state = repository.currentState
        assertTrue(state.recoveryBlocked, "Corrupt journal must fail closed with recoveryBlocked = true")
        assertTrue(state.recoveryBlockReason?.contains("Corrupt") == true, "Must provide recovery block reason")
    }

    @Test
    fun testTerminalAttemptsRemainAsHistory() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val attempt = SetupAttemptRecord(
            attemptId = "attempt-hist-1",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            status = AttemptStatus.RUNNING,
        )
        repository.recordAttemptAuthorized(attempt)
        assertEquals("attempt-hist-1", repository.currentState.activeAttempt?.attemptId)

        // Record terminal status
        repository.recordAttemptTerminal("attempt-hist-1", AttemptStatus.SUCCEEDED, SetupOutcome.Succeeded())

        // Active attempt must be cleared, terminal attempt must be in attemptHistory
        assertNull(repository.currentState.activeAttempt, "Active attempt must be null after terminal outcome")
        assertEquals(1, repository.currentState.attemptHistory.size, "Terminal attempt must be archived in history")
        val archived = repository.currentState.attemptHistory.first()
        assertEquals("attempt-hist-1", archived.attemptId)
        assertEquals(AttemptStatus.SUCCEEDED, archived.status)
    }

    @Test
    fun testResetProtection_CannotEraseActiveAttemptWithoutExplicitAuthorization() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val attempt = SetupAttemptRecord(
            attemptId = "attempt-active-protect",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            status = AttemptStatus.RUNNING,
        )
        repository.recordAttemptAuthorized(attempt)

        // Clear without force should fail
        val resetResult = repository.clearToolchainState(force = false)
        assertTrue(resetResult.isFailure, "Reset without force must fail when an active attempt exists")
        assertEquals(
            "attempt-active-protect",
            repository.currentState.activeAttempt?.attemptId,
            "Active attempt must be preserved",
        )

        // Clear with explicit authorization (force = true) succeeds
        val forceReset = repository.clearToolchainState(force = true)
        assertTrue(forceReset.isSuccess)
        assertNull(repository.currentState.activeAttempt)
    }

    @Test
    fun testDurableMutationBlockAndTerminalProofMigration() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val attempt = SetupAttemptRecord(
            attemptId = "attempt-proof-1",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            status = AttemptStatus.RUNNING,
            orderedIntents = listOf(
                ChildIntentRecord(
                    childIndex = 0,
                    stage = "REPO_SYNCHRONIZATION",
                    actionId = "android-tools",
                    request = PersistedExecutionRequest(toolId = "git"),
                    idempotencyKey = "key-1",
                    workspaceLock = "lock-1",
                    submissionTimestampEpochMs = 1000L,
                    runId = "server-run-1",
                ),
            ),
            recoveryBlocked = true,
        )
        repository.saveToolchainState(ToolchainPersistenceState(activeAttempt = attempt, recoveryBlocked = true))

        assertTrue(repository.currentState.recoveryBlocked, "Must start recovery blocked")

        // Record terminal proof for child 0
        val proof = ChildTerminalProof(
            environmentKey = "Ubuntu",
            attemptId = "attempt-proof-1",
            runId = "server-run-1",
            authoritativeTerminalStatus = "COMPLETED",
            observedAtEpochMs = System.currentTimeMillis(),
        )
        repository.recordChildTerminalProof(proof)

        // Once proof is recorded, recoveryBlocked is cleared
        assertFalse(repository.currentState.recoveryBlocked, "Recovery block must be cleared after terminal proof")
    }

    @Test
    fun testAppendChildIntentAssignsIndexAndFailsClosedWhenWriteFails() = runTest {
        val failingSettings = FailingSettings()
        val dataSource =
            ToolchainPreferencesDataSource(settings = failingSettings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)
        repository.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = "attempt-append-1",
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
            ),
        )
        val intent = ChildIntentRecord(
            childIndex = -1,
            stage = "REPO_SYNCHRONIZATION",
            actionId = "submodule:android-tools",
            request = PersistedExecutionRequest(toolId = "git", arguments = listOf("clone")),
            idempotencyKey = "Ubuntu:attempt-append-1:submodule:android-tools",
            workspaceLock = "lock:Ubuntu:attempt-append-1:submodule:android-tools",
        )

        val first = repository.appendChildIntent("attempt-append-1", intent)
        assertEquals(0, first.getOrNull(), "First journaled child gets index 0")
        val second = repository.appendChildIntent("attempt-append-1", intent.copy(actionId = "submodule:apktool"))
        assertEquals(1, second.getOrNull(), "Second journaled child gets index 1")
        val journaled = repository.currentState.activeAttempt?.orderedIntents
        assertEquals(2, journaled?.size)
        assertTrue(
            (journaled?.get(0)?.submissionTimestampEpochMs ?: 0L) > 0L,
            "Journal stamps the pre-submission time",
        )
        assertEquals(AttemptStatus.RUNNING, repository.currentState.activeAttempt?.status)

        // "Journal write failure prevents submission": the caller gets a failure and nothing was appended.
        failingSettings.failWrites = true
        val third = repository.appendChildIntent("attempt-append-1", intent.copy(actionId = "submodule:signapk"))
        assertTrue(third.isFailure)
        assertEquals(
            2,
            repository.currentState.activeAttempt?.orderedIntents?.size,
            "A failed append leaves the journal unchanged",
        )
    }

    @Test
    fun testNotSubmittedChildIsClosedAndUnprovenSetShrinks() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)
        repository.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = "attempt-ns-1",
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
            ),
        )
        val index = repository.appendChildIntent(
            "attempt-ns-1",
            ChildIntentRecord(
                childIndex = -1,
                stage = "REPO_SYNCHRONIZATION",
                actionId = "submodule:android-tools",
                request = PersistedExecutionRequest(toolId = "git"),
                idempotencyKey = "k",
                workspaceLock = "l",
            ),
        ).getOrThrow()
        assertEquals(
            1,
            repository.currentState.activeAttempt?.unprovenIntents?.size,
            "A journaled child is possibly submitted",
        )

        assertTrue(repository.recordChildNotSubmitted("attempt-ns-1", index, "NOT_SUBMITTED").isSuccess)
        val closed = repository.currentState.activeAttempt?.orderedIntents?.get(index)
        assertEquals("NOT_SUBMITTED", closed?.terminalStatus)
        assertNull(closed?.runId, "Closing an unsubmitted child never invents a runId")
        assertTrue(repository.currentState.activeAttempt?.unprovenIntents?.isEmpty() == true)
    }

    @Test
    fun testResetProtectionAlsoCoversRecoveryBlockUntilForced() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)
        repository.setRecoveryBlocked(true, "ambiguous receipt")

        assertTrue(
            repository.clearToolchainState(force = false).isFailure,
            "A recovery block survives an unforced reset",
        )
        assertTrue(repository.currentState.recoveryBlocked)
        assertTrue(repository.clearToolchainState(force = true).isSuccess)
        assertFalse(
            repository.currentState.recoveryBlocked,
            "The explicit, confirmed reset is the user-directed escape",
        )
    }

    @Test
    fun testArchivingAttemptClearsRecoveryBlock() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)
        repository.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = "attempt-arch-1",
                environmentKey = "Ubuntu",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
            ),
        )
        repository.setRecoveryBlocked(true, "lost connection")
        assertEquals(AttemptStatus.INTERRUPTED, repository.currentState.activeAttempt?.status)

        repository.recordAttemptTerminal(
            "attempt-arch-1",
            AttemptStatus.INTERRUPTED,
            SetupOutcome.Interrupted("reconciled"),
        )
        assertNull(repository.currentState.activeAttempt)
        assertFalse(repository.currentState.recoveryBlocked, "Archiving ends reconciliation and lifts the block")
        assertEquals("reconciled", repository.currentState.attemptHistory.single().terminalReason)
    }
}
