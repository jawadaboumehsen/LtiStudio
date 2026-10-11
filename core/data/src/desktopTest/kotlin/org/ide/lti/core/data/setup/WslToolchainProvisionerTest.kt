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
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanActionHandler
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.handlers.InstallPackagesHandler
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass", "TooManyFunctions")
class WslToolchainProvisionerTest {
    /**
     * The v2 setup plan delegates all toolchain mutation to ComponentToolsHandler.
     * These provisioner tests exercise orchestration, so this handler simulates a verified
     * installation while preserving the real package-handoff handler and final live check.
     */
    private fun testPlanExecutor(cli: FakeWslCliExecutor): PlanExecutor = PlanExecutor(
        installPackagesHandler = InstallPackagesHandler(),
        componentToolsHandler =
        object : PlanActionHandler<SetupPlanAction.SwitchToolchain> {
            override suspend fun execute(
                action: SetupPlanAction.SwitchToolchain,
                context: ExecutionContext,
            ): SetupOutcome? {
                cli.execute(context.distro, listOf("mkdir", "-p", context.workDir))
                cli.existingPaths += "/home/lti/LtiRomTools/external"
                recommendedToolSources().forEach { submodule ->
                    cli.existingPaths += "/home/lti/LtiRomTools/external/${submodule.name}"
                }
                ToolGroupCatalog.allOutputs.forEach {
                    cli.existingPaths += "/home/lti/LtiRomTools/bin/${it.file}"
                    cli.existingPaths += "/home/lti/LtiRomTools/bin/${it.toolId}"
                }
                return null
            }
        },
    )

    @Test
    fun `checkStatus reports failure when WSL is not installed`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { wslInstalled = false }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()

