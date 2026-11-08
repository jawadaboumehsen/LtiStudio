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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
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
import org.ide.lti.core.model.workspace.PackageType
import org.ide.lti.core.model.workspace.SigningPolicy
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

    // Follows the live setup state (the one the Setup screen shows), not a value cached at the last refresh():
    // a cache started as ENV_ABSENT and stayed so until something happened to call refresh(). A check in progress
    // resets its steps before re-running them, so only settled results are reported; otherwise every re-check (the
    // create-workspace flow runs two) flashes "not ready" and back.
    override fun observe(): Flow<EnvironmentReadiness> = toolchainService.state
        .filter { !it.isChecking }
        .map(::mapToolchainStateToReadiness)
        .distinctUntilChanged { old, new -> old.copy(observedAt = 0) == new.copy(observedAt = 0) }

    override suspend fun refresh(): EnvironmentReadiness = mapToolchainStateToReadiness(toolchainService.checkStatus())

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
        ?: failedStep(state, SetupStepStage.SERVER_CONNECTIVITY)?.let(::serverFailure)
        ?: failedStep(state, SetupStepStage.SYSTEM_PACKAGES)?.let(::packagesFailure)
        ?: failedStep(state, SetupStepStage.TOOLCHAIN_COMPILATION)?.let { toolchainFailure(it, state) }
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
        val (state, remediation) =
            when (step.failureCategory ?: DaemonFailureCategory.UNREACHABLE) {
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

        manifestProblem(readFileContent(linuxPath, "workspace.json"))?.let { problem ->
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = problem,
                remediation = "Upgrade or re-provision the workspace",
            )
        }

        if (!hasFile(linuxPath, "config.json")) {
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing configuration snapshot at $linuxPath/config.json",
                remediation = "Save a valid configuration snapshot",
            )
        }

        val config = readFileContent(linuxPath, "config.json")
        val signing = config?.let(::signingOf) ?: return EnvironmentReadiness(
            state = EnvironmentReadinessState.WORKSPACE_INVALID,
            failingCheck = "Unreadable configuration snapshot at $linuxPath/config.json",
            remediation = "Save a valid configuration snapshot",
        )
        requiredKeys(signing, buildsAbOta(config)).firstOrNull { !hasFile(linuxPath, it) }?.let { missing ->
            return EnvironmentReadiness(
                state = EnvironmentReadinessState.WORKSPACE_INVALID,
                failingCheck = "Missing cryptographic signing key: $missing",
                remediation = "Provision signing keys for workspace",
            )
        }

        return EnvironmentReadiness(state = EnvironmentReadinessState.READY)
    }

    private val configJson = Json { ignoreUnknownKeys = true }

    /** The signing settings of config.json, or null when it is not one: only they matter to this gate. */
    private fun signingOf(config: String): SigningPolicy? = runCatching {
        val signing = Json.parseToJsonElement(config).jsonObject.getValue("build").jsonObject.getValue("signing")
        configJson.decodeFromJsonElement<SigningPolicy>(signing)
    }.getOrNull()

    /** Why workspace.json is not one this app can use, or null. */
    private fun manifestProblem(content: String?): String? {
        val manifest = content?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val version = (manifest?.get("layoutVersion") as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
        return when {
            manifest == null -> "Workspace manifest is unreadable or not JSON"
            version == null -> "Workspace manifest has no layoutVersion"
            version != 1 -> "Workspace manifest layoutVersion drift: expected 1, found $version"
            else -> null
        }
    }

    private fun buildsAbOta(config: String): Boolean = runCatching {
        val type = Json.parseToJsonElement(config).jsonObject.getValue("build").jsonObject["packageType"]
        PackageType.valueOf((type as JsonPrimitive).content).abOta
    }.getOrDefault(false)

    /** The private keys the workspace's own signing settings use, and only those. */
    private fun requiredKeys(signing: SigningPolicy, abOta: Boolean): List<String> = buildList {
        if (signing.signImages) add(signing.avbKeyRef)
        if (signing.signPackage) {
            add("${signing.platformKeyRef}.x509.pem")
            add("${signing.platformKeyRef}.pk8")
        }
        // The A/B payload is signed with the key as a PEM, whichever way the package is.
        if (abOta) add("${signing.platformKeyRef}.key.pem")
    }

    private suspend fun readFileContent(linuxPath: String, fileName: String): String? {
        val res =
            transport.execute(
                ToolExecutionRequest(
                    toolId = "cat",
                    arguments = listOf("$linuxPath/$fileName"),
                    workingDirectory = "/",
                ),
            )
        return if (res.exitCode == 0) res.stdout else null
    }

    private suspend fun hasFile(linuxPath: String, fileName: String): Boolean {
        val check =
            transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf("-f", if (fileName.startsWith("/")) fileName else "$linuxPath/$fileName"),
                    workingDirectory = "/",
                ),
            )
        return check.exitCode == 0
    }
}
