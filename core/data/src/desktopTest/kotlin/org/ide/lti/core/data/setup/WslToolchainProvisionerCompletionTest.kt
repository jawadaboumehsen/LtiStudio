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

import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.remote.EnvironmentReadinessAdapter
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WslToolchainProvisionerCompletionTest {

    @Test
    fun `FULL_SETUP follow-up failure when workspace cannot launch yields Failed`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        transport.publishedTools.removeIf { it.tool == "payload-dumper-go" }

        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(
            outcome is SetupOutcome.Failed,
            "Expected Failed outcome when canLaunchWorkspace is false, got $outcome",
        )
        assertEquals(SetupStepStage.TOOLCHAIN_COMPILATION.name, outcome.stage)
    }

    @Test
    fun `STAGE_RETRY succeeds when targeted step is SUCCESS even if overall is not ready`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        transport.publishedTools.clear()
        val plan = provisioner.prepare(
            kind = SetupPlanKind.STAGE_RETRY,
            targetId = SetupStepStage.REPO_SYNCHRONIZATION.name,
        )
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "Target stage succeeded so STAGE_RETRY must succeed: $outcome")
    }

    @Test
    fun `cancellation during active run yields Cancelled, never Failed`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { missingDevPackages = true }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val repo = inMemoryToolchainRepository()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repo,
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val pauseOutcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(pauseOutcome is SetupOutcome.AwaitingUserAction)

        val cancelOutcome = provisioner.cancel()
        assertTrue(cancelOutcome is SetupOutcome.Cancelled, "Expected Cancelled, got $cancelOutcome")
    }

    @Test
    fun `service unreachable during check fails SERVER_CONNECTIVITY without reporting missing tools`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val fakePublication = object : ToolPublicationPort {
            override suspend fun publish(binDir: String, toolIds: Set<String>) = PublicationReport(
                requested = toolIds,
                registered = toolIds,
                failed = emptyMap(),
                pruned = emptySet(),
            )
            override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.ServiceError("Daemon offline")
        }

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
            toolPublicationPort = fakePublication,
        )

        val state = provisioner.verifyEnvironment()

        val serverStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, serverStep.status)
        assertEquals(DaemonFailureCategory.UNREACHABLE, serverStep.failureCategory)

        val toolchainStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertFalse(toolchainStep.description.contains("Missing published tools"))
        assertEquals(StepStatus.PENDING, toolchainStep.status)

        val readinessAdapter = EnvironmentReadinessAdapter(provisioner, transport)
        val readiness = readinessAdapter.refresh()
        assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, readiness.state)
    }

    @Test
    fun `verify run emits CHECK start and result events with duration in ms, and ERROR on failure`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor().apply { missingDevPackages = true }
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        val events = mutableListOf<SetupLogEvent>()
        val collectJob = launch(testDispatcher) {
            provisioner.observeActivity().collect { events.add(it) }
        }

        provisioner.verifyEnvironment()
        collectJob.cancel()

        val checkEvents = events.filter { it.kind == SetupLogKind.CHECK }
        assertTrue(checkEvents.isNotEmpty(), "Must emit CHECK events")
        assertTrue(checkEvents.any { it.text.startsWith("[check] WSL 2 Runtime: started") })
        assertTrue(checkEvents.any { it.text.contains("ms)") }, "Result line must include duration in ms")

        val errorEvents = events.filter { it.kind == SetupLogKind.ERROR }
        assertTrue(errorEvents.isNotEmpty(), "Failures must emit ERROR kind event")
    }

    @Test
    fun `prerequisite probe issues exactly one wsl execution per check`() = runTest {
        val fakeCli = FakeWslCliExecutor()
        val probe = WslHostPrerequisiteProbe(cli = fakeCli)
        val initialExecutions = fakeCli.commandLog.size

        val env = SetupEnvironment(
            distro = "Ubuntu",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "lti",
            home = "/home/lti",
        )
        probe.probe(env)

        val probeExecutions = fakeCli.commandLog.size - initialExecutions
        assertEquals(1, probeExecutions, "Prerequisite probe must issue exactly one wsl execution")
    }

    @Test
    fun `tolerated WARNING still counts as ready for completion`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        provisioner.verifyEnvironment()
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(
            outcome is SetupOutcome.Succeeded,
            "Full setup must succeed when all steps are SUCCESS or WARNING: $outcome",
        )
    }

    @Test
    fun `step progress API emits formatted description and startedAt`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val fakeCli = FakeWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = fakeCli)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = fakeCli,
            dispatcher = testDispatcher,
        )

        provisioner.setStepProgress(SetupStepStage.SYSTEM_DIAGNOSTICS, "Dual JDK", 3, 8)
        var state = provisioner.state.value
        val doctorStep = state.steps.first { it.stage == SetupStepStage.SYSTEM_DIAGNOSTICS }
        assertEquals("Checking 3 of 8: Dual JDK", doctorStep.description)
        assertEquals("Checking 3 of 8: Dual JDK", state.activeLogLine)
        val startedAt = doctorStep.startedAt
        assertTrue(startedAt != null && startedAt > 0)

        provisioner.setStepProgress(SetupStepStage.REPO_SYNCHRONIZATION, "android-tools", 1, 2)
        state = provisioner.state.value
        var syncStep = state.steps.first { it.stage == SetupStepStage.REPO_SYNCHRONIZATION }
        assertEquals("Syncing android-tools (1 of 2)", syncStep.description)

        provisioner.setStepProgress(SetupStepStage.REPO_SYNCHRONIZATION, "erofs-utils", 2, 2)
        state = provisioner.state.value
        syncStep = state.steps.first { it.stage == SetupStepStage.REPO_SYNCHRONIZATION }
        assertEquals("Syncing erofs-utils (2 of 2)", syncStep.description)

        provisioner.setStepProgress(SetupStepStage.TOOLCHAIN_COMPILATION, "adb", 1, 2)
        state = provisioner.state.value
        var compileStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals("Building adb (1 of 2)", compileStep.description)

        provisioner.setStepProgress(SetupStepStage.TOOLCHAIN_COMPILATION, "fastboot", 2, 2)
        state = provisioner.state.value
        compileStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals("Building fastboot (2 of 2)", compileStep.description)
    }
}
