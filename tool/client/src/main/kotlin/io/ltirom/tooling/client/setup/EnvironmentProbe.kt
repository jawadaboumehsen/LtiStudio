package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.ports.DaemonProbeStatus
import org.ide.lti.core.domain.ports.EnvironmentProbePort
import org.ide.lti.core.domain.ports.EnvironmentProbeResult
import org.ide.lti.core.domain.ports.ToolPublicationPort

/**
 * Transport-backed implementation of [EnvironmentProbePort].
 *
 * Direct WSL CLI interaction is limited to host bootstrap (detect WSL, default distro, resolve home,
 * start daemon). All subsequent probes execute strictly over [RemoteTransportPort].
 */
public class EnvironmentProbe(
    private val detector: WslEnvironmentDetector,
    private val supervisor: DaemonSupervisorPort,
    private val transport: RemoteTransportPort,
    private val toolPublicationPort: ToolPublicationPort? = null,
    private val expectedProductTools: Set<String> = DEFAULT_EXPECTED_PRODUCT_TOOLS,
) : EnvironmentProbePort {

    public companion object {
        public val DEFAULT_EXPECTED_PRODUCT_TOOLS: Set<String> = setOf(
            "payload-dumper-go",
            "lpunpack",
            "lpmake",
            "simg2img",
            "erofsfuse",
            "mkfs.erofs",
            "avbtool",
            "unpack_bootimg",
            "img2sdat",
            "signapk",
        )
    }

    override suspend fun probe(): EnvironmentProbeResult {
        if (!detector.isWslInstalled()) {
            return absentWslResult()
        }
        return probeRunningEnvironment()
    }

    private fun absentWslResult(): EnvironmentProbeResult = EnvironmentProbeResult(
        distro = null,
        home = null,
        daemon = DaemonProbeStatus.Failed(
            reason = "WSL 2 is not installed or has no distributions",
            logTail = null,
        ),
        pingMs = null,
        executables = emptySet(),
        publishedTools = emptySet(),
        workDirExists = false,
        submodules = emptySet(),
        avbKeyPresent = false,
    )

    private suspend fun probeRunningEnvironment(): EnvironmentProbeResult {
        val distro = detector.getDefaultDistro() ?: "Ubuntu"
        val home = detector.resolveWslUserHome(distro)

        return try {
            val startMs = System.currentTimeMillis()
            val desc = supervisor.ensureStarted()
            val health = transport.checkHealth()
            val pingMs = if (health != null) (System.currentTimeMillis() - startMs).coerceAtLeast(1L) else null

            val daemonStatus = if (supervisor.lastStartAdopted) {
                DaemonProbeStatus.Adopted(pid = desc.pid, port = desc.port, host = desc.host)
            } else {
                DaemonProbeStatus.Launched(pid = desc.pid, port = desc.port, host = desc.host)
            }

            val binDir = "$home/LtiRomTools/bin"
            val findBin = transport.execute(
                ToolExecutionRequest(
                    toolId = "find",
                    arguments = listOf(
                        "-L", binDir, "-maxdepth", "1", "-type", "f", "-perm", "-u+x", "-printf", "%f\n"
                    ),
                    workingDirectory = "/",
                )
            )
            val executables = if (findBin.exitCode == 0) {
                findBin.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }

            var publishedTools = transport.listTools()
                .filter { it.installed }
                .map { it.tool }
                .toSet()

            val missingProductTools = expectedProductTools - publishedTools
            if (missingProductTools.isNotEmpty() && executables.any { it in expectedProductTools }) {
                toolPublicationPort?.publish(binDir)
                publishedTools = transport.listTools()
                    .filter { it.installed }
                    .map { it.tool }
                    .toSet()
            }

            val extDir = "$home/LtiRomTools/external"
            val findExt = transport.execute(
                ToolExecutionRequest(
                    toolId = "find",
                    arguments = listOf(extDir, "-maxdepth", "1", "-mindepth", "1", "-type", "d", "-printf", "%f\n"),
                    workingDirectory = "/",
                )
            )
            val submodules = if (findExt.exitCode == 0) {
                findExt.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }

            val workDir = "$home/LtiRomWorkDir"
            val testWorkDir = transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf("-d", workDir),
                    workingDirectory = "/",
                )
            )
            val workDirExists = testWorkDir.exitCode == 0

            val testAvb = transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf("-f", "$workDir/security/avb/lti_rsa4096.pem"),
                    workingDirectory = "/",
                )
            )
            val avbKeyPresent = testAvb.exitCode == 0

            EnvironmentProbeResult(
                distro = distro,
                home = home,
                daemon = daemonStatus,
                pingMs = pingMs,
                executables = executables,
                publishedTools = publishedTools,
                workDirExists = workDirExists,
                submodules = submodules,
                avbKeyPresent = avbKeyPresent,
            )
        } catch (e: Exception) {
            val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
            EnvironmentProbeResult(
                distro = distro,
                home = home,
                daemon = DaemonProbeStatus.Failed(
                    reason = e.message ?: "Failed to start or connect to LtiRomServer daemon",
                    logTail = logTail,
                ),
                pingMs = null,
                executables = emptySet(),
                publishedTools = emptySet(),
                workDirExists = false,
                submodules = emptySet(),
                avbKeyPresent = false,
            )
        }
    }
}
