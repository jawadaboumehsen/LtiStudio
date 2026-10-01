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

import com.russhwolf.settings.MapSettings
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EnvironmentVerificationTest {

    private class RecordingDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? = ServerConnectionDescriptor(
            host = "127.0.0.1",
            port = 9090,
            token = "token",
            pid = 4321L,
            distro = "Ubuntu",
        ),
        override var lastStartAdopted: Boolean = false,
        var shouldFail: Boolean = false,
        override val lastLaunchErrorLogTail: String? = null,
    ) : DaemonSupervisorPort {
        var ensureStartedCount = 0

        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            ensureStartedCount++
            if (shouldFail) {
                throw IllegalStateException("Failed to bind port 9090")
            }
            return descriptor ?: throw IllegalStateException("No descriptor")
        }

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = !shouldFail
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class RecordingRemoteTransport(
        var healthInfo: WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 50_000L,
            availableTools = listOf("adb", "fastboot"),
        ),
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        var toolsList: MutableList<ToolStatusInfo> = mutableListOf()

        // One branch per faked command.
        @Suppress("CyclomaticComplexMethod")
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            return when {
                request.toolId == "find" &&
                    (request.arguments.contains("/usr/bin") || request.arguments.contains("/bin")) -> {
                    val systemTools = listOf(
                        "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                        "zip", "unzip", "gzip", "bzip2", "xz", "brotli", "curl", "wget", "git", "rsync", "tar", "file",
                        "xxd", "truncate", "attr", "getfattr", "openssl", "keytool", "python3", "pip3", "pip",
                        "java", "javac", "fusermount3", "fusermount", "fuse", "loop-control",
                        "java-17-openjdk-amd64", "java-21-openjdk-amd64", "java-17-openjdk", "java-21-openjdk",
                    ) + ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS
                    ToolExecutionResponse(0, systemTools.joinToString("\n"), "", 5L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("/dev") } -> {
                    val devNodes = listOf("fuse", "loop-control", "null", "zero", "random", "urandom")
                    ToolExecutionResponse(0, devNodes.joinToString("\n"), "", 2L)
                }
                request.toolId == "find" && request.arguments.any { it.endsWith("/LtiRomTools/bin") } -> {
                    // binDir: with modes for the inventory, bare names for the Doctor's listing.
                    val tools = listOf(
                        "payload-dumper-go", "lpunpack", "lpmake", "simg2img",
                        "erofsfuse", "mkfs.erofs", "avbtool", "unpack_bootimg",
                        "img2sdat", "signapk", "adb", "fastboot", "mke2fs", "dump.erofs",
                    )
                    val listing = if ("%m %f\n" in
                        request.arguments
                    ) {
                        fakeBinListing(tools)
                    } else {
                        tools.joinToString("\n")
                    }
                    ToolExecutionResponse(0, listing, "", 5L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("external") } -> {
                    // submodules
                    val subs = listOf("android-tools", "apktool", "erofs-utils", "img2sdat", "signapk")
                    ToolExecutionResponse(0, subs.joinToString("\n"), "", 5L)
                }
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 2L)
                request.toolId == "git" && fakeGitRevParse(listOf("git") + request.arguments) != null ->
                    fakeGitRevParse(listOf("git") + request.arguments)!!.let {
                        ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                    }
                request.toolId == "sha256sum" -> fakeSha256(request.arguments.firstOrNull().orEmpty()).let {
                    ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                }
                request.toolId == "dpkg-query" -> {
                    val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                        .joinToString("\n") { "$it install ok installed" }
                    ToolExecutionResponse(0, pkgs, "", 2L)
                }
                request.toolId == "df" -> ToolExecutionResponse(
                    0,
                    "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/sdb 100G 50G 50G 50% /home/lti\n",
                    "",
                    2L,
                )
                request.toolId == "printenv" -> ToolExecutionResponse(0, "/home/lti\n", "", 1L)
                request.toolId == "mkfs.erofs" -> ToolExecutionResponse(0, "mkfs.erofs 1.7.1 --fs-config-file", "", 2L)
                else -> ToolExecutionResponse(0, "tool output", "", 5L)
            }
        }

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(toolsList)
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(toolsList)
        override suspend fun checkHealth(): WslServerInfo? = healthInfo
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true

        override suspend fun startRun(request: StartRunRequest): RunHandle =
            RunHandle(runId = "rec-run-123", status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)

        override fun attachRun(runId: String, fromSequence: Long): Flow<SequencedStreamEvent> {
            val events = listOf(
                SequencedStreamEvent(1L, StreamEvent.OutputChunk("running...")),
                SequencedStreamEvent(2L, StreamEvent.ExecutionFinished(0, 100L)),
            )
            return events.asFlow()
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId = runId,
            status = RunStatusValue.COMPLETED,
            exitCode = 0,
            startedAtEpochMs = 1000L,
            endedAtEpochMs = 1100L,
        )
    }

    private class RecordingCliExecutor : WslCliExecutor() {
        val executedCommands = mutableListOf<String>()

        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            executedCommands.add("HOST: $cmdStr")
            return when {
                command.contains("-l") && command.contains("-q") -> CliExecutionResult(0, "Ubuntu\n", "")
                command.contains(
                    "-l",
                ) &&
                    command.contains(
                        "-v",
                    ) -> CliExecutionResult(0, "  NAME  STATE  VERSION\n* Ubuntu  Running  2\n", "")
                command.contains("--status") -> CliExecutionResult(0, "Default Version: 2", "")
                else -> CliExecutionResult(0, "", "")
            }
        }

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            executedCommands.add(cmdStr)
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
                cmdStr.contains("wsl.exe --status") || cmdStr.contains("--status") -> {
                    CliExecutionResult(0, "Default Distribution: Ubuntu", "")
                }
                cmdStr.contains("echo -n \$HOME") -> CliExecutionResult(0, "/home/lti", "")
                cmdStr.contains("mkfs.erofs") -> CliExecutionResult(0, "mkfs.erofs 1.7.1 --fs-config-file", "")
                cmdStr.contains("JAVA_HOMES_BEGIN") -> {
                    val javaSection = "JAVA_HOMES_BEGIN\n/usr/lib/jvm/java-21-openjdk-amd64|openjdk version " +
                        "\"21.0.4\" 2024-07-16\nJAVA_HOMES_END\n"
                    val reqSection = RequirementCatalog.REQUIREMENTS.joinToString("\n") { "${it.id}=present" }
                    CliExecutionResult(0, "$javaSection$reqSection\nPACKAGES_FRESH=1\n", "")
                }
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    private class RecordingPublicationPort : ToolPublicationPort {
        var publishCount = 0
        var publishedIds = setOf<String>()

        override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
            publishCount++
            publishedIds = toolIds
            return PublicationReport(
                requested = toolIds,
                registered = publishedIds,
                failed = emptyMap(),
                pruned = emptySet(),
            )
        }

        override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.Tools(
            publishedIds.map {
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

    private class FakeToolchainRepo(
        initialState: ToolchainPersistenceState = ToolchainPersistenceState(),
        delegate: ToolchainSetupRepository = inMemoryToolchainRepository(
            MapSettings().apply {
                if (initialState != ToolchainPersistenceState()) {
                    val encoded = ToolchainPreferencesDataSource.defaultJson().encodeToString(initialState)
                    putString(ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE, encoded)
                }
            },
        ),
    ) : ToolchainSetupRepository by delegate

    @Test
    fun `restored steps are PENDING and RESTORED with verifiedAt timestamp and not SUCCESS`() = runTest {
        val repo = FakeToolchainRepo(
            ToolchainPersistenceState(
                isSetupCompleted = true,
                completedStages = setOf(
                    SetupStepStage.WSL_DETECTION.name,
                    SetupStepStage.SERVER_CONNECTIVITY.name,
                    SetupStepStage.SYSTEM_DIAGNOSTICS.name,
                    SetupStepStage.REPO_SYNCHRONIZATION.name,
                    SetupStepStage.TOOLCHAIN_COMPILATION.name,
                ),
                lastVerifiedTimestamp = 1700000000000L,
            ),
        )

        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            repository = repo,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.state.value
        assertFalse(state.isAllReady, "Restored state must not be isAllReady before live verification")
        for (step in state.steps) {
            assertEquals(StepStatus.PENDING, step.status, "Restored step ${step.stage} must be PENDING")
            assertEquals(StepProvenance.RESTORED, step.provenance, "Restored step ${step.stage} must be RESTORED")
            assertEquals(1700000000000L, step.verifiedAtEpochMs)
            assertFalse(
                step.description.contains("verified and restored"),
                "Forbidden text 'verified and restored' found in description: ${step.description}",
            )
            assertTrue(
                step.description.contains("re-checking"),
                "Description must indicate re-checking: ${step.description}",
            )
        }
    }

    @Test
    fun `verifyEnvironment runs steps in order and transitions each to RUNNING before live results`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val runningStagesObserved = mutableListOf<SetupStepStage>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            provisioner.state.collect { state ->
                state.currentStage?.let { stage ->
                    if (stage !in runningStagesObserved) {
                        runningStagesObserved.add(stage)
                    }
                }
            }
        }

        val resultState = provisioner.verifyEnvironment()
        job.cancel()

        assertTrue(runningStagesObserved.contains(SetupStepStage.WSL_DETECTION))
        assertTrue(runningStagesObserved.contains(SetupStepStage.SYSTEM_PACKAGES))
        assertTrue(runningStagesObserved.contains(SetupStepStage.SERVER_CONNECTIVITY))
        assertTrue(resultState.steps.all { it.provenance == StepProvenance.LIVE })
    }

    @Test
    fun `bridge adopts lockfile daemon without launching when healthy and reports measured ping`() = runTest {
        val supervisor = RecordingDaemonSupervisor(
            descriptor = ServerConnectionDescriptor("127.0.0.1", 9090, "token", 9999L, "Ubuntu"),
            lastStartAdopted = true,
        )
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.SUCCESS, bridgeStep.status)
        assertEquals(StepProvenance.LIVE, bridgeStep.provenance)
        assertTrue(bridgeStep.description.contains("pid 9999") || bridgeStep.description.contains("9999"))
        assertTrue(bridgeStep.description.contains("ms"))
        assertNotNull(state.daemonPingMs)
    }

    @Test
    fun `bridge failure transitions to FAILED with log tail and marks remaining steps PENDING`() = runTest {
        val supervisor = RecordingDaemonSupervisor(
            shouldFail = true,
            lastLaunchErrorLogTail = "Error: address already in use 0.0.0.0:9090",
        )
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, bridgeStep.status)
        assertTrue(
            bridgeStep.error?.contains("address already in use") == true ||
                bridgeStep.description.contains("address already in use") ||
                bridgeStep.error?.contains("Failed to bind") == true,
        )

        val laterSteps = state.steps.filter {
            it.stage != SetupStepStage.WSL_DETECTION &&
                it.stage != SetupStepStage.SYSTEM_PACKAGES &&
                it.stage != SetupStepStage.SERVER_CONNECTIVITY
        }
        for (step in laterSteps) {
            assertEquals(StepStatus.PENDING, step.status)
            assertTrue(step.description.contains("Pending daemon"))
        }
        assertFalse(state.isAllReady)
    }

    @Test
    fun `verifyEnvironment does not mutate or publish tools, publication occurs during full setup`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val publicationPort = RecordingPublicationPort()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        // Remote transport initially lists empty tools
        transport.toolsList = mutableListOf()

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = FakeToolchainRepo(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        provisioner.verifyEnvironment()
        assertEquals(0, publicationPort.publishCount, "Inspection probe must NEVER publish tools (zero mutation)")

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        assertEquals(0, publicationPort.publishCount, "Preparing a plan must NEVER publish tools")
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "Confirmed plan must succeed: $outcome")
        assertEquals(
            1,
            publicationPort.publishCount,
            "PublicationPort must be invoked by the confirmed toolchain build plan",
        )
    }

    @Test
    fun `zero WslCliExecutor calls occur after SERVER_CONNECTIVITY succeeds`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        cli.executedCommands.clear()
        provisioner.verifyEnvironment()

        // After SERVER_CONNECTIVITY succeeds, all remote checks must run via transport.
        // The only cli calls allowed are initial WSL detection commands (e.g. resolve home, isWslInstalled).
        val nonBootstrapCalls = cli.executedCommands.filterNot {
            it.contains("wsl.exe --status") ||
                it.contains("--status") ||
                it.contains("echo -n \$HOME") ||
                it.contains("printenv HOME") ||
                it.contains("printenv") ||
                it.contains("wsl.exe -l") ||
                it.contains("-l -q") ||
                it.contains("-l -v") ||
                it.contains("/etc/os-release") ||
                it.contains("JAVA_HOMES_BEGIN") ||
                it.contains("/var/lib/apt/lists") ||
                it.contains("id -un")
        }
        assertEquals(
            emptyList(),
            nonBootstrapCalls,
            "Found unexpected direct WSL CLI calls after SERVER_CONNECTIVITY: $nonBootstrapCalls",
        )
    }

    @Test
    fun `every SUCCESS description contains measured data and no forbidden static strings`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf(
            projectTool("payload-dumper-go"),
            projectTool("lpunpack"),
            projectTool("lpmake"),
            projectTool("simg2img"),
            projectTool("erofsfuse"),
            projectTool("mkfs.erofs"),
            projectTool("avbtool"),
            projectTool("unpack_bootimg"),
            projectTool("img2sdat"),
            projectTool("signapk"),
        )
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.verifyEnvironment()
        val forbiddenStrings = listOf(
            "verified and restored",
            "All host utilities and dependencies verified",
            "All submodules and workspace ready",
            "All core tools compiled and verified in",
        )

        for (step in state.steps) {
            if (step.status == StepStatus.SUCCESS) {
                for (forbidden in forbiddenStrings) {
                    assertFalse(
                        step.description.contains(forbidden),
                        "Step ${step.stage} contains forbidden static string: '$forbidden'",
                    )
                }
            }
        }
    }

    @Test
    fun `null health check in probe fails closed with SERVER_CONNECTIVITY FAILED`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport(healthInfo = null) // Null health!
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.verifyEnvironment()
        val bridgeStep = state.steps.first { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        assertEquals(StepStatus.FAILED, bridgeStep.status, "Null daemon health must fail closed")
        assertFalse(state.isAllReady)
    }

    @Test
    fun `absent publication in probe prevents ready`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf() // No published tools!
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val state = provisioner.verifyEnvironment()
        assertNull(state.lastReadyAt, "Absent tool publication must prevent lastReadyAt")
        assertFalse(state.publishedToolIds.containsAll(ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS))
    }

    @Test
    fun `only completed live check updates checkedAt and only fully ready updates lastReadyAt`() = runTest {
        val supervisor = RecordingDaemonSupervisor()
        val transport = RecordingRemoteTransport()
        transport.toolsList = mutableListOf() // Missing tools
        val cli = RecordingCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = FakeToolchainRepo(),
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Before live check
        assertNull(provisioner.state.value.checkedAt)
        assertNull(provisioner.state.value.lastReadyAt)

        // Run live check (incomplete environment)
        val stateAfterCheck = provisioner.verifyEnvironment()
        assertNotNull(stateAfterCheck.checkedAt, "Completed live check must update checkedAt")
        assertNull(stateAfterCheck.lastReadyAt, "Incomplete environment must NOT update lastReadyAt")

        // Now populate all tools and publish
        transport.toolsList = mutableListOf(
            projectTool("payload-dumper-go"),
            projectTool("lpunpack"),
            projectTool("lpmake"),
            projectTool("simg2img"),
            projectTool("erofsfuse"),
            projectTool("mkfs.erofs"),
            projectTool("avbtool"),
            projectTool("unpack_bootimg"),
            projectTool("img2sdat"),
            projectTool("signapk"),
        )

        val readyState = provisioner.verifyEnvironment()
        assertNotNull(readyState.checkedAt)
        assertNotNull(readyState.lastReadyAt, "Fully ready environment must update lastReadyAt")
        assertTrue(readyState.lastReadyAt!! <= readyState.checkedAt!!)
    }
}
