package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.domain.ports.DaemonProbeStatus
import org.ide.lti.core.domain.ports.EnvironmentProbePort
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.Workspace

/**
 * Adapter implementing [EnvironmentReadinessPort] by querying WSL status, the daemon, and remote tool availability.
 */
public class EnvironmentReadinessAdapter(
    private val wslPresence: WslPresencePort,
    private val supervisor: DaemonSupervisorPort,
    private val transport: RemoteTransportPort,
    private val probePort: EnvironmentProbePort? = null,
    private val requiredProductTools: Set<String> = DEFAULT_REQUIRED_PRODUCT_TOOLS,
    private val requiredHostPackages: Set<String> = DEFAULT_REQUIRED_HOST_PACKAGES
) : EnvironmentReadinessPort {

    public companion object {
        public const val WORK_DIR_NAME: String = "LtiRomWorkDir"
        public val DEFAULT_REQUIRED_PRODUCT_TOOLS: Set<String> = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS
        public val DEFAULT_REQUIRED_HOST_PACKAGES: Set<String> = ToolchainReadinessPolicy.REQUIRED_HOST_PACKAGES
    }

    private val _readiness = MutableStateFlow(
        EnvironmentReadiness(state = EnvironmentReadinessState.ENV_ABSENT)
    )

    override fun observe(): Flow<EnvironmentReadiness> = _readiness.asStateFlow()

    override suspend fun refresh(): EnvironmentReadiness {
        val result = evaluateEnvironment()
        _readiness.value = result
        return result
    }

    override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness {
        val env = refresh()
        if (env.state != EnvironmentReadinessState.READY) {
            return env
        }

        return validateWorkspace(workspace)
    }

    private suspend fun validateWorkspace(workspace: Workspace): EnvironmentReadiness {
        val linuxPath = workspace.linuxPath
        if (linuxPath.isNullOrBlank()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Workspace Linux path is missing",
                remediation = "Specify a valid Linux path for the workspace"
            )
        }

        if (workspace.layoutVersion != 1) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Workspace layout version drift: expected 1, found ${workspace.layoutVersion}",
                remediation = "Upgrade or re-provision the workspace"
            )
        }

        if (!hasFile(linuxPath, "workspace.json")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing workspace manifest at $linuxPath/workspace.json",
                remediation = "Re-provision workspace for target"
            )
        }

        val manifestContent = readFileContent(linuxPath, "workspace.json")
        if (manifestContent != null) {
            val match = Regex("\"layoutVersion\"\\s*:\\s*(\\d+)").find(manifestContent)
            if (match != null && match.groupValues[1] != "1") {
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.WORKSPACE_INVALID,
                    failingCheck = "Workspace manifest layoutVersion drift: expected 1, found ${match.groupValues[1]}",
                    remediation = "Upgrade or re-provision the workspace"
                )
            }
        }

        if (!hasFile(linuxPath, "config.json")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing configuration snapshot at $linuxPath/config.json",
                remediation = "Save a valid configuration snapshot"
            )
        }

        if (!hasFile(linuxPath, "keys/avb.pem") && !hasFile(linuxPath, "keys/avb.avbpubkey")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing cryptographic signing keys at $linuxPath/keys",
                remediation = "Provision signing keys for workspace"
            )
        }

        return EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    }

    private suspend fun readFileContent(linuxPath: String, fileName: String): String? {
        val res = transport.execute(
            ToolExecutionRequest(
                toolId = "cat",
                arguments = listOf("$linuxPath/$fileName"),
                workingDirectory = "/"
            )
        )
        return if (res.exitCode == 0) res.stdout else null
    }

    private suspend fun hasFile(linuxPath: String, fileName: String): Boolean {
        val check = transport.execute(
            ToolExecutionRequest(
                toolId = "test",
                arguments = listOf("-f", "$linuxPath/$fileName"),
                workingDirectory = "/"
            )
        )
        return check.exitCode == 0
    }

    private suspend fun evaluateEnvironment(): EnvironmentReadiness {
        if (requiredProductTools.isEmpty()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_MISSING,
                failingCheck = "Required product tools inventory is empty",
                remediation = "Configure required product tools inventory"
            )
        }

        if (probePort != null) {
            val probeResult = probePort.probe()
            if (probeResult.distro == null) {
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.ENV_ABSENT,
                    failingCheck = "WSL 2 distribution not found",
                    remediation = "Install WSL 2 and register an Ubuntu distribution"
                )
            }
            if (probeResult.daemon is DaemonProbeStatus.Failed) {
                val reason = (probeResult.daemon as DaemonProbeStatus.Failed).reason
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                    failingCheck = "LtiRomServer daemon unreachable: $reason",
                    remediation = "Start the LtiRomServer daemon or verify WSL connectivity"
                )
            }
            val missing = requiredProductTools - probeResult.publishedTools
            if (missing.isNotEmpty()) {
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.TOOLS_MISSING,
                    failingCheck = "Required tools missing: ${missing.sorted().joinToString(", ")}",
                    remediation = "Run toolchain setup to compile and install missing tools",
                    missingToolIds = missing
                )
            }
            val erofsCheck = transport.execute(
                ToolExecutionRequest(
                    toolId = "mkfs.erofs",
                    arguments = listOf("--help"),
                    workingDirectory = "/"
                )
            )
            if (erofsCheck.exitCode != 0 || !erofsCheck.stdout.contains(ToolchainReadinessPolicy.EROFS_REQUIRED_FLAG)) {
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                    failingCheck = "mkfs.erofs lacks required ${ToolchainReadinessPolicy.EROFS_REQUIRED_FLAG} support",
                    remediation = "Rebuild toolchain with vendored erofs-utils"
                )
            }
            val workDir = probeResult.home?.let { "$it/$WORK_DIR_NAME" }
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.READY,
                workDirLinuxPath = workDir,
            )
        }
        val failure = checkDistroAndDaemon() ?: checkToolsAndCompatibility()
        return failure ?: EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = resolveWorkDir(),
        )
    }

    /** Resolves `$HOME/LtiRomWorkDir` on the service host via coreutils; null when HOME is unknown. */
    private suspend fun resolveWorkDir(): String? {
        val res = transport.execute(
            ToolExecutionRequest(
                toolId = "printenv",
                arguments = listOf("HOME"),
                workingDirectory = "/"
            )
        )
        val home = res.stdout.trim()
        return if (res.exitCode == 0 && home.startsWith("/")) "$home/$WORK_DIR_NAME" else null
    }

    private suspend fun checkDistroAndDaemon(): EnvironmentReadiness? {
        if (!wslPresence.hasDistro()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL 2 distribution not found",
                remediation = "Install WSL 2 and register an Ubuntu distribution"
            )
        }
        return evaluateDaemonHealth()
    }

    private suspend fun evaluateDaemonHealth(): EnvironmentReadiness? {
        val started = runCatching { supervisor.ensureStarted() }
        if (started.isFailure) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                failingCheck = "LtiRomServer daemon unreachable: ${started.exceptionOrNull()?.message}",
                remediation = "Start the LtiRomServer daemon or verify WSL connectivity"
            )
        }

        val health = transport.checkHealth()
        return when {
            health == null -> EnvironmentReadiness(
                state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                failingCheck = "Daemon health check failed: response was null",
                remediation = "Start the LtiRomServer daemon or verify WSL connectivity"
            )
            health.status != "HEALTHY" -> EnvironmentReadiness(
                state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                failingCheck = "Daemon reported unhealthy status: ${health.status}",
                remediation = "Check daemon logs and restart the daemon"
            )
            !ToolchainReadinessPolicy.isJavaVersionSupported(health.javaVersion) -> EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                failingCheck = "Unsupported daemon Java runtime version: ${health.javaVersion}. " +
                    "Requires Java >= ${ToolchainReadinessPolicy.MIN_JAVA_VERSION}",
                remediation = "Install Java ${ToolchainReadinessPolicy.MIN_JAVA_VERSION}+ in WSL distribution"
            )
            else -> null
        }
    }

    private suspend fun checkToolsAndCompatibility(): EnvironmentReadiness? {
        val missing = checkMissingTools()
        if (missing.isNotEmpty()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_MISSING,
                failingCheck = "Required tools missing: ${missing.sorted().joinToString(", ")}",
                remediation = "Run toolchain setup to compile and install missing tools",
                missingToolIds = missing
            )
        }

        val erofsCheck = transport.execute(
            ToolExecutionRequest(
                toolId = "mkfs.erofs",
                arguments = listOf("--help"),
                workingDirectory = "/"
            )
        )
        val missingFlag = !erofsCheck.stdout.contains(ToolchainReadinessPolicy.EROFS_REQUIRED_FLAG)
        return if (erofsCheck.exitCode != 0 || missingFlag) {
            EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                failingCheck = "mkfs.erofs probe failed or lacks required " +
                    "${ToolchainReadinessPolicy.EROFS_REQUIRED_FLAG} support",
                remediation = "Rebuild toolchain with vendored erofs-utils"
            )
        } else {
            null
        }
    }

    private suspend fun checkMissingTools(): Set<String> {
        val availableServerTools = transport.listTools()
            .filter { it.installed }
            .map { it.tool }
            .toSet()

        val missing = mutableSetOf<String>()
        missing.addAll(requiredProductTools - availableServerTools)

        for (pkg in requiredHostPackages) {
            val checkRes = transport.execute(
                ToolExecutionRequest(
                    toolId = "which",
                    arguments = listOf(pkg),
                    workingDirectory = "/"
                )
            )
            if (checkRes.exitCode != 0) {
                missing.add(pkg)
            }
        }
        return missing
    }
}
