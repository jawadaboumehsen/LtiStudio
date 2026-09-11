package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val requiredProductTools: Set<String> = DEFAULT_REQUIRED_PRODUCT_TOOLS,
    private val requiredHostPackages: Set<String> = DEFAULT_REQUIRED_HOST_PACKAGES
) : EnvironmentReadinessPort {

    public companion object {
        public val DEFAULT_REQUIRED_PRODUCT_TOOLS: Set<String> = setOf(
            "payload-dumper-go",
            "lpunpack",
            "lpmake",
            "simg2img",
            "erofsfuse",
            "mkfs.erofs",
            "avbtool",
            "unpack_bootimg",
            "img2sdat",
            "signapk"
        )

        public val DEFAULT_REQUIRED_HOST_PACKAGES: Set<String> = setOf(
            "curl",
            "unzip",
            "zip",
            "sha256sum",
            "rsync",
            "getfattr",
            "brotli",
            "file",
            "xxd",
            "truncate"
        )
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
        return when {
            linuxPath.isNullOrBlank() -> EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Workspace Linux path is missing",
                remediation = "Specify a valid Linux path for the workspace"
            )
            !hasFile(linuxPath, "workspace.json") -> EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing workspace manifest at $linuxPath/workspace.json",
                remediation = "Re-provision workspace for target"
            )
            !hasFile(linuxPath, "config.json") -> EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing configuration snapshot at $linuxPath/config.json",
                remediation = "Save a valid configuration snapshot"
            )
            else -> EnvironmentReadiness(state = EnvironmentReadinessState.READY)
        }
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
        val failure = checkDistroAndDaemon() ?: checkToolsAndCompatibility()
        return failure ?: EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    }

    private suspend fun checkDistroAndDaemon(): EnvironmentReadiness? {
        if (!wslPresence.hasDistro()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = "WSL 2 distribution not found",
                remediation = "Install WSL 2 and register an Ubuntu distribution"
            )
        }

        val started = runCatching { supervisor.ensureStarted() }
        return if (started.isFailure) {
            EnvironmentReadiness(
                state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                failingCheck = "LtiRomServer daemon unreachable: ${started.exceptionOrNull()?.message}",
                remediation = "Start the LtiRomServer daemon or verify WSL connectivity"
            )
        } else {
            null
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
        return if (!erofsCheck.stdout.contains("--fs-config-file")) {
            EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                failingCheck = "mkfs.erofs lacks required --fs-config-file support",
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
