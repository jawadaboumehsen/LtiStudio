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
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.setup.SetupStepDetail
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

    private val readinessState = MutableStateFlow(
        EnvironmentReadiness(state = EnvironmentReadinessState.ENV_ABSENT),
    )

    override fun observe(): Flow<EnvironmentReadiness> = readinessState.asStateFlow()

    override suspend fun refresh(): EnvironmentReadiness {
        val toolchainState = toolchainService.checkStatus()
        val result = mapToolchainStateToReadiness(toolchainState)
        readinessState.value = result
        return result
    }

    override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness {
        val env = refresh()
        if (env.state != EnvironmentReadinessState.READY) {
            return env
        }

        return validateWorkspace(workspace)
    }

    /** The first failing stage, in pipeline order, decides readiness; otherwise the live check does. */
    private fun mapToolchainStateToReadiness(state: ToolchainSetupState): EnvironmentReadiness = emptyInventory()
        ?: failedStep(state, SetupStepStage.WSL_DETECTION)?.let(::wslFailure)
        ?: failedStep(state, SetupStepStage.SYSTEM_PACKAGES)?.let(::packagesFailure)
        ?: failedStep(state, SetupStepStage.SERVER_CONNECTIVITY)?.let(::serverFailure)
        ?: failedStep(state, SetupStepStage.TOOLCHAIN_COMPILATION)?.let { toolchainFailure(it, state) }
        ?: failedStep(state, SetupStepStage.SYSTEM_DIAGNOSTICS)?.let(::doctorFailure)
        ?: liveReadiness(state)

    private fun failedStep(state: ToolchainSetupState, stage: SetupStepStage): SetupStepDetail? =
        state.steps.firstOrNull { it.stage == stage && it.status == StepStatus.FAILED }

    private fun emptyInventory(): EnvironmentReadiness? = if (requiredProductTools.isEmpty()) {
        EnvironmentReadiness(
            state = EnvironmentReadinessState.TOOLS_MISSING,
            failingCheck = "Required product tools inventory is empty",
            remediation = "Configure required product tools inventory",
        )
    } else {
        null
    }

    private fun wslFailure(step: SetupStepDetail) = EnvironmentReadiness(
        state = EnvironmentReadinessState.ENV_ABSENT,
        failingCheck = step.description,
        remediation = step.error ?: "Install WSL 2 and register an Ubuntu distribution",
    )

    /** Missing Ubuntu packages: the step error carries the exact `sudo apt-get` command to run. */
    private fun packagesFailure(step: SetupStepDetail) = EnvironmentReadiness(
        state = EnvironmentReadinessState.ENV_ABSENT,
        failingCheck = step.description,
        remediation = step.error?.takeIf { it.isNotBlank() } ?: "Install the missing Ubuntu packages from Setup",
    )

    private fun serverFailure(step: SetupStepDetail): EnvironmentReadiness {
        val (state, remediation) = when (step.failureCategory ?: DaemonFailureCategory.UNREACHABLE) {
            DaemonFailureCategory.UNSUPPORTED_JAVA ->
                EnvironmentReadinessState.TOOLS_INCOMPATIBLE to
                    "Install Java ${ToolchainReadinessPolicy.MIN_JAVA_VERSION}+ in WSL distribution"
            DaemonFailureCategory.UNHEALTHY_STATUS,
            DaemonFailureCategory.UNRECOGNIZED_STATUS,
            -> EnvironmentReadinessState.SERVICE_UNHEALTHY to "Check daemon logs and restart the daemon"
            DaemonFailureCategory.UNREACHABLE ->
                EnvironmentReadinessState.SERVICE_UNREACHABLE to
                    "Start the LtiRomServer daemon or verify WSL connectivity"
            DaemonFailureCategory.COMPATIBILITY_MODE ->
                EnvironmentReadinessState.SERVICE_UNHEALTHY to
                    "Verify daemon version and check daemon logs"
        }
        return EnvironmentReadiness(state = state, failingCheck = step.description, remediation = remediation)
    }

    private fun toolchainFailure(step: SetupStepDetail, state: ToolchainSetupState) =
        if (step.toolchainFailureCategory == ToolchainFailureCategory.EROFS_INCOMPATIBLE) {
            EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
                failingCheck = step.description,
                remediation = "Rebuild toolchain with vendored erofs-utils",
            )
        } else {
            EnvironmentReadiness(
                state = EnvironmentReadinessState.TOOLS_MISSING,
                failingCheck = step.description,
                remediation = "Run toolchain setup to compile and install missing tools",
                missingToolIds = requiredProductTools - state.publishedToolIds,
            )
        }

    private fun doctorFailure(step: SetupStepDetail) = EnvironmentReadiness(
        state = EnvironmentReadinessState.TOOLS_INCOMPATIBLE,
        failingCheck = step.description,
        remediation = "Run pre-flight doctor checks and apply repairs",
    )

    private fun liveReadiness(state: ToolchainSetupState) = if (state.canLaunchWorkspace) {
        EnvironmentReadiness(
            state = EnvironmentReadinessState.READY,
            workDirLinuxPath = state.workDirLinuxPath ?: state.userHome?.let { "$it/$WORK_DIR_NAME" },
        )
    } else {
        EnvironmentReadiness(
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
                remediation = "Specify a valid Linux path for the workspace",
            )
        }

        if (workspace.layoutVersion != 1) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Workspace layout version drift: expected 1, found ${workspace.layoutVersion}",
                remediation = "Upgrade or re-provision the workspace",
            )
        }

        if (!hasFile(linuxPath, "workspace.json")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing workspace manifest at $linuxPath/workspace.json",
                remediation = "Re-provision workspace for target",
            )
        }

        val manifestContent = readFileContent(linuxPath, "workspace.json")
        if (manifestContent != null) {
            val match = Regex("\"layoutVersion\"\\s*:\\s*(\\d+)").find(manifestContent)
            if (match != null && match.groupValues[1] != "1") {
                return EnvironmentReadiness(
                    state = EnvironmentReadinessState.WORKSPACE_INVALID,
                    failingCheck = "Workspace manifest layoutVersion drift: expected 1, found ${match.groupValues[1]}",
                    remediation = "Upgrade or re-provision the workspace",
                )
            }
        }

        if (!hasFile(linuxPath, "config.json")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing configuration snapshot at $linuxPath/config.json",
                remediation = "Save a valid configuration snapshot",
            )
        }

        if (!hasFile(linuxPath, "keys/avb.pem") && !hasFile(linuxPath, "keys/avb.avbpubkey")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing cryptographic signing keys at $linuxPath/keys",
                remediation = "Provision signing keys for workspace",
            )
        }

        return EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    }

    private suspend fun readFileContent(linuxPath: String, fileName: String): String? {
        val res = transport.execute(
            ToolExecutionRequest(
                toolId = "cat",
                arguments = listOf("$linuxPath/$fileName"),
                workingDirectory = "/",
            ),
        )
        return if (res.exitCode == 0) res.stdout else null
    }

    private suspend fun hasFile(linuxPath: String, fileName: String): Boolean {
        val check = transport.execute(
            ToolExecutionRequest(
                toolId = "test",
                arguments = listOf("-f", "$linuxPath/$fileName"),
                workingDirectory = "/",
            ),
        )
        return check.exitCode == 0
    }
}
