package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ActivateToolchainRequest
import io.ltirom.tooling.core.remote.ActivateToolchainResponse
import io.ltirom.tooling.core.remote.ToolchainStateResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.FakeWslCliExecutor
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.InstallId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WslToolchainInstallationAdapterTest {

    private lateinit var cli: FakeWslCliExecutor
    private lateinit var installationManager: InstallationManager
    private lateinit var fakeTransport: FakeRemoteTransportPort
    private lateinit var adapter: WslToolchainInstallationAdapter

    @BeforeTest
    fun setup() {
        cli = FakeWslCliExecutor()
        cli.userHome = "/home/lti"
        installationManager = InstallationManager(cli, "Ubuntu")
        fakeTransport = FakeRemoteTransportPort()
        adapter = WslToolchainInstallationAdapter(
            installationManager = installationManager,
            transport = fakeTransport,
            cli = cli,
        )
    }

    @Test
    fun `activate committed outcome`() = runTest {
        fakeTransport.activateResponse = ActivateToolchainResponse(
            state = "COMMITTED",
            activeInstallId = "i-target-install-123",
        )

        val outcome = adapter.activate(
            ActivationRequest(
                requestId = "req-1",
                expectedActiveInstallId = InstallId("i-expected-123"),
                targetInstallId = InstallId("i-target-install-123"),
            ),
        )

        assertEquals(ActivationOutcome.Committed(InstallId("i-target-install-123")), outcome)
        assertEquals("req-1", fakeTransport.lastActivateRequest?.activationRequestId)
        assertEquals("i-expected-123", fakeTransport.lastActivateRequest?.expectedActiveInstallId)
        assertEquals("i-target-install-123", fakeTransport.lastActivateRequest?.targetInstallId)
    }

    @Test
    fun `activate blocked outcome`() = runTest {
        fakeTransport.activateResponse = ActivateToolchainResponse(
            code = "BLOCKED",
            activeWork = 3,
        )

        val outcome = adapter.activate(
            ActivationRequest(
                requestId = "req-2",
                expectedActiveInstallId = null,
                targetInstallId = InstallId("i-target-123"),
            ),
        )

        assertEquals(ActivationOutcome.Blocked(3), outcome)
    }

    @Test
    fun `activate conflict outcome`() = runTest {
        fakeTransport.activateResponse = ActivateToolchainResponse(
            code = "CONFLICT",
            actual = "i-actual-active-999",
        )

        val outcome = adapter.activate(
            ActivationRequest(
                requestId = "req-3",
                expectedActiveInstallId = InstallId("i-stale-111"),
                targetInstallId = InstallId("i-target-123"),
            ),
        )

        assertEquals(ActivationOutcome.Conflict(InstallId("i-actual-active-999")), outcome)
    }

    private class FakeRemoteTransportPort : RemoteTransportPort {
        var toolchainStateResponse: ToolchainStateResponse? = null
        var activateResponse: ActivateToolchainResponse? = null
        var lastActivateRequest: ActivateToolchainRequest? = null
        var throwOnActivate: Throwable? = null
        val activationRecords = mutableMapOf<String, String>()

        override suspend fun getToolchainState(): ToolchainStateResponse? = toolchainStateResponse

        override suspend fun activateToolchain(request: ActivateToolchainRequest): ActivateToolchainResponse {
            lastActivateRequest = request
            throwOnActivate?.let { throw it }
            return activateResponse ?: error("activateResponse not set")
        }

        override suspend fun getActivation(requestId: String): String? = activationRecords[requestId]

        override suspend fun execute(
            request: io.ltirom.tooling.core.remote.ToolExecutionRequest,
        ): io.ltirom.tooling.core.remote.ToolExecutionResponse = error("unused")

        override fun stream(
            request: io.ltirom.tooling.core.remote.ToolExecutionRequest,
        ): Flow<io.ltirom.tooling.core.remote.StreamEvent> = emptyFlow()
        override suspend fun checkHealth(): io.ltirom.tooling.core.remote.WslServerInfo? = null
        override suspend fun listTools(): io.ltirom.tooling.core.remote.ToolListResult = error("unused")
        override suspend fun refreshTools(): io.ltirom.tooling.core.remote.ToolListResult = error("unused")
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = false
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }
}
