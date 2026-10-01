/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ide.lti.core.data.setup.ToolchainReadinessPolicy
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.closestToUsable
import org.ide.lti.core.model.setup.distroName

public sealed interface WslPreparationResult {
    public data class Success(
        val environment: SetupEnvironment,
        val installedDistros: List<String>,
        val detectionStatuses: List<DistroStatus>,
    ) : WslPreparationResult

    public data class Failure(val description: String, val error: String, val detectionStatuses: List<DistroStatus>) :
        WslPreparationResult
}

public sealed interface PackagesPreparationResult {
    public val prerequisiteReport: PrerequisiteReport

    public data class Success(val javaHome: String, override val prerequisiteReport: PrerequisiteReport) :
        PackagesPreparationResult

    public data class Failure(
        val description: String,
        val error: String,
        override val prerequisiteReport: PrerequisiteReport,
    ) : PackagesPreparationResult
}

public sealed interface ServerPreparationResult {
    public data class Success(val pingMs: Long, val connPid: Long?) : ServerPreparationResult

    public data class Outdated(val runningVersion: String?, val activeVersion: String?, val runningActiveRuns: Int) :
        ServerPreparationResult

    public data class Failure(
        val description: String,
        val error: String,
        val failureCategory: DaemonFailureCategory? = DaemonFailureCategory.UNREACHABLE,
    ) : ServerPreparationResult
}

/**
 * Service preparation component responsible for WSL selection, host prerequisite probing,
 * and bundled build service lifecycle (FR-003, T029).
 */
