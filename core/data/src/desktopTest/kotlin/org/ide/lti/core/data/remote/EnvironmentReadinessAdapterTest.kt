package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnvironmentReadinessAdapterTest {

    private class FakeWslPresence(
        var isInstalled: Boolean = true,
        var hasDistroResult: Boolean = true,
        var defaultDistroResult: String? = "Ubuntu"
    ) : WslPresencePort {
        override suspend fun isWslInstalled(): Boolean = isInstalled
        override suspend fun hasDistro(): Boolean = hasDistroResult
        override suspend fun getDefaultDistro(): String? = defaultDistroResult
    }

    private class FakeDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor(
            host = "127.0.0.1",
            port = 9090,
            token = "token",
            pid = 1234L,
            distro = "Ubuntu"
        ),
        var shouldThrowOnEnsure: Boolean = false
    ) : DaemonSupervisorPort {
        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            check(!shouldThrowOnEnsure) { "Daemon start failed" }
            return checkNotNull(descriptor) { "No descriptor" }
        }

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class FakeRemoteTransport : RemoteTransportPort {
        var toolsList = listOf<ToolStatusInfo>()
        var executedCommands = mutableListOf<ToolExecutionRequest>()
        var customExecuteHandler: ((ToolExecutionRequest) -> ToolExecutionResponse)? = null

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedCommands.add(request)
            return customExecuteHandler?.invoke(request) ?: ToolExecutionResponse(
                exitCode = 0,
                stdout = "/usr/bin/${request.toolId}",
                stderr = "",
                durationMs = 5L
            )
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun listTools(): List<ToolStatusInfo> = toolsList
        override suspend fun refreshTools(): List<ToolStatusInfo> = toolsList
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private val allStandardProductTools = setOf(
        "payload-dumper-go", "lpunpack", "lpmake", "simg2img", "erofsfuse",
        "mkfs.erofs", "avbtool", "unpack_bootimg", "img2sdat", "signapk"
    )

    private fun healthyToolsList(): List<ToolStatusInfo> {
        return allStandardProductTools.map {
            ToolStatusInfo(tool = it, installed = true, path = "/tools/$it")
        }
    }

    @Test
    fun testEnvAbsentWhenNoDistro() = runTest {
        val wsl = FakeWslPresence(hasDistroResult = false)
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.ENV_ABSENT, readiness.state)
        assertNotNull(readiness.failingCheck)
        assertNotNull(readiness.remediation)
        assertEquals(readiness, adapter.observe().first())
    }

    @Test
    fun testServiceUnreachableWhenEnsureStartedFails() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor(shouldThrowOnEnsure = true)
        val transport = FakeRemoteTransport()

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.SERVICE_UNREACHABLE, readiness.state)
        assertNotNull(readiness.failingCheck)
        assertNotNull(readiness.remediation)
    }

    @Test
    fun testToolsMissingWhenRequiredToolsNotInListTools() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = listOf(
            ToolStatusInfo(tool = "lpmake", installed = true),
            ToolStatusInfo(tool = "signapk", installed = true)
        )

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.TOOLS_MISSING, readiness.state)
        assertTrue(readiness.missingToolIds.contains("erofsfuse"))
        assertTrue(readiness.missingToolIds.contains("avbtool"))
        assertNotNull(readiness.failingCheck)
    }

    @Test
    fun testToolsMissingWhenHostPackageMissing() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "which" && req.arguments.contains("brotli")) {
                ToolExecutionResponse(exitCode = 1, stdout = "", stderr = "not found", durationMs = 5L)
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.TOOLS_MISSING, readiness.state)
        assertTrue(readiness.missingToolIds.contains("brotli"))
    }

    @Test
    fun testToolsIncompatibleWhenToolFailsCapabilityCheck() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(
                    exitCode = 0,
                    stdout = "mkfs.erofs 1.7.1 without fs-config",
                    stderr = "",
                    durationMs = 5L
                )
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "--fs-config-file", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.TOOLS_INCOMPATIBLE, readiness.state)
        assertNotNull(readiness.failingCheck)
        assertTrue(readiness.failingCheck!!.contains("mkfs.erofs"))
    }

    @Test
    fun testReadyWhenAllChecksPass() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(
                    exitCode = 0,
                    stdout = "Usage: mkfs.erofs --fs-config-file",
                    stderr = "",
                    durationMs = 5L
                )
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "/usr/bin/tool", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val readiness = adapter.refresh()

        assertEquals(EnvironmentReadinessState.READY, readiness.state)
    }

    @Test
    fun testWorkspaceInvalidWhenWorkspaceLayoutMissing() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(exitCode = 0, stdout = "--fs-config-file", stderr = "", durationMs = 5L)
            } else if (req.toolId == "test" && req.arguments.contains("-f")) {
                // Fail workspace manifest check
                ToolExecutionResponse(exitCode = 1, stdout = "", stderr = "not found", durationMs = 5L)
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val ws = Workspace(
            id = "test-ws",
            name = "Test Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/test-ws"
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.WORKSPACE_INVALID, readiness.state)
        assertNotNull(readiness.failingCheck)
    }

    @Test
    fun testWorkspaceInvalidWhenLayoutVersionDrift() = runTest {
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(exitCode = 0, stdout = "--fs-config-file", stderr = "", durationMs = 5L)
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
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
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(exitCode = 0, stdout = "--fs-config-file", stderr = "", durationMs = 5L)
            } else if (req.toolId == "test" && req.arguments.contains("-f")) {
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

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
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
        val wsl = FakeWslPresence()
        val supervisor = FakeDaemonSupervisor()
        val transport = FakeRemoteTransport()
        transport.toolsList = healthyToolsList()
        transport.customExecuteHandler = { req ->
            if (req.toolId == "mkfs.erofs" && req.arguments.contains("--help")) {
                ToolExecutionResponse(exitCode = 0, stdout = "--fs-config-file", stderr = "", durationMs = 5L)
            } else if (req.toolId == "test" && req.arguments.contains("-f")) {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            } else {
                ToolExecutionResponse(exitCode = 0, stdout = "ok", stderr = "", durationMs = 5L)
            }
        }

        val adapter = EnvironmentReadinessAdapter(wsl, supervisor, transport)
        val ws = Workspace(
            id = "valid-ws",
            name = "Valid Workspace",
            path = "C:\\test",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/valid-ws",
        )
        val readiness = adapter.forWorkspace(ws)

        assertEquals(EnvironmentReadinessState.READY, readiness.state)
    }
}
