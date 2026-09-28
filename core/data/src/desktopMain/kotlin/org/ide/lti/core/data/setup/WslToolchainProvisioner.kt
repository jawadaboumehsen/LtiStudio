package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.wsl.TransportCliExecutor
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolListResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.ide.lti.core.data.remote.ToolStatusMapper
import org.ide.lti.core.data.setup.doctor.CompositeSystemDoctorEngine
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.data.setup.doctor.WslPackageRemediator
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolEvidence
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.PersistedPlanAction
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.closestToUsable
import org.ide.lti.core.model.setup.distroName
import org.ide.lti.core.model.setup.toDomain
import org.ide.lti.core.model.setup.toPersisted
import java.util.UUID

/**
 * Concrete implementation of [ToolchainProvisioningService] orchestrating WSL environment detection,
 * daemon bridge connectivity, submodule repository synchronization, and native tool compilation.
 *
 * Adheres to:
 * - Single Responsibility Principle: coordinates sequential setup workflow and state emissions.
 *   Diagnostic health checks are delegated to [CompositeSystemDoctorEngine] over the service transport.
 *   Submodule management is delegated to [SubmoduleSyncEngine].
 *   Binary compilation is delegated to [ToolchainBuildEngine].
 *   Mutation admission and single ownership are delegated to [SetupOperationCoordinator].
 * - Dependency Inversion Principle: depends on abstractions ([DaemonSupervisorPort], [RemoteTransportPort],
 *   [ToolchainSetupRepository]).
 * - Persistence & Incremental Execution: Leverages [ToolchainSetupRepository] to eliminate redundant
 *   zero-start builds, Git clones, and native compilations across launches.
 */