public class ServicePreparation(
    private val hostPrerequisitePort: HostPrerequisitePort,
    private val supervisor: DaemonSupervisorPort,
    private val transport: RemoteTransportPort,
    private val serverArtifactPort: ServerArtifactPort? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    public suspend fun verifyWsl(
        preferredDistro: String?,
        persistedDistro: String?,
        currentEnvironment: SetupEnvironment?,
        alreadyVerifiedWsl: Boolean,
        fallbackDistros: List<String> = emptyList(),
    ): WslPreparationResult = withContext(dispatcher) {
        val (selectedEnv, detectionStatuses) = if (alreadyVerifiedWsl && currentEnvironment != null) {
            currentEnvironment to emptyList()
        } else {
            val statuses = hostPrerequisitePort.detect()
            val wanted = preferredDistro ?: persistedDistro
            val picked = statuses.filterIsInstance<DistroStatus.Usable>()
                .firstOrNull { it.environment.distro == wanted }?.environment
            (picked ?: hostPrerequisitePort.selectEnvironment(statuses)) to statuses
        }

        if (selectedEnv == null) {
            val (desc, error) = detectionFailure(detectionStatuses.closestToUsable())
            return@withContext WslPreparationResult.Failure(desc, error, detectionStatuses)
        }

        val distro = selectedEnv.distro
        val distros = detectionStatuses.mapNotNull { it.distroName }
            .ifEmpty { fallbackDistros }
            .ifEmpty { listOf(distro) }

        WslPreparationResult.Success(selectedEnv, distros, detectionStatuses)
    }

    public suspend fun verifySystemPackages(selectedEnv: SetupEnvironment): PackagesPreparationResult =
        withContext(dispatcher) {
            val prerequisiteReport = hostPrerequisitePort.probe(selectedEnv)
            val missingPkgs = prerequisiteReport.missingPackages
            val javaHome = prerequisiteReport.javaHome
            val checkFailure = prerequisiteReport.results.values
                .filterIsInstance<RequirementStatus.CheckFailed>()
                .firstOrNull()
            val unavailable = prerequisiteReport.results.filterValues { it is RequirementStatus.Unavailable }.keys

            val failure: Pair<String, String>? = when {
                checkFailure != null ->
                    "Couldn't check packages: ${checkFailure.reason}" to checkFailure.reason
                unavailable.isNotEmpty() ->
                    "${unavailable.joinToString(", ")} isn't available on Ubuntu ${selectedEnv.osVersionId}" to
                        "Enable the Ubuntu 'universe' repository or use a supported Ubuntu LTS release."
                missingPkgs.isNotEmpty() -> {
                    val suffix = if (missingPkgs.size > 3) "..." else ""
                    val summary = "${missingPkgs.size} package(s) missing: " +
                        "${missingPkgs.take(3).joinToString(", ")}$suffix"
                    summary to prerequisiteReport.installCommand.orEmpty()
                }
                javaHome == null ->
                    "Couldn't check packages: Java 21 was not found under /usr/lib/jvm" to "No JDK 21 home found"
                else -> null
            }

            if (failure != null) {
                PackagesPreparationResult.Failure(failure.first, failure.second, prerequisiteReport)
            } else {
                PackagesPreparationResult.Success(javaHome ?: "/usr/lib/jvm/java-21-openjdk-amd64", prerequisiteReport)
            }
        }

    public suspend fun verifyServer(selectedEnv: SetupEnvironment, javaHome: String): ServerPreparationResult =
        withContext(dispatcher) {
            if (serverArtifactPort != null) {
                val installed = serverArtifactPort.ensureInstalled(selectedEnv)
                val running = (installed.installState as? InstallState.Failed)?.let { installed }
                    ?: serverArtifactPort.ensureRunningCurrent(selectedEnv, javaHome)
                val state = running.installState
                when {
                    state is InstallState.Failed -> {
                        val verb = if (running === installed) "install" else "start"
                        return@withContext ServerPreparationResult.Failure(
                            description = "Failed to $verb server: ${state.step}",
                            error = state.stderr,
                            failureCategory = DaemonFailureCategory.UNREACHABLE,
                        )
                    }
                    state == InstallState.Outdated -> {
                        return@withContext ServerPreparationResult.Outdated(
                            runningVersion = running.runningVersion,
                            activeVersion = running.activeVersion,
                            runningActiveRuns = running.runningActiveRuns,
                        )
                    }
                    else -> Unit
                }
            } else {
                val desc = runCatching { supervisor.ensureStarted(selectedEnv.distro) }
                    .onFailure { if (it is CancellationException) throw it }
                if (desc.isFailure) {
                    val err = desc.exceptionOrNull()
                    val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
                        ?: supervisor.lastLaunchErrorLogTail
                    val errMsg = logTail ?: err?.message ?: "Failed to connect to daemon"
                    return@withContext ServerPreparationResult.Failure(
                        description = "Daemon bridge failed: ${err?.message ?: "unreachable"}",
                        error = errMsg,
                        failureCategory = DaemonFailureCategory.UNREACHABLE,
                    )
                }
            }

            checkServerHealth()
        }

    private suspend fun checkServerHealth(): ServerPreparationResult {
        val startMs = System.currentTimeMillis()
        val health = transport.checkHealth()
        val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
            ?: supervisor.lastLaunchErrorLogTail
        val daemonEval = ToolchainReadinessPolicy.evaluateDaemonHealth(health, logTail)
        if (!daemonEval.isHealthy) {
            return ServerPreparationResult.Failure(
                description = "Daemon bridge failed: ${daemonEval.message}",
                error = daemonEval.technicalDetails ?: daemonEval.message,
                failureCategory = daemonEval.category,
            )
        }
        val pingMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val connPid = supervisor.getConnectionInfo()?.pid
        return ServerPreparationResult.Success(pingMs = pingMs, connPid = connPid)
    }

    private fun detectionFailure(status: DistroStatus?): Pair<String, String> = when (status) {
        is DistroStatus.WslUnavailable -> "WSL isn't installed" to status.detail
        is DistroStatus.NoDistro, null ->
            "No Linux distribution" to
                "Install a supported Ubuntu LTS distribution (e.g. wsl --install -d Ubuntu-24.04)"
        is DistroStatus.StartupFailed -> "${status.distro} didn't start" to status.detail
        is DistroStatus.Unsupported -> "${status.distro} isn't a supported Ubuntu LTS" to status.reason
        is DistroStatus.NoUsableUser -> "${status.distro} needs a user" to status.reason
        is DistroStatus.Usable -> "Multiple distributions available" to "Please select a distribution"
    }
}
