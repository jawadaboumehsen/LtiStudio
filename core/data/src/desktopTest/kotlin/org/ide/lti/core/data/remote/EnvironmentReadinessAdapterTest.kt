/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvironmentReadinessAdapterTest {

    private class FakeToolchainProvisioningService(
        var stateResult: ToolchainSetupState = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server",
                    description = "Connected · 1ms · pid 1234",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tools",
                    description = "10/10 tools · 10 published",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.REPO_SYNCHRONIZATION,
                    title = "Repos",
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    title = "Doctor",
                    description = "OK",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
            ),
            activeDistro = "Ubuntu",
            userHome = "/home/Ubuntu",
            workDirLinuxPath = "/home/Ubuntu/LtiRomWorkDir",
            publishedToolIds = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS,
        ),
    ) : ToolchainProvisioningService {
        private val _state = MutableStateFlow(stateResult)
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()
        override suspend fun checkStatus(): ToolchainSetupState {
            _state.value = stateResult
            return stateResult
        }
        override suspend fun verifyEnvironment(): ToolchainSetupState = checkStatus()
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan =
            error("Not implemented")
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome = error("Not implemented")
        override suspend fun resume(planId: String): SetupOutcome = error("Not implemented")
        override fun observe(): Flow<SetupOutcome?> = emptyFlow()
        override fun observeActivity(): Flow<SetupLogEvent> = emptyFlow()
        override suspend fun recover(attemptId: String?): SetupOutcome = error("Not implemented")
        override suspend fun cancel(): SetupOutcome = error("Not implemented")
        override suspend fun testTool(toolId: String): SetupOutcome = error("Not implemented")
        override suspend fun provisionAvbKey(): Boolean = true
    }

    private class FakeRemoteTransport : RemoteTransportPort {
        var customExecuteHandler: ((ToolExecutionRequest) -> ToolExecutionResponse)? = null

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse =
            customExecuteHandler?.invoke(request) ?: ToolExecutionResponse(
                exitCode = 0,
                stdout = "ok",
                stderr = "",
                durationMs = 5L,
            )

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun testEnvAbsentWhenWslDetectionFails() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "WSL 2 not detected or no distribution found.",
                        status = StepStatus.FAILED,
                        error = "WSL 2 is not installed.",
                    ),
                ),
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.ENV_ABSENT, readiness.state)
        assertNotNull(readiness.failingCheck)
        assertNotNull(readiness.remediation)
        assertEquals(readiness, adapter.observe().first())
    }

    @Test
    fun testServiceUnreachableWhenServerConnectivityFails() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "Daemon bridge failed: connection refused",
                        status = StepStatus.FAILED,
                        error = "connection refused",
                    ),
                ),
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, readiness.state)
        assertNotNull(readiness.failingCheck)
        assertNotNull(readiness.remediation)
    }

    @Test
    fun testServiceUnhealthyWhenDaemonReportsUnhealthy() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "Daemon bridge failed: Daemon reported unhealthy status: DOWN",
                        status = StepStatus.FAILED,
                        error = "Daemon reported unhealthy status: DOWN",
                        failureCategory = DaemonFailureCategory.UNHEALTHY_STATUS,
                    ),
                ),
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.SERVICE_UNHEALTHY, readiness.state)
        assertEquals("Check daemon logs and restart the daemon", readiness.remediation)
    }

    @Test
    fun testToolsIncompatibleWhenUnsupportedJava() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "Daemon bridge failed: Unsupported daemon Java runtime version: 11",
                        status = StepStatus.FAILED,
                        error = "Unsupported daemon Java runtime version: 11",
                        failureCategory = DaemonFailureCategory.UNSUPPORTED_JAVA,
                    ),
                ),
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.TOOLS_INCOMPATIBLE, readiness.state)
        assertTrue(readiness.failingCheck?.contains("Java") == true)
    }

    @Test
    fun testToolsMissingWhenToolsCompilationFails() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "Connected",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "Missing published tools: lpunpack",
                        status = StepStatus.FAILED,
                        error = "Required tools not published on daemon: lpunpack",
                    ),
                ),
                publishedToolIds = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS - "lpunpack",
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.TOOLS_MISSING, readiness.state)
        assertTrue(readiness.missingToolIds.contains("lpunpack"))
    }

    @Test
    fun testToolsIncompatibleWhenErofsFails() = runTest {
        val toolchain = FakeToolchainProvisioningService(
            stateResult = ToolchainSetupState(
                steps = listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "OK",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "Connected",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "mkfs.erofs incompatible",
                        status = StepStatus.FAILED,
                        error = "mkfs.erofs does not support --fs-config-file",
                        toolchainFailureCategory = ToolchainFailureCategory.EROFS_INCOMPATIBLE,
                    ),
                ),
            ),
        )
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.TOOLS_INCOMPATIBLE, readiness.state)
        assertTrue(readiness.failingCheck?.contains("mkfs.erofs") == true)
    }

    @Test
    fun testReadyWhenAllStepsPassLive() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)

        val readiness = adapter.refresh()
        assertEquals(EnvironmentReadinessState.READY, readiness.state)
        assertEquals("/home/Ubuntu/LtiRomWorkDir", readiness.workDirLinuxPath)
    }

    @Test
    fun testWorkspaceInvalidWhenWorkspaceLayoutMissing() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "test" && req.arguments.contains("-f")) {
                ToolExecutionResponse(exitCode = 1, stdout = "", stderr = "not found", durationMs = 5L)
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(toolchain, transport)
        val ws = Workspace(
            id = "test-ws",
            name = "Test Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/test-ws",
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.WORKSPACE_INVALID, readiness.state)
        assertNotNull(readiness.failingCheck)
    }

    @Test
    fun testWorkspaceInvalidWhenLayoutVersionDrift() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(toolchain, transport)
        val ws = Workspace(
            id = "drift-ws",
            name = "Drift Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/drift-ws",
            layoutVersion = 2,
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.WORKSPACE_INVALID, readiness.state)
        assertTrue(readiness.failingCheck?.contains("layout version drift") == true)
    }

    @Test
    fun testWorkspaceInvalidWhenKeysMissing() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "test" && req.arguments.contains("-f")) {
                val path = req.arguments.last()
                if (path.contains("keys/avb")) {
                    ToolExecutionResponse(exitCode = 1, stdout = "", stderr = "not found", durationMs = 5L)
                } else {
                    ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
                }
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(toolchain, transport)
        val ws = Workspace(
            id = "nokeys-ws",
            name = "No Keys Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/nokeys-ws",
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.WORKSPACE_INVALID, readiness.state)
        assertTrue(readiness.failingCheck?.contains("cryptographic signing keys") == true)
    }

    @Test
    fun testWorkspaceReadyWhenAllFilesAndKeysPresent() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        transport.customExecuteHandler = { req ->
            ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
        }

        val adapter = EnvironmentReadinessAdapter(toolchain, transport)
        val ws = Workspace(
            id = "valid-ws",
            name = "Valid Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/valid-ws",
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.READY, readiness.state)
    }

    @Test
    fun testEmptyRequiredInventoryFailsClosed() = runTest {
        val toolchain = FakeToolchainProvisioningService()
        val transport = FakeRemoteTransport()
        val adapter = EnvironmentReadinessAdapter(
            toolchainService = toolchain,
            transport = transport,
            requiredProductTools = emptySet(),
        )
        val readiness = adapter.refresh()

        assertFalse(readiness.state == EnvironmentReadinessState.READY, "Empty inventory must never be READY")
        assertEquals(EnvironmentReadinessState.TOOLS_MISSING, readiness.state)
    }
}
