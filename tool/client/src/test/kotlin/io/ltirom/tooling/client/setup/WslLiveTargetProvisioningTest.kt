package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import kotlin.test.Test
import kotlin.test.assertTrue

class WslLiveTargetProvisioningTest {

    private class NoopDaemonSupervisor : DaemonSupervisorPort {
        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = null
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true
        override suspend fun ensureStarted(): ServerConnectionDescriptor = throw UnsupportedOperationException()
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class NoopRemoteTransport : RemoteTransportPort {
        override suspend fun checkHealth(): WslServerInfo? = null
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse = throw UnsupportedOperationException()
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun listTools(): List<ToolStatusInfo> = emptyList()
        override suspend fun refreshTools(): List<ToolStatusInfo> = emptyList()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = false
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun testLiveProvisioningBothRegionsInRealWsl() = runBlocking {
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli = cli)
        if (!detector.isWslInstalled()) {
            println("WSL not installed, skipping live test")
            return@runBlocking
        }

        val provisioner = WslToolchainProvisioner(
            supervisor = NoopDaemonSupervisor(),
            transport = NoopRemoteTransport(),
            detector = detector,
            cli = cli,
            dispatcher = Dispatchers.IO
        )

        val globalTarget = TargetDevice(
            id = "PQ84P01_GLOBAL",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            selectedRegion = TargetRegion.GLOBAL,
            selectedFirmware = TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
                isOfficial = true,
                isRecommended = true,
            ),
            socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
            filesystemType = "erofs",
            dynamicPartitions = listOf("system", "vendor", "product", "system_ext", "odm", "vendor_dlkm", "system_dlkm"),
            bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            status = TargetStatus.QUALIFIED,
            description = "Official Global & European retail baseline (NP05J_GB)",
            isDefault = true,
        )

        val chinaTarget = TargetDevice(
            id = "PQ84P01_CN",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            selectedRegion = TargetRegion.CHINA,
            selectedFirmware = TargetFirmware(
                version = "REDMAGICOS10.5.16_NP05J_CN",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.16_NP05J_CN/update.zip",
                isOfficial = true,
                isRecommended = true,
            ),
            socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
            filesystemType = "erofs",
            dynamicPartitions = listOf("system", "vendor", "product", "system_ext", "odm", "vendor_dlkm", "system_dlkm"),
            bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            status = TargetStatus.QUALIFIED,
            description = "Official China mainland retail baseline (NP05J_CN)",
            isDefault = false,
        )

        val globalSuccess = provisioner.provisionTargetWorkspace(globalTarget)
        assertTrue(globalSuccess, "Global target provisioning failed")

        val chinaSuccess = provisioner.provisionTargetWorkspace(chinaTarget)
        assertTrue(chinaSuccess, "China target provisioning failed")
    }
}
