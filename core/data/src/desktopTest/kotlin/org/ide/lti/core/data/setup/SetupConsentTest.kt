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
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.StorageHeadroomInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupConsentTest {

    private class SpyDaemonSupervisor(
        var descriptor: ServerConnectionDescriptor? =
            ServerConnectionDescriptor("127.0.0.1", 9090, "token", 1234L, "Ubuntu"),
        var shouldThrow: Boolean = false,
    ) : DaemonSupervisorPort {
        var ensureStartedCount = 0

        override suspend fun getConnectionInfo(): ServerConnectionDescriptor? = descriptor
        override suspend fun isHealthy(info: ServerConnectionDescriptor): Boolean = !shouldThrow
        override suspend fun ensureStarted(): ServerConnectionDescriptor {
            ensureStartedCount++
            if (shouldThrow) throw IllegalStateException("Daemon start failure simulated")
            return descriptor ?: throw IllegalStateException("No descriptor")
        }
        override suspend fun shutdownDaemon(): Boolean = true
        override fun close() {}
    }

    private class SpyRemoteTransport(
        var dfBytes: Long = 100_000_000_000L,
        var dfFails: Boolean = false,
        var missingPackages: Boolean = false,
        var runIdGenerator: (String) -> String = { "durable-run-$it" },
    ) : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val startedRuns = mutableListOf<StartRunRequest>()
        var toolsList: MutableList<ToolStatusInfo> = mutableListOf()

        override suspend fun checkHealth(): WslServerInfo? = WslServerInfo(
            status = "UP",
            distro = "Ubuntu",
            kernelRelease = "5.15.0",
            architecture = "x86_64",
            javaVersion = "21.0.11",
            serverUptimeMs = 100_000L,
            availableTools = listOf("adb", "fastboot"),
        )

        @Suppress("CyclomaticComplexMethod")
        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            val isAptInstall = request.toolId == "sudo" &&
                request.arguments.contains("apt-get") &&
                request.arguments.contains("install")
            if (isAptInstall) {
                // The fake package manager "installs" the packages: subsequent dpkg-query probes see them.
                missingPackages = false
            }
            return when {
                request.toolId == "df" && dfFails -> ToolExecutionResponse(1, "", "df: failed to query mount", 2L)
                request.toolId == "df" -> {
                    val out = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/root 200000000 50000000 " +
                        "60G 25% /home/lti\n"
                    ToolExecutionResponse(0, out, "", 2L)
                }
                request.toolId == "find" &&
                    (request.arguments.contains("/usr/bin") || request.arguments.contains("/bin")) -> {
                    val systemTools = listOf(
                        "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                        "zip", "unzip", "gzip", "bzip2", "xz", "brotli", "curl", "wget", "git", "rsync", "tar", "file",
                        "xxd", "truncate", "attr", "getfattr", "openssl", "keytool", "python3", "pip3", "pip",
                        "java", "javac", "fusermount3", "fusermount", "fuse", "loop-control",
                        "java-17-openjdk-amd64", "java-21-openjdk-amd64", "java-17-openjdk", "java-21-openjdk",
                    ) + ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS
                    ToolExecutionResponse(0, systemTools.joinToString("\n"), "", 2L)
                }
                request.toolId == "find" && request.arguments.any { it.contains("/dev") } -> {
                    ToolExecutionResponse(0, "fuse\nloop-control\nnull\nzero", "", 2L)
                }
                request.toolId == "find" && "%m %f\n" in request.arguments -> {
                    val baseBins = listOf(
                        "payload-dumper-go", "lpunpack", "lpmake", "simg2img", "erofsfuse", "mkfs.erofs",
                        "avbtool", "unpack_bootimg", "img2sdat", "signapk", "adb", "fastboot",
                    )
                    val allBins = (baseBins + ToolCatalog.CORE_BINARIES).distinct()
                    ToolExecutionResponse(
                        0,
                        fakeBinListing(allBins),
                        "",
                        2L,
                    )
                }
                request.toolId == "find" -> ToolExecutionResponse(
                    0,
                    "android-tools\napktool\nerofs-utils\nimg2sdat\nsignapk",
                    "",
                    2L,
                )
                request.toolId == "dpkg-query" && missingPackages -> {
                    ToolExecutionResponse(1, "", "dpkg-query: no packages found", 2L)
                }
                request.toolId == "dpkg-query" -> {
                    val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                        .joinToString("\n") { "$it install ok installed" }
                    ToolExecutionResponse(0, pkgs, "", 2L)
                }
                // No machine signing key exists in this environment; every other path probe succeeds.
                request.toolId == "test" && request.arguments.any { it.endsWith("lti_rsa4096.pem") } ->
                    ToolExecutionResponse(1, "", "", 1L)
                request.toolId == "test" -> ToolExecutionResponse(0, "", "", 1L)
                request.toolId == "git" && fakeGitRevParse(listOf("git") + request.arguments) != null ->
                    fakeGitRevParse(listOf("git") + request.arguments)!!.let {
                        ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                    }
                request.toolId == "sha256sum" -> fakeSha256(request.arguments.firstOrNull().orEmpty()).let {
                    ToolExecutionResponse(it.exitCode, it.output, it.error, 1L)
                }
                request.toolId == "printenv" -> ToolExecutionResponse(0, "/home/lti\n", "", 1L)
                request.toolId == "mkfs.erofs" -> ToolExecutionResponse(0, "mkfs.erofs 1.7.1 --fs-config-file", "", 1L)
                else -> ToolExecutionResponse(0, "ok", "", 1L)
            }
        }

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(toolsList)
        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(toolsList)
        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()
        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true
        override suspend fun downloadFile(remotePath: String): ByteArray? = null
        override suspend fun shutdown(): Boolean = true

        override suspend fun startRun(request: StartRunRequest): RunHandle {
            startedRuns.add(request)
            val id = runIdGenerator(request.idempotencyKey ?: "key")
            return RunHandle(runId = id, status = RunStatusValue.RUNNING, startedAtEpochMs = 1000L)
        }

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

    private class SpyPublicationPort : ToolPublicationPort {
        var publishCount = 0
        var publishedBinDir: String? = null

        override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
            publishCount++
            publishedBinDir = binDir
            val registered = toolIds
            return PublicationReport(
                requested = toolIds,
                registered = registered,
                failed = emptyMap(),
                pruned = emptySet(),
            )
        }

        override suspend fun resolvedTools(): ToolRegistryResult = ToolRegistryResult.Tools(
            ToolCatalog.REQUIRED_PRODUCT_TOOLS.map {
                org.ide.lti.core.domain.ports.ToolStatus(
                    tool = it,
                    installed = true,
                    path = ToolCatalog.expectedPath(FAKE_BIN_DIR, it) ?: "",
                    source = org.ide.lti.core.domain.ports.ToolSource.DYNAMIC,
                    reason = null,
                )
            },
        )
    }

    private class SpyCliExecutor(
        var dfExitCode: Int = 0,
        var dfOutput: String = "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/root 100G 50G 50G 50% " +
            "/home/lti\n",
    ) : WslCliExecutor() {
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
                cmdStr.contains(
                    "wsl.exe --status",
                ) ||
                    cmdStr.contains("--status") -> CliExecutionResult(0, "Default Distribution: Ubuntu", "")
                cmdStr.contains("echo -n \$HOME") -> CliExecutionResult(0, "/home/lti", "")
                cmdStr.contains("JAVA_HOMES_BEGIN") -> {
                    val javaSection = "JAVA_HOMES_BEGIN\n/usr/lib/jvm/java-21-openjdk-amd64|openjdk version " +
                        "\"21.0.4\" 2024-07-16\nJAVA_HOMES_END\n"
                    val reqSection = RequirementCatalog.REQUIREMENTS.joinToString("\n") { "${it.id}=present" }
                    CliExecutionResult(0, "$javaSection$reqSection\nPACKAGES_FRESH=1\n", "")
                }
                command.firstOrNull() == "df" -> CliExecutionResult(
                    dfExitCode,
                    if (dfExitCode ==
                        0
                    ) {
                        dfOutput
                    } else {
                        ""
                    },
                    if (dfExitCode != 0) "df failed" else "",
                )
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    @Test
    fun testProbeInspectionZeroInitialMutations() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val probeResult = provisioner.verifyEnvironment()

        // Assert probe was inspection only
        assertEquals(0, publicationPort.publishCount, "Initial probe must NEVER trigger tool publication")
        assertFalse(probeResult.isBusy, "State must not remain busy after probe")
        assertNull(probeResult.currentStage, "Current stage must be cleared after probe")

        // No git clone commands during probe
        val hasGitClone = transport.executedRequests.any { it.toolId == "git" && it.arguments.contains("clone") } ||
            cli.executedCommands.any { it.contains("git clone") }
        assertFalse(hasGitClone, "Initial probe must NEVER clone git repositories")

        // No compiler invocations during probe
        val hasBuild = transport.executedRequests.any { it.toolId == "make" || it.toolId == "cmake" } ||
            cli.executedCommands.any {
                !it.contains("JAVA_HOMES_BEGIN") && (it.contains("cmake") || it.contains("make"))
            }
        assertFalse(hasBuild, "Initial probe must NEVER run cmake or make compilation")

        // No package installation commands during probe
        val hasApt = transport.executedRequests.any { it.arguments.contains("apt-get") } ||
            cli.executedCommands.any { it.contains("apt-get") }
        assertFalse(hasApt, "Initial probe must NEVER execute apt-get install")
    }

    @Test
    fun testRealStorageHeadroomReadingsWithoutHardcodedFallback() = runTest {
        val cliSuccess = SpyCliExecutor(
            dfExitCode = 0,
            dfOutput = "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/root 100G 40G 60G 40% /home/lti\n",
        )
        val inspector = StorageHeadroomInspector(cliSuccess)
        val context =
            DiagnosticContext(
                distro = "Ubuntu",
                home = "/home/lti",
                binDir = "/home/lti/LtiRomTools/bin",
                binariesPresent = false,
            )

        val resultSuccess = inspector.inspect(context)
        assertEquals(StepStatus.SUCCESS, resultSuccess.status)
        assertTrue(resultSuccess.detail.contains("60 GB free"))

        // When df command fails, StorageHeadroomInspector must return FAILED without fabricating 50L fallback
        val cliFail = SpyCliExecutor(dfExitCode = 1, dfOutput = "")
        val inspectorFail = StorageHeadroomInspector(cliFail)
        val resultFail = inspectorFail.inspect(context)

        assertEquals(StepStatus.FAILED, resultFail.status, "Failed df measurement must result in FAILED status")
        assertTrue(
            resultFail.detail.contains("Unable to measure available disk space"),
            "Detail must report failure instead of fabricated 50L: ${resultFail.detail}",
        )
    }

    /** Commands executed through the daemon service transport, rendered as "tool arg arg...". */
    private fun SpyRemoteTransport.commandLines(): List<String> =
        executedRequests.map { (listOf(it.toolId) + it.arguments).joinToString(" ") }

    @Test
    fun testTargetedPerStageRetryExecutesOnlyRequestedStage() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Sources measured at their pins first: only then is a compilation retry compilation alone (a clean
        // machine's retry syncs first, see SetupPlanFactoryTest).
        provisioner.verifyEnvironment()
        // Retry compilation stage only: the retry is previewed as a STAGE_RETRY plan and confirmed.
        val plan = provisioner.prepare(SetupPlanKind.STAGE_RETRY, SetupStepStage.TOOLCHAIN_COMPILATION.name)
        assertTrue(
            plan.orderedActions.none { it is SetupPlanAction.SyncSources || it is SetupPlanAction.InstallPackages },
            "With synced sources a compilation retry must not expand into sync or remediation: ${plan.orderedActions}",
        )
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")

        // Publication port is invoked during compilation retry
        assertEquals(1, publicationPort.publishCount, "Retrying TOOLCHAIN_COMPILATION must invoke tool publication")

        // Submodules sync was NOT triggered by targeted compilation retry
        val hasClone = transport.startedRuns.any { it.request.toolId == "git" }
        assertFalse(hasClone, "Retrying TOOLCHAIN_COMPILATION must NOT trigger git clone submodules")
    }

    @Test
    fun testFailureCleanupFinallyRestoresBusyState() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport(runIdGenerator = { throw IllegalStateException("startRun exploded") })
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val repository = inMemoryToolchainRepository()
        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = repository,
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // A non-cancellation exception inside a confirmed run is never a hang, a leaked owner or a
        // synthesized success. The child intent was journaled before startRun threw, so the receipt is
        // ambiguous: the outcome is typed Interrupted, the journal is recovery-blocked, and nothing is
        // resubmitted until reconciliation (FR-006, FR-008).
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = false)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Interrupted, "Ambiguous receipt must map to typed Interrupted: $outcome")
        assertTrue(repository.currentState.recoveryBlocked, "an unproven journaled child blocks recovery")
        assertEquals(1, transport.startedRuns.size, "the exploding submission was attempted exactly once")

        val state = provisioner.state.value
        assertFalse(state.isBusy, "isBusy must be cleaned up to false in finally block")
        assertFalse(state.isRunning, "isRunning must be cleaned up to false in finally block")
        assertNull(state.currentStage, "currentStage must be cleared in finally block")
        assertNull(state.activeOperationId, "operation owner must be released in finally block")
        assertNotNull(state.recoveryBlockReason, "the block reason is published to the observable state")

        // A fresh mutation is refused while the journal needs recovery; no second startRun happens.
        val refused = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = false)
        val refusedOutcome = provisioner.confirm(refused.planId, refused.revisionHash)
        assertTrue(refusedOutcome is SetupOutcome.Interrupted, "journal gate refuses new work: $refusedOutcome")
        assertEquals(1, transport.startedRuns.size, "no blind resubmission while blocked")

        // Reconciliation inside the deduplication window finds no server run holding the child lock:
        // the command was never accepted, the child is closed and the attempt is archived as interrupted.
        val recovered = provisioner.recover(null)
        assertTrue(recovered is SetupOutcome.Interrupted, "unfinished planned work is never success: $recovered")
        assertFalse(repository.currentState.recoveryBlocked, "reconciliation lifts the block")
        assertNull(repository.currentState.activeAttempt, "the attempt is history after reconciliation")
        assertEquals(1, transport.startedRuns.size, "reconciliation never resubmits")
    }

    @Test
    fun testDurableIdAttachOnReconnection() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport(runIdGenerator = { key -> "durable-$key-789" })
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Execute a confirmed full setup, which triggers durable runs with idempotency keys
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Succeeded, "$outcome")

        val state = provisioner.state.value
        // Verify durable run IDs were recorded in stepRunIds and steps
        assertNotNull(
            state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION],
            "Durable run ID must be recorded for toolchain compilation",
        )
        assertTrue(state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION]?.contains("durable-") == true)
        val compilationStep = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(state.stepRunIds[SetupStepStage.TOOLCHAIN_COMPILATION], compilationStep.runId)
    }

    @Test
    fun testStalePlanRevisionHashChangedRejectsConfirmation() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP)
        val outcome = provisioner.confirm(plan.planId, "stale-revision-hash-999")

        assertFalse(outcome.isSuccess, "Stale confirmation must not succeed")
        assertTrue(outcome is SetupOutcome.Failed, "Stale confirmation must yield SetupOutcome.Failed")
        assertEquals(
            "Confirmation requires the displayed plan ID and unchanged revision hash.",
            outcome.reason,
        )
        assertEquals(0, publicationPort.publishCount, "No tool publication must occur on stale confirmation")
    }

    @Test
    fun testNoPreviewAtAllConfirmWithoutPrepareRejectsConfirmation() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val outcome = provisioner.confirm("unprepared-plan-1", "unprepared-hash-1")

        assertFalse(outcome.isSuccess, "Confirm without prepare must not succeed")
        assertTrue(outcome is SetupOutcome.Failed, "Confirm without prepare must yield SetupOutcome.Failed")
        assertEquals(
            "Confirmation requires the displayed plan ID and unchanged revision hash.",
            outcome.reason,
        )
    }

    @Test
    fun testPolicyChangeAfterPreviewRejectsConfirmation() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Preview with Auto Doctor OFF, then flip the policy and preview again: the first plan is
        // superseded, so confirming it must be rejected instead of running with the new policy.
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = false)
        assertFalse(plan.autoDoctorEnabled)
        val replan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        assertTrue(replan.autoDoctorEnabled)
        assertNotEquals(plan.revisionHash, replan.revisionHash, "Policy is part of the revision hash")

        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome is SetupOutcome.Failed, "Superseded plan must be rejected: $outcome")
        assertEquals(
            "Confirmation requires the displayed plan ID and unchanged revision hash.",
            outcome.reason,
        )
        assertTrue(
            (transport.commandLines() + cli.executedCommands).none { it.contains("apt-get") },
            "No remediation may run from a superseded plan",
        )
    }

    @Test
    fun testEvidenceChangedAfterPreviewRejectsConfirmationUntilNewPreview() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Plan previewed against the initial (unchecked) evidence.
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        // A live check afterwards changes stage/tool evidence, so the previewed plan is stale.
        provisioner.verifyEnvironment()

        val stale = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(stale is SetupOutcome.Failed, "Stale plan must not execute: $stale")
        assertTrue(stale.reason.contains("preview again"), stale.reason)
        assertEquals(0, publicationPort.publishCount, "No mutation may run from a stale plan")

        // A fresh preview against the new evidence is confirmable.
        val fresh = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        assertNotEquals(plan.revisionHash, fresh.revisionHash)
        val outcome = provisioner.confirm(fresh.planId, fresh.revisionHash)
        assertTrue(outcome.isSuccess, "Fresh plan must execute: $outcome")
    }

    @Test
    fun testConfirmedPlanIsConsumedAndCannotBeReExecuted() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        assertTrue(provisioner.confirm(plan.planId, plan.revisionHash).isSuccess)
        val publishesAfterFirstRun = publicationPort.publishCount

        val second = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(second is SetupOutcome.Failed, "Re-confirming a consumed plan must be rejected: $second")
        assertEquals(publishesAfterFirstRun, publicationPort.publishCount, "Consumed plan must not run twice")
    }

    @Test
    fun testPackageSummaryParityPreviewListsExactlyWhatExecutionRuns() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport(missingPackages = true)
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Seed doctor state with a failed package requirement
        provisioner.verifyEnvironment()

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        val installAction = plan.orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>().firstOrNull()
        assertNotNull(installAction, "Plan must contain InstallPackages action when packages need remediation")

        // The previewed packages must be concrete package names, not diagnostic category IDs
        val previewedPackages = installAction.packages
        assertTrue(
            previewedPackages.isNotEmpty(),
            "Plan package preview must list concrete packages to install",
        )

        // Confirm and execute the plan: pauses in AwaitingUserAction for terminal authorization
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(
            outcome is SetupOutcome.AwaitingUserAction,
            "Confirmed plan requiring packages must pause with AwaitingUserAction: $outcome",
        )

        // Execution parity: the terminal handoff contains exactly the previewed apt packages
        val terminalCommand = outcome.handoff.terminalCommand
        assertTrue(
            previewedPackages.all { terminalCommand.contains(it) },
            "Terminal handoff command must contain all previewed packages: preview=$previewedPackages, " +
                "cmd=$terminalCommand",
        )
        assertTrue(
            cli.executedCommands.none { it.contains("apt-get") },
            "Remediation must not bypass terminal authorization or execute apt-get in background: " +
                "${cli.executedCommands}",
        )
    }

    @Test
    fun testNoGlobalSigningNoAvbOrMachineKeyGeneratedBySetup() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = true)
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)

        assertTrue(outcome.isSuccess, "Confirmed plan must succeed")

        // Verify that NO AVB or machine RSA signing key was created on either execution path.
        // (A read-only `test -f <key>` existence probe during the post-setup live check is inspection,
        // not generation, and is explicitly allowed.)
        val signingCommands = (cli.executedCommands + transport.commandLines()).filter { line ->
            val isReadOnlyProbe = line.startsWith("test ") || line.contains("JAVA_HOMES_BEGIN")
            !isReadOnlyProbe &&
                (
                    line.contains("openssl") ||
                        line.contains("genpkey") ||
                        line.contains("rsa_keygen") ||
                        line.contains("lti_rsa4096.pem")
                    )
        }
        assertTrue(
            signingCommands.isEmpty(),
            "Setup must NEVER generate global signing keys (FR-003, FR-004): found $signingCommands",
        )
        assertFalse(
            provisioner.state.value.isAvbKeyProvisioned,
            "Setup must not provision AVB key implicitly",
        )
    }

    @Test
    fun testAutoDoctorDisabledExecutesZeroPackageRemediation() = runTest {
        val supervisor = SpyDaemonSupervisor()
        val transport = SpyRemoteTransport()
        val publicationPort = SpyPublicationPort()
        val cli = SpyCliExecutor()
        val detector = WslEnvironmentDetector(cli)

        val provisioner = WslToolchainProvisioner(
            supervisor = supervisor,
            transport = transport,
            repository = inMemoryToolchainRepository(),
            detector = detector,
            cli = cli,
            toolPublicationPort = publicationPort,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        // Prepare with Auto Doctor OFF
        val plan = provisioner.prepare(SetupPlanKind.FULL_SETUP, autoDoctorEnabled = false)
        assertFalse(plan.autoDoctorEnabled)

        // Plan must NOT contain InstallPackages
        val hasInstallAction = plan.orderedActions.any { it is SetupPlanAction.InstallPackages }
        assertFalse(hasInstallAction, "Plan must NOT have InstallPackages when Auto Doctor is disabled")

        // Confirm plan
        val outcome = provisioner.confirm(plan.planId, plan.revisionHash)
        assertTrue(outcome.isSuccess, "Plan with Auto Doctor off must execute remaining actions")

        // Zero apt-get calls on either execution path
        val aptCalls = (cli.executedCommands + transport.commandLines()).filter { it.contains("apt-get") }
        assertTrue(
            aptCalls.isEmpty(),
            "When Auto Doctor is disabled, zero apt-get calls must be made: found $aptCalls",
        )
    }
}
