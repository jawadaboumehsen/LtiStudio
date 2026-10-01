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
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BootstrapHandoffTest {

    private class CountingTransport : RemoteTransportPort {
        var checkHealthCount = 0

        override suspend fun checkHealth(): WslServerInfo? {
            checkHealthCount++
            return null
        }

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            if (request.arguments.contains("HOME")) {
                ToolExecutionResponse(0, "/home/testuser\n", "", 10L)
            } else {
                ToolExecutionResponse(0, "ok", "", 10L)
            }

        override fun stream(request: ToolExecutionRequest): Flow<io.ltirom.tooling.core.remote.StreamEvent> =
            emptyFlow()

        override suspend fun listTools(): io.ltirom.tooling.core.remote.ToolListResult =
            io.ltirom.tooling.core.remote.ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): io.ltirom.tooling.core.remote.ToolListResult =
            io.ltirom.tooling.core.remote.ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private class FakeDaemonSupervisor : DaemonSupervisorPort {
        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = null
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = false
        override suspend fun ensureStarted(): ServerConnectionDescriptor = error("Not implemented")
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private fun createRepository(dispatcher: CoroutineDispatcher): ToolchainSetupRepository {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = dispatcher)
        return ToolchainSetupRepositoryImpl(dataSource)
    }

    private class FakeHostPrerequisiteProbe(var probeReport: PrerequisiteReport) : HostPrerequisitePort {
        var probeCount = 0

        override suspend fun detect(): List<DistroStatus> = listOf(
            DistroStatus.Usable(probeReport.environment),
        )

        override suspend fun probe(environment: SetupEnvironment): PrerequisiteReport {
            probeCount++
            return probeReport
        }
    }

    private class FakeServerArtifactAdapter : ServerArtifactPort {
        override suspend fun status(environment: SetupEnvironment): ServerArtifact = ServerArtifact(
            bundledVersion = "1.0.0",
            activeVersion = null,
            runningVersion = null,
            runningActiveRuns = 0,
            installState = InstallState.NotInstalled,
            javaHome = null,
        )

        override suspend fun ensureInstalled(environment: SetupEnvironment): ServerArtifact = ServerArtifact(
            bundledVersion = "1.0.0",
            activeVersion = "1.0.0",
            runningVersion = null,
            runningActiveRuns = 0,
            installState = InstallState.Current,
            javaHome = null,
        )

        override suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String): ServerArtifact =
            ServerArtifact(
                bundledVersion = "1.0.0",
                activeVersion = "1.0.0",
                runningVersion = "1.0.0",
                runningActiveRuns = 0,
                installState = InstallState.Current,
                javaHome = javaHome,
            )
    }

    private val testEnv = SetupEnvironment(
        distro = "Ubuntu-24.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "24.04",
        user = "testuser",
        home = "/home/testuser",
    )

    private val missingReport = PrerequisiteReport(
        environment = testEnv,
        results = mapOf(
            "git" to RequirementStatus.Missing,
            "cmake" to RequirementStatus.Missing,
            "java-server" to RequirementStatus.Missing,
        ),
        missingPackages = listOf("cmake", "git", "openjdk-21-jdk"),
        installCommand = "sudo apt-get update && sudo apt-get install -y cmake git openjdk-21-jdk",
        javaHome = null,
        packageListsFresh = true,
        checkedAt = 1000L,
    )

    @Test
    fun `confirming BOOTSTRAP_PACKAGES never calls transport checkHealth and journals AWAITING_USER_ACTION`() =
        runTest {
            val transport = CountingTransport()
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val repository = createRepository(dispatcher)
            val probe = FakeHostPrerequisiteProbe(missingReport)

            val provisioner = WslToolchainProvisioner(
                repository = repository,
                transport = transport,
                supervisor = FakeDaemonSupervisor(),
                dispatcher = dispatcher,
                hostPrerequisitePort = probe,
                serverArtifactPort = FakeServerArtifactAdapter(),
            )

            // Run check to establish live evidence
            provisioner.verifyEnvironment()

            val plan = provisioner.prepare(SetupPlanKind.BOOTSTRAP_PACKAGES)
            assertNotNull(plan, "Bootstrap plan should be prepared")
            assertEquals(SetupPlanKind.BOOTSTRAP_PACKAGES, plan.kind)

            val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

            // Transport checkHealth must NEVER be called during bootstrap handoff confirmation
            assertEquals(
                0,
                transport.checkHealthCount,
                "transport.checkHealth() must NOT be called for BOOTSTRAP_PACKAGES",
            )

            assertTrue(outcome is SetupOutcome.AwaitingUserAction, "Outcome must be AwaitingUserAction, was $outcome")
            val handoff = (outcome as SetupOutcome.AwaitingUserAction).handoff
            assertEquals("Ubuntu-24.04", handoff.distro)
            assertTrue(handoff.packages.contains("git"))

            val activeAttempt = repository.currentState.activeAttempt
            assertNotNull(activeAttempt)
            assertEquals(AttemptStatus.AWAITING_USER_ACTION, activeAttempt.status)
            assertEquals(plan.planId, activeAttempt.planId)
            assertEquals("Ubuntu-24.04", activeAttempt.environmentKey)
            assertNotNull(activeAttempt.awaitingHandoff)
        }

    @Test
    fun `on resume with packages present, reprobes via hostPrerequisitePort and succeeds without Doctor`() = runTest {
        val transport = CountingTransport()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val probe = FakeHostPrerequisiteProbe(missingReport)

        val provisioner = WslToolchainProvisioner(
            repository = repository,
            transport = transport,
            supervisor = FakeDaemonSupervisor(),
            dispatcher = dispatcher,
            hostPrerequisitePort = probe,
            serverArtifactPort = FakeServerArtifactAdapter(),
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.BOOTSTRAP_PACKAGES)
        provisioner.confirm(plan.planId, plan.revisionHash)

        // Now packages are present upon user running the command
        probe.probeReport = missingReport.copy(
            results = mapOf(
                "git" to RequirementStatus.Present,
                "cmake" to RequirementStatus.Present,
                "java-server" to RequirementStatus.Present,
            ),
            missingPackages = emptyList(),
            installCommand = null,
            javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
        )

        val resumeOutcome = provisioner.resume(plan.planId)

        // Must have reprobed via HostPrerequisitePort
        assertTrue(probe.probeCount >= 2, "Must re-probe during resume")
        // No health checks via transport during bootstrap resume
        assertEquals(0, transport.checkHealthCount)

        assertTrue(
            resumeOutcome is SetupOutcome.Succeeded,
            "Resume with packages present should succeed, was $resumeOutcome",
        )
    }

    @Test
    fun `on resume with partial packages remaining, stays AWAITING_USER_ACTION with updated list`() = runTest {
        val transport = CountingTransport()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val probe = FakeHostPrerequisiteProbe(missingReport)

        val provisioner = WslToolchainProvisioner(
            repository = repository,
            transport = transport,
            supervisor = FakeDaemonSupervisor(),
            dispatcher = dispatcher,
            hostPrerequisitePort = probe,
            serverArtifactPort = FakeServerArtifactAdapter(),
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.BOOTSTRAP_PACKAGES)
        provisioner.confirm(plan.planId, plan.revisionHash)

        // Only git was installed, cmake and java still missing
        probe.probeReport = missingReport.copy(
            results = mapOf(
                "git" to RequirementStatus.Present,
                "cmake" to RequirementStatus.Missing,
                "java-server" to RequirementStatus.Missing,
            ),
            missingPackages = listOf("cmake", "openjdk-21-jdk"),
            installCommand = "sudo apt-get update && sudo apt-get install -y cmake openjdk-21-jdk",
        )

        val resumeOutcome = provisioner.resume(plan.planId)

        assertTrue(resumeOutcome is SetupOutcome.AwaitingUserAction, "Must stay AwaitingUserAction on partial result")
        val handoff = (resumeOutcome as SetupOutcome.AwaitingUserAction).handoff
        assertFalse(handoff.packages.contains("git"), "Installed package 'git' should no longer be in handoff")
        assertTrue(handoff.packages.contains("cmake"))
    }

    @Test
    fun `abandon marks attempt ABANDONED in repository`() = runTest {
        val transport = CountingTransport()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val probe = FakeHostPrerequisiteProbe(missingReport)

        val provisioner = WslToolchainProvisioner(
            repository = repository,
            transport = transport,
            supervisor = FakeDaemonSupervisor(),
            dispatcher = dispatcher,
            hostPrerequisitePort = probe,
            serverArtifactPort = FakeServerArtifactAdapter(),
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.BOOTSTRAP_PACKAGES)
        provisioner.confirm(plan.planId, plan.revisionHash)

        val abandonOutcome = provisioner.cancel()
        assertTrue(abandonOutcome is SetupOutcome.Cancelled)

        val active = repository.currentState.activeAttempt
        // Either active is null (archived) or status is ABANDONED
        val archived = repository.currentState.attemptHistory.firstOrNull { it.planId == plan.planId }
        assertTrue(
            archived?.status == AttemptStatus.ABANDONED || active?.status == AttemptStatus.ABANDONED,
            "Attempt must be marked ABANDONED in repository",
        )
    }

    /** Repository whose writes can be made to fail, to prove journal failures are never reported as success. */
    private class FailingWritesRepository(private val delegate: ToolchainSetupRepository) :
        ToolchainSetupRepository by delegate {
        var failAwaiting = false
        var failTerminal = false

        override suspend fun recordAttemptAwaitingUserAction(
            attemptId: String,
            handoff: PersistedUserHandoff,
            plan: PersistedSetupPlan,
        ): Result<Unit> = if (failAwaiting) {
            Result.failure(java.io.IOException("disk full"))
        } else {
            delegate.recordAttemptAwaitingUserAction(attemptId, handoff, plan)
        }

        override suspend fun recordAttemptTerminal(
            attemptId: String,
            status: AttemptStatus,
            outcome: SetupOutcome,
        ): Result<Unit> = if (failTerminal) {
            Result.failure(java.io.IOException("disk full"))
        } else {
            delegate.recordAttemptTerminal(attemptId, status, outcome)
        }
    }

    private suspend fun startHandoff(
        repository: ToolchainSetupRepository,
        probe: FakeHostPrerequisiteProbe,
        dispatcher: CoroutineDispatcher,
    ): Pair<WslToolchainProvisioner, String> {
        val provisioner = WslToolchainProvisioner(
            repository = repository,
            transport = CountingTransport(),
            supervisor = FakeDaemonSupervisor(),
            dispatcher = dispatcher,
            hostPrerequisitePort = probe,
            serverArtifactPort = FakeServerArtifactAdapter(),
        )
        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.BOOTSTRAP_PACKAGES)
        provisioner.confirm(plan.planId, plan.revisionHash)
        return provisioner to plan.planId
    }

    private val allPresentReport = missingReport.copy(
        results = mapOf(
            "git" to RequirementStatus.Present,
            "cmake" to RequirementStatus.Present,
            "java-server" to RequirementStatus.Present,
        ),
        missingPackages = emptyList(),
        installCommand = null,
        javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
    )

    @Test
    fun `an unavailable package never completes the handoff`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val probe = FakeHostPrerequisiteProbe(missingReport)
        val (provisioner, planId) = startHandoff(repository, probe, dispatcher)

        // Everything installable is present, but one requirement has no apt candidate.
        probe.probeReport = allPresentReport.copy(
            results = allPresentReport.results + ("legacy" to RequirementStatus.Unavailable("24.04")),
        )
        val outcome = provisioner.resume(planId)

        assertTrue(outcome is SetupOutcome.Failed, "Unavailable must not be reported as success: $outcome")
        assertEquals(AttemptStatus.AWAITING_USER_ACTION, repository.currentState.activeAttempt?.status)
    }

    @Test
    fun `a package that regressed since the first probe blocks success and joins the command`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = createRepository(dispatcher)
        val probe = FakeHostPrerequisiteProbe(missingReport)
        val (provisioner, planId) = startHandoff(repository, probe, dispatcher)

        // The handoff's packages are installed now, but rsync (present at the first probe) is gone.
        probe.probeReport = allPresentReport.copy(
            results = allPresentReport.results + ("rsync" to RequirementStatus.Missing),
            missingPackages = listOf("rsync"),
            installCommand = "sudo apt-get update && sudo apt-get install -y rsync",
        )
        val outcome = provisioner.resume(planId)

        assertTrue(outcome is SetupOutcome.AwaitingUserAction, "A regressed package must keep the handoff: $outcome")
        assertEquals(listOf("rsync"), outcome.handoff.packages)
    }

    @Test
    fun `a journal write failure on a partial resume is not reported as a pending step`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = FailingWritesRepository(createRepository(dispatcher))
        val probe = FakeHostPrerequisiteProbe(missingReport)
        val (provisioner, planId) = startHandoff(repository, probe, dispatcher)

        probe.probeReport = missingReport.copy(
            results = missingReport.results + ("git" to RequirementStatus.Present),
            missingPackages = listOf("cmake", "openjdk-21-jdk"),
        )
        repository.failAwaiting = true
        val outcome = provisioner.resume(planId)

        assertTrue(outcome is SetupOutcome.Interrupted, "A lost journal write must surface: $outcome")
    }

    @Test
    fun `an archive failure on a complete resume is not reported as success`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = FailingWritesRepository(createRepository(dispatcher))
        val probe = FakeHostPrerequisiteProbe(missingReport)
        val (provisioner, planId) = startHandoff(repository, probe, dispatcher)

        probe.probeReport = allPresentReport
        repository.failTerminal = true
        val outcome = provisioner.resume(planId)

        assertTrue(outcome is SetupOutcome.Interrupted, "An unarchived attempt must not claim success: $outcome")
        val packagesStep = provisioner.state.value.steps.first { it.stage == SetupStepStage.SYSTEM_PACKAGES }
        assertFalse(
            packagesStep.status == StepStatus.SUCCESS && packagesStep.description == "Prerequisite packages installed",
        )
    }
}