@Suppress("TooManyFunctions")
public class WslToolchainProvisioner(
    private val supervisor: DaemonSupervisorPort,
    private val transport: RemoteTransportPort,
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(),
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val repository: ToolchainSetupRepository? = null,
    private val toolPublicationPort: ToolPublicationPort? = null,
    private val coordinator: SetupOperationCoordinator = SetupOperationCoordinator(repository),
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
    private val hostPrerequisitePort: HostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
    private val serverArtifactPort: ServerArtifactPort? = null,
) : ToolchainProvisioningService {

    private var currentEnvironment: SetupEnvironment? = null

    /** Distro the user picked on the WSL row; wins over automatic selection while it stays usable. */
    @Volatile
    private var preferredDistro: String? = null

    private var lastPrerequisiteReport: org.ide.lti.core.model.setup.PrerequisiteReport? = null
    private var activeVerificationDeferred: Deferred<ToolchainSetupState>? = null
    private val verificationMutex = Mutex()

    /** Bounded activity history (FR-011); run output is recorded here with real journal identity. */
    private val logBuffer = SetupLogBuffer(onRecorded = { event -> publishLogTail(event.text) })

    /** Journal reconciliation engine (US3); absent without a repository, in which case nothing is durable. */
    private val recovery: SetupRunRecovery? = repository?.let { SetupRunRecovery(transport, it, activity = logBuffer) }

    /**
     * Last prepared plan. "Plans are session-scoped before confirmation": a confirmation consumes it,
     * and a later preview replaces it.
     */
    private var activePlan: SetupPlan? = null
    private val _operationOutcome = MutableStateFlow<SetupOutcome?>(null)

    private val _state = MutableStateFlow(restoreInitialState())
    override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

    private fun formatAgo(epochMs: Long?): String {
        if (epochMs == null || epochMs <= 0L) return "previously"
        val diffMs = System.currentTimeMillis() - epochMs
        val minutes = diffMs / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            diffMs < 60_000L -> "just now"
            minutes < 60L -> "$minutes min ago"
            hours < 24L -> "$hours h ago"
            else -> "$days d ago"
        }
    }

    private fun restoreInitialState(): ToolchainSetupState {
        val persisted = repository?.currentState ?: return ToolchainSetupState()
        // An unreconciled attempt or a recovery block is restored first: it is the journal's truth about
        // possibly-running server work and must be visible before any evidence (US3 T023).
        val journal = ToolchainSetupState(
            pendingAttemptId = persisted.activeAttempt?.attemptId,
            recoveryBlockReason = persisted.recoveryBlockReason?.takeIf { persisted.recoveryBlocked },
        )
        if (!persisted.isSetupCompleted && persisted.completedStages.isEmpty() && persisted.tools.isEmpty()) {
            return journal
        }

        val lastVerified = persisted.lastVerifiedTimestamp
        val restoredSteps = ToolchainSetupState.defaultSteps().map { step ->
            if (persisted.isStageCompleted(step.stage.name) || persisted.isSetupCompleted) {
                step.copy(
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.RESTORED,
                    verifiedAtEpochMs = lastVerified,
                    description = "Last verified ${formatAgo(lastVerified)} — re-checking…",
                )
            } else {
                step.copy(
                    provenance = StepProvenance.RESTORED,
                    verifiedAtEpochMs = lastVerified,
                )
            }
        }

        val defaultTools = ToolchainSetupState.defaultToolsMatrix()
        val restoredTools = defaultTools.map { tool ->
            val pTool = persisted.tools[tool.id]
            if (pTool != null && pTool.isCompiled) {
                // Installed is a persisted fact; "tested" only restores from a real verification
                // timestamp, never from the binary's mere existence (FR-010).
                tool.copy(
                    status = StepStatus.SUCCESS,
                    path = pTool.binaryPath,
                    version = pTool.version,
                    lastTested = pTool.lastVerifiedTimestamp?.takeIf { pTool.isVerified }?.let { "Restored" },
                )
            } else {
                tool
            }
        }

        return ToolchainSetupState(
            steps = restoredSteps,
            toolsMatrix = restoredTools,
            isAvbKeyProvisioned = persisted.isAvbKeyProvisioned,
            avbKeyPath = persisted.avbKeyPath,
            activeDistro = persisted.activeDistro,
            lastVerifiedTimestamp = persisted.lastVerifiedTimestamp,
            checkedAt = null,
            lastReadyAt = persisted.lastVerifiedTimestamp,
            pendingAttemptId = journal.pendingAttemptId,
            recoveryBlockReason = journal.recoveryBlockReason,
        )
    }

    /** Mirrors the journal's active-attempt / block state into the observable setup state. */
    private fun publishJournalState() {
        val persisted = repository?.currentState ?: return
        _state.update {
            it.copy(
                pendingAttemptId = persisted.activeAttempt?.attemptId,
                recoveryBlockReason = persisted.recoveryBlockReason?.takeIf { _ -> persisted.recoveryBlocked },
            )
        }
    }

    public companion object {
        private const val STATE_LOG_TAIL_LINES: Int = 100
        private const val LOCAL_ATTEMPT_ID: String = "local"
        private const val PROVISIONER_CHILD_ID: String = "provisioner"

        public data class PinnedSubmodule(val name: String, val url: String, val commit: String)

        public val SUBMODULES: List<PinnedSubmodule> = listOf(
            PinnedSubmodule(
                "android-tools",
                "https://github.com/UN1CA/external_android-tools.git",
                "2c5e1b7c192f30ec3d50ec5f983618bb87b9895c",
            ),
            PinnedSubmodule(
                "apktool",
                "https://github.com/iBotPeaches/Apktool.git",
                "1c1d15b070c2138d958fa8b685d430c49c4bb117",
            ),
            PinnedSubmodule(
                "erofs-utils",
                "https://github.com/sekaiacg/erofs-utils.git",
                "24ebe947b889fabcd72f802e4f184b0f807a0e0e",
            ),
            PinnedSubmodule(
                "img2sdat",
                "https://github.com/UN1CA/external_img2sdat.git",
                "601317ed0bf502eb8facdd048f2d75dc75ffcd53",
            ),
            PinnedSubmodule(
                "signapk",
                "https://github.com/UN1CA/external_signapk.git",
                "cb88f4b27d8a07ca101eade5d40a6893920cb55e",
            ),
        )

        public val CORE_BINARIES: List<String> get() = ToolCatalog.CORE_BINARIES

        public val ALL_CORE_TOOLS: List<String> get() = ToolCatalog.ALL_CORE_TOOLS

        public val REQUIRED_DEV_PACKAGES: List<String> = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES

        public val SUPPORTED_RECIPES: Set<String> get() = ToolCapabilities.SUPPORTED_TOOL_IDS

        public fun isRecipeSupported(toolId: String): Boolean = ToolCapabilities.isRecipeSupported(toolId)
    }

    /** Names of the executable files in [binDir] (symlinks followed) via a single `find` call. */
    private suspend fun listExecutables(distro: String, binDir: String, executor: WslCliExecutor): Set<String> {
        val result = runInterruptible(dispatcher) {
            executor.execute(
                distro,
                listOf("find", "-L", binDir, "-maxdepth", "1", "-type", "f", "-perm", "-u+x", "-printf", "%f\n"),
            )
        }
        if (result.exitCode != 0) return emptySet()
        return result.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    override suspend fun checkStatus(): ToolchainSetupState = verifyEnvironment()

    override suspend fun selectDistro(distro: String): ToolchainSetupState {
        val snapshot = _state.value
        val usable = snapshot.distroStatuses.filterIsInstance<DistroStatus.Usable>().any {
            it.environment.distro ==
                distro
        }
        if (!usable || snapshot.isBusy) return snapshot
        preferredDistro = distro
        currentEnvironment = null
        // Detection must run again for the new target instead of reusing the previous verified one.
        _state.update { state ->
            state.copy(
                steps = state.steps.map { step ->
                    if (step.stage == SetupStepStage.WSL_DETECTION ||
                        step.stage == SetupStepStage.SERVER_CONNECTIVITY
                    ) {
                        step.copy(status = StepStatus.PENDING)
                    } else {
                        step
                    }
                },
            )
        }
        return verifyEnvironment()
    }

    override suspend fun verifyEnvironment(): ToolchainSetupState {
        val deferred = verificationMutex.withLock {
            activeVerificationDeferred ?: scope.async(dispatcher) {
                runVerificationInternal()
            }.also { created ->
                activeVerificationDeferred = created
            }
        }
        return deferred.await()
    }

    private suspend fun runVerificationInternal(): ToolchainSetupState {
        // Invariant: A held Deferred can be joined by post-repair checks (:851, :1021, :1557). That is only safe
        // because repairs cannot start while isBusy.
        _state.update { it.copy(isChecking = true) }
        return try {
            runStages()
            _state.value
        } catch (e: CancellationException) {
            // Cancellation is never a verification failure; propagate after finally-cleanup (FR-006).
            throw e
        } catch (e: Exception) {
            val currentStage = _state.value.currentStage ?: SetupStepStage.SERVER_CONNECTIVITY
            val cat = if (currentStage ==
                SetupStepStage.SERVER_CONNECTIVITY
            ) {
                DaemonFailureCategory.UNREACHABLE
            } else {
                null
            }
            setStepStatus(
                currentStage,
                StepStatus.FAILED,
                desc = "Verification failed: ${e.message ?: "network or daemon error"}",
                error = e.message,
                failureCategory = cat,
            )
            shortCircuitRemaining(currentStage, "Pending resolution of ${currentStage.name}.")
            _state.value
        } finally {
            _state.update { it.copy(isChecking = false, currentStage = null) }
            withContext(NonCancellable) {
                verificationMutex.withLock {
                    activeVerificationDeferred = null
                }
            }
        }
    }

    /** Runs the check stages in order; a failed stage marks the rest pending and stops the chain. */
    private suspend fun runStages() {
        val environment = verifyWslStage() ?: return
        val javaHome = verifySystemPackagesStage(environment) ?: return
        if (verifyServerStage(environment, javaHome)) verifyBuildEnvironment(environment)
    }

    /** WSL_DETECTION: picks the environment for this check, or fails the step and returns null. */
    private suspend fun verifyWslStage(): SetupEnvironment? {
        val wslStep = _state.value.steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
        val isBridgeConnected = _state.value.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
            ?.let { it.status == StepStatus.SUCCESS && it.provenance == StepProvenance.LIVE } == true
        val alreadyVerifiedWsl =
            isBridgeConnected ||
                (wslStep?.status == StepStatus.SUCCESS && wslStep.provenance == StepProvenance.LIVE)
        setStepRunning(SetupStepStage.WSL_DETECTION)
        val (selectedEnv, detectionStatuses) = if (alreadyVerifiedWsl && currentEnvironment != null) {
            currentEnvironment to emptyList()
        } else {
            val statuses = hostPrerequisitePort.detect()
            _state.update { it.copy(distroStatuses = statuses) }
            val picked = statuses.filterIsInstance<DistroStatus.Usable>()
                .firstOrNull { it.environment.distro == preferredDistro }?.environment
            (picked ?: hostPrerequisitePort.selectEnvironment(statuses)) to statuses
        }

        if (selectedEnv == null) {
            val (desc, error) = detectionFailure(detectionStatuses.closestToUsable())
            setStepStatus(
                SetupStepStage.WSL_DETECTION,
                StepStatus.FAILED,
                desc = desc,
                error = error,
            )
            shortCircuitRemaining(SetupStepStage.WSL_DETECTION, "Pending WSL.")
            return null
        }

        currentEnvironment = selectedEnv
        val distro = selectedEnv.distro
        val distros = detectionStatuses.mapNotNull { it.distroName }
            .ifEmpty { _state.value.installedDistros }
            .ifEmpty { listOf(distro) }

        _state.update {
            it.copy(
                activeDistro = distro,
                installedDistros = distros,
            )
        }

        setStepStatus(
            SetupStepStage.WSL_DETECTION,
            StepStatus.SUCCESS,
            desc = "WSL 2 active with distribution '$distro'.",
        )

        return selectedEnv
    }

    /** Step description and error for a check that found no usable distro. */
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

    /** SYSTEM_PACKAGES: checks host prerequisites without the build service; returns the Java 21 home. */
    private suspend fun verifySystemPackagesStage(selectedEnv: SetupEnvironment): String? {
        setStepRunning(SetupStepStage.SYSTEM_PACKAGES)
        val prerequisiteReport = hostPrerequisitePort.probe(selectedEnv)
        lastPrerequisiteReport = prerequisiteReport
        val missingPkgs = prerequisiteReport.missingPackages
        val javaHome = prerequisiteReport.javaHome
        val checkFailure = prerequisiteReport.results.values
            .filterIsInstance<RequirementStatus.CheckFailed>()
            .firstOrNull()
        val unavailable = prerequisiteReport.results.filterValues { it is RequirementStatus.Unavailable }.keys

        // Row texts are the contract read by SetupPresentationMapper.mapSystemPackagesRow.
        val packagesFailure: Pair<String, String>? = when {
            checkFailure != null ->
                "Couldn't check packages: ${checkFailure.reason}" to checkFailure.reason
            unavailable.isNotEmpty() ->
                "${unavailable.joinToString(", ")} isn't available on Ubuntu ${selectedEnv.osVersionId}" to
                    "Enable the Ubuntu 'universe' repository or use a supported Ubuntu LTS release."
            missingPkgs.isNotEmpty() ->
                "${missingPkgs.size} package(s) missing: ${missingPkgs.take(
                    3,
                ).joinToString(", ")}${if (missingPkgs.size > 3) "..." else ""}" to
                    prerequisiteReport.installCommand.orEmpty()
            javaHome == null ->
                "Couldn't check packages: Java 21 was not found under /usr/lib/jvm" to "No JDK 21 home found"
            else -> null
        }
        if (packagesFailure != null) {
            setStepStatus(
                SetupStepStage.SYSTEM_PACKAGES,
                StepStatus.FAILED,
                desc = packagesFailure.first,
                error = packagesFailure.second,
            )
            shortCircuitRemaining(SetupStepStage.SYSTEM_PACKAGES, "Pending system packages.")
            return null
        }
        setStepStatus(
            SetupStepStage.SYSTEM_PACKAGES,
            StepStatus.SUCCESS,
            desc = "All prerequisite packages installed · Java: $javaHome",
        )
        return javaHome
    }

    /** SERVER_CONNECTIVITY: installs/starts the build service and checks its health. */
    private suspend fun verifyServerStage(selectedEnv: SetupEnvironment, javaHome: String): Boolean {
        setStepRunning(SetupStepStage.SERVER_CONNECTIVITY)
        val started = if (serverArtifactPort != null) {
            startBundledServer(serverArtifactPort, selectedEnv, javaHome)
        } else {
            startLegacyServer(selectedEnv.distro)
        }
        return started && checkServerHealth()
    }

    private suspend fun startBundledServer(
        serverArtifactPort: ServerArtifactPort,
        selectedEnv: SetupEnvironment,
        javaHome: String,
    ): Boolean {
        val installFailure = serverArtifactPort.ensureInstalled(selectedEnv).installState as? InstallState.Failed
        val failure = installFailure?.let { "Failed to install server: ${it.step}" to it }
            ?: (serverArtifactPort.ensureRunningCurrent(selectedEnv, javaHome).installState as? InstallState.Failed)
                ?.let { "Failed to start server: ${it.step}" to it }
        if (failure != null) {
            setStepStatus(
                SetupStepStage.SERVER_CONNECTIVITY,
                StepStatus.FAILED,
                desc = failure.first,
                error = failure.second.stderr,
                failureCategory = DaemonFailureCategory.UNREACHABLE,
            )
            shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending server startup.")
        }
        return failure == null
    }

    /** Without a [ServerArtifactPort] (tests, legacy wiring) the supervisor starts the service itself. */
    private suspend fun startLegacyServer(distro: String): Boolean {
        val desc = runCatching { supervisor.ensureStarted(distro) }
            .onFailure { if (it is CancellationException) throw it }
        if (desc.isFailure) {
            val err = desc.exceptionOrNull()
            val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
                ?: supervisor.lastLaunchErrorLogTail
            val errMsg = logTail ?: err?.message ?: "Failed to connect to daemon"
            setStepStatus(
                SetupStepStage.SERVER_CONNECTIVITY,
                StepStatus.FAILED,
                desc = "Daemon bridge failed: ${err?.message ?: "unreachable"}",
                error = errMsg,
                failureCategory = DaemonFailureCategory.UNREACHABLE,
            )
            shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending daemon.")
            return false
        }
        return true
    }

    private suspend fun checkServerHealth(): Boolean {
        val startMs = System.currentTimeMillis()
        val health = transport.checkHealth()
        val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
            ?: supervisor.lastLaunchErrorLogTail
        val daemonEval = ToolchainReadinessPolicy.evaluateDaemonHealth(health, logTail)
        if (!daemonEval.isHealthy) {
            setStepStatus(
                SetupStepStage.SERVER_CONNECTIVITY,
                StepStatus.FAILED,
                desc = "Daemon bridge failed: ${daemonEval.message}",
                error = daemonEval.technicalDetails ?: daemonEval.message,
                failureCategory = daemonEval.category,
            )
            shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending daemon.")
            return false
        }
        val pingMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val connPid = supervisor.getConnectionInfo()?.pid
        val pidSuffix = if (connPid != null) " · pid $connPid" else ""
        setStepStatus(
            SetupStepStage.SERVER_CONNECTIVITY,
            StepStatus.SUCCESS,
            desc = "Connected · ${pingMs}ms$pidSuffix",
        )
        _state.update { it.copy(daemonPingMs = pingMs) }

        return true
    }

    /** Doctor, repositories, toolchain and the tools matrix: everything that runs through the service. */
    private suspend fun verifyBuildEnvironment(environment: SetupEnvironment) {
        val distro = environment.distro
        // Post-bridge: All remote calls use transport
        val home = currentEnvironment?.home ?: resolveHome(distro)
        val binDir = "$home/LtiRomTools/bin"
        val extDir = "$home/LtiRomTools/external"
        val workDir = "$home/LtiRomWorkDir"
        val avbKeyFile = "$workDir/security/avb/lti_rsa4096.pem"
        val transportCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)

        val findBin = transport.execute(
            ToolExecutionRequest(
                toolId = "find",
                arguments = listOf(
                    "-L", binDir, "-maxdepth", "1", "-type", "f", "-perm", "-u+x", "-printf", "%f\n",
                ),
                workingDirectory = "/",
            ),
        )
        val executables = if (findBin.exitCode == 0) {
            findBin.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            emptySet()
        }
        val missingBins = CORE_BINARIES.filterNot { it in executables }
        val binariesPresent = missingBins.isEmpty()

        val diagnosticsList = runDoctorStage(transportCli, home, distro, binariesPresent)
        verifyRepoStage(extDir, workDir)

        val publishedTools = verifyToolchainStage(missingBins, executables)

        // AVB Key Check
        val testAvb = transport.execute(
            ToolExecutionRequest(
                toolId = "test",
                arguments = listOf("-f", avbKeyFile),
                workingDirectory = "/",
            ),
        )
        val hasAvbKey = testAvb.exitCode == 0

        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val updatedTools = currentTools.map { tool ->
            val toolPath = "$binDir/${tool.binaryName}"
            val exists = tool.binaryName in executables
            val newStatus = when {
                !exists -> StepStatus.PENDING
                tool.status == StepStatus.FAILED -> StepStatus.FAILED
                else -> StepStatus.SUCCESS
            }
            tool.copy(
                path = if (exists) toolPath else null,
                status = newStatus,
                lastTested = tool.lastTested,
            )
        }

        val (storageAvailableBytes, storageMeasuredAt) = measureStorage(home)

        val now = System.currentTimeMillis()
        _state.update { s ->
            val allPublished = publishedTools.containsAll(ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS)
            val isFullyReady = s.isAllReady && allPublished
            s.copy(
                diagnostics = diagnosticsList.ifEmpty { s.diagnostics },
                isAvbKeyProvisioned = hasAvbKey,
                avbKeyPath = if (hasAvbKey) avbKeyFile else null,
                activeDistro = distro,
                toolsMatrix = updatedTools,
                currentStage = null,
                isChecking = false,
                lastVerifiedTimestamp = if (isFullyReady) now else s.lastVerifiedTimestamp,
                checkedAt = now,
                lastReadyAt = if (isFullyReady) now else s.lastReadyAt,
                // Live daemon listing is the publication fact; it replaces any earlier/restored set.
                publishedToolIds = publishedTools,
                storageAvailableBytes = storageAvailableBytes ?: s.storageAvailableBytes,
                storageMeasuredAt = storageMeasuredAt ?: s.storageMeasuredAt,
                userHome = home,
                workDirLinuxPath = workDir,
            )
        }
        if (_state.value.isAllReady) {
            val now = _state.value.lastVerifiedTimestamp
            repository?.let { repo ->
                repo.saveToolchainState(repo.currentState.copy(lastVerifiedTimestamp = now))
            }
        }
        _state.value
    }

    private suspend fun runDoctorStage(
        transportCli: TransportCliExecutor,
        home: String,
        distro: String,
        binariesPresent: Boolean,
    ): List<DiagnosticCheckItem> {
        setStepRunning(SetupStepStage.SYSTEM_DIAGNOSTICS)
        val doctorEngine = CompositeSystemDoctorEngine(
            cli = transportCli,
            detector = detector,
            dispatcher = dispatcher,
            inspectors = CompositeSystemDoctorEngine.defaultInspectors(transportCli),
            remediator = WslPackageRemediator(transportCli),
            userHome = home,
        )
        val diagnosticsList = doctorEngine.runDiagnostics(distro, binariesPresent)
        val anyDiagFailed = diagnosticsList.any { it.status == StepStatus.FAILED }
        val anyDiagWarn = diagnosticsList.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
        val diagStatus = when {
            anyDiagFailed -> StepStatus.FAILED
            anyDiagWarn -> StepStatus.WARNING
            else -> StepStatus.SUCCESS
        }
        val diagDesc = when {
            anyDiagFailed -> "${diagnosticsList.count { it.status == StepStatus.FAILED }} critical checks failed."
            anyDiagWarn ->
                "${diagnosticsList.count {
                    it.status == StepStatus.WARNING
                }} warnings detected in pre-flight checks."
            else -> "${diagnosticsList.size}/${diagnosticsList.size} host and build system checks verified."
        }
        setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, diagStatus, desc = diagDesc)
        _state.update { it.copy(diagnostics = diagnosticsList) }
        return diagnosticsList
    }

    private suspend fun verifyRepoStage(extDir: String, workDir: String) {
        setStepRunning(SetupStepStage.REPO_SYNCHRONIZATION)
        val findExt = transport.execute(
            ToolExecutionRequest(
                toolId = "find",
                arguments = listOf(extDir, "-maxdepth", "1", "-mindepth", "1", "-type", "d", "-printf", "%f\n"),
                workingDirectory = "/",
            ),
        )
        val subdirs = if (findExt.exitCode == 0) {
            findExt.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            emptySet()
        }
        val testWorkDir = transport.execute(
            ToolExecutionRequest(
                toolId = "test",
                arguments = listOf("-d", workDir),
                workingDirectory = "/",
            ),
        )
        val workDirExists = testWorkDir.exitCode == 0
        val missingSubs = SUBMODULES.filter { it.name !in subdirs }
        val repoStatus = if (missingSubs.isEmpty() && workDirExists) StepStatus.SUCCESS else StepStatus.PENDING
        val repoDesc = if (missingSubs.isEmpty() && workDirExists) {
            "${SUBMODULES.size} submodules · ~/LtiRomWorkDir"
        } else if (!workDirExists) {
            "Workspace directory $workDir not created yet."
        } else {
            "${missingSubs.size} submodule(s) missing in $extDir."
        }
        setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, repoStatus, desc = repoDesc)
    }

    /** TOOLCHAIN_COMPILATION: binaries, published tools and mkfs.erofs; returns the published tool IDs. */
    private suspend fun verifyToolchainStage(missingBins: List<String>, executables: Set<String>): Set<String> {
        setStepRunning(SetupStepStage.TOOLCHAIN_COMPILATION)
        val registryResult = toolPublicationPort?.resolvedTools()
            ?: ToolStatusMapper.toDomain(transport.listTools())
        val isServiceError = registryResult is ToolRegistryResult.ServiceError
        val publishedTools = when (registryResult) {
            is ToolRegistryResult.Tools -> registryResult.list.filter { it.installed }.map { it.tool }.toSet()
            is ToolRegistryResult.ServiceError -> {
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.FAILED,
                    desc = "Can't reach the build service",
                    error = "Build service unreachable: ${registryResult.reason}",
                    failureCategory = DaemonFailureCategory.UNREACHABLE,
                )
                emptySet()
            }
        }

        val missingPublished =
            if (isServiceError) {
                emptySet()
            } else {
                ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS - publishedTools
            }

        // Check EROFS compatibility only when binaries and published tools are present
        val erofsCompatible = if (!isServiceError && missingBins.isEmpty() && missingPublished.isEmpty()) {
            val erofsCheck = transport.execute(
                ToolExecutionRequest(
                    toolId = "mkfs.erofs",
                    arguments = ToolCapabilities.probeArgumentsFor("mkfs.erofs"),
                    workingDirectory = "/",
                ),
            )
            ToolchainReadinessPolicy.isErofsCompatible(erofsCheck.exitCode, erofsCheck.stdout)
        } else {
            true
        }

        data class ToolchainOutcome(
            val status: StepStatus,
            val desc: String,
            val error: String?,
            val category: ToolchainFailureCategory?,
        )
        val outcome = when {
            isServiceError -> ToolchainOutcome(
                StepStatus.PENDING,
                "Waiting for build service",
                null,
                null,
            )
            missingBins.isNotEmpty() -> ToolchainOutcome(
                StepStatus.FAILED,
                "${missingBins.first()} not built",
                "Missing core binaries: ${missingBins.joinToString()}",
                ToolchainFailureCategory.MISSING_BINARIES,
            )
            missingPublished.isNotEmpty() -> ToolchainOutcome(
                StepStatus.FAILED,
                "Missing published tools: ${missingPublished.first()}",
                "Required tools not published on daemon: ${missingPublished.joinToString()}",
                ToolchainFailureCategory.MISSING_PUBLISHED_TOOLS,
            )
            !erofsCompatible -> ToolchainOutcome(
                StepStatus.FAILED,
                "mkfs.erofs incompatible",
                "mkfs.erofs does not support ${ToolchainReadinessPolicy.EROFS_REQUIRED_FLAG}",
                ToolchainFailureCategory.EROFS_INCOMPATIBLE,
            )
            else -> ToolchainOutcome(
                StepStatus.SUCCESS,
                "${executables.count {
                    it in ALL_CORE_TOOLS
                }}/${ALL_CORE_TOOLS.size} tools · ${publishedTools.size} published",
                null,
                null,
            )
        }
        setStepStatus(
            SetupStepStage.TOOLCHAIN_COMPILATION,
            outcome.status,
            desc = outcome.desc,
            error = outcome.error,
            toolchainFailureCategory = outcome.category,
        )
        return publishedTools
    }

    /** Free bytes under [home] from `df`, with when they were measured; nulls when `df` fails. */
    private suspend fun measureStorage(home: String): Pair<Long?, Long?> {
        val storageDf = runCatching {
            transport.execute(
                ToolExecutionRequest(
                    toolId = "df",
                    arguments = listOf("-B1", home),
                    workingDirectory = "/",
                ),
            )
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        return if (storageDf != null && storageDf.exitCode == 0) {
            val lines = storageDf.stdout.lines()
            val tokens = if (lines.size >= 2) lines[1].trim().split("\\s+".toRegex()) else null
            val bytes = tokens?.getOrNull(3)?.toLongOrNull()
            if (bytes != null) Pair(bytes, System.currentTimeMillis()) else Pair(null, null)
        } else {
            Pair(null, null)
        }
    }

    /**
     * Environment identity for admission and plans. Live evidence from a completed check is
     * authoritative (WSL/bridge verified LIVE); only without it is the host default distro probed.
     */
    private fun resolveEnvironmentKey(): String {
        val snapshot = _state.value
        val liveVerified = snapshot.steps.any {
            (it.stage == SetupStepStage.WSL_DETECTION || it.stage == SetupStepStage.SERVER_CONNECTIVITY) &&
                it.status == StepStatus.SUCCESS &&
                it.provenance == StepProvenance.LIVE
        }
        return if (liveVerified) {
            currentEnvironment?.distro ?: snapshot.activeDistro ?: error("No verified distribution in state")
        } else {
            currentEnvironment?.distro ?: snapshot.activeDistro ?: detector.getDefaultDistro()
                ?: error("No WSL distribution detected")
        }
    }

    private suspend fun setStepRunning(stage: SetupStepStage) {
        _state.update { s ->
            val updatedSteps = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        status = StepStatus.RUNNING,
                        provenance = StepProvenance.LIVE,
                    )
                } else {
                    step
                }
            }
            s.copy(
                steps = updatedSteps,
                currentStage = stage,
            )
        }
        kotlinx.coroutines.yield()
    }

    private fun shortCircuitRemaining(failedStage: SetupStepStage, reason: String) {
        val now = System.currentTimeMillis()
        _state.update { s ->
            var markPending = false
            val updatedSteps = s.steps.map { step ->
                if (step.stage == failedStage) {
                    markPending = true
                    step
                } else if (markPending) {
                    step.copy(
                        status = StepStatus.PENDING,
                        description = reason,
                        provenance = StepProvenance.LIVE,
                        error = null,
                    )
                } else {
                    step
                }
            }
            s.copy(
                steps = updatedSteps,
                currentStage = null,
                isChecking = false,
                checkedAt = now,
            )
        }
    }

    override suspend fun provisionAvbKey(): Boolean = provisionAvbKey(serviceCli = null, home = null, distroName = null)

    public suspend fun provisionAvbKey(
        serviceCli: WslCliExecutor? = null,
        home: String? = null,
        distroName: String? = null,
    ): Boolean = withContext(dispatcher) {
        if (_state.value.isBusy && serviceCli == null) return@withContext false
        val distro =
            distroName ?: currentEnvironment?.distro ?: _state.value.activeDistro
                ?: (if (serviceCli == null) detector.getDefaultDistro() else null)
                ?: error("No active distribution detected or specified")
        val userHome = home ?: resolveHome(distro)
        val effectiveCli = serviceCli ?: TransportCliExecutor(transport, defaultWorkingDirectory = userHome)
        val workDir = "$userHome/LtiRomWorkDir"
        val avbDir = "$workDir/security/avb"
        val keyPath = "$avbDir/lti_rsa4096.pem"
        val pubKeyPath = "$avbDir/lti_rsa4096.avbpubkey"
        val binDir = "$userHome/LtiRomTools/bin"

        appendLog("[AVB 2.0] Initializing security/avb directory in $workDir...")
        effectiveCli.execute(distro, listOf("mkdir", "-p", avbDir))

        val keyExists = effectiveCli.execute(distro, listOf("test", "-f", keyPath)).exitCode == 0
        if (!keyExists) {
            appendLog("[AVB 2.0] Generating RSA-4096 private key via OpenSSL...")
            val genRes = effectiveCli.execute(
                distro,
                listOf("openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096", "-out", keyPath),
            )
            effectiveCli.execute(distro, listOf("chmod", "600", keyPath))
            appendLog("[AVB 2.0] Private key generated with mode 0600 at $keyPath.")
        } else {
            appendLog("[AVB 2.0] Existing RSA-4096 private key detected.")
        }

        // Extract public key using avbtool if available
        val hasAvbTool = effectiveCli.execute(distro, listOf("test", "-x", "$binDir/avbtool")).exitCode == 0
        if (hasAvbTool) {
            effectiveCli.execute(
                distro,
                listOf("$binDir/avbtool", "extract_public_key", "--key", keyPath, "--output", pubKeyPath),
            )
            appendLog("[AVB 2.0] Derived AVB public key at $pubKeyPath.")
        }

        val verified = effectiveCli.execute(distro, listOf("test", "-f", keyPath)).exitCode == 0
        if (verified) {
            _state.update { s ->
                s.copy(
                    isAvbKeyProvisioned = true,
                    avbKeyPath = keyPath,
                )
            }
            appendLog("[AVB 2.0] Machine signing key successfully verified.")
        }
        verified
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun testTool(toolId: String): SetupOutcome = withContext(dispatcher) {
        val toolItem = _state.value.toolsMatrix.firstOrNull { it.id == toolId }
            ?: return@withContext SetupOutcome.Failed(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                reason = "Tool $toolId not found in tools matrix",
            )
        if (_state.value.isChecking) {
            // A live check is not an operation owner, but its probes must not race a tool probe.
            return@withContext SetupOutcome.Busy(ownerId = "environment-check")
        }
        val distro = resolveEnvironmentKey()

        coordinator.withAdmission(
            environmentKey = distro,
            operationId = "test-$toolId-${System.currentTimeMillis()}",
            kind = "TEST_TOOL",
        ) {
            val userHome = resolveHome(distro)
            val binDir = "$userHome/LtiRomTools/bin"
            val binaryName = toolItem.binaryName
            val binaryPath = "$binDir/$binaryName"

            _state.update { s ->
                s.copy(
                    toolsMatrix = s.toolsMatrix.map {
                        if (it.id == toolId) it.copy(status = StepStatus.RUNNING) else it
                    },
                )
            }

            try {
                val effectiveCli = TransportCliExecutor(transport, defaultWorkingDirectory = userHome)
                val exists = effectiveCli.execute(distro, listOf("test", "-x", binaryPath)).exitCode == 0
                val probeArgv = ToolCapabilities.probeArgumentsFor(toolId, binaryPath)
                val acceptedExitCodes = ToolCapabilities.acceptedExitCodesFor(toolId)
                var exitCode = -1
                // Real process exit code: do NOT infer success from stdout text alone (FR-009)
                val testStatus = if (exists) {
                    val execRes = effectiveCli.execute(distro, probeArgv, timeoutSeconds = 5)
                    exitCode = execRes.exitCode
                    if (exitCode in acceptedExitCodes) StepStatus.SUCCESS else StepStatus.FAILED
                } else {
                    StepStatus.FAILED
                }

                val now = System.currentTimeMillis()
                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId) {
                                it.copy(
                                    status = testStatus,
                                    path = if (exists) binaryPath else null,
                                    // A failed probe replaces any earlier success evidence (FR-010).
                                    lastTested = if (testStatus == StepStatus.SUCCESS) "Just now" else null,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
                if (testStatus == StepStatus.SUCCESS) {
                    repository?.updateTool(toolId) { tool ->
                        tool.copy(
                            isVerified = true,
                            isCompiled = true,
                            binaryPath = if (exists) binaryPath else tool.binaryPath,
                            lastVerifiedTimestamp = now,
                        )
                    }
                    SetupOutcome.Succeeded(
                        evidence = mapOf(
                            toolId to ToolEvidence(
                                toolId = toolId,
                                recipeGroup = ToolCapabilities.recipeGroupFor(toolId),
                                probeArgv = probeArgv,
                                acceptedExitCodes = acceptedExitCodes,
                                isInstalled = exists,
                                isVerified = true,
                                isFailedExecution = false,
                                lastVerifiedTimestamp = now,
                            ),
                        ),
                    )
                } else {
                    // Persisted verification evidence is withdrawn so a restart cannot restore it.
                    repository?.updateTool(toolId) { tool -> tool.copy(isVerified = false) }
                    SetupOutcome.Failed(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                        reason = if (!exists) {
                            "Tool $toolId binary not found or not executable"
                        } else {
                            "Tool $toolId verification probe failed with exit code $exitCode"
                        },
                    )
                }
            } finally {
                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId && it.status == StepStatus.RUNNING) {
                                it.copy(status = StepStatus.FAILED)
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }.also { _operationOutcome.value = it }
    }

    // ---- Plan-driven operation contract (003 US2) ----
    //
    // Every mutation is previewed by [prepare] and executed only by [confirm]. A plan is derived
    // from the real current state (never mutating anything); confirmation enforces the plan ID /
    // revision invariant, revalidates the live evidence and environment, freezes the Auto Doctor
    // policy carried by the plan, and runs under the single environment operation owner. Every
    // attempt is journaled before submission and reconciled by [recover] (003 US3).

    override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan =
        withContext(dispatcher) {
            val snapshot = _state.value
            val distro = resolveEnvironmentKey()
            val bootstrapPkgs = if (kind == SetupPlanKind.BOOTSTRAP_PACKAGES ||
                targetId == SetupStepStage.SYSTEM_PACKAGES.name
            ) {
                val report = lastPrerequisiteReport ?: (currentEnvironment?.let { hostPrerequisitePort.probe(it) })
                report?.missingPackages ?: emptyList()
            } else {
                emptyList()
            }
            val plan = SetupPlanFactory.createPlan(
                kind = kind,
                targetId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
                snapshot = snapshot,
                environmentKey = distro,
                bootstrapPackages = bootstrapPkgs,
            )
            activePlan = plan
            plan
        }

    @Suppress("CyclomaticComplexMethod") // plan validation, per-kind execution, cancel/failure archiving and post-check
    override suspend fun confirm(
        planId: String,
        revisionHash: String,
    ): SetupOutcome = withContext(dispatcher) {
        val currentPlan = activePlan
        val matches = currentPlan != null &&
            currentPlan.planId == planId &&
            currentPlan.revisionHash == revisionHash
        if (currentPlan == null || !matches) {
            val outcome = SetupOutcome.Failed(
                stage = null,
                reason = "Confirmation requires the displayed plan ID and unchanged revision hash.",
            )
            _operationOutcome.value = outcome
            return@withContext outcome
        }

        // Revalidate revision and environment against the CURRENT state: a plan previewed before a
        // later check (or against another distro) is stale and needs a fresh preview (FR-004).
        val liveRevision = SetupPlanFactory.computeRevisionHash(_state.value, currentPlan.autoDoctorEnabled)
        val liveDistro = resolveEnvironmentKey()
        if (liveRevision != currentPlan.revisionHash || liveDistro != currentPlan.environmentKey) {
            val outcome = SetupOutcome.Failed(
                stage = null,
                reason = "Environment evidence changed since the plan was previewed; preview again before confirming.",
            )
            _operationOutcome.value = outcome
            return@withContext outcome
        }

        val distro = currentPlan.environmentKey

        coordinator.withAdmission(
            environmentKey = distro,
            operationId = currentPlan.planId,
            kind = currentPlan.kind.name,
        ) {
            val journal = openAttempt(
                environmentKey = distro,
                planId = currentPlan.planId,
                revisionHash = currentPlan.revisionHash,
                kind = currentPlan.kind,
                plannedActionIds = currentPlan.orderedActions.map { it.actionId() },
            ).getOrElse {
                return@withAdmission SetupOutcome.Interrupted(it.message ?: "Attempt could not be journaled.")
            }
            val newAttemptId = journal?.attemptId ?: currentPlan.planId

            // The plan is consumed by this attempt: a second confirmation must preview again.
            activePlan = null
            _state.update { it.copy(isRunning = true, activeOperationId = currentPlan.planId) }
            try {
                val planOutcome = when (currentPlan.kind) {
                    SetupPlanKind.CACHE_RESET -> executeCacheReset()
                    SetupPlanKind.BOOTSTRAP_PACKAGES -> executePlan(currentPlan, distro, journal)
                    SetupPlanKind.FULL_SETUP,
                    SetupPlanKind.STAGE_RETRY,
                    SetupPlanKind.REPAIR_TOOL,
                    -> {
                        if (currentPlan.targetStageOrToolId == SetupStepStage.SERVER_CONNECTIVITY.name) {
                            val env = currentEnvironment
                            if (serverArtifactPort != null && env != null) {
                                serverArtifactPort.ensureInstalled(env)
                                // The single Java discovery result; re-probe once rather than guess a path.
                                val jHome = lastPrerequisiteReport?.javaHome
                                    ?: hostPrerequisitePort.probe(env).also { lastPrerequisiteReport = it }.javaHome
                                if (jHome != null) {
                                    serverArtifactPort.ensureRunningCurrent(env, jHome)
                                }
                            } else {
                                supervisor.ensureStarted(distro)
                            }
                        }
                        // Failed health is a terminal prerequisite failure: nothing else runs.
                        val health = transport.checkHealth()
                        if (health == null) {
                            SetupOutcome.Failed(
                                stage = SetupStepStage.SERVER_CONNECTIVITY.name,
                                reason = "Daemon bridge failed: health check returned null.",
                            )
                        } else {
                            executePlan(currentPlan, distro, journal)
                        }
                    }
                }
                archiveAttempt(newAttemptId, planOutcome)
            } catch (ce: CancellationException) {
                // Explicit cancel: the attempt is archived as cancelled only if every submitted child is
                // proven; otherwise it stays journaled for reconciliation ("cancellation acknowledgement
                // alone never unlocks mutation").
                archiveAttempt(newAttemptId, SetupOutcome.Cancelled)
                throw ce
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Transport/adapter failure escaping the plan is a typed Failed, but the attempt is only
                // archived when every submitted child has terminal proof; otherwise it stays journaled and
                // recovery is blocked until reconciled (never a silent retry).
                appendLog("[Plan] ERROR: ${e.message}")
                archiveAttempt(
                    newAttemptId,
                    SetupOutcome.Failed(
                        stage = _state.value.currentStage?.name,
                        reason = e.message ?: "Plan execution failed",
                    ),
                )
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
                publishJournalState()
            }
        }.let { outcome ->
            // "On success verify required facts": readiness after a mutation is measured by a live
            // check (checkedAt/lastReadyAt come from that check), never asserted by the plan.
            if (outcome is SetupOutcome.Succeeded && currentPlan.kind != SetupPlanKind.CACHE_RESET) {
                verifyEnvironment()
            }
            outcome
        }.also {
            _operationOutcome.value = it
        }
    }

    private suspend fun resolveHome(distro: String): String = currentEnvironment?.takeIf { it.distro == distro }?.home
        ?: runCatching {
            val res = transport.execute(
                ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"),
            )
            if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        ?: runInterruptible(dispatcher) { detector.resolveWslUserHome(distro) }

    /**
     * Resumes a package handoff: probes the attempt's own environment (never the current selection),
     * keeps the handoff open with whatever is still missing, and archives it once everything is present.
     */
    private suspend fun resumeBootstrap(
        repo: ToolchainSetupRepository,
        active: SetupAttemptRecord,
        handoff: PersistedUserHandoff,
        planId: String,
        distro: String,
    ): SetupOutcome {
        // The attempt's own journaled environment wins; nothing is guessed (FR-006, FR-007c).
        val env = active.environment?.toDomain()
            ?: currentEnvironment?.takeIf { it.distro == active.environmentKey }
            ?: return SetupOutcome.Interrupted(
                "Attempt '${active.attemptId}' has no recorded environment; run the environment check again.",
            )
        currentEnvironment = env
        val probeReport = hostPrerequisitePort.probe(env)
        lastPrerequisiteReport = probeReport
        val checkFailure = probeReport.results.values.filterIsInstance<RequirementStatus.CheckFailed>().firstOrNull()
        val stillMissing = probeReport.missingPackages.filter { it in handoff.packages }
        return when {
            // An unreadable probe proves nothing: keep the handoff pending instead of archiving it.
            checkFailure != null -> SetupOutcome.Interrupted("Couldn't check packages: ${checkFailure.reason}")
            stillMissing.isNotEmpty() -> {
                val newCommand = AptCommandBuilder.install(stillMissing)
                val persistedPlan = active.persistedPlan ?: PersistedSetupPlan(
                    planId = active.planId,
                    revisionHash = active.planRevisionHash,
                    environmentKey = active.environmentKey,
                    kind = active.planKind,
                )
                repo.recordAttemptAwaitingUserAction(
                    active.attemptId,
                    handoff.copy(packages = stillMissing, command = newCommand),
                    persistedPlan,
                )
                SetupOutcome.AwaitingUserAction(
                    pendingPlanId = planId,
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    reason = "Required packages are still not installed in WSL: ${stillMissing.joinToString()}",
                    handoff = UserRepairHandoff(
                        actionId = handoff.actionId,
                        description = "Required packages are still missing",
                        terminalCommand = newCommand,
                        packages = stillMissing,
                        distro = distro,
                    ),
                )
            }
            else -> {
                setStepStatus(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StepStatus.SUCCESS,
                    desc = "Prerequisite packages installed",
                )
                archiveAttempt(active.attemptId, SetupOutcome.Succeeded())
                SetupOutcome.Succeeded()
            }
        }
    }

    /** A doctor whose checks run through the build service, rooted at the user's home. */
    private fun serviceDoctor(home: String): CompositeSystemDoctorEngine {
        val serviceCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)
        return CompositeSystemDoctorEngine(
            cli = serviceCli,
            detector = detector,
            dispatcher = dispatcher,
            inspectors = CompositeSystemDoctorEngine.defaultInspectors(serviceCli),
            remediator = WslPackageRemediator(serviceCli),
            userHome = home,
        )
    }

    /** Rebuilds the journaled plan so a resumed attempt runs exactly the actions that were previewed. */
    private fun PersistedSetupPlan.toDomainPlan(): SetupPlan = SetupPlan(
        planId = planId,
        revisionHash = revisionHash,
        environmentKey = environmentKey,
        kind = SetupPlanKind.valueOf(kind),
        targetStageOrToolId = targetStageOrToolId,
        autoDoctorEnabled = autoDoctorEnabled,
        orderedActions = actions.map { act ->
            when (act.type) {
                "INSTALL_PACKAGES" -> SetupPlanAction.InstallPackages(packages = act.payload)
                "SYNC_SOURCES" -> SetupPlanAction.SyncSources(submodules = act.payload)
                "BUILD_RECIPES" -> SetupPlanAction.BuildRecipes(recipeGroups = act.payload)
                "PUBLISH_TOOLS" -> SetupPlanAction.PublishTools(toolIds = act.payload)
                else -> error("Unknown action type: ${act.type}")
            }
        },
    )

    @Suppress("CyclomaticComplexMethod")
    override suspend fun resume(planId: String): SetupOutcome = withContext(dispatcher) {
        val distro = resolveEnvironmentKey()
        coordinator.withAdmission(
            environmentKey = distro,
            operationId = planId,
            kind = SetupOperationCoordinator.KIND_RESUME,
        ) {
            val repo = repository ?: return@withAdmission SetupOutcome.Failed(
                stage = null,
                reason = "No repository configured to resume setup.",
            )
            val active = repo.currentState.activeAttempt
            if (active == null ||
                (active.planId != planId && active.attemptId != planId) ||
                active.status != AttemptStatus.AWAITING_USER_ACTION
            ) {
                return@withAdmission SetupOutcome.Failed(
                    stage = null,
                    reason = "No pending attempt matching plan or attempt '$planId' in AWAITING_USER_ACTION state.",
                )
            }
            if (active.environmentKey != distro) {
                return@withAdmission SetupOutcome.Failed(
                    stage = null,
                    reason = "Attempt '${active.attemptId}' was for distro '${active.environmentKey}', not current " +
                        "'$distro'.",
                )
            }
            if (active.unprovenIntents.isNotEmpty()) {
                return@withAdmission SetupOutcome.Interrupted(
                    "Attempt '${active.attemptId}' has unproven commands; reconnect before resuming.",
                )
            }
            val handoff = active.awaitingHandoff ?: return@withAdmission SetupOutcome.Failed(
                stage = null,
                reason = "Attempt '${active.attemptId}' has no handoff details.",
            )

            val isBootstrap = active.planKind == SetupPlanKind.BOOTSTRAP_PACKAGES.name ||
                active.persistedPlan?.kind == SetupPlanKind.BOOTSTRAP_PACKAGES.name ||
                active.bootstrapRequirementIds.isNotEmpty() ||
                (
                    handoff.actionId.contains("install_packages", ignoreCase = true) &&
                        active.planKind == SetupPlanKind.BOOTSTRAP_PACKAGES.name
                    )

            if (isBootstrap) return@withAdmission resumeBootstrap(repo, active, handoff, planId, distro)

            // Re-run diagnostics to verify package installation facts
            val diagnostics = serviceDoctor(resolveHome(distro)).runDiagnostics(distro, binariesPresent = false)
            _state.update { it.copy(diagnostics = diagnostics) }

            val remainingRemediation = WslPackageRemediator.resolve(diagnostics)
            val stillMissing = remainingRemediation.aptPackages.filter { it in handoff.packages }
            if (stillMissing.isNotEmpty()) {
                appendLog("[Resume] Required packages still missing: ${stillMissing.joinToString()}")
                return@withAdmission SetupOutcome.AwaitingUserAction(
                    pendingPlanId = planId,
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                    reason = "Required packages are still not installed in WSL: ${stillMissing.joinToString()}",
                    handoff = UserRepairHandoff(
                        actionId = handoff.actionId,
                        description = "Required packages are still missing",
                        terminalCommand = handoff.command,
                        packages = stillMissing,
                        distro = distro,
                    ),
                )
            }

            // Require all relevant diagnostic checks to pass before continuing.
            // If another required check failed during the pause, do not mark success or continue.
            val failed = diagnostics.filter { it.status == StepStatus.FAILED }
            val nonOptionalWarnings = diagnostics.filter { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
            val criticalProblems = failed + nonOptionalWarnings
            if (criticalProblems.isNotEmpty()) {
                val problemNames = criticalProblems.joinToString { "${it.title}: ${it.detail}" }
                appendLog(
                    "[Resume] ERROR: Environment became unhealthy while paused. Critical pre-flight diagnostics " +
                        "failed: $problemNames",
                )
                setStepStatus(
                    SetupStepStage.SYSTEM_DIAGNOSTICS,
                    StepStatus.FAILED,
                    desc = "Environment check failed before resumption: $problemNames",
                    error = problemNames,
                )
                return@withAdmission SetupOutcome.Failed(
                    stage = SetupStepStage.SYSTEM_DIAGNOSTICS.name,
                    reason = "Environment check failed before resumption: $problemNames",
                )
            }

            // Mark the handoff action as completed!
            repo.recordPlanActionCompleted(active.attemptId, handoff.actionId).onFailure { failure ->
                return@withAdmission SetupOutcome.Interrupted("Failed to record action completion: ${failure.message}")
            }
            repo.updateAttemptStatus(active.attemptId, AttemptStatus.RUNNING).onFailure { failure ->
                return@withAdmission SetupOutcome.Interrupted("Failed to update attempt status: ${failure.message}")
            }

            val anyWarn = diagnostics.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
            setStepStatus(
                SetupStepStage.SYSTEM_DIAGNOSTICS,
                if (anyWarn) StepStatus.WARNING else StepStatus.SUCCESS,
                "Host runtimes, compilers, and dependencies verified.",
            )
            repo.updateDiagnosticsSnapshot(
                diagnostics.filter { it.status == StepStatus.SUCCESS }.map { it.id }.toSet(),
            )

            val persistedPlan = active.persistedPlan
            if (persistedPlan == null) {
                return@withAdmission SetupOutcome.Failed(
                    stage = null,
                    reason = "Attempt has no persisted plan to resume.",
                )
            }
            val domainPlan = persistedPlan.toDomainPlan()

            val completedActionIds = (active.completedActionIds + handoff.actionId).toSet()
            val journal = SetupJournalContext(
                environmentKey = distro,
                attemptId = active.attemptId,
                activity = logBuffer,
            )

            _state.update { it.copy(isRunning = true, activeOperationId = active.planId) }
            val resumeOutcome = try {
                val planOutcome = executePlan(domainPlan, distro, journal, actionsToSkip = completedActionIds)
                archiveAttempt(active.attemptId, planOutcome)
            } catch (ce: CancellationException) {
                archiveAttempt(active.attemptId, SetupOutcome.Cancelled)
                throw ce
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                appendLog("[Resume] ERROR: ${e.message}")
                archiveAttempt(
                    active.attemptId,
                    SetupOutcome.Failed(
                        stage = _state.value.currentStage?.name,
                        reason = e.message ?: "Plan resumption failed",
                    ),
                )
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
                publishJournalState()
            }

            if (resumeOutcome is SetupOutcome.Succeeded) {
                verifyEnvironment()
            }
            resumeOutcome
        }.also {
            _operationOutcome.value = it
        }
    }

    /**
     * Clears the persisted setup evidence. Reached only through a confirmed CACHE_RESET plan; there
     * is no direct entry point (FR-003). A cleared state is Unknown, never Ready.
     */
    private suspend fun executeCacheReset(): SetupOutcome {
        // A confirmed CACHE_RESET is the explicit, user-directed authorization to discard an
        // unreconcilable journal (FR-008 "unknown run requires user-directed recovery").
        repository?.clearToolchainState(force = true)?.onFailure { failure ->
            return SetupOutcome.Failed(stage = null, reason = "Cache reset was not persisted: ${failure.message}")
        }
        _state.value = ToolchainSetupState()
        appendLog("[Cache] Reset toolchain persistence cache to clean default.")
        return SetupOutcome.Succeeded(evidence = emptyMap())
    }

    /**
     * Archives the attempt with its typed outcome. Succeeded/Failed/Cancelled are only recorded when every
     * journaled child has authoritative terminal proof; otherwise the attempt stays active for recovery.
     */
    @Suppress("ReturnCount") // no-journal, foreign attempt, unproven children and write failure are distinct verdicts
    private suspend fun archiveAttempt(attemptId: String, outcome: SetupOutcome): SetupOutcome {
        val repo = repository ?: return outcome
        val active = repo.currentState.activeAttempt
        if (active == null || active.attemptId != attemptId) return outcome
        if (outcome is SetupOutcome.AwaitingUserAction) {
            return outcome
        }
        if (active.unprovenIntents.isNotEmpty()) {
            val reason = "Attempt $attemptId ended (${outcome::class.simpleName}) while " +
                "${active.unprovenIntents.size} journaled command(s) lack terminal proof; reconnect to reconcile."
            repo.setRecoveryBlocked(true, reason)
            return SetupOutcome.Interrupted(reason)
        }
        val status = when (outcome) {
            is SetupOutcome.Succeeded -> AttemptStatus.SUCCEEDED
            is SetupOutcome.Cancelled -> if (active.status == AttemptStatus.AWAITING_USER_ACTION &&
                active.planKind == SetupPlanKind.BOOTSTRAP_PACKAGES.name
            ) {
                AttemptStatus.ABANDONED
            } else {
                AttemptStatus.CANCELLED
            }
            is SetupOutcome.Failed -> AttemptStatus.FAILED
            is SetupOutcome.Interrupted -> AttemptStatus.INTERRUPTED
            is SetupOutcome.Busy -> AttemptStatus.INTERRUPTED
            is SetupOutcome.AwaitingUserAction -> return outcome
        }
        repo.recordAttemptTerminal(attemptId, status, outcome).onFailure { failure ->
            return SetupOutcome.Interrupted("Attempt $attemptId finished but could not be archived: ${failure.message}")
        }
        return outcome
    }

    /**
     * Journals a new attempt before anything is submitted ("Journal write failure prevents submission").
     * Returns the journal context, or null when no repository is configured (nothing durable exists, and the
     * engines then refuse durable server runs). Every mutation - confirmed plans and tool repair alike -
     * opens its attempt here, so "explicit retry allocates a new attemptId" holds for all of them.
     */
    private suspend fun openAttempt(
        environmentKey: String,
        planId: String,
        revisionHash: String,
        kind: SetupPlanKind,
        plannedActionIds: List<String>,
    ): Result<SetupJournalContext?> {
        val repo = repository ?: return Result.success(null)
        val attemptId = UUID.randomUUID().toString()
        val record = SetupAttemptRecord(
            attemptId = attemptId,
            environmentKey = environmentKey,
            planId = planId,
            planRevisionHash = revisionHash,
            planKind = kind.name,
            status = AttemptStatus.AUTHORIZED,
            plannedActionIds = plannedActionIds,
            environment = currentEnvironment?.toPersisted(),
        )
        return repo.recordAttemptAuthorized(record).fold(
            onSuccess = {
                publishJournalState()
                Result.success(SetupJournalContext(environmentKey, attemptId, activity = logBuffer))
            },
            onFailure = { failure ->
                Result.failure(
                    IllegalStateException(
                        "Attempt could not be journaled; nothing was started: ${failure.message}",
                        failure,
                    ),
                )
            },
        )
    }

    private fun SetupPlanAction.actionId(): String = when (this) {
        is SetupPlanAction.InstallPackages -> "packages"
        is SetupPlanAction.SyncSources -> "sync-sources"
        is SetupPlanAction.BuildRecipes -> "build-recipes"
        is SetupPlanAction.PublishTools -> "publish-tools"
    }

    private fun recordRunStarted(stage: SetupStepStage, runId: String) {
        _state.update { s ->
            s.copy(
                stepRunIds = s.stepRunIds + (stage to runId),
                steps = s.steps.map { if (it.stage == stage) it.copy(runId = runId) else it },
            )
        }
    }

    /**
     * Executes the confirmed plan's actions in order. All mutations go through the daemon service
     * transport ([TransportCliExecutor]) - never the raw host `wsl.exe` executor - and every step's
     * status comes from a verified fact (re-run diagnostics, engine result, real listing), never from
     * a discarded Boolean or a command that merely returned.
     */
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
    private suspend fun executePlan(
        currentPlan: SetupPlan,
        distro: String,
        journal: SetupJournalContext?,
        actionsToSkip: Set<String> = emptySet(),
    ): SetupOutcome {
        val home = resolveHome(distro)
        val toolsRoot = "$home/LtiRomTools"
        val extDir = "$toolsRoot/external"
        val binDir = "$toolsRoot/bin"
        val workDir = "$home/LtiRomWorkDir"
        val serviceCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)

        val targetTool = currentPlan.targetStageOrToolId
        if (currentPlan.kind == SetupPlanKind.REPAIR_TOOL) {
            if (targetTool == null || !ToolCapabilities.isRecipeSupported(targetTool)) {
                val reason = ToolCapabilities.unavailableReasonFor(targetTool ?: "")
                    ?: ToolCapabilities.REPAIR_UNAVAILABLE_REASON
                appendLog("[Tool Matrix] Repair unavailable for '$targetTool': $reason")
                return SetupOutcome.Failed(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                    reason = reason,
                )
            }
            val group = ToolCapabilities.recipeGroupFor(targetTool)
            if (group != null) {
                val affectedTools = ToolCapabilities.toolsForRecipeGroup(group)
                // Invalidate only owning recipe group tools in repository and state (FR-009)
                for (toolId in affectedTools) {
                    repository?.updateTool(toolId) { it.copy(isVerified = false) }
                }
                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id in affectedTools) {
                                it.copy(
                                    status = if (it.id == targetTool) StepStatus.RUNNING else StepStatus.PENDING,
                                    lastTested = null,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }
            }
        }

        for (action in currentPlan.orderedActions) {
            if (action.actionId() in actionsToSkip) {
                appendLog("[Plan] Skipping completed action: ${action.actionId()}")
                continue
            }
            when (action) {
                is SetupPlanAction.InstallPackages -> {
                    // "Auto Doctor policy is immutable for the confirmed attempt": the plan carried the
                    // policy at preview time; a plan built with the policy off never contains this action.
                    if (action.packages.isNotEmpty() || action.configureLoopMountElevation) {
                        val handoffPackages = action.packages.filter { RequirementCatalog.isPackageApproved(it) }
                        val pkgString = handoffPackages.joinToString(" ")
                        val terminalCommand = if (handoffPackages.isNotEmpty()) {
                            AptCommandBuilder.install(handoffPackages)
                        } else {
                            "echo 'No elevated package installation required.'"
                        }
                        val handoff = UserRepairHandoff(
                            actionId = action.actionId(),
                            description = action.description,
                            terminalCommand = terminalCommand,
                            packages = handoffPackages,
                            distro = distro,
                        )
                        val persistedHandoff = PersistedUserHandoff(
                            actionId = action.actionId(),
                            distro = distro,
                            command = terminalCommand,
                            packages = handoffPackages,
                            timestampEpochMs = System.currentTimeMillis(),
                        )
                        val persistedPlan = PersistedSetupPlan(
                            planId = currentPlan.planId,
                            revisionHash = currentPlan.revisionHash,
                            environmentKey = currentPlan.environmentKey,
                            kind = currentPlan.kind.name,
                            targetStageOrToolId = currentPlan.targetStageOrToolId,
                            autoDoctorEnabled = currentPlan.autoDoctorEnabled,
                            actions = currentPlan.orderedActions.map { act ->
                                when (act) {
                                    is SetupPlanAction.InstallPackages -> PersistedPlanAction(
                                        act.actionId(),
                                        "INSTALL_PACKAGES",
                                        act.packages,
                                    )
                                    is SetupPlanAction.SyncSources -> PersistedPlanAction(
                                        act.actionId(),
                                        "SYNC_SOURCES",
                                        act.submodules,
                                    )
                                    is SetupPlanAction.BuildRecipes -> PersistedPlanAction(
                                        act.actionId(),
                                        "BUILD_RECIPES",
                                        act.recipeGroups,
                                    )
                                    is SetupPlanAction.PublishTools -> PersistedPlanAction(
                                        act.actionId(),
                                        "PUBLISH_TOOLS",
                                    )
                                }
                            },
                        )
                        val repo = repository
                        val targetStage = if (currentPlan.kind ==
                            SetupPlanKind.BOOTSTRAP_PACKAGES
                        ) {
                            SetupStepStage.SYSTEM_PACKAGES
                        } else {
                            SetupStepStage.SYSTEM_DIAGNOSTICS
                        }
                        if (repo == null || journal == null) {
                            appendLog(
                                "[Plan] ERROR: Cannot pause for user authorization without a configured repository.",
                            )
                            setStepStatus(
                                targetStage,
                                StepStatus.FAILED,
                                desc = "No repository configured to persist handoff",
                                error = "No repository configured to persist handoff",
                            )
                            return SetupOutcome.Failed(
                                stage = targetStage.name,
                                reason = "User authorization handoff requires a configured repository to survive " +
                                    "and resume.",
                            )
                        }
                        val recordResult = repo.recordAttemptAwaitingUserAction(
                            journal.attemptId,
                            persistedHandoff,
                            persistedPlan,
                        )
                        if (recordResult.isFailure) {
                            val err = recordResult.exceptionOrNull()?.message ?: "Unknown journal error"
                            appendLog("[Plan] Journal write failed while recording pending handoff: $err")
                            return SetupOutcome.Interrupted(
                                "Journal write failed while recording pending handoff: $err",
                            )
                        }
                        setStepStatus(
                            targetStage,
                            StepStatus.WARNING,
                            "Awaiting external authorization in WSL terminal: $terminalCommand",
                        )
                        appendLog("[Plan] Paused for user terminal authorization: $terminalCommand")
                        return SetupOutcome.AwaitingUserAction(
                            pendingPlanId = currentPlan.planId,
                            stage = targetStage,
                            reason = "Privileged packages require installation in WSL terminal: $pkgString",
                            handoff = handoff,
                        )
                    } else {
                        val outcome = executeRemediation(action, distro, home, binDir, serviceCli)
                        if (outcome != null) return outcome
                    }
                }
                is SetupPlanAction.SyncSources -> {
                    setStepRunning(SetupStepStage.REPO_SYNCHRONIZATION)
                    appendLog("[Step 4/5] Ensuring workspace root directory exists at $workDir...")
                    serviceCli.execute(distro, listOf("mkdir", "-p", workDir))
                    appendLog("[Step 4/5] Ensuring tools directory exists at $extDir...")
                    serviceCli.execute(distro, listOf("mkdir", "-p", extDir))

                    val syncEngine = SubmoduleSyncEngine(
                        cli = serviceCli,
                        repository = repository,
                        transport = transport,
                    )
                    val syncRes = syncEngine.syncAll(
                        distro,
                        extDir,
                        journal = journal,
                        onRunStarted = ::recordRunStarted,
                    ) { appendLog(it) }
                    if (syncRes.isFailure) {
                        val err = syncRes.exceptionOrNull()?.message ?: "Failed to synchronize submodules"
                        appendLog("[Step 4/5] ERROR: $err")
                        setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.FAILED, error = err)
                        return SetupOutcome.Failed(SetupStepStage.REPO_SYNCHRONIZATION.name, err)
                    }
                    setStepStatus(
                        SetupStepStage.REPO_SYNCHRONIZATION,
                        StepStatus.SUCCESS,
                        "All ${action.submodules.size} submodules and $workDir ready.",
                    )
                }
                is SetupPlanAction.BuildRecipes -> {
                    setStepRunning(SetupStepStage.TOOLCHAIN_COMPILATION)
                    appendLog("[Step 5/5] Preparing binary directory at $binDir...")
                    serviceCli.execute(distro, listOf("mkdir", "-p", binDir))

                    val buildEngine = ToolchainBuildEngine(
                        cli = serviceCli,
                        repository = repository,
                        transport = transport,
                    )
                    val isRepair = currentPlan.kind == SetupPlanKind.REPAIR_TOOL
                    val buildRes = buildEngine.buildAndDistributeAll(
                        distro = distro,
                        extDir = extDir,
                        binDir = binDir,
                        journal = journal,
                        targetRecipeGroups = if (isRepair) action.recipeGroups.toSet() else null,
                        forceRebuild = isRepair,
                        onRunStarted = ::recordRunStarted,
                    ) { appendLog(it) }
                    if (buildRes.isFailure) {
                        val err =
                            buildRes.exceptionOrNull()?.message
                                ?: "Some core tool binaries are missing in $binDir after build."
                        appendLog("[Step 5/5] ERROR: $err")
                        setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.FAILED, error = err)
                        if (isRepair && targetTool != null) {
                            val group = ToolCapabilities.recipeGroupFor(targetTool)
                            val affected = group?.let { ToolCapabilities.toolsForRecipeGroup(it) } ?: setOf(targetTool)
                            _state.update { s ->
                                s.copy(
                                    toolsMatrix = s.toolsMatrix.map {
                                        if (it.id in affected) it.copy(status = StepStatus.FAILED) else it
                                    },
                                )
                            }
                        }
                        return SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, err)
                    }

                    if (isRepair && targetTool != null) {
                        // Verifies all sibling required tools of that group via domain probe argv + accepted exit codes
                        val group = ToolCapabilities.recipeGroupFor(targetTool)
                        if (group != null) {
                            val siblingTools = ToolCapabilities.toolsForRecipeGroup(group)
                            val requiredTools = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS
                            for (siblingId in siblingTools) {
                                val item = _state.value.toolsMatrix.firstOrNull { it.id == siblingId } ?: continue
                                val binaryPath = "$binDir/${item.binaryName}"
                                val exists = serviceCli.execute(distro, listOf("test", "-x", binaryPath)).exitCode == 0
                                val probeArgv = ToolCapabilities.probeArgumentsFor(siblingId, binaryPath)
                                val acceptedExitCodes = ToolCapabilities.acceptedExitCodesFor(siblingId)
                                val execRes = if (exists) {
                                    serviceCli.execute(distro, probeArgv, timeoutSeconds = 5)
                                } else {
                                    null
                                }
                                val isOk = exists && execRes != null && execRes.exitCode in acceptedExitCodes

                                _state.update { s ->
                                    s.copy(
                                        toolsMatrix = s.toolsMatrix.map {
                                            if (it.id == siblingId) {
                                                it.copy(
                                                    status = if (isOk) StepStatus.SUCCESS else StepStatus.FAILED,
                                                    path = if (exists) binaryPath else null,
                                                    lastTested = if (isOk) "Just now" else null,
                                                )
                                            } else {
                                                it
                                            }
                                        },
                                    )
                                }
                                if (isOk) {
                                    repository?.updateTool(siblingId) { tool ->
                                        tool.copy(
                                            isVerified = true,
                                            isCompiled = true,
                                            binaryPath = binaryPath,
                                            lastVerifiedTimestamp = System.currentTimeMillis(),
                                        )
                                    }
                                } else if (siblingId in requiredTools || siblingId == targetTool) {
                                    setStepStatus(
                                        SetupStepStage.TOOLCHAIN_COMPILATION,
                                        StepStatus.FAILED,
                                        "Tool $siblingId verification probe failed",
                                    )
                                    return SetupOutcome.Failed(
                                        stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                                        reason = "Tool $siblingId verification probe failed",
                                    )
                                }
                            }
                        }
                    }

                    setStepStatus(
                        SetupStepStage.TOOLCHAIN_COMPILATION,
                        StepStatus.SUCCESS,
                        "Core tools ready in $binDir.",
                    )
                }
                is SetupPlanAction.PublishTools -> {
                    val pubRes = toolPublicationPort?.publish(binDir, action.toolIds.toSet())
                    val publishedIds = pubRes?.registered ?: emptySet()
                    val unpublishedIds = pubRes?.failed?.keys ?: emptySet()
                    _state.update {
                        it.copy(
                            publishedToolIds = publishedIds,
                            unpublishedToolIds = unpublishedIds,
                        )
                    }
                    if (pubRes != null && !pubRes.isComplete) {
                        val failureReasons = pubRes.failed.entries.joinToString(", ") { "${it.key}: ${it.value}" }
                        setStepStatus(
                            SetupStepStage.TOOLCHAIN_COMPILATION,
                            StepStatus.FAILED,
                            desc = "Tool publication incomplete: $failureReasons",
                            error = "Failed to register tools: $failureReasons",
                            toolchainFailureCategory = ToolchainFailureCategory.MISSING_PUBLISHED_TOOLS,
                        )
                        appendLog("[Step 5/5] ERROR: Failed to publish tools: $failureReasons")
                        return SetupOutcome.Failed(
                            stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                            reason = "Tool publication incomplete: $failureReasons",
                        )
                    }
                    appendLog(
                        "[Step 5/5] Published ${publishedIds.size} tools to daemon.",
                    )
                }
            }
            if (journal != null) {
                repository?.recordPlanActionCompleted(journal.attemptId, action.actionId())?.onFailure { failure ->
                    val reason = "Plan action '${action.actionId()}' completed but could not be journaled: " +
                        failure.message
                    appendLog("[Journal] ERROR: $reason")
                    return SetupOutcome.Interrupted(reason)
                }
            }
        }

        // Installed evidence from a real listing of the binary directory. This is file discovery
        // only: it updates paths and installed status, and never overrides a failed execution probe.
        val builtExecutables = listExecutables(distro, binDir, serviceCli)
        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val now = System.currentTimeMillis()
        val updatedTools = currentTools.map { tool ->
            val toolPath = "$binDir/${tool.binaryName}"
            val exists = tool.binaryName in builtExecutables
            if (exists) {
                repository?.updateTool(tool.id) { t ->
                    t.copy(isCompiled = true, binaryPath = toolPath)
                }
            }
            tool.copy(
                path = if (exists) toolPath else null,
                status = when {
                    !exists -> tool.status
                    tool.status == StepStatus.FAILED -> StepStatus.FAILED
                    else -> StepStatus.SUCCESS
                },
            )
        }
        _state.update { it.copy(toolsMatrix = updatedTools, lastVerifiedTimestamp = now) }
        if (currentPlan.kind == SetupPlanKind.FULL_SETUP) {
            repository?.markSetupCompleted(true, now)
        }

        val evidenceMap = updatedTools
            .filter { it.status == StepStatus.SUCCESS }
            .associate { tool ->
                tool.id to ToolEvidence(
                    toolId = tool.id,
                    recipeGroup = ToolCapabilities.recipeGroupFor(tool.id),
                    probeArgv = ToolCapabilities.probeArgumentsFor(tool.id, "$binDir/${tool.binaryName}"),
                    acceptedExitCodes = ToolCapabilities.acceptedExitCodesFor(tool.id),
                    isInstalled = true,
                    isVerified = tool.lastTested != null,
                    isFailedExecution = false,
                    lastVerifiedTimestamp = now,
                )
            }
        appendLog("=== Plan ${currentPlan.planId} (${currentPlan.kind}) completed ===")
        return SetupOutcome.Succeeded(evidence = evidenceMap)
    }

    /**
     * Runs the previewed remediation through the service transport and verifies the result by
     * re-running the doctor. Returns a typed failure, or null when the diagnostics stage is healthy.
     */
    private suspend fun executeRemediation(
        action: SetupPlanAction.InstallPackages,
        distro: String,
        home: String,
        binDir: String,
        serviceCli: TransportCliExecutor,
    ): SetupOutcome? {
        setStepRunning(SetupStepStage.SYSTEM_DIAGNOSTICS)
        val remediation = WslPackageRemediator.RemediationPlan(
            aptPackages = action.packages,
            pipPackages = action.pipPackages,
            configureLoopMountElevation = action.configureLoopMountElevation,
            syncToolchainBinaries = false,
        )
        appendLog("[Step 3/5] Applying previewed remediation: ${action.description}")
        val context = DiagnosticContext(distro = distro, home = home, binDir = binDir, binariesPresent = false)
        val applied = WslPackageRemediator(serviceCli).remediate(context, remediation) { appendLog("[Step 3/5] $it") }
        if (!applied) {
            return diagnosticsFailure("Automated remediation could not run (passwordless sudo is not configured).")
        }

        // Verify the fact, do not trust the install command's return.
        val doctor = CompositeSystemDoctorEngine(
            cli = serviceCli,
            detector = detector,
            dispatcher = dispatcher,
            inspectors = CompositeSystemDoctorEngine.defaultInspectors(serviceCli),
            remediator = WslPackageRemediator(serviceCli),
            userHome = home,
        )
        val diagnostics = doctor.runDiagnostics(distro, binariesPresent = false)
        _state.update { it.copy(diagnostics = diagnostics) }
        val failed = diagnostics.filter { it.status == StepStatus.FAILED }
        val anyWarn = diagnostics.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
        return if (failed.isNotEmpty()) {
            val failedNames = failed.joinToString { it.title }
            diagnosticsFailure("Critical pre-flight requirements still not met after remediation: $failedNames.")
        } else {
            setStepStatus(
                SetupStepStage.SYSTEM_DIAGNOSTICS,
                if (anyWarn) StepStatus.WARNING else StepStatus.SUCCESS,
                "Host runtimes, compilers, and dependencies verified.",
            )
            repository?.updateDiagnosticsSnapshot(
                diagnostics.filter { it.status == StepStatus.SUCCESS }.map { it.id }.toSet(),
            )
            null
        }
    }

    private fun diagnosticsFailure(reason: String): SetupOutcome.Failed {
        appendLog("[Step 3/5] ERROR: $reason")
        setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, StepStatus.FAILED, error = reason)
        return SetupOutcome.Failed(SetupStepStage.SYSTEM_DIAGNOSTICS.name, reason)
    }

    override fun observe(): Flow<SetupOutcome?> = _operationOutcome.asStateFlow()

    override fun observeActivity(): Flow<SetupLogEvent> = logBuffer.observe()

    override suspend fun recover(attemptId: String?): SetupOutcome = withContext(dispatcher) {
        val distro = resolveEnvironmentKey()
        val engine = recovery
            ?: return@withContext SetupOutcome.Failed(
                stage = null,
                reason = "No durable journal is configured; nothing to recover.",
            )
        val pending = repository?.currentState?.activeAttempt?.attemptId
        coordinator.withAdmission(
            environmentKey = distro,
            operationId = attemptId ?: pending ?: "recovery",
            kind = SetupOperationCoordinator.KIND_RECOVERY,
        ) {
            _state.update { it.copy(isRunning = true, activeOperationId = attemptId ?: pending) }
            try {
                engine.recover(distro, attemptId) { appendLog(it) }
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
                publishJournalState()
            }
        }.let { outcome ->
            // Reconciled work is measured, never asserted: readiness comes from a live check afterwards.
            if (outcome is SetupOutcome.Succeeded) verifyEnvironment()
            outcome
        }.also { _operationOutcome.value = it }
    }

    override suspend fun cancel(): SetupOutcome = withContext(dispatcher) {
        val active = repository?.currentState?.activeAttempt
        if (active != null && active.status == AttemptStatus.AWAITING_USER_ACTION) {
            val outcome = archiveAttempt(active.attemptId, SetupOutcome.Cancelled)
            publishJournalState()
            _operationOutcome.value = outcome
            return@withContext outcome
        }
        val outcome = coordinator.cancelActiveOperation(resolveEnvironmentKey())
        _operationOutcome.value = outcome
        outcome
    }

    private fun setStepStatus(
        stage: SetupStepStage,
        status: StepStatus,
        desc: String? = null,
        error: String? = null,
        failureCategory: DaemonFailureCategory? = null,
        toolchainFailureCategory: ToolchainFailureCategory? = null,
    ) {
        _state.update { s ->
            val updatedSteps = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        status = status,
                        description = desc ?: step.description,
                        error = error,
                        provenance = StepProvenance.LIVE,
                        failureCategory = failureCategory,
                        toolchainFailureCategory = toolchainFailureCategory,
                    )
                } else {
                    step
                }
            }
            s.copy(
                steps = updatedSteps,
                currentStage = if (status == StepStatus.RUNNING) stage else null,
            )
        }
    }

    /**
     * Provisioner-originated narration (not server run output). Streamed run output reaches
     * [logBuffer] through [SetupJournalContext.activity] with its real journal identity instead.
     */
    private fun appendLog(line: String) {
        val attemptId = repository?.currentState?.activeAttempt?.attemptId ?: LOCAL_ATTEMPT_ID
        logBuffer.append(text = line, attemptId = attemptId, childRunId = PROVISIONER_CHILD_ID)
        publishLogTail(line)
    }

    /** Keeps only a short tail on the immutable snapshot; the full bounded history lives in [logBuffer]. */
    private fun publishLogTail(line: String) {
        _state.update { s ->
            s.copy(
                logs = (s.logs + line).takeLast(STATE_LOG_TAIL_LINES),
                activeLogLine = line,
            )
        }
    }
}
