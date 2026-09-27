package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.Workspace

/**
 * Adapter implementing [EnvironmentReadinessPort] by delegating host readiness checks to [ToolchainProvisioningService]
 * and performing workspace-specific structure, manifest, and cryptographic key validation.
 */
public class EnvironmentReadinessAdapter(
    private val toolchainService: ToolchainProvisioningService,
    private val transport: RemoteTransportPort,
    private val requiredProductTools: Set<String> = DEFAULT_REQUIRED_PRODUCT_TOOLS,
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
        val toolchainState = toolchainService.checkStatus()
        val result = mapToolchainStateToReadiness(toolchainState)
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

    private fun mapToolchainStateToReadiness(state: ToolchainSetupState): EnvironmentReadiness {
        if (requiredProductTools.isEmpty()) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_MISSING,
                failingCheck = "Required product tools inventory is empty",
                remediation = "Configure required product tools inventory"
            )
        }

        val wslStep = state.steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
        if (wslStep?.status == StepStatus.FAILED) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.ENV_ABSENT,
                failingCheck = wslStep.description,
                remediation = wslStep.error ?: "Install WSL 2 and register an Ubuntu distribution",
            )
        }

        val serverStep = state.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        if (serverStep?.status == StepStatus.FAILED) {
            val failureCategory = serverStep.failureCategory ?: DaemonFailureCategory.UNREACHABLE
            return when (failureCategory) {
                DaemonFailureCategory.UNSUPPORTED_JAVA -> EnvironmentReadiness(
                    state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                    failingCheck = serverStep.description,
                    remediation = "Install Java ${ToolchainReadinessPolicy.MIN_JAVA_VERSION}+ in WSL distribution",
                )
                DaemonFailureCategory.UNHEALTHY_STATUS, DaemonFailureCategory.UNRECOGNIZED_STATUS -> EnvironmentReadiness(
                    state = EnvironmentReadinessState.SERVICE_UNHEALTHY,
                    failingCheck = serverStep.description,
                    remediation = "Check daemon logs and restart the daemon",
                )
                DaemonFailureCategory.UNREACHABLE -> EnvironmentReadiness(
                    state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                    failingCheck = serverStep.description,
                    remediation = "Start the LtiRomServer daemon or verify WSL connectivity",
                )
                DaemonFailureCategory.COMPATIBILITY_MODE -> EnvironmentReadiness(
                    state = EnvironmentReadinessState.SERVICE_UNHEALTHY,
                    failingCheck = serverStep.description,
                    remediation = "Verify daemon version and check daemon logs",
                )
            }
        }

        val toolchainStep = state.steps.firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        if (toolchainStep?.status == StepStatus.FAILED) {
            val isErofs = toolchainStep.toolchainFailureCategory == ToolchainFailureCategory.EROFS_INCOMPATIBLE
            val missing = (requiredProductTools - state.publishedToolIds)
            return if (isErofs) {
                EnvironmentReadiness(
                    state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                    failingCheck = toolchainStep.description,
                    remediation = "Rebuild toolchain with vendored erofs-utils",
                )
            } else {
                EnvironmentReadiness(
                    state = EnvironmentReadinessState.TOOLS_MISSING,
                    failingCheck = toolchainStep.description,
                    remediation = "Run toolchain setup to compile and install missing tools",
                    missingToolIds = missing,
                )
            }
        }

        val doctorStep = state.steps.firstOrNull { it.stage == SetupStepStage.SYSTEM_DIAGNOSTICS }
        if (doctorStep?.status == StepStatus.FAILED) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                failingCheck = doctorStep.description,
                remediation = "Run pre-flight doctor checks and apply repairs",
            )
        }

        if (state.canLaunchWorkspace) {
            val workDir = state.workDirLinuxPath
                ?: state.userHome?.let { "$it/$WORK_DIR_NAME" }
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.READY,
                workDirLinuxPath = workDir,
            )
        }

        return EnvironmentReadiness(
            state = EnvironmentReadinessState.TOOLS_MISSING,
            failingCheck = "Environment verification incomplete",
            remediation = "Run environment check to verify readiness",
        )
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
}
