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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An old build service that is still running jobs defers the update. Setup must show that and stop there,
 * never continue (and report ready) against the old version.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServerUpdateWaitingTest {

    /** The new version is installed, but the old one keeps running because it has 2 active runs. */
    private class OldServiceStillBusy : ServerArtifactPort {
        private fun artifact(state: InstallState) = ServerArtifact(
            bundledVersion = "2.0.0",
            activeVersion = "2.0.0",
            runningVersion = "1.0.0",
            runningActiveRuns = 2,
            installState = state,
            javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
        )

        override suspend fun status(environment: SetupEnvironment) = artifact(InstallState.Outdated)
        override suspend fun ensureInstalled(environment: SetupEnvironment) = artifact(InstallState.Current)
        override suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String) =
            artifact(InstallState.Outdated)
    }

    @Test
    fun `a deferred update stops setup at the build service with the waiting state`() = runTest {
        val fakeCli = FakeWslCliExecutor()
        val transport = FakeRemoteTransport(fakeCli = fakeCli)
        val provisioner = WslToolchainProvisioner(
            supervisor = FakeDaemonSupervisor(),
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = WslEnvironmentDetector(cli = fakeCli),
            cli = fakeCli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            serverArtifactPort = OldServiceStillBusy(),
        )

        val state = provisioner.verifyEnvironment()

        val server = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.WARNING, server.status)
        assertTrue(server.description.startsWith("Update waiting"), server.description)
        assertTrue("2 active run" in server.description, server.description)
        // Nothing after the service runs against the old version, and the machine is not ready.
        val later = listOf(
            SetupStepStage.SYSTEM_DIAGNOSTICS,
            SetupStepStage.REPO_SYNCHRONIZATION,
            SetupStepStage.TOOLCHAIN_COMPILATION,
        )
        assertTrue(state.steps.filter { it.stage in later }.all { it.status == StepStatus.PENDING }, "${state.steps}")
        assertFalse(state.canLaunchWorkspace)
    }
}