        val wslStep = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.FAILED, wslStep.status)
        assertTrue(wslStep.error?.contains("WSL 2 is not installed") == true)

        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.PENDING, serverStep.status)
        // Not ready because the toolchain is absent in this fixture, not because the daemon is idle.
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus reports failure when no distro is installed`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { hostDistros = emptyList() }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()

        val wslStep = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.FAILED, wslStep.status)
        assertEquals("No Linux distribution", wslStep.description)
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus fails when server daemon cannot be started`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
                recommendedToolSources().forEach {
                    existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
                }
                ToolGroupCatalog.allOutputs.forEach {
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.file}")
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.toolId}")
                }
            }
        val provisioner =
            WslToolchainProvisioner(
                supervisor = FakeDaemonSupervisor(descriptor = null, isHealthyResult = false),
                transport = FakeRemoteTransport(fakeCli = fakeCli, healthInfo = null),
                repository = inMemoryToolchainRepository(),
                detector = WslEnvironmentDetector(cli = fakeCli),
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()

        assertEquals(StepStatus.FAILED, state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }.status)
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus reports failed when server daemon is not running`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor(descriptor = null, isHealthyResult = false)
        val transport = FakeRemoteTransport(fakeCli = fakeCli, healthInfo = null)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()

        val wslStep = state.steps.first { it.stage == SetupStepStage.WSL_DETECTION }
        assertEquals(StepStatus.SUCCESS, wslStep.status)

        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertFalse(state.isAllReady)
    }

    @Test
    fun `checkStatus reports success when all components exist`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
                recommendedToolSources().forEach {
                    existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
                }
                ToolGroupCatalog.allOutputs.forEach {
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.file}")
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.toolId}")
                }
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor(isHealthyResult = true)
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()
        assertTrue(
            state.steps.all {
                it.status == StepStatus.SUCCESS
            },
            "Failed step: ${state.steps.firstOrNull { it.status != StepStatus.SUCCESS }}",
        )
        assertTrue(state.isAllReady)
    }

    @Test
    fun `confirmed FULL_SETUP plan executes end-to-end pipeline and live check marks state ready`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                planExecutor = testPlanExecutor(fakeCli),
            )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome is SetupOutcome.Succeeded, "Confirmed plan must succeed: $outcome")
        val finalState = provisioner.state.value
        assertTrue(finalState.isAllReady)
        assertFalse(finalState.isRunning)
        assertEquals(4, finalState.steps.filter { it.status == StepStatus.SUCCESS }.size)
        assertTrue(finalState.logs.any { it.contains("completed") })
        assertEquals(
            finalState.checkedAt,
            finalState.lastReadyAt,
            "Readiness after setup comes from the live check",
        )
    }

    @Test
    fun `confirmed plan fails closed at SERVER_CONNECTIVITY when daemon health is null`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        transport.healthInfo = null
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome is SetupOutcome.Failed, "Null health is a terminal prerequisite failure: $outcome")
        assertEquals(SetupStepStage.SERVER_CONNECTIVITY.name, outcome.stage)
        val finalState = provisioner.state.value
        assertFalse(finalState.isAllReady)
        assertFalse(finalState.isRunning)
        assertFalse(finalState.isBusy, "Owner and busy flags must be released on the failure path")
    }

    @Test
    fun `confirmed FULL_SETUP plan creates LtiRomWorkDir if not present`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                planExecutor = testPlanExecutor(fakeCli),
            )

        assertFalse(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")
        assertTrue(fakeCli.existingPaths.contains("/home/lti/LtiRomWorkDir"))
        // The command itself, not the narration: the short in-state log tail rolls over on a full setup.
        assertTrue(
            fakeCli.commandLog.any { it.endsWith("mkdir -p /home/lti/LtiRomWorkDir") },
            "${fakeCli.commandLog}",
        )
    }

    private class FailingJournalRepository(private val delegate: ToolchainSetupRepository) :
        ToolchainSetupRepository by delegate {
        override suspend fun recordAttemptAwaitingUserAction(
            attemptId: String,
            handoff: PersistedUserHandoff,
            plan: PersistedSetupPlan,
        ): Result<Unit> = Result.failure(IllegalStateException("Simulated journal write failure"))
    }

    private class EmptyPersistedPlanRepository(private val delegate: ToolchainSetupRepository) :
        ToolchainSetupRepository by delegate {
        override suspend fun recordAttemptAwaitingUserAction(
            attemptId: String,
            handoff: PersistedUserHandoff,
            plan: PersistedSetupPlan,
        ): Result<Unit> = delegate.recordAttemptAwaitingUserAction(
            attemptId,
            handoff,
            plan.copy(actions = emptyList()),
        )
    }

    @Test
    fun `resume refuses an empty persisted plan instead of falsely succeeding`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { missingDevPackages = true }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val repo = EmptyPersistedPlanRepository(inMemoryToolchainRepository())
        val provisioner =
            WslToolchainProvisioner(
                supervisor = FakeDaemonSupervisor(),
                transport = FakeRemoteTransport(fakeCli = fakeCli),
                repository = repo,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
        val handoff = provisioner.confirm(plan.planId, plan.revisionHash) as SetupOutcome.AwaitingUserAction
        fakeCli.missingDevPackages = false

        val resumed = provisioner.resume(handoff.pendingPlanId)

        assertTrue(resumed is SetupOutcome.Interrupted, "Empty frozen plan must fail closed: $resumed")
        assertTrue(resumed.reason.contains("no resumable actions"))
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, repo.currentState.activeAttempt?.status)
    }

    @Test
    fun `packages plan pauses for the user, survives restart and recovery, then resumes`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val sharedSettings = MapSettings()
        val repo1 = inMemoryToolchainRepository(sharedSettings)

        // 1. Initial check finds dev packages missing
        fakeCli.missingDevPackages = true
        val provisioner1 =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = repo1,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                planExecutor = testPlanExecutor(fakeCli),
            )

        provisioner1.verifyEnvironment()
        assertFalse(provisioner1.state.value.isAllReady)

        // 2. Prepare STAGE_RETRY plan: derives InstallPackages because host_libraries is FAILED
        val plan = provisioner1.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
        val outcome = provisioner1.confirm(plan.planId, plan.revisionHash)

        // 3. Must pause with AwaitingUserAction
        assertTrue(outcome is SetupOutcome.AwaitingUserAction, "Expected AwaitingUserAction but got $outcome")
        assertEquals(plan.planId, outcome.pendingPlanId)
        assertTrue(outcome.handoff.terminalCommand.contains("apt-get install"))
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, repo1.currentState.activeAttempt?.status)

        // 4. Simulate App Restart: create new provisioner over shared persistence
        val repo2 = inMemoryToolchainRepository(sharedSettings)
        val provisioner2 =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = repo2,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                planExecutor = testPlanExecutor(fakeCli),
            )

        // Initial restored state detects pending attempt in AWAITING_USER_ACTION
        assertEquals(repo1.currentState.activeAttempt?.attemptId, provisioner2.state.value.pendingAttemptId)
        // The Recovery view reads the whole journal record (kind, status, issue time, evidence) from state.
        assertEquals(repo1.currentState.activeAttempt, provisioner2.state.value.pendingAttempt)

        // 5. Recovery restores the pending attempt and returns AwaitingUserAction
        val recoveryOutcome = provisioner2.recover()
        assertTrue(
            recoveryOutcome is SetupOutcome.AwaitingUserAction,
            "Recovery must yield AwaitingUserAction: $recoveryOutcome",
        )
        assertEquals(plan.planId, recoveryOutcome.pendingPlanId)

        // 6. User installs packages in WSL: now dpkg-query succeeds
        fakeCli.missingDevPackages = false

        // 7. Resume using planId
        val resumeOutcome = provisioner2.resume(recoveryOutcome.pendingPlanId)
        assertTrue(resumeOutcome is SetupOutcome.Succeeded, "Resume must succeed: $resumeOutcome")

        // 8. Attempt is now marked SUCCEEDED
        val finalAttempt = repo2.currentState.activeAttempt
        assertEquals(null, finalAttempt, "Active attempt must be cleared after archiving terminal success")
        assertEquals(
            StepStatus.SUCCESS,
            provisioner2.state.value.steps.first {
                it.stage == SetupStepStage.SYSTEM_PACKAGES
            }.status,
        )
    }

    @Test
    fun `executePlan fails closed with Interrupted when repository fails to record AwaitingUserAction`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val underlyingRepo = inMemoryToolchainRepository()
        val failingRepo = FailingJournalRepository(underlyingRepo)

        fakeCli.missingDevPackages = true
        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = failingRepo,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(
            outcome is SetupOutcome.Interrupted,
            "Must fail closed with Interrupted on journal failure: $outcome",
        )
        assertTrue(outcome.reason.contains("Journal write failed"))
    }

    @Test
    fun `resume fails closed when a different prerequisite becomes unhealthy during pause`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        // 1. Initial check finds dev packages missing
        fakeCli.missingDevPackages = true
        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = repo,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                planExecutor = testPlanExecutor(fakeCli),
            )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
        val pauseOutcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(pauseOutcome is SetupOutcome.AwaitingUserAction)

        // 2. Dev packages are installed during the pause...
        fakeCli.missingDevPackages = false
        // ...BUT Python runtime is accidentally broken/uninstalled during pause!
        fakeCli.pythonMissing = true

        // 3. Resume must NOT succeed or mark diagnostics as successful!
        val resumeOutcome = provisioner.resume(pauseOutcome.pendingPlanId)
        assertTrue(
            resumeOutcome is SetupOutcome.Failed,
            "Resume must fail closed when another prerequisite fails: $resumeOutcome",
        )
        assertEquals(SetupStepStage.SYSTEM_PACKAGES.name, resumeOutcome.stage)
        assertTrue(resumeOutcome.reason.contains("Environment check failed before resumption"))
        assertTrue(resumeOutcome.reason.contains("Python", ignoreCase = true))

        val activeAttempt = repo.currentState.activeAttempt
        kotlin.test.assertNotNull(activeAttempt, "Attempt must not be cleared or archived as succeeded")
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, activeAttempt.status)

        // 4. Fixing Python allows resumption to pass and complete
        fakeCli.pythonMissing = false
        val fixedResumeOutcome = provisioner.resume(pauseOutcome.pendingPlanId)
        assertTrue(
            fixedResumeOutcome is SetupOutcome.Succeeded,
            "Resume must succeed once all prerequisites pass: $fixedResumeOutcome",
        )
        assertEquals(
            StepStatus.SUCCESS,
            provisioner.state.value.steps.first {
                it.stage == SetupStepStage.SYSTEM_PACKAGES
            }.status,
        )
    }

    @Test
    fun `executePlan fails closed with Failed when constructed without repository and handoff is required`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        fakeCli.missingDevPackages = true
        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = null,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(
            outcome is SetupOutcome.Failed,
            "Must fail closed with Failed when no repository is present to persist handoff: $outcome",
        )
        assertEquals(SetupStepStage.SYSTEM_PACKAGES.name, outcome.stage)
        assertTrue(outcome.reason.contains("requires a configured repository"))
    }

    @Test
    fun `concurrent verifyEnvironment calls share single verification run and single daemon start`() = runTest {
        val testDispatcher = Dispatchers.Default
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
                recommendedToolSources().forEach {
                    existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
                }
                ToolGroupCatalog.allOutputs.forEach {
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.file}")
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.toolId}")
                }
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val inFlightGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val supervisor =
            FakeDaemonSupervisor(isHealthyResult = false).apply {
                onEnsureStarted = {
                    inFlightGate.await()
                }
            }
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = repo,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val deferred1 = async(testDispatcher) { provisioner.verifyEnvironment() }
        while (supervisor.ensureStartedCount == 0) {
            kotlinx.coroutines.delay(10)
        }
        val deferred2 = async(testDispatcher) { provisioner.verifyEnvironment() }

        inFlightGate.complete(Unit)

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        assertEquals(result1, result2, "Concurrent callers must receive identical completed results")
        assertEquals(1, supervisor.ensureStartedCount, "ensureStarted() must be called exactly once")
        assertTrue(result1.isAllReady)
    }

    @Test
    fun `caller cancellation does not abort shared verification for other callers`() = runTest {
        val testDispatcher = Dispatchers.Default
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
                recommendedToolSources().forEach {
                    existingPaths.add("/home/lti/LtiRomTools/external/${it.name}")
                }
                ToolGroupCatalog.allOutputs.forEach {
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.file}")
                    existingPaths.add("/home/lti/LtiRomTools/bin/${it.toolId}")
                }
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val inFlightGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val supervisor =
            FakeDaemonSupervisor(isHealthyResult = false).apply {
                onEnsureStarted = {
                    inFlightGate.await()
                }
            }
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = repo,
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val caller1Job =
            launch(testDispatcher) {
                provisioner.verifyEnvironment()
            }
        while (supervisor.ensureStartedCount == 0) {
            kotlinx.coroutines.delay(10)
        }
        val caller2Deferred =
            async(testDispatcher) {
                provisioner.verifyEnvironment()
            }

        // Cancel caller 1 while verification is in flight
        caller1Job.cancel()

        // Release the barrier so the shared verification completes
        inFlightGate.complete(Unit)

        // Caller 2 should complete normally
        val result2 = caller2Deferred.await()
        assertTrue(result2.isAllReady, "Caller 2 must successfully complete verification")
        assertEquals(1, supervisor.ensureStartedCount)
    }

    private class FailingTerminalArchiveRepository(
        private val delegate: ToolchainSetupRepository,
        var shouldFailRecordAttemptTerminal: Boolean = true,
    ) : ToolchainSetupRepository by delegate {
        override suspend fun recordAttemptTerminal(
            attemptId: String,
            status: AttemptStatus,
            outcome: SetupOutcome,
        ): Result<Unit> {
            if (shouldFailRecordAttemptTerminal) {
                return Result.failure(IllegalStateException("Simulated terminal write failure"))
            }
            return delegate.recordAttemptTerminal(attemptId, status, outcome)
        }
    }

    @Test
    fun `cancel awaiting user action returns Interrupted when journal write fails and leaves attempt active`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val fakeCli = FakeWslCliExecutor().apply { missingDevPackages = true }
            val detector = WslEnvironmentDetector(cli = fakeCli)
            val supervisor = FakeDaemonSupervisor()
            val transport = FakeRemoteTransport(fakeCli = fakeCli)
            val baseRepo = inMemoryToolchainRepository()
            val failingRepo = FailingTerminalArchiveRepository(baseRepo)

            val provisioner =
                WslToolchainProvisioner(
                    supervisor = supervisor,
                    transport = transport,
                    repository = failingRepo,
                    detector = detector,
                    cli = fakeCli,
                    dispatcher = testDispatcher,
                )

            provisioner.verifyEnvironment()
            val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, targetId = SetupStepStage.SYSTEM_PACKAGES.name)
            val pauseOutcome = provisioner.confirm(plan.planId, plan.revisionHash)
            assertTrue(pauseOutcome is SetupOutcome.AwaitingUserAction)
            assertEquals(AttemptStatus.AWAITING_USER_ACTION, baseRepo.currentState.activeAttempt?.status)

            // Cancel while terminal archive fails
            val cancelOutcome = provisioner.cancel()

            // Must report Interrupted, NOT falsely claim Cancelled!
            assertTrue(cancelOutcome is SetupOutcome.Interrupted, "Expected Interrupted but got $cancelOutcome")
            assertTrue(cancelOutcome.reason.contains("could not be archived"))
            assertEquals(AttemptStatus.AWAITING_USER_ACTION, baseRepo.currentState.activeAttempt?.status)

            // Now fix the journal write failure and cancel again
            failingRepo.shouldFailRecordAttemptTerminal = false
            val secondCancelOutcome = provisioner.cancel()
            assertTrue(secondCancelOutcome is SetupOutcome.Cancelled, "Expected Cancelled once journal write succeeds")
            assertNull(baseRepo.currentState.activeAttempt)
        }

    @Test
    fun `server connectivity establishes heartbeat before running build environment checks`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()
        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.SUCCESS, serverStep.status)
        assertTrue(transport.sendHeartbeatCount >= 1, "Heartbeat must be established before checks")
    }

    @Test
    fun `failed heartbeat in server connectivity short circuits doctor checks`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport =
            FakeRemoteTransport(fakeCli = fakeCli).apply {
                sendHeartbeatResult = false
            }

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
            )

        val state = provisioner.checkStatus()
        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertTrue(serverStep.error?.contains("Command admission was not acknowledged") == true)

        val packagesStep = state.steps.first { it.stage == SetupStepStage.SYSTEM_PACKAGES }
        assertEquals(
            StepStatus.PENDING,
            packagesStep.status,
            "Packages stage must remain pending when heartbeat fails",
        )
    }

    @Test
    fun `tool storage inspection failure does not overwrite healthy server bridge status`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val failingStore =
            RecordingComponentStore().apply {
                statusResult = { Result.failure(IllegalStateException("sqlite busy")) }
            }
        val manager = org.ide.lti.core.domain.tools.ToolManager(port = failingStore)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                toolManager = manager,
            )

        val state = provisioner.checkStatus()
        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.SUCCESS, serverStep.status, "Server bridge must remain healthy")

        val toolsStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(StepStatus.FAILED, toolsStep.status)
        assertTrue(toolsStep.error?.contains("sqlite busy") == true)
    }

    @Test
    fun `connection unverified during tool inspection marks server bridge disconnected and tools pending`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val failingStore =
            RecordingComponentStore().apply {
                statusResult = {
                    // Simulate connection unverified at tool inspection time
                    transport.healthInfo = null
                    Result.failure(IllegalStateException("daemon unreachable"))
                }
            }
        val manager = org.ide.lti.core.domain.tools.ToolManager(port = failingStore)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                toolManager = manager,
            )

        val state = provisioner.checkStatus()
        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertEquals("Connection could not be verified", serverStep.description)

        val toolsStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(StepStatus.PENDING, toolsStep.status)
    }

    @Test
    fun `typed admission error during tool inspection marks tools pending and keeps server bridge healthy`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli =
            FakeWslCliExecutor().apply {
                existingPaths.add("/home/lti/LtiRomWorkDir")
                existingPaths.add("/home/lti/LtiRomTools/external")
            }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val failingStore =
            RecordingComponentStore().apply {
                statusResult = {
                    val err =
                        org.ide.lti.core.domain.tools.ToolError.AdmissionRequired(
                            "Build service did not acknowledge the client heartbeat",
                        )
                    Result.failure(org.ide.lti.core.data.tools.ToolErrorException(err))
                }
            }
        val manager = org.ide.lti.core.domain.tools.ToolManager(port = failingStore)

        val provisioner =
            WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = fakeCli,
                dispatcher = testDispatcher,
                toolManager = manager,
            )

        val state = provisioner.checkStatus()
        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.SUCCESS, serverStep.status, "Server bridge must remain healthy")

        val toolsStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(StepStatus.PENDING, toolsStep.status)
        assertEquals("Awaiting command admission handshake", toolsStep.description)
        assertTrue(toolsStep.error?.contains("admission closed") == true)
    }
}
