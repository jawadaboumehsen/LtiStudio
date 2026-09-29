/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupExecutionPathTest {

    private class SpyWslCliExecutor : WslCliExecutor() {
        var bridgeConnected: Boolean = false
        val bootstrapCommands = mutableListOf<List<String>>()
        val postBridgeCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            if (bridgeConnected) {
                postBridgeCommands.add(command)
            } else {
                bootstrapCommands.add(command)
            }
            val cmdStr = command.joinToString(" ")
            return when {
                cmdStr.contains("/etc/os-release") -> {
                    CliExecutionResult(
                        0,
                        "NAME=\"Ubuntu\"\nVERSION=\"24.04 LTS (Noble " +
                            "Numbat)\"\nID=ubuntu\nVERSION_ID=\"24.04\"\nVERSION_CODENAME=noble\n",
                        "",
                    )
                }
                cmdStr.contains("id -un; printf '%s' \"\$HOME\"") || cmdStr.contains("id -un") -> {
                    CliExecutionResult(0, "lti\n/home/lti", "")
                }
                command.contains("wsl.exe") || command.contains("--status") -> {
                    CliExecutionResult(0, "Default Distribution: Ubuntu", "")
                }
                command.contains("echo -n \$HOME") -> CliExecutionResult(0, "/home/lti", "")
                cmdStr.contains("JAVA_HOMES_BEGIN") -> {
                    val javaSection = "JAVA_HOMES_BEGIN\n/usr/lib/jvm/java-21-openjdk-amd64|openjdk version " +
                        "\"21.0.4\" 2024-07-16\nJAVA_HOMES_END\n"
                    val reqSection = RequirementCatalog.REQUIREMENTS.joinToString("\n") { "${it.id}=present" }
                    CliExecutionResult(0, "$javaSection$reqSection\nPACKAGES_FRESH=1\n", "")
                }
                else -> CliExecutionResult(0, "ok", "")
            }
        }

        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult {
            if (bridgeConnected) {
                postBridgeCommands.add(command)
            } else {
                bootstrapCommands.add(command)
            }
            if (command.contains("-l") && command.contains("-v")) {
                return CliExecutionResult(0, "  NAME  STATE  VERSION\n* Ubuntu  Running  2\n", "")
            }
            return if (command.contains("-l")) {
                CliExecutionResult(0, "Ubuntu\n", "")
            } else {
                CliExecutionResult(0, "ok", "")
            }
        }
    }

    private class RecordingSupervisor(private val onBridgeConnected: () -> Unit) : DaemonSupervisorPort {
        override val lastLaunchErrorLogTail: String? = null
        override var lastStartAdopted: Boolean = false
        private var descriptor: ServerConnectionDescriptor? = null

        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            onBridgeConnected()
            val desc = ServerConnectionDescriptor(
                host = "127.0.0.1",
                port = 9090,
                token = "secret",
                pid = 4321L,
                distro = "Ubuntu",
            )
            descriptor = desc
            return desc
        }

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = true
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class RecordingTransport : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val startedRuns = mutableListOf<StartRunRequest>()

        /** One submodule is missing until a durable run has synchronised the sources. */
        private var sourcesSynced = false

        override suspend fun checkHealth(): WslServerInfo = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 60_000L,
            availableTools = listOf("adb", "fastboot", "mkfs.erofs"),
        )

        @Suppress("CyclomaticComplexMethod")
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return when {
                request.toolId == "printenv" && request.arguments.contains("HOME") -> {
                    ToolExecutionResponse(0, "/home/lti\n", "", 2L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("external") } -> {
                    val subs = listOf("android-tools", "apktool", "erofs-utils", "img2sdat") +
                        (if (sourcesSynced) listOf("signapk") else emptyList())
                    ToolExecutionResponse(0, subs.joinToString("\n"), "", 5L)
                }
                request.toolId == "find" -> {
                    val sample = listOf(
                        "adb", "fastboot", "mke2fs", "dump.erofs", "mkfs.erofs", "img2sdat", "avbtool", "erofsfuse",
                        "cmake", "make", "clang", "clang++", "gcc", "g++", "python3", "pip3", "java", "javac",
                        "java-17-openjdk-amd64", "java-21-openjdk-amd64", "attr", "getfattr", "setfattr",
                        "zip", "unzip", "brotli", "curl", "git", "rsync", "tar", "file", "xxd", "truncate",
                        "fuse", "fusermount3", "openssl", "sudo", "loop-control", "ccache",
                        "apktool.jar", "signapk.jar", "zipalign", "lpunpack", "lpmake", "lpdump", "payload-dumper-go",
                    )
                    val listing = if ("%m %f\n" in
                        request.arguments
                    ) {
                        fakeBinListing(sample)
                    } else {
                        sample.joinToString("\n")
                    }
                    ToolExecutionResponse(0, listing, "", 5L)
                }
                request.toolId == "dpkg-query" -> {
                    val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                    ToolExecutionResponse(0, pkgs.joinToString("\n") { "$it install ok installed" }, "", 2L)
                }
                request.toolId == "java" -> {
                    ToolExecutionResponse(0, "openjdk version \"21.0.11\" 2024-04-16\n", "", 2L)
                }
                request.toolId == "df" -> {
                    ToolExecutionResponse(
                        0,
                        "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sdb 100G 50G 50G 50% /home/lti\n",
                        "",
                        2L,
                    )
                }
                request.toolId == "test" &&
                    !sourcesSynced &&
                    request.arguments.any { it.endsWith("/external/signapk") } ->
                    ToolExecutionResponse(1, "", "", 1L)
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 1L)
                request.toolId == "git" && fakeGitRevParse(listOf("git") + request.arguments) != null ->
                    fakeGitRevParse(listOf("git") + request.arguments)!!.let {
                        ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                    }
                request.toolId == "sha256sum" -> fakeSha256(request.arguments.firstOrNull().orEmpty()).let {
                    ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                }
                request.toolId == "which" -> ToolExecutionResponse(
                    0,
                    "/usr/bin/${request.arguments.firstOrNull()}",
                    "",
                    1L,
                )
                request.toolId == "sudo" && request.arguments.contains("true") -> ToolExecutionResponse(0, "", "", 1L)
                request.toolId == "mkfs.erofs" -> ToolExecutionResponse(0, "mkfs.erofs 1.7.1 --fs-config-file", "", 1L)
                else -> ToolExecutionResponse(0, "success", "", 5L)
            }
        }

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            sourcesSynced = true
            return RunHandle(
                runId = "run-${startedRuns.size}",
                status = RunStatusValue.RUNNING,
                startedAtEpochMs = 1000L,
            )
        }

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = listOf(
            SequencedStreamEvent(1L, StreamEvent.OutputChunk("running...")),
            SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 100L)),
        ).asFlow()

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 1100L,
        )

        var tools: List<ToolStatusInfo> = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS.map {
            ToolStatusInfo(tool = it, installed = true)
        }
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(tools)
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(tools)
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true
    }

    private class FakePublicationPort : ToolPublicationPort {
        override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport = PublicationReport(
            requested = toolIds,
            registered = toolIds,
            failed = emptyMap(),
            pruned = emptySet(),
        )

        override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.Tools(
            ToolCatalog.ALL_TOOL_IDS.map {
                ToolStatus(
                    tool = it,
                    installed = true,
                    path = ToolCatalog.expectedPath(FAKE_BIN_DIR, it) ?: "",
                    source = ToolSource.DYNAMIC,
                    reason = null,
                )
            },
        )
    }

    @Test
    fun `after SERVER_CONNECTIVITY succeeds setup uses strictly transport executor and records zero wsl calls`() =
        runTest {
            val spyCli = SpyWslCliExecutor()
            val supervisor = RecordingSupervisor {
                spyCli.bridgeConnected = true
            }
            val transport = RecordingTransport()
            val detector = WslEnvironmentDetector(spyCli)
            val publicationPort = FakePublicationPort()

            val provisioner = WslToolchainProvisioner(
                supervisor = supervisor,
                transport = transport,
                repository = inMemoryToolchainRepository(),
                detector = detector,
                cli = spyCli,
                toolPublicationPort = publicationPort,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            )

            // Product flow: a live Check establishes WSL/bridge evidence (the only host wsl.exe calls),
            // then the plan is previewed and confirmed against that evidence.
            provisioner.verifyEnvironment()
            val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
            val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
            assertTrue(outcome is SetupOutcome.Succeeded, "Confirmed plan must succeed: $outcome")

            // Assert setup finished successfully
            val finalState = provisioner.state.value
            val stageStatuses = finalState.steps.associate { it.stage to it.status }
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.WSL_DETECTION])
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.SYSTEM_PACKAGES])
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.SERVER_CONNECTIVITY])
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.SYSTEM_DIAGNOSTICS])
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.REPO_SYNCHRONIZATION])
            assertEquals(StepStatus.SUCCESS, stageStatuses[SetupStepStage.TOOLCHAIN_COMPILATION])

            // CRITICAL ASSERTION: Zero calls made to WslCliExecutor after SERVER_CONNECTIVITY (excluding pre-server
            // host prerequisite probe)
            val nonProbePostBridge = spyCli.postBridgeCommands.filterNot {
                it.any { arg -> arg.contains("JAVA_HOMES_BEGIN") || arg.contains("/var/lib/apt/lists") }
            }
            assertTrue(
                nonProbePostBridge.isEmpty(),
                "Expected ZERO calls to direct WslCliExecutor after SERVER_CONNECTIVITY succeeded, but found " +
                    "${nonProbePostBridge.size} call(s): $nonProbePostBridge",
            )

            // Assert transport handled commands
            assertTrue(transport.executedRequests.isNotEmpty(), "Transport executor must handle post-bridge commands")
            assertTrue(transport.startedRuns.isNotEmpty(), "Durable runs must be initiated via transport")
        }
}
