/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.SettingsToolchainJournalStorage
import org.ide.lti.core.datastore.ToolchainJournalStorage
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import org.ide.lti.core.model.setup.toDomain
import org.ide.lti.core.model.setup.toPersisted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BootstrapJournalTest {

    private class NoOpTransport : RemoteTransportPort {
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(0, "", "", 0L)
        override fun stream(request: ToolExecutionRequest): Flow<io.ltirom.tooling.core.remote.StreamEvent> =
            emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = false
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
        override suspend fun startRun(request: StartRunRequest): RunHandle = error("Not expected in bootstrap tests")
        override suspend fun getRun(runId: String): RunStatus? = null
        override suspend fun listRuns(workspaceLock: String?): List<RunStatus> = emptyList()
        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = emptyFlow()
        override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse? = null
    }

    private class FailingStorage : ToolchainJournalStorage {
        override fun read(): String? = null
        override fun write(json: String): Unit = throw java.io.IOException("Disk full / permission denied")
    }

    /** Journals a package handoff exactly as the provisioner does: authorize, then pause for the user. */
    private suspend fun journalBootstrap(
        repo: ToolchainSetupRepositoryImpl,
        attemptId: String,
        environment: SetupEnvironment,
        packages: List<String>,
        command: String,
    ): Result<Unit> = repo.recordAttemptAuthorized(
        SetupAttemptRecord(
            attemptId = attemptId,
            environmentKey = environment.distro,
            planId = attemptId,
            planRevisionHash = "rev",
            planKind = SetupPlanKind.BOOTSTRAP_PACKAGES.name,
            status = AttemptStatus.AUTHORIZED,
            environment = environment.toPersisted(),
        ),
    ).mapCatching {
        repo.recordAttemptAwaitingUserAction(
            attemptId,
            PersistedUserHandoff(
                actionId = "install_packages",
                distro = environment.distro,
                command = command,
                packages = packages,
            ),
            PersistedSetupPlan(attemptId, "rev", environment.distro, SetupPlanKind.BOOTSTRAP_PACKAGES.name),
        ).getOrThrow()
    }

    @Test
    fun oldFormatJournalLoadsWithDefaults() = runTest {
        // Old-format journal without environment or bootstrapRequirementIds
        val oldJson = """
            {
                "activeAttempt": {
                    "attemptId": "old-att-1",
                    "environmentKey": "Ubuntu-24.04",
                    "planId": "plan-1",
                    "planRevisionHash": "rev-1",
                    "planKind": "FULL_SETUP",
                    "status": "AWAITING_USER_ACTION",
                    "plannedActionIds": ["packages"]
                }
            }
        """.trimIndent()

        val json = ToolchainPreferencesDataSource.defaultJson()
        val decoded = json.decodeFromString<ToolchainPersistenceState>(oldJson)
        val attempt = decoded.activeAttempt
        assertNotNull(attempt)
        assertEquals("old-att-1", attempt.attemptId)
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, attempt.status)
        assertNull(attempt.environment, "Legacy attempt must deserialize with null environment")
        assertTrue(
            attempt.bootstrapRequirementIds.isEmpty(),
            "Legacy attempt must deserialize with empty bootstrap requirement IDs",
        )
    }

    @Test
    fun abandonedRoundTrips() = runTest {
        val settings = MapSettings()
        val storage = SettingsToolchainJournalStorage(settings)
        val dataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val repo = ToolchainSetupRepositoryImpl(dataSource)

        val env = SetupEnvironment(
            distro = "Ubuntu-24.04",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "developer",
            home = "/home/developer",
        )

        val writeResult = journalBootstrap(
            repo = repo,
            attemptId = "abandon-att-1",
            environment = env,
            packages = listOf("git", "cmake"),
            command = "sudo apt-get update && sudo apt-get install -y git cmake",
        )
        assertTrue(writeResult.isSuccess)
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, repo.currentState.activeAttempt?.status)

        val abandonResult = repo.recordAttemptTerminal("abandon-att-1", AttemptStatus.ABANDONED, SetupOutcome.Cancelled)
        assertTrue(abandonResult.isSuccess)
        assertNull(repo.currentState.activeAttempt, "Abandoned attempt is no longer active")
        val archived = repo.currentState.attemptHistory.singleOrNull { it.attemptId == "abandon-att-1" }
        assertNotNull(archived)
        assertEquals(AttemptStatus.ABANDONED, archived.status)

        // Reload fresh from storage to verify deserialization of ABANDONED
        val reloadedDataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val reloadedArchived = reloadedDataSource.currentToolchainState.attemptHistory.singleOrNull {
            it.attemptId ==
                "abandon-att-1"
        }
        assertNotNull(reloadedArchived)
        assertEquals(AttemptStatus.ABANDONED, reloadedArchived.status)
    }

    @Test
    fun bootstrapAttemptRestoresFullEnvironmentAndPackagesAfterRestart() = runTest {
        val settings = MapSettings()
        val storage = SettingsToolchainJournalStorage(settings)
        val dataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val repo = ToolchainSetupRepositoryImpl(dataSource)

        val env = SetupEnvironment(
            distro = "Ubuntu-24.04",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "developer",
            home = "/home/developer",
        )
        val reqIds = listOf("git", "cmake", "python3-cryptography")
        val command = "sudo apt-get update && sudo apt-get install -y git cmake python3-cryptography"

        val result = journalBootstrap(
            repo = repo,
            attemptId = "bootstrap-att-1",
            environment = env,
            packages = reqIds,
            command = command,
        )
        assertTrue(result.isSuccess)

        // Simulate app restart by constructing a new dataSource from the same persistent storage
        val restartedDataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val active = restartedDataSource.currentToolchainState.activeAttempt
        assertNotNull(active)
        assertEquals("bootstrap-att-1", active.attemptId)
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, active.status)
        assertEquals(env, active.environment?.toDomain())
        assertEquals(reqIds, active.awaitingHandoff?.packages)
        assertEquals(command, active.awaitingHandoff?.command)
        assertEquals("Ubuntu-24.04", active.environmentKey)
    }

    @Test
    fun journalWriteFailureWhileIssuingHandoffGivesFailure() = runTest {
        val settings = MapSettings()
        val failingStorage = FailingStorage()
        val dataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = failingStorage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val repo = ToolchainSetupRepositoryImpl(dataSource)

        val env = SetupEnvironment(
            distro = "Ubuntu-24.04",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "developer",
            home = "/home/developer",
        )

        val result = journalBootstrap(
            repo = repo,
            attemptId = "fail-att-1",
            environment = env,
            packages = listOf("git"),
            command = "sudo apt-get install -y git",
        )
        assertTrue(result.isFailure, "Journal write failure must return failure")
        val outcome = result.fold(
            onSuccess = { SetupOutcome.Succeeded() },
            onFailure = { SetupOutcome.Interrupted("Attempt could not be journaled: ${it.message}") },
        )
        assertTrue(outcome is SetupOutcome.Interrupted, "Failure maps to SetupOutcome.Interrupted")
    }

    @Test
    fun restartDuringVerificationRestoresAwaitingUserAction() = runTest {
        val settings = MapSettings()
        val storage = SettingsToolchainJournalStorage(settings)
        val dataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val repo = ToolchainSetupRepositoryImpl(dataSource)

        val env = SetupEnvironment(
            distro = "Ubuntu-24.04",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "developer",
            home = "/home/developer",
        )
        val reqIds = listOf("git", "cmake")
        val command = "sudo apt-get update && sudo apt-get install -y git cmake"

        journalBootstrap(repo, "verify-att-1", env, reqIds, command)

        // Verification begins: status transitions to RUNNING
        repo.updateAttemptStatus("verify-att-1", AttemptStatus.RUNNING)
        assertEquals(AttemptStatus.RUNNING, repo.currentState.activeAttempt?.status)

        // Crash and restart: fresh repo and fresh recovery engine
        val restartedDataSource = ToolchainPreferencesDataSource(
            settings = settings,
            storage = storage,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val restartedRepo = ToolchainSetupRepositoryImpl(restartedDataSource)
        val recovery = SetupRunRecovery(
            transport = NoOpTransport(),
            repository = restartedRepo,
        )

        val outcome = recovery.recover("Ubuntu-24.04", "verify-att-1")
        assertTrue(outcome is SetupOutcome.AwaitingUserAction, "Outcome must be AwaitingUserAction, got $outcome")
        assertEquals(SetupStepStage.SYSTEM_PACKAGES, outcome.stage)
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, restartedRepo.currentState.activeAttempt?.status)
    }
}
