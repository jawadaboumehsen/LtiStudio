/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ServicePreparationTest {

    private val testEnv = SetupEnvironment(
        distro = "Ubuntu-24.04",
        wslVersion = 2,
        osId = "ubuntu",
        osVersionId = "24.04",
        user = "testuser",
        home = "/home/testuser",
    )

    private class FakeHostPrerequisitePort(
        var statuses: List<DistroStatus> = emptyList(),
        var probeReport: PrerequisiteReport? = null,
    ) : HostPrerequisitePort {
        override suspend fun detect(): List<DistroStatus> = statuses
        override suspend fun selectEnvironment(statuses: List<DistroStatus>): SetupEnvironment? =
            statuses.filterIsInstance<DistroStatus.Usable>().firstOrNull()?.environment
        override suspend fun probe(environment: SetupEnvironment): PrerequisiteReport =
            probeReport ?: PrerequisiteReport(
                environment = environment,
                results = emptyMap(),
                packageListsFresh = true,
                missingPackages = emptyList(),
                installCommand = null,
                javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
                checkedAt = 1000L,
            )
    }

    private class FakeDaemonSupervisor(
        var started: Boolean = false,
        var launchError: String? = null,
        var pid: Long = 1234L,
    ) : DaemonSupervisorPort {
        override val lastLaunchErrorLogTail: String? get() = launchError
        override var lastStartAdopted: Boolean = false

        override suspend fun ensureStarted(): ServerConnectionDescriptor = ensureStarted("Ubuntu")

        override suspend fun ensureStarted(distro: String): ServerConnectionDescriptor {
            if (launchError != null) error(launchError!!)
            started = true
            return ServerConnectionDescriptor("127.0.0.1", 8080, "test-token", pid, distro)
        }

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = if (started) {
            ServerConnectionDescriptor("127.0.0.1", 8080, "test-token", pid, "Ubuntu")
        } else {
            null
        }

        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = started

        override suspend fun shutdownDaemon(): Boolean {
            started = false
            return true
        }

        override fun close() {}
    }

    private class FakeTransport(
        var serverInfo: WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 50_000L,
            availableTools = listOf("adb", "fastboot"),
        ),
    ) : RemoteTransportPort {
        override suspend fun checkHealth(): WslServerInfo? = serverInfo
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            ToolExecutionResponse(0, "ok", "", 10L)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private class FakeServerArtifactPort(
        var installState: InstallState = InstallState.Current,
        var runningVersion: String? = "1.0.0",
        var activeVersion: String? = "1.0.0",
        var runningActiveRuns: Int = 0,
    ) : ServerArtifactPort {
        private fun artifact() = ServerArtifact(
            bundledVersion = "1.0.0",
            activeVersion = activeVersion,
            runningVersion = runningVersion,
            runningActiveRuns = runningActiveRuns,
            installState = installState,
            javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
        )

        override suspend fun status(environment: SetupEnvironment): ServerArtifact = artifact()
        override suspend fun ensureInstalled(environment: SetupEnvironment): ServerArtifact = artifact()
        override suspend fun ensureRunningCurrent(environment: SetupEnvironment, javaHome: String): ServerArtifact =
            artifact()
    }

    @Test
    fun verifyWslSucceedsWhenUsableDistroPresent() = runTest {
        val hostPort = FakeHostPrerequisitePort(
            statuses = listOf(DistroStatus.Usable(testEnv)),
        )
        val prep = ServicePreparation(
            hostPrerequisitePort = hostPort,
            supervisor = FakeDaemonSupervisor(),
            transport = FakeTransport(),
        )

        val result = prep.verifyWsl(
            preferredDistro = null,
            persistedDistro = null,
            currentEnvironment = null,
            alreadyVerifiedWsl = false,
        )

        val success = assertIs<WslPreparationResult.Success>(result)
        assertEquals("Ubuntu-24.04", success.environment.distro)
        assertTrue(success.installedDistros.contains("Ubuntu-24.04"))
    }

    @Test
    fun verifyWslFailsWhenNoDistroInstalled() = runTest {
        val hostPort = FakeHostPrerequisitePort(
            statuses = listOf(DistroStatus.NoDistro),
        )
        val prep = ServicePreparation(
            hostPrerequisitePort = hostPort,
            supervisor = FakeDaemonSupervisor(),
            transport = FakeTransport(),
        )

        val result = prep.verifyWsl(
            preferredDistro = null,
            persistedDistro = null,
            currentEnvironment = null,
            alreadyVerifiedWsl = false,
        )

        val failure = assertIs<WslPreparationResult.Failure>(result)
        assertEquals("No Linux distribution", failure.description)
    }

    @Test
    fun verifySystemPackagesReportsMissingPackages() = runTest {
        val report = PrerequisiteReport(
            environment = testEnv,
            results = mapOf("git" to RequirementStatus.Missing),
            packageListsFresh = true,
            missingPackages = listOf("git"),
            installCommand = "sudo apt-get install -y git",
            javaHome = "/usr/lib/jvm/java-21-openjdk-amd64",
            checkedAt = 1000L,
        )
        val prep = ServicePreparation(
            hostPrerequisitePort = FakeHostPrerequisitePort(probeReport = report),
            supervisor = FakeDaemonSupervisor(),
            transport = FakeTransport(),
        )

        val result = prep.verifySystemPackages(testEnv)
        val failure = assertIs<PackagesPreparationResult.Failure>(result)
        assertTrue(failure.description.contains("1 package(s) missing"))
        assertEquals("sudo apt-get install -y git", failure.error)
    }

    @Test
    fun verifyServerStartsBundledServerAndChecksHealth() = runTest {
        val artifactPort = FakeServerArtifactPort()
        val supervisor = FakeDaemonSupervisor(started = true)
        val prep = ServicePreparation(
            hostPrerequisitePort = FakeHostPrerequisitePort(),
            supervisor = supervisor,
            transport = FakeTransport(),
            serverArtifactPort = artifactPort,
        )

        val result = prep.verifyServer(testEnv, "/usr/lib/jvm/java-21-openjdk-amd64")
        val success = assertIs<ServerPreparationResult.Success>(result)
        assertEquals(1234L, success.connPid)
    }

    @Test
    fun verifyServerReportsOutdatedWhenRunsActive() = runTest {
        val artifactPort = FakeServerArtifactPort(
            installState = InstallState.Outdated,
            runningActiveRuns = 2,
        )
        val supervisor = FakeDaemonSupervisor(started = true)
        val prep = ServicePreparation(
            hostPrerequisitePort = FakeHostPrerequisitePort(),
            supervisor = supervisor,
            transport = FakeTransport(),
            serverArtifactPort = artifactPort,
        )

        val result = prep.verifyServer(testEnv, "/usr/lib/jvm/java-21-openjdk-amd64")
        val outdated = assertIs<ServerPreparationResult.Outdated>(result)
        assertEquals(2, outdated.runningActiveRuns)
    }
}
