/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.ServerPreparationResult
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.InstallState

/**
 * Build service connectivity and daemon verification stage (T039).
 * Ensures server bundle is installed and intact, starts daemon via supervisor,
 * and verifies health/heartbeat response.
 */
public class ServiceStage(
    private val installer: ServerArtifactInstaller = ServerArtifactInstaller(),
    private val supervisor: DaemonSupervisorPort? = null,
    private val transport: RemoteTransportPort? = null,
    private val heartbeatMonitor: io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor? = null,
    private val servicePreparation: ServicePreparation? = null,
) : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.SERVER_CONNECTIVITY

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val prep = servicePreparation
        if (prep != null) {
            val res = prep.verifyServer(ctx.environment)
            return when (res) {
                is ServerPreparationResult.Success -> {
                    scope.setDaemonPing(res.pingMs)
                    val heartbeatOk =
                        heartbeatMonitor?.awaitInitialHeartbeat()
                            ?: (transport ?: ctx.transport)?.sendHeartbeat()
                            ?: true
                    if (!heartbeatOk) {
                        StageOutcome.Failed(
                            "Build service heartbeat check failed",
                            error = "Command admission was not acknowledged",
                        )
                    } else {
                        heartbeatMonitor?.start()
                        val pid = if (res.connPid != null) "pid ${res.connPid}, " else ""
                        StageOutcome.Success("Build service ready (${pid}ping ${res.pingMs} ms)")
                    }
                }
                is ServerPreparationResult.Outdated -> {
                    val waitingFor = res.runningActiveRuns
                    StageOutcome.Warning(
                        "Update waiting: build service ${res.runningVersion ?: "(old)"} is finishing " +
                            "$waitingFor active run(s); ${res.activeVersion ?: "the new version"} starts after.",
                    )
                }
                is ServerPreparationResult.Failure -> {
                    StageOutcome.Failed(
                        description = res.description,
                        error = res.error.ifBlank { res.description },
                    )
                }
            }
        }

        val env = ctx.environment
        val artifact = installer.ensureInstalled(env)
        if (artifact.installState is InstallState.Failed) {
            val failure = artifact.installState as InstallState.Failed
            return StageOutcome.Failed("Build service installation failed at step '${failure.step}': ${failure.stderr}")
        }

        val activeSupervisor = supervisor
        val activeTransport = transport ?: ctx.transport
        if (activeSupervisor != null) {
            val descriptor =
                try {
                    activeSupervisor.ensureStarted(env.distro)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    return StageOutcome.Failed("Failed to start build service daemon: ${e.message}")
                }
            if (!activeSupervisor.isHealthy(descriptor)) {
                return StageOutcome.Failed("Build service daemon is not healthy on port ${descriptor.port}")
            }
            val heartbeatOk = heartbeatMonitor?.awaitInitialHeartbeat() ?: activeTransport?.sendHeartbeat() ?: true
            if (!heartbeatOk) {
                return StageOutcome.Failed("Build service heartbeat check failed")
            }
            heartbeatMonitor?.start()
            val ver = artifact.activeVersion ?: "unknown"
            return StageOutcome.Success("Daemon v$ver (PID ${descriptor.pid})")
        } else if (activeTransport != null) {
            val health = activeTransport.checkHealth()
            if (health == null) {
                return StageOutcome.Failed("Build service health check failed or returned null")
            }
            val heartbeatOk = heartbeatMonitor?.awaitInitialHeartbeat() ?: activeTransport.sendHeartbeat()
            if (!heartbeatOk) {
                return StageOutcome.Failed("Build service heartbeat check failed")
            }
            heartbeatMonitor?.start()
            val ver = health.serverVersion ?: artifact.activeVersion ?: "unknown"
            return StageOutcome.Success("Daemon v$ver")
        }

        val ver = artifact.activeVersion ?: "installed"
        return StageOutcome.Success("Build service $ver ready")
    }
}
