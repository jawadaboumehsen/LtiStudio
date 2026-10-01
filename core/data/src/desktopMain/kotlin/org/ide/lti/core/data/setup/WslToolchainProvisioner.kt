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

import io.ltirom.tooling.client.wsl.TransportCliExecutor
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
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
import org.ide.lti.core.data.setup.check.PackagesPreparationResult
import org.ide.lti.core.data.setup.check.ServerPreparationResult
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.check.WslPreparationResult
import org.ide.lti.core.data.setup.doctor.CompositeSystemDoctorEngine
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.WslPackageRemediator
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.handlers.InstallPackagesHandler
import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.data.setup.state.InstalledScope
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolEvidence
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
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
    servicePreparation: ServicePreparation? = null,
    planExecutor: PlanExecutor? = null,
    recoveryCoordinator: RecoveryCoordinator? = null,
    private val toolchainLayoutV2: Boolean = false,
) : ToolchainProvisioningService {

    private val servicePreparation: ServicePreparation = servicePreparation ?: ServicePreparation(
        hostPrerequisitePort = hostPrerequisitePort,
        supervisor = supervisor,
        transport = transport,
        serverArtifactPort = serverArtifactPort,
        dispatcher = dispatcher,
    )
    private val planExecutor: PlanExecutor = planExecutor ?: PlanExecutor(
        installPackagesHandler = InstallPackagesHandler(
            remediator = { action, ctx ->
                val sCli = ctx.serviceCli as? TransportCliExecutor
                if (sCli != null) {
                    executeRemediation(action, ctx.distro, ctx.home, ctx.binDir, sCli)
                } else {
                    null
                }
            },
        ),
    )

    /** Bounded activity history (FR-011); run output is recorded here with real journal identity. */
    private val logBuffer = SetupLogBuffer(onRecorded = { event -> publishLogTail(event.text) })

    private val recoveryCoordinator: RecoveryCoordinator = recoveryCoordinator ?: RecoveryCoordinator(
        transport = transport,
        supervisor = supervisor,
        repository = repository,
        activity = logBuffer,
        dispatcher = dispatcher,
    )

    private var currentEnvironment: SetupEnvironment? = null

    /** Distro the user picked on the WSL row; wins over automatic selection while it stays usable. */
    @Volatile
    private var preferredDistro: String? = null

    private var lastPrerequisiteReport: org.ide.lti.core.model.setup.PrerequisiteReport? = null
    private var activeVerificationDeferred: Deferred<ToolchainSetupState>? = null
    private val verificationMutex = Mutex()

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
            pendingAttempt = persisted.activeAttempt,
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
            pendingAttempt = journal.pendingAttempt,
            recoveryBlockReason = journal.recoveryBlockReason,
        )
    }

    /** Mirrors the journal's active-attempt / block state into the observable setup state. */
    private fun publishJournalState() {
        val persisted = repository?.currentState ?: return
        _state.update {
            it.copy(
                pendingAttemptId = persisted.activeAttempt?.attemptId,
                pendingAttempt = persisted.activeAttempt,
                recoveryBlockReason = persisted.recoveryBlockReason?.takeIf { _ -> persisted.recoveryBlocked },
            )
        }
    }

    public companion object {
        private const val STATE_LOG_TAIL_LINES: Int = 100
        private const val LOCAL_ATTEMPT_ID: String = "local"
        private const val PROVISIONER_CHILD_ID: String = "provisioner"
        private const val OWNER_READ: Int = 0x100 // 0400
        private const val OWNER_EXECUTE: Int = 0x40 // 0100

        public val SUBMODULES: List<SubmoduleSyncEngine.PinnedSubmodule> get() = SubmoduleSyncEngine.DEFAULT_SUBMODULES

        public val CORE_BINARIES: List<String> get() = ToolCatalog.CORE_BINARIES

        public val ALL_CORE_TOOLS: List<String> get() = ToolCatalog.ALL_CORE_TOOLS

        public val REQUIRED_DEV_PACKAGES: List<String> = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES

        public val SUPPORTED_RECIPES: Set<String> get() = ToolCapabilities.SUPPORTED_TOOL_IDS

        public fun isRecipeSupported(toolId: String): Boolean = ToolCapabilities.isRecipeSupported(toolId)

        /** Names from `find -printf "%m %f\n"` lines that are usable tools: a JAR readable, the rest executable. */
        internal fun usableToolFiles(listing: String): Set<String> = listing.lineSequence().mapNotNull { line ->
            val mode = line.substringBefore(' ').toIntOrNull(radix = 8) ?: return@mapNotNull null
            val name = line.substringAfter(' ').trim()
            val usable = if (name.endsWith(".jar")) mode and OWNER_READ != 0 else mode and OWNER_EXECUTE != 0
            name.takeIf { usable && it.isNotEmpty() }
        }.toSet()
    }

    /**
     * Names of the usable tool files in [binDir] (symlinks followed) via a single `find` call, by the same
     * rule as the probe: a JAR must be readable (it runs as `java -jar`), anything else executable.
     */
    private suspend fun listExecutables(distro: String, binDir: String, executor: WslCliExecutor): Set<String> {
        val result = runInterruptible(dispatcher) {
            executor.execute(
                distro,
                listOf("find", "-L", binDir, "-maxdepth", "1", "-type", "f", "-printf", "%m %f\n"),
            )
        }
        return if (result.exitCode == 0) usableToolFiles(result.output) else emptySet()
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
        val environment = runCheckStage(SetupStepStage.WSL_DETECTION) { verifyWslStage() } ?: return
        val packagesStage = SetupStepStage.SYSTEM_PACKAGES
        val javaHome = runCheckStage(packagesStage) {
            verifySystemPackagesStage(environment)
        } ?: return
        val serverOk = runCheckStage(SetupStepStage.SERVER_CONNECTIVITY) { verifyServerStage(environment, javaHome) }
        if (serverOk) {
            verifyBuildEnvironment(environment)
        }
    }

    private suspend fun <T> runCheckStage(stage: SetupStepStage, block: suspend () -> T): T {
        appendLog("[check] ${stage.displayName}: started", SetupLogKind.CHECK)
        val startMs = System.currentTimeMillis()
        return try {
            val result = block()
            val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val step = _state.value.steps.firstOrNull { it.stage == stage }
            val desc = step?.description ?: "ok"
            appendLog("[check] ${stage.displayName}: $desc ($durationMs ms)", SetupLogKind.CHECK)
            if (step?.status == StepStatus.FAILED) {
                appendLog("[check] ${stage.displayName} ERROR: ${step.error ?: desc}", SetupLogKind.ERROR)
            }
            _state.update { s ->
                s.copy(steps = s.steps.map { if (it.stage == stage) it.copy(durationMs = durationMs) else it })
            }
            result
        } catch (ce: CancellationException) {
            throw ce
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            val durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            appendLog("[check] ${stage.displayName}: failed ($durationMs ms)", SetupLogKind.CHECK)
            appendLog("[check] ${stage.displayName} ERROR: ${e.message ?: "error"}", SetupLogKind.ERROR)
            throw e
        }
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
        val res = servicePreparation.verifyWsl(
            preferredDistro = preferredDistro,
            persistedDistro = repository?.currentState?.activeAttempt?.environment?.distro,
            currentEnvironment = currentEnvironment,
            alreadyVerifiedWsl = alreadyVerifiedWsl,
            fallbackDistros = _state.value.installedDistros,
        )
        return when (res) {
            is WslPreparationResult.Failure -> {
                _state.update { it.copy(distroStatuses = res.detectionStatuses) }
                setStepStatus(
                    SetupStepStage.WSL_DETECTION,
                    StepStatus.FAILED,
                    desc = res.description,
                    error = res.error,
                )
                shortCircuitRemaining(SetupStepStage.WSL_DETECTION, "Pending WSL.")
                null
            }
            is WslPreparationResult.Success -> {
                currentEnvironment = res.environment
                _state.update {
                    it.copy(
                        distroStatuses = res.detectionStatuses.ifEmpty { it.distroStatuses },
                        activeDistro = res.environment.distro,
                        installedDistros = res.installedDistros,
                    )
                }
                setStepStatus(
                    SetupStepStage.WSL_DETECTION,
                    StepStatus.SUCCESS,
                    desc = "WSL 2 active with distribution '${res.environment.distro}'.",
                )
                res.environment
            }
        }
    }

    /** SYSTEM_PACKAGES: checks host prerequisites without the build service; returns the Java 21 home. */
    private suspend fun verifySystemPackagesStage(selectedEnv: SetupEnvironment): String? {
        setStepRunning(SetupStepStage.SYSTEM_PACKAGES)
        val res = servicePreparation.verifySystemPackages(selectedEnv)
        lastPrerequisiteReport = res.prerequisiteReport
        return when (res) {
            is PackagesPreparationResult.Failure -> {
                setStepStatus(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StepStatus.FAILED,
                    desc = res.description,
                    error = res.error,
                )
                shortCircuitRemaining(SetupStepStage.SYSTEM_PACKAGES, "Pending system packages.")
                null
            }
            is PackagesPreparationResult.Success -> {
                setStepStatus(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StepStatus.SUCCESS,
                    desc = "All prerequisite packages installed · Java: ${res.javaHome}",
                )
                res.javaHome
            }
        }
    }

    /** SERVER_CONNECTIVITY: installs/starts the build service and checks its health. */
    private suspend fun verifyServerStage(selectedEnv: SetupEnvironment, javaHome: String): Boolean {
        setStepRunning(SetupStepStage.SERVER_CONNECTIVITY)
        val res = servicePreparation.verifyServer(selectedEnv, javaHome)
        return when (res) {
            is ServerPreparationResult.Success -> {
                val pidSuffix = if (res.connPid != null) " · pid ${res.connPid}" else ""
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.SUCCESS,
                    desc = "Connected · ${res.pingMs}ms$pidSuffix",
                )
                _state.update { it.copy(daemonPingMs = res.pingMs) }
                true
            }
            is ServerPreparationResult.Outdated -> {
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.WARNING,
                    desc = "Update waiting: build service ${res.runningVersion ?: "(old)"} is finishing " +
                        "${res.runningActiveRuns} active run(s); " +
                        "${res.activeVersion ?: "the new version"} starts after.",
                )
                shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending build service update.")
                false
            }
            is ServerPreparationResult.Failure -> {
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.FAILED,
                    desc = res.description,
                    error = res.error,
                    failureCategory = res.failureCategory,
                )
                shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending daemon.")
                false
            }
        }
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

        // One inventory rule everywhere (JARs readable, the rest executable): see listExecutables.
        val executables = listExecutables(distro, binDir, transportCli)
        val missingBins = CORE_BINARIES.filterNot { it in executables }
        val binariesPresent = missingBins.isEmpty()

        val diagnosticsList = runCheckStage(SetupStepStage.SYSTEM_DIAGNOSTICS) {
            runDoctorStage(transportCli, home, distro, binariesPresent)
        }
        runCheckStage(SetupStepStage.REPO_SYNCHRONIZATION) {
            verifyRepoStage(extDir, workDir)
        }
        val publishedTools = runCheckStage(SetupStepStage.TOOLCHAIN_COMPILATION) {
            verifyToolchainStage(missingBins, executables, binDir)
        }

        // AVB Key Check
        val testAvb = transport.execute(
            ToolExecutionRequest(
                toolId = "test",
                arguments = listOf("-f", avbKeyFile),
                workingDirectory = "/",
                purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
            ),
        )
        val hasAvbKey = testAvb.exitCode == 0

        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val updatedTools = currentTools.map { tool ->
            val toolPath = ToolCatalog.expectedPath(binDir, tool.id) ?: "$binDir/${tool.binaryName}"
            val exists = toolPath.substringAfterLast('/') in executables
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
        val diagnosticsList = doctorEngine.runDiagnostics(
            distro = distro,
            binariesPresent = binariesPresent,
            onProgress = { title, idx, total ->
                setStepProgress(SetupStepStage.SYSTEM_DIAGNOSTICS, title, idx, total)
            },
        )
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
                purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
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
                purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
            ),
        )
        val workDirExists = testWorkDir.exitCode == 0
        val missingSubs = SUBMODULES.filter { it.name !in subdirs }
        // Present is not enough: each must be its own clone at its pinned commit (an imported folder inside a
        // larger repository, or another commit, builds something nobody pinned).
        val notAtPin = SUBMODULES.filter { it.name in subdirs }.filterNot { sub ->
            val dir = "$extDir/${sub.name}"
            val res = transport.execute(
                ToolExecutionRequest(
                    toolId = "git",
                    arguments = listOf("-C", dir, "rev-parse", "--show-toplevel", "HEAD"),
                    workingDirectory = "/",
                    purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
                ),
            )
            val lines = res.stdout.lines().map { it.trim() }
            res.exitCode == 0 &&
                lines.getOrNull(0)?.trimEnd('/') == dir.trimEnd('/') &&
                lines.getOrNull(1) == sub.commit
        }
        val repoStatus = when {
            notAtPin.isNotEmpty() -> StepStatus.FAILED
            missingSubs.isEmpty() && workDirExists -> StepStatus.SUCCESS
            else -> StepStatus.PENDING
        }
        val repoDesc = when {
            notAtPin.isNotEmpty() -> {
                val subject = if (notAtPin.size ==
                    1
                ) {
                    "1 source repository is"
                } else {
                    "${notAtPin.size} source repositories are"
                }
                "$subject not at the pinned commit: ${notAtPin.joinToString { it.name }}"
            }
            missingSubs.isEmpty() && workDirExists -> "${SUBMODULES.size} submodules · ~/LtiRomWorkDir"
            !workDirExists -> "Workspace directory $workDir not created yet."
            else -> "${missingSubs.size} submodule(s) missing in $extDir."
        }
        if (notAtPin.isNotEmpty()) {
            setStepStatus(
                SetupStepStage.REPO_SYNCHRONIZATION,
                repoStatus,
                desc = repoDesc,
                error =
                notAtPin.joinToString("\n") { "${it.name}: expected its own clone of ${it.url} at ${it.commit}" } +
                    "\nRetry syncs them: a folder that is not its own clone is moved aside (not deleted) " +
                    "and cloned fresh.",
            )
            return
        }
        setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, repoStatus, desc = repoDesc)
    }

    /** TOOLCHAIN_COMPILATION: binaries, published tools and mkfs.erofs; returns the published tool IDs. */
    private suspend fun verifyToolchainStage(
        missingBins: List<String>,
        executables: Set<String>,
        binDir: String,
    ): Set<String> {
        setStepRunning(SetupStepStage.TOOLCHAIN_COMPILATION)
        // A rescan, not the service's cached list: manifests written by an interrupted publish count.
        val registryResult = toolPublicationPort?.resolvedTools()
            ?: ToolStatusMapper.toDomain(transport.refreshTools())
        val isServiceError = registryResult is ToolRegistryResult.ServiceError
        val publishedTools = when (registryResult) {
            // Same rule as publication: resolved from this app's manifest at its bin path, not a fallback.
            is ToolRegistryResult.Tools ->
                registryResult.list
                    .filter { ToolCatalog.isProjectPublished(it, binDir) }
                    .map { it.tool }
                    .toSet()
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

        val outdated = if (isServiceError) emptyList() else markOutdatedPinnedReleases(executables, binDir)

        // Check EROFS compatibility only when binaries and published tools are present
        val erofsCompatible = if (!isServiceError && missingBins.isEmpty() && missingPublished.isEmpty()) {
            val erofsCheck = transport.execute(
                ToolExecutionRequest(
                    toolId = "mkfs.erofs",
                    arguments = ToolCapabilities.probeArgumentsFor("mkfs.erofs"),
                    workingDirectory = "/",
                    purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
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
            outdated.isNotEmpty() -> ToolchainOutcome(
                StepStatus.FAILED,
                "${outdated.first().toolId} outdated",
                "Not the pinned download: " + outdated.joinToString { "${it.toolId} (expected ${it.version})" } +
                    ". Retry installs the pinned versions.",
                ToolchainFailureCategory.MISSING_BINARIES,
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

    /**
     * Pinned downloads in [binDir] whose checksum is not their pin's (e.g. an app update moved the pin). A tool
     * that is not installed is reported as missing elsewhere, not here.
     */
    private suspend fun markOutdatedPinnedReleases(executables: Set<String>, binDir: String): List<PinnedRelease> {
        val outdated = PinnedReleases.ALL.filter { it.toolId in executables }.filter { release ->
            val res = transport.execute(
                ToolExecutionRequest(
                    toolId = "sha256sum",
                    arguments = listOf("$binDir/${release.toolId}"),
                    workingDirectory = "/",
                    purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
                ),
            )
            res.stdout.trim().substringBefore(' ') != release.sha256
        }
        // Marked failed so the Toolchain retry reinstalls them (it installs whatever differs from the pin).
        val ids = outdated.map { it.toolId }.toSet()
        if (ids.isNotEmpty()) {
            _state.update { s ->
                s.copy(
                    toolsMatrix = s.toolsMatrix.map { if (it.id in ids) it.copy(status = StepStatus.FAILED) else it },
                )
            }
        }
        return outdated
    }

    /** Free bytes under [home] from `df`, with when they were measured; nulls when `df` fails. */
    private suspend fun measureStorage(home: String): Pair<Long?, Long?> {
        val storageDf = runCatching {
            transport.execute(
                ToolExecutionRequest(
                    toolId = "df",
                    arguments = listOf("-B1", home),
                    workingDirectory = "/",
                    purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
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
        val now = System.currentTimeMillis()
        _state.update { s ->
            val updatedSteps = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        status = StepStatus.RUNNING,
                        provenance = StepProvenance.LIVE,
                        startedAt = step.startedAt ?: now,
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

    override fun setStepProgress(stage: SetupStepStage, currentItem: String, index: Int, total: Int) {
        val desc = when (stage) {
            SetupStepStage.SYSTEM_DIAGNOSTICS -> "Checking $index of $total: $currentItem"
            SetupStepStage.REPO_SYNCHRONIZATION -> "Syncing $currentItem ($index of $total)"
            SetupStepStage.TOOLCHAIN_COMPILATION -> "Building $currentItem ($index of $total)"
            else -> "$currentItem ($index of $total)"
        }
        _state.update { s ->
            val updated = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        description = desc,
                        startedAt = step.startedAt ?: System.currentTimeMillis(),
                    )
                } else {
                    step
                }
            }
            s.copy(steps = updated, activeLogLine = desc)
        }
    }

    /**
     * The persisted log of [attemptId], else of the running or pending attempt, else the newest log file.
     * `activeOperationId` can be a plan or run ID rather than an attempt ID, and both are cleared when a
     * run ends, so "export after a failed run" relies on the newest-file fallback.
     */
    override suspend fun getAttemptLog(attemptId: String?): String? = withContext(dispatcher) {
        val targetId = attemptId ?: _state.value.activeOperationId ?: _state.value.pendingAttemptId
        (targetId?.let(logBuffer::getLogFile) ?: logBuffer.latestLogFile())?.readText()
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
            val probe = ToolCatalog.probePlan(toolId, binDir, toolItem.binaryName)
            val binaryPath = probe.path

            _state.update { s ->
                s.copy(
                    toolsMatrix = s.toolsMatrix.map {
                        if (it.id == toolId) it.copy(status = StepStatus.RUNNING) else it
                    },
                )
            }

            try {
                val effectiveCli = TransportCliExecutor(transport, defaultWorkingDirectory = userHome)
                val verifier = ToolVerifier(effectiveCli, repository)
                val verifyResult = verifier.verify(distro, toolId, binDir, toolItem.binaryName)
                val testStatus = if (verifyResult.isSuccess) StepStatus.SUCCESS else StepStatus.FAILED
                val exists = (verifyResult as? ToolVerifier.Result.Failure)?.isInstalled != false

                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId) {
                                it.copy(
                                    status = testStatus,
                                    path = if (exists) binaryPath else null,
                                    lastTested = if (testStatus == StepStatus.SUCCESS) "Just now" else null,
                                )
                            } else {
                                it
                            }
                        },
                    )
                }

                when (verifyResult) {
                    is ToolVerifier.Result.Success -> {
                        SetupOutcome.Succeeded(evidence = mapOf(toolId to verifyResult.evidence))
                    }
                    is ToolVerifier.Result.Failure -> {
                        val reason = if (!exists) {
                            "Tool $toolId binary not found or not executable"
                        } else {
                            "Tool $toolId verification probe failed with exit code ${verifyResult.exitCode}"
                        }
                        appendLog("[Test] ERROR: $reason")
                        if (toolId in ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS) {
                            setStepStatus(
                                SetupStepStage.TOOLCHAIN_COMPILATION,
                                StepStatus.FAILED,
                                reason,
                                error = reason,
                            )
                            repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, false)
                        }
                        SetupOutcome.Failed(stage = SetupStepStage.TOOLCHAIN_COMPILATION.name, reason = reason)
                    }
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
            val (switchInputs, selectionRevision) = resolveDesiredSwitchInputs(distro)
            val plan = SetupPlanFactory.createPlan(
                kind = kind,
                targetId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
                snapshot = snapshot,
                environmentKey = distro,
                bootstrapPackages = bootstrapPkgs,
                toolchainLayoutV2 = toolchainLayoutV2,
                switchInputs = switchInputs,
                selectionRevision = selectionRevision,
            )
            activePlan = plan
            plan
        }

    private suspend fun resolveDesiredSwitchInputs(distro: String): Pair<Map<ToolGroupId, ResolvedInput>?, Long?> {
        val repo = repository
        val selections = if (toolchainLayoutV2 && repo != null) repo.desiredSelections(distro) else null
        return if (selections != null && selections.desired.isNotEmpty()) {
            val mappedInputs = SetupPlanFactory.defaultCatalogInputs().toMutableMap()
            for ((groupStr, sel) in selections.desired) {
                val groupId = ToolGroupId(groupStr)
                val catGroup = ToolGroupCatalog.group(groupId)
                mappedInputs[groupId] = mapSelectionToResolvedInput(groupId, sel, catGroup)
            }
            mappedInputs to selections.revision
        } else {
            null to null
        }
    }

    private fun mapSelectionToResolvedInput(
        groupId: ToolGroupId,
        sel: ToolSelection,
        catGroup: ToolGroup?,
    ): ResolvedInput = when (val ref = sel.ref) {
        is ToolRef.Commit -> ResolvedInput.Git(
            group = groupId,
            repoUrl = sel.repoUrl,
            ref = ref,
            commit = ref.sha,
            resolvedAt = System.currentTimeMillis(),
        )
        is ToolRef.Tag -> {
            val gitSource = catGroup?.source as? ToolSource.Git
            val commit = if (sel.repoUrl == null &&
                gitSource != null &&
                gitSource.recommendedLabel == ref.name
            ) {
                gitSource.recommendedCommit
            } else {
                ref.name
            }
            ResolvedInput.Git(
                group = groupId,
                repoUrl = sel.repoUrl,
                ref = ref,
                commit = commit,
                resolvedAt = System.currentTimeMillis(),
            )
        }
        is ToolRef.Branch -> ResolvedInput.Git(
            group = groupId,
            repoUrl = sel.repoUrl,
            ref = ref,
            commit = ref.name,
            resolvedAt = System.currentTimeMillis(),
        )
        is ToolRef.ReleaseVersion -> {
            val relConfig = catGroup?.recipe as? RecipeConfig.Release
            val ver = relConfig?.versions?.firstOrNull { it.version == ref.version }
                ?: relConfig?.versions?.firstOrNull()
            ResolvedInput.Release(
                group = groupId,
                version = ref.version,
                url = ver?.url.orEmpty(),
                archiveMember = ver?.archiveMember.orEmpty(),
                sha256 = ver?.sha256.orEmpty(),
                platform = detectHostArch(),
            )
        }
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
        val switchAction = currentPlan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        val liveRevision = SetupPlanFactory.computeRevisionHash(
            snapshot = _state.value,
            autoDoctorEnabled = currentPlan.autoDoctorEnabled,
            switchInputs = switchAction?.inputs,
            selectionRevision = switchAction?.selectionRevision,
        )
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
                val shouldVerifyAfter = planOutcome is SetupOutcome.Succeeded &&
                    currentPlan.kind != SetupPlanKind.CACHE_RESET
                val finalOutcome = if (shouldVerifyAfter) {
                    verifyEnvironment()
                    evaluateCompletionRule(currentPlan, _state.value, planOutcome)
                } else {
                    planOutcome
                }
                archiveAttempt(newAttemptId, finalOutcome)
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
        }.also {
            _operationOutcome.value = it
        }
    }

    private suspend fun resolveHome(distro: String): String = currentEnvironment?.takeIf { it.distro == distro }?.home
        ?: runCatching {
            val res = transport.execute(
                ToolExecutionRequest(
                    toolId = "printenv",
                    arguments = listOf("HOME"),
                    workingDirectory = "/",
                    purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
                ),
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
        val unavailable = probeReport.results.filterValues { it is RequirementStatus.Unavailable }.keys
        // Judged against every requirement, not the shrinking handoff list: a package that was present
        // before but is gone now blocks success too, and joins the command.
        val missing = probeReport.missingPackages
        return when {
            // An unreadable probe proves nothing: keep the handoff pending instead of archiving it.
            checkFailure != null -> SetupOutcome.Interrupted("Couldn't check packages: ${checkFailure.reason}")
            unavailable.isNotEmpty() -> {
                // apt has no candidate, so no terminal command can fix it; the attempt stays pending.
                val reason = "${unavailable.joinToString(", ")} isn't available on Ubuntu ${env.osVersionId}"
                setStepStatus(
                    SetupStepStage.SYSTEM_PACKAGES,
                    StepStatus.FAILED,
                    desc = reason,
                    error = "Enable the Ubuntu 'universe' repository or use a supported Ubuntu LTS release.",
                )
                SetupOutcome.Failed(stage = SetupStepStage.SYSTEM_PACKAGES.name, reason = reason)
            }
            missing.isNotEmpty() -> keepHandoffOpen(repo, active, handoff, planId, distro, missing)
            else -> archiveAttempt(active.attemptId, SetupOutcome.Succeeded()).also { archived ->
                // Only a durably archived attempt may show the step as done.
                if (archived is SetupOutcome.Succeeded) {
                    setStepStatus(
                        SetupStepStage.SYSTEM_PACKAGES,
                        StepStatus.SUCCESS,
                        desc = "Prerequisite packages installed",
                    )
                }
            }
        }
    }

    /** Re-journals the handoff with [missing] as its new command; a failed journal write is not a pending step. */
    private suspend fun keepHandoffOpen(
        repo: ToolchainSetupRepository,
        active: SetupAttemptRecord,
        handoff: PersistedUserHandoff,
        planId: String,
        distro: String,
        missing: List<String>,
    ): SetupOutcome {
        val newCommand = AptCommandBuilder.install(missing)
        val persistedPlan = active.persistedPlan ?: PersistedSetupPlan(
            planId = active.planId,
            revisionHash = active.planRevisionHash,
            environmentKey = active.environmentKey,
            kind = active.planKind,
        )
        repo.recordAttemptAwaitingUserAction(
            active.attemptId,
            handoff.copy(packages = missing, command = newCommand),
            persistedPlan,
        ).onFailure { failure ->
            return SetupOutcome.Interrupted("The updated package list could not be journaled: ${failure.message}")
        }
        return SetupOutcome.AwaitingUserAction(
            pendingPlanId = planId,
            stage = SetupStepStage.SYSTEM_PACKAGES,
            reason = "Required packages are still not installed in WSL: ${missing.joinToString()}",
            handoff = UserRepairHandoff(
                actionId = handoff.actionId,
                description = "Required packages are still missing",
                terminalCommand = newCommand,
                packages = missing,
                distro = distro,
            ),
        )
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
                val shouldVerifyAfter = planOutcome is SetupOutcome.Succeeded &&
                    domainPlan.kind != SetupPlanKind.CACHE_RESET
                val finalOutcome = if (shouldVerifyAfter) {
                    verifyEnvironment()
                    evaluateCompletionRule(domainPlan, _state.value, planOutcome)
                } else {
                    planOutcome
                }
                archiveAttempt(active.attemptId, finalOutcome)
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

            resumeOutcome
        }.also {
            _operationOutcome.value = it
        }
    }

    /**
     * Evaluates the completion rule per plan kind (data-model § Completion rule per plan kind).
     * Succeeded requires live facts to confirm the intended target or workspace readiness.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun evaluateCompletionRule(
        plan: SetupPlan,
        state: ToolchainSetupState,
        originalOutcome: SetupOutcome,
    ): SetupOutcome {
        if (originalOutcome !is SetupOutcome.Succeeded) return originalOutcome
        return when (plan.kind) {
            SetupPlanKind.CACHE_RESET -> originalOutcome
            SetupPlanKind.FULL_SETUP -> {
                if (state.canLaunchWorkspace) {
                    originalOutcome
                } else {
                    val firstFailed = state.steps.firstOrNull { it.status == StepStatus.FAILED }
                    SetupOutcome.Failed(
                        stage = firstFailed?.stage?.name,
                        reason = firstFailed?.error ?: "Environment is not ready to launch workspace after full setup.",
                    )
                }
            }
            SetupPlanKind.STAGE_RETRY -> {
                val target = plan.targetStageOrToolId
                val step = state.steps.firstOrNull { it.stage.name == target }
                if (step != null) {
                    if (step.status == StepStatus.SUCCESS || step.status == StepStatus.WARNING) {
                        originalOutcome
                    } else {
                        SetupOutcome.Failed(
                            stage = target,
                            reason = step.error ?: "Target stage '$target' is not ready after retry.",
                        )
                    }
                } else {
                    val tool = state.toolsMatrix.firstOrNull { it.id == target }
                    if (tool != null && (tool.status == StepStatus.SUCCESS || tool.status == StepStatus.WARNING)) {
                        originalOutcome
                    } else {
                        SetupOutcome.Failed(
                            stage = target,
                            reason = "Target '$target' is not ready after retry.",
                        )
                    }
                }
            }
            SetupPlanKind.REPAIR_TOOL -> {
                val toolId = plan.targetStageOrToolId
                val tool = state.toolsMatrix.firstOrNull { it.id == toolId }
                if (tool != null && (tool.status == StepStatus.SUCCESS || tool.status == StepStatus.WARNING)) {
                    originalOutcome
                } else {
                    SetupOutcome.Failed(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                        reason = "Target tool '$toolId' is not ready after repair.",
                    )
                }
            }
            SetupPlanKind.BOOTSTRAP_PACKAGES -> {
                val action = plan.orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>().firstOrNull()
                val expectedPackages = action?.packages.orEmpty()
                val missing = lastPrerequisiteReport?.missingPackages.orEmpty()
                val stillMissing = expectedPackages.filter { it in missing }
                if (stillMissing.isEmpty()) {
                    originalOutcome
                } else {
                    SetupOutcome.Failed(
                        stage = SetupStepStage.SYSTEM_PACKAGES.name,
                        reason = "Required packages are still missing: ${stillMissing.joinToString()}",
                    )
                }
            }
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
        is SetupPlanAction.SwitchToolchain -> "switch-toolchain"
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

        val operationScope = OperationScope(
            environmentKey = distro,
            operationId = journal?.attemptId ?: "plan-${currentPlan.planId}",
            stateFlow = _state,
            activeOpId = { _state.value.activeOperationId },
        )
        val installedScope = InstalledScope(
            environmentKey = distro,
            stateFlow = _state,
        )
        val context = ExecutionContext(
            distro = distro,
            home = home,
            binDir = binDir,
            extDir = extDir,
            workDir = workDir,
            plan = currentPlan,
            scope = operationScope,
            installedScope = installedScope,
            journal = journal,
            actionsToSkip = actionsToSkip,
            serviceCli = serviceCli,
            repository = repository,
            transport = transport,
            toolPublicationPort = toolPublicationPort,
            recordRunStarted = ::recordRunStarted,
            setStepProgress = { stage, label, idx, total ->
                setStepProgress(stage, label, idx, total)
            },
            setStepStatus = { stage, status, desc, error, daemonFail, toolchainFail ->
                setStepStatus(stage, status, desc, error, daemonFail, toolchainFail)
            },
            appendLog = { appendLog(it) },
        )

        val planOutcome = planExecutor.execute(context)
        if (planOutcome !is SetupOutcome.Succeeded) {
            return planOutcome
        }

        // Installed evidence from a real listing of the binary directory. This is file discovery
        // only: it updates paths and installed status, and never overrides a failed execution probe.
        val builtExecutables = listExecutables(distro, binDir, serviceCli)
        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val now = System.currentTimeMillis()
        val updatedTools = currentTools.map { tool ->
            // The same file the probe and the publisher use (a JAR is <id>.jar).
            val toolPath = ToolCatalog.expectedPath(binDir, tool.id) ?: "$binDir/${tool.binaryName}"
            val exists = toolPath.substringAfterLast('/') in builtExecutables
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
        val pending = repository?.currentState?.activeAttempt?.attemptId
        coordinator.withAdmission(
            environmentKey = distro,
            operationId = attemptId ?: pending ?: "recovery",
            kind = SetupOperationCoordinator.KIND_RECOVERY,
        ) {
            _state.update { it.copy(isRunning = true, activeOperationId = attemptId ?: pending) }
            try {
                recoveryCoordinator.recover(
                    distro = distro,
                    attemptId = attemptId,
                    ensureConnected = { verifyEnvironment() },
                    verifyEnvironment = { verifyEnvironment() },
                    appendLog = { appendLog(it) },
                )
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
                publishJournalState()
            }
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
    private fun appendLog(line: String, kind: SetupLogKind = SetupLogKind.RUN) {
        val attemptId = repository?.currentState?.activeAttempt?.attemptId ?: LOCAL_ATTEMPT_ID
        logBuffer.append(text = line, attemptId = attemptId, childRunId = PROVISIONER_CHILD_ID, kind = kind)
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
