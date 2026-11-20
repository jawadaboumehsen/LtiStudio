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
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.data.remote.ToolStatusMapper
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.EnvironmentCheck
import org.ide.lti.core.data.setup.check.PackagesPreparationResult
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.check.stages.DistroStage
import org.ide.lti.core.data.setup.check.stages.PackagesStage
import org.ide.lti.core.data.setup.check.stages.ServiceStage
import org.ide.lti.core.data.setup.check.stages.ToolsStage
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.handlers.InstallPackagesHandler
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.data.setup.state.InstalledScope
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
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
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PersistedSetupPlan
import org.ide.lti.core.model.setup.PersistedUserHandoff
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.setup.SetupEnvironment
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
 *   Component sources and builds are delegated to the tool manager.
 *   The bundled helper performs compilation inside WSL.
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
    private val coordinator: SetupOperationCoordinator = SetupOperationCoordinator(repository),
    private val scope: CoroutineScope = CoroutineScope(dispatcher + SupervisorJob()),
    private val hostPrerequisitePort: HostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
    private val serverArtifactPort: ServerArtifactPort? = null,
    servicePreparation: ServicePreparation? = null,
    planExecutor: PlanExecutor? = null,
    recoveryCoordinator: RecoveryCoordinator? = null,
    private val toolManager: org.ide.lti.core.domain.tools.ToolManager? = null,
    private val heartbeatMonitor: io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor? = null,
    environmentCheck: EnvironmentCheck? = null,
) : ToolchainProvisioningService {
    private val servicePreparation: ServicePreparation =
        servicePreparation ?: ServicePreparation(
            hostPrerequisitePort = hostPrerequisitePort,
            supervisor = supervisor,
            transport = transport,
            serverArtifactPort = serverArtifactPort,
            dispatcher = dispatcher,
        )
    private val planExecutor: PlanExecutor =
        planExecutor ?: PlanExecutor(
            installPackagesHandler = InstallPackagesHandler(),
        )

    private val environmentCheck: EnvironmentCheck =
        environmentCheck ?: EnvironmentCheck(
            listOf(
                DistroStage(detector),
                ServiceStage(
                    supervisor = supervisor,
                    transport = transport,
                    heartbeatMonitor = heartbeatMonitor,
                    servicePreparation = this.servicePreparation,
                ),
                PackagesStage(servicePreparation = this.servicePreparation),
                ToolsStage(toolManager = toolManager),
            ),
        )

    /** Bounded activity history (FR-011); run output is recorded here with real journal identity. */
    private val toolProbe = ToolProbe(transport)

    private val logBuffer = SetupLogBuffer(onRecorded = { event -> publishLogTail(event.text) })

    private val recoveryCoordinator: RecoveryCoordinator =
        recoveryCoordinator ?: RecoveryCoordinator(
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

    private val _state = MutableStateFlow(restoredSetupState(repository?.currentState))
    override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

    /** Mirrors the journal's active-attempt / block state into the observable setup state. */
    private fun publishJournalState() {
        val persisted = repository?.currentState ?: return
        _state.update { it.withJournal(persisted) }
    }

    public companion object {
        private const val STATE_LOG_TAIL_LINES: Int = 100

        /** `$1` = output file: a new RSA-4096 key, created readable by its owner only (umask before openssl). */
        private const val AVB_GENERATE_SCRIPT: String =
            "umask 077 && rm -f \"$1\" && openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out \"$1\" " +
                "2>/dev/null && chmod 600 \"$1\""

        /** `$1` = key file: a regular file, mode 600, an RSA key that passes openssl's check, of 4096 bits. */
        private const val AVB_CHECK_SCRIPT: String =
            "[ -f \"$1\" ] && [ \"$(stat -c %a \"$1\")\" = 600 ] && " +
                "openssl rsa -in \"$1\" -check -noout >/dev/null 2>&1 && " +
                "openssl rsa -in \"$1\" -noout -text 2>/dev/null | grep -q '(4096 bit'"
        private const val CONFIRM_MISMATCH: String =
            "Confirmation requires the displayed plan ID and unchanged revision hash."
        private const val LOCAL_ATTEMPT_ID: String = "local"
        private const val PROVISIONER_CHILD_ID: String = "provisioner"
        private const val OWNER_READ: Int = 0x100 // 0400
        private const val OWNER_EXECUTE: Int = 0x40 // 0100

        public val REQUIRED_DEV_PACKAGES: List<String> = RequirementCatalog.HOST_LIBRARY_PACKAGES + "libfuse3-dev"

        public val SUPPORTED_RECIPES: Set<String> get() = ToolCapabilities.SUPPORTED_TOOL_IDS

        /** Whether the app can build [toolId] itself (its group has a recipe). */
        public fun isRecipeSupported(toolId: String): Boolean = ToolCapabilities.isRecipeSupported(toolId)

        /** Names from `find -printf "%m %f\n"` lines that are usable tools: a JAR readable, the rest executable. */
        internal fun usableToolFiles(listing: String): Set<String> = listing.lineSequence().mapNotNull { line ->
            val mode = line.substringBefore(' ').toIntOrNull(radix = 8) ?: return@mapNotNull null
            val name = line.substringAfter(' ').trim()
            val usable = if (name.endsWith(".jar")) mode and OWNER_READ != 0 else mode and OWNER_EXECUTE != 0
            name.takeIf { usable && it.isNotEmpty() }
        }.toSet()
    }

    override suspend fun checkStatus(): ToolchainSetupState = verifyEnvironment()

    override suspend fun selectDistro(distro: String): ToolchainSetupState {
        val snapshot = _state.value
        val usable =
            snapshot.distroStatuses.filterIsInstance<DistroStatus.Usable>().any {
                it.environment.distro ==
                    distro
            }
        if (!usable || snapshot.isBusy) return snapshot
        withContext(dispatcher) {
            detector.setDefaultDistro(distro)
        }
        preferredDistro = distro
        currentEnvironment = null
        // Detection must run again for the new target instead of reusing the previous verified one.
        _state.update { state ->
            state.copy(
                steps =
                state.steps.map { step ->
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
        val deferred =
            verificationMutex.withLock {
                activeVerificationDeferred ?: scope.async(dispatcher) {
                    runVerificationInternal()
                }.also { created ->
                    activeVerificationDeferred = created
                }
            }
        return deferred.await()
    }

    /** One check of every stage, flagged as checking while it runs; the state it left. */
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
            val cat =
                if (currentStage ==
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

    /**
     * Whether this check can skip detecting WSL again: the service answered or WSL was verified live in this session,
     * in the distribution an open attempt [wanted] (any when none), and that environment is still current. The OS and
     * user are kept from the earlier verification; the default distribution and WSL version are read again.
     */
    private suspend fun canReuseWsl(wanted: String?): Boolean {
        val steps = _state.value.steps
        val wslStep = steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
        val serviceStep = steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
        val serviceUp = serviceStep?.status == StepStatus.SUCCESS
        val wslLive = wslStep?.status == StepStatus.SUCCESS && wslStep.provenance == StepProvenance.LIVE
        val env = currentEnvironment?.takeIf { wanted == null || it.distro == wanted }
        return (serviceUp || wslLive) && env != null &&
            runInterruptible(dispatcher) { detector.stillCurrent(env, requireDefault = wanted == null) }
    }

    /** Runs the check stages in order; delegated to [EnvironmentCheck]. */
    private suspend fun runStages() {
        val wanted =
            repository?.currentState?.activeAttempt?.let {
                it.environmentKey ?: it.environment?.distro
            }
        val alreadyVerifiedWsl = canReuseWsl(wanted)

        val detected =
            if (alreadyVerifiedWsl && currentEnvironment != null) {
                listOf(DistroStatus.Usable(currentEnvironment!!))
            } else {
                detector.detectAll()
            }
        val accessDenied = detected.filterIsInstance<DistroStatus.WslUnavailable>().firstOrNull { it.accessDenied }
        if (accessDenied != null) {
            val stage = SetupStepStage.WSL_DETECTION
            _state.update { it.copy(distroStatuses = detected) }
            setStepStatus(stage, StepStatus.FAILED, desc = accessDenied.detail, error = accessDenied.detail)
            shortCircuitRemaining(stage, "Waiting for Windows to grant access to WSL.")
            return
        }
        val target = checkTarget(detected, wanted, preferredDistro, currentEnvironment)
        val distro = target.distro
        val cleanHome = target.homeHint?.takeIf { it.startsWith("/") } ?: resolveHome(distro)
        val env = target.environment ?: defaultEnvironment(distro, cleanHome)
        val workDir = "$cleanHome/LtiRomWorkDir"
        val binDir = "$cleanHome/LtiRomTools/bin"
        val extDir = "$cleanHome/LtiRomTools/external"
        val serviceCli = TransportCliExecutor(transport, defaultWorkingDirectory = cleanHome)
        val readOnlyCommands = ReadOnlyCommands(serviceCli)
        val checkId = UUID.randomUUID().toString()
        val scope =
            CheckScope(
                environmentKey = distro,
                checkId = checkId,
                stateFlow = _state,
                activeCheckId = { checkId },
            )
        val ctx =
            CheckContext(
                environment = env,
                readOnlyCommands = readOnlyCommands,
                userHome = cleanHome,
                workDir = workDir,
                binDir = binDir,
                extDir = extDir,
                transport = transport,
                repository = repository,
                log = { msg, kind -> appendLog(msg, kind) },
                onPrerequisiteReport = { report -> lastPrerequisiteReport = report },
                alreadyVerifiedWsl = alreadyVerifiedWsl,
            )

        environmentCheck.run(ctx, scope)
        val currentSteps = _state.value.steps
        val (storageAvailableBytes, storageMeasuredAt) = measureStorage(cleanHome)
        val now = System.currentTimeMillis()
        val detectedDistro = _state.value.activeDistro
        val detectedUsable =
            _state.value.distroStatuses.filterIsInstance<DistroStatus.Usable>()
                .firstOrNull { it.environment.distro == detectedDistro }
        if (detectedUsable != null) {
            currentEnvironment = detectedUsable.environment
        }
        val registry = reconcileWithToolRegistry()
        _state.update { it.withCheckFinished(cleanHome, storageAvailableBytes to storageMeasuredAt, registry, now) }
        if (_state.value.isAllReady) {
            val ts = _state.value.lastVerifiedTimestamp
            repository?.let { repo ->
                repo.saveToolchainState(repo.currentState.copy(lastVerifiedTimestamp = ts))
            }
        }
    }

    /**
     * Compares what the service offers with what the product requires, and says so on the tools step. Fully
     * ready needs every step green *and* a readable tool list that has every required tool.
     */
    private suspend fun reconcileWithToolRegistry(): ToolRegistryCheck {
        val registryResult = readToolRegistry()
        val publishedTools: Set<String> =
            (registryResult as? ToolRegistryResult.Tools)?.list?.filter { it.installed }?.map { it.tool }?.toSet()
                ?: emptySet()
        // Unknown (not asked, or no readable answer) is not "everything is missing".
        val unpublished: Set<String> =
            if (registryResult is ToolRegistryResult.Tools) {
                ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS - publishedTools
            } else {
                emptySet()
            }
        reportRegistryOnSteps(registryResult, unpublished)
        val allReady =
            _state.value.steps.all { it.status == StepStatus.SUCCESS || it.status == StepStatus.WARNING } &&
                registryResult is ToolRegistryResult.Tools
        return ToolRegistryCheck(publishedTools, unpublished, allReady && unpublished.isEmpty())
    }

    /**
     * The tool list is asked only of a service the earlier stages found connected: after a failed stage there is
     * nothing to learn from it, and its error would hide the real first failure. Null: not asked.
     */
    private suspend fun readToolRegistry(): ToolRegistryResult? {
        val serverUp = _state.value.steps.any {
            it.stage == SetupStepStage.SERVER_CONNECTIVITY && it.status == StepStatus.SUCCESS
        }
        if (!serverUp) return null
        return try {
            ToolStatusMapper.toDomain(transport.listTools())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolRegistryResult.Unreachable(e.message ?: "no answer")
        }
    }

    /** Marks the service and tools steps by what the service's tool list said ([unpublished]: tools it lacks). */
    private fun reportRegistryOnSteps(registryResult: ToolRegistryResult?, unpublished: Set<String>) {
        when (registryResult) {
            is ToolRegistryResult.Unreachable -> {
                setStepStatus(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    status = StepStatus.FAILED,
                    desc = "Build service unreachable: ${registryResult.reason}",
                    error = "Build service unreachable: ${registryResult.reason}",
                    failureCategory = DaemonFailureCategory.UNREACHABLE,
                )
                setStepStatus(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    status = StepStatus.PENDING,
                    desc = "Waiting for build service",
                )
            }

            is ToolRegistryResult.ServiceError ->
                // The service is there; its tool list is what failed. The bridge stays connected.
                failToolsStep("Couldn't read the tool list from the build service: ${registryResult.reason}")

            is ToolRegistryResult.Tools ->
                if (unpublished.isNotEmpty() && toolsStepSucceeded()) {
                    failToolsStep("The build service does not offer: ${unpublished.sorted().joinToString(", ")}")
                }

            null -> Unit
        }
    }

    /** Marks the tools step failed with [message] as both its description and its error. */
    private fun failToolsStep(message: String) = setStepStatus(
        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
        status = StepStatus.FAILED,
        desc = message,
        error = message,
    )

    /** Whether the tools step has already passed in this state. */
    private fun toolsStepSucceeded(): Boolean =
        _state.value.steps.any { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION && it.status == StepStatus.SUCCESS }

    /** Free bytes under [home] from `df`, with when they were measured; nulls when `df` fails. */
    private suspend fun measureStorage(home: String): Pair<Long?, Long?> {
        val storageDf =
            runCatchingCancellable {
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
        val liveVerified =
            snapshot.steps.any {
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

    /** Shows [stage] as running and yields, so the UI sees it before the stage blocks. */
    private suspend fun setStepRunning(stage: SetupStepStage) {
        _state.update { it.withStepRunning(stage, System.currentTimeMillis()) }
        kotlinx.coroutines.yield()
    }

    override fun setStepProgress(stage: SetupStepStage, currentItem: String, index: Int, total: Int) {
        _state.update { it.withStepProgress(stage, currentItem, index, total, System.currentTimeMillis()) }
    }

    /**
     * The persisted log of [attemptId], else of the running or pending attempt, else the newest log file.
     * `activeOperationId` can be a plan or run ID rather than an attempt ID, and both are cleared when a
     * run ends, so "export after a failed run" relies on the newest-file fallback.
     */
    override suspend fun getAttemptLog(attemptId: String?): String? = withContext(dispatcher) {
        // An attempt asked for by id is that attempt's log or none; only "the current one" falls back to the newest.
        val file = if (attemptId != null) {
            logBuffer.getLogFile(attemptId)
        } else {
            (_state.value.activeOperationId ?: _state.value.pendingAttemptId)?.let(logBuffer::getLogFile)
                ?: logBuffer.latestLogFile()
        }
        file?.readText()
    }

    /** After [failedStage] failed: every later stage shows as pending with [reason]. */
    private fun shortCircuitRemaining(failedStage: SetupStepStage, reason: String) {
        _state.update { it.withRemainingPending(failedStage, reason, System.currentTimeMillis()) }
    }

    override suspend fun provisionAvbKey(): Boolean = provisionAvbKey(serviceCli = null, home = null, distroName = null)

    /**
     * Makes the AVB signing key in WSL when it is missing; false when busy or it could not be made. The service,
     * home and distro are given when called from setup, else found.
     */
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

        val keyExists = effectiveCli.execute(distro, listOf("test", "-e", keyPath)).exitCode == 0
        val keyReady = if (keyExists) {
            // Never replaced: earlier builds may be signed with it. The user moves it away to get a new one.
            // Only its mode is tightened, so a key copied in with looser rights is not reported as damaged.
            effectiveCli.execute(distro, listOf("chmod", "600", keyPath))
            validAvbKey(effectiveCli, distro, keyPath).also { ok ->
                appendLog(
                    if (ok) {
                        "[AVB 2.0] Existing RSA-4096 private key detected."
                    } else {
                        "[AVB 2.0] ERROR: $keyPath is not a valid RSA-4096 private key. Move it away and provision " +
                            "again to make a new one."
                    },
                )
            }
        } else {
            generateAvbKey(effectiveCli, distro, keyPath)
        }
        if (!keyReady) return@withContext false

        // Extract public key using avbtool if available
        val hasAvbTool = effectiveCli.execute(distro, listOf("test", "-x", "$binDir/avbtool")).exitCode == 0
        val publicKeyOk = !hasAvbTool || effectiveCli.execute(
            distro,
            listOf("$binDir/avbtool", "extract_public_key", "--key", keyPath, "--output", pubKeyPath),
        ).exitCode == 0
        appendLog(
            when {
                !hasAvbTool -> "[AVB 2.0] avbtool is not installed yet; the public key is derived later."
                publicKeyOk -> "[AVB 2.0] Derived AVB public key at $pubKeyPath."
                else -> "[AVB 2.0] ERROR: avbtool could not derive the public key from $keyPath."
            },
        )
        if (publicKeyOk) {
            _state.update { it.copy(isAvbKeyProvisioned = true, avbKeyPath = keyPath) }
            appendLog("[AVB 2.0] Machine signing key successfully verified.")
        }
        publicKeyOk
    }

    /**
     * Makes a new RSA-4096 key: written as `<key>.partial` with only the owner able to read it, checked, then renamed
     * onto [keyPath] in one step, so a failed or cut-off generation never leaves a key that looks usable.
     */
    private suspend fun generateAvbKey(cli: WslCliExecutor, distro: String, keyPath: String): Boolean {
        appendLog("[AVB 2.0] Generating RSA-4096 private key via OpenSSL...")
        val partial = "$keyPath.partial"
        val generated = cli.execute(distro, listOf("sh", "-c", AVB_GENERATE_SCRIPT, "sh", partial)).exitCode == 0
        val ok = generated && validAvbKey(cli, distro, partial) &&
            cli.execute(distro, listOf("mv", "-f", "-T", partial, keyPath)).exitCode == 0
        if (!ok) cli.execute(distro, listOf("rm", "-f", partial))
        appendLog(
            if (ok) {
                "[AVB 2.0] Private key generated with mode 0600 at $keyPath."
            } else {
                "[AVB 2.0] ERROR: OpenSSL could not generate a valid RSA-4096 key; nothing was kept."
            },
        )
        return ok
    }

    /** Whether [path] is a readable, consistent RSA private key of 4096 bits that only its owner can read. */
    private suspend fun validAvbKey(cli: WslCliExecutor, distro: String, path: String): Boolean =
        cli.execute(distro, listOf("sh", "-c", AVB_CHECK_SCRIPT, "sh", path)).exitCode == 0

    override suspend fun testTool(toolId: String): SetupOutcome = withContext(dispatcher) {
        val toolItem =
            _state.value.toolsMatrix.firstOrNull { it.id == toolId }
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
            val binary = toolBinary(resolveHome(distro), toolId, toolItem.binaryName)
            _state.update { it.withToolStatus(toolId, StepStatus.RUNNING) }
            try {
                val probe = toolProbe.probe(toolId, binary)
                val status = if (probe.verified) StepStatus.SUCCESS else StepStatus.FAILED
                _state.update {
                    it.withToolResult(
                        toolId,
                        status,
                        path = binary.path.takeIf { probe.exists },
                        lastTested = "Just now".takeIf { probe.verified },
                    )
                }
                if (probe.verified) toolVerified(toolId, binary.path) else toolFailed(toolId, probe.exists)
            } finally {
                // Cut off part way (a cancel): never left showing as running.
                _state.update { s ->
                    s.toolsMatrix.firstOrNull { it.id == toolId && it.status == StepStatus.RUNNING }
                        ?.let { s.withToolStatus(toolId, StepStatus.FAILED) } ?: s
                }
            }
        }.also { _operationOutcome.value = it }
    }

    /** Records [toolId] as built and verified at [binaryPath], in the repository and the tools matrix. */
    private suspend fun toolVerified(toolId: String, binaryPath: String): SetupOutcome {
        val now = System.currentTimeMillis()
        repository?.updateTool(toolId) {
            it.copy(isCompiled = true, isVerified = true, binaryPath = binaryPath, lastVerifiedTimestamp = now)
        }
        val groupName = ToolGroupCatalog.groupByToolId(toolId)?.id?.value ?: toolId
        val evidence = ToolEvidence(
            toolId = toolId,
            recipeGroup = groupName,
            isInstalled = true,
            isVerified = true,
            lastVerifiedTimestamp = now,
        )
        return SetupOutcome.Succeeded(evidence = mapOf(toolId to evidence))
    }

    /** A required tool that fails its probe fails the tools stage too: a build could not run without it. */
    private suspend fun toolFailed(toolId: String, exists: Boolean): SetupOutcome {
        repository?.updateTool(toolId) { it.copy(isVerified = false) }
        val reason = if (!exists) {
            "Tool $toolId binary not found or not executable"
        } else {
            "Tool $toolId verification probe failed"
        }
        appendLog("[Test] ERROR: $reason")
        if (toolId in ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS) {
            setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.FAILED, reason, error = reason)
            repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, false)
        }
        return SetupOutcome.Failed(stage = SetupStepStage.TOOLCHAIN_COMPILATION.name, reason = reason)
    }

    // ---- Plan-driven operation contract (003 US2) ----
    //
    // Every mutation is previewed by [prepare] and executed only by [confirm]. A plan is derived
    // from the real current state (never mutating anything); confirmation enforces the plan ID /
    // revision invariant, revalidates the live evidence and environment, freezes the Auto Doctor
    // policy carried by the plan, and runs under the single environment operation owner. Every
    // attempt is journaled before submission and reconciled by [recover] (003 US3).

    override suspend fun prepare(kind: SetupPlanKind, targetId: String?): SetupPlan = withContext(dispatcher) {
        val snapshot = _state.value
        val distro = resolveEnvironmentKey()
        val bootstrapPkgs =
            if (kind == SetupPlanKind.BOOTSTRAP_PACKAGES ||
                kind == SetupPlanKind.FULL_SETUP ||
                targetId == SetupStepStage.SYSTEM_PACKAGES.name
            ) {
                val report = lastPrerequisiteReport ?: (currentEnvironment?.let { hostPrerequisitePort.probe(it) })
                report?.missingPackages ?: emptyList()
            } else {
                emptyList()
            }
        val switchInputs = SetupPlanFactory.toolManagerTargets(toolManager)
        val plan =
            SetupPlanFactory.createPlan(
                kind = kind,
                targetId = targetId,
                snapshot = snapshot,
                environmentKey = distro,
                bootstrapPackages = bootstrapPkgs,
                switchInputs = switchInputs,
                selectionRevision = null,
            )
        activePlan = plan
        plan
    }

    override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome = withContext(dispatcher) {
        val currentPlan = activePlan
        val refusal = confirmRefusal(currentPlan, planId, revisionHash)
        if (currentPlan == null || refusal != null) {
            val outcome = SetupOutcome.Failed(stage = null, reason = refusal ?: CONFIRM_MISMATCH)
            _operationOutcome.value = outcome
            return@withContext outcome
        }
        val distro = currentPlan.environmentKey
        coordinator.withAdmission(
            environmentKey = distro,
            operationId = currentPlan.planId,
            kind = currentPlan.kind.name,
        ) {
            val journal =
                openAttempt(
                    environmentKey = distro,
                    planId = currentPlan.planId,
                    revisionHash = currentPlan.revisionHash,
                    kind = currentPlan.kind,
                    plannedActionIds = currentPlan.orderedActions.map { it.actionId() },
                ).getOrElse {
                    return@withAdmission SetupOutcome.Interrupted(it.message ?: "Attempt could not be journaled.")
                }
            // The plan is consumed by this attempt: a second confirmation must preview again.
            activePlan = null
            runAttempt(currentPlan, journal?.attemptId ?: currentPlan.planId, currentPlan.planId, "Plan") {
                when (currentPlan.kind) {
                    SetupPlanKind.CACHE_RESET -> executeCacheReset()

                    SetupPlanKind.BOOTSTRAP_PACKAGES -> executePlan(currentPlan, distro, journal)

                    // Failed health is a terminal prerequisite failure: nothing else runs.
                    SetupPlanKind.FULL_SETUP, SetupPlanKind.STAGE_RETRY, SetupPlanKind.REPAIR_TOOL ->
                        bridgeFailure(currentPlan, distro) ?: executePlan(currentPlan, distro, journal)
                }
            }
        }.also {
            _operationOutcome.value = it
        }
    }

    /**
     * Why [plan] cannot be confirmed as [planId] / [revisionHash], or null. Revision and environment are checked
     * against the current state: a plan previewed before a later check (or for another distro) is stale (FR-004).
     */
    private suspend fun confirmRefusal(plan: SetupPlan?, planId: String, revisionHash: String): String? {
        if (plan == null || plan.planId != planId || plan.revisionHash != revisionHash) return CONFIRM_MISMATCH
        val switchAction = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        val liveRevision = SetupPlanFactory.computeRevisionHash(
            snapshot = _state.value,
            switchInputs = switchAction?.inputs,
            selectionRevision = switchAction?.selectionRevision,
        )
        val stale = liveRevision != plan.revisionHash || resolveEnvironmentKey() != plan.environmentKey
        return "Environment evidence changed since the plan was previewed; preview again before confirming."
            .takeIf { stale }
    }

    /**
     * Brings the build service up for a server retry, then requires its health and the client heartbeat: the
     * failure that stops the plan before anything else runs, or null when the bridge is up.
     */
    private suspend fun bridgeFailure(plan: SetupPlan, distro: String): SetupOutcome.Failed? {
        if (plan.targetStageOrToolId == SetupStepStage.SERVER_CONNECTIVITY.name) {
            val env = currentEnvironment
            if (serverArtifactPort != null && env != null) {
                serverArtifactPort.ensureInstalled(env)
                serverArtifactPort.ensureRunningCurrent(env)
            } else {
                supervisor.ensureStarted(distro)
            }
        }
        val reason = when {
            transport.checkHealth() == null -> "Daemon bridge failed: health check returned null."

            !(heartbeatMonitor?.awaitInitialHeartbeat() ?: transport.sendHeartbeat()) ->
                "Daemon bridge failed: client heartbeat was not acknowledged."

            else -> null
        }
        if (reason == null) heartbeatMonitor?.start()
        return reason?.let { SetupOutcome.Failed(stage = SetupStepStage.SERVER_CONNECTIVITY.name, reason = it) }
    }

    /**
     * Runs attempt [attemptId] of [plan] ([run] does the work): a success is checked again against the live
     * environment ([completionOutcome]) before it counts; the attempt is archived as it ended. A cancel archives it
     * as cancelled only when every submitted child is proven, and is rethrown; any other failure escaping the plan
     * is a typed Failed, archived only with terminal proof of every child (else recovery is blocked until
     * reconciled, never a silent retry).
     */
    private suspend fun runAttempt(
        plan: SetupPlan,
        attemptId: String,
        operationId: String,
        logTag: String,
        run: suspend () -> SetupOutcome,
    ): SetupOutcome {
        _state.update { it.copy(isRunning = true, activeOperationId = operationId) }
        return try {
            val planOutcome = run()
            val finalOutcome = if (planOutcome is SetupOutcome.Succeeded && plan.kind != SetupPlanKind.CACHE_RESET) {
                verifyEnvironment()
                completionOutcome(plan, _state.value, planOutcome, lastPrerequisiteReport?.missingPackages.orEmpty())
            } else {
                planOutcome
            }
            archiveAttempt(attemptId, finalOutcome)
        } catch (ce: CancellationException) {
            archiveAttempt(attemptId, SetupOutcome.Cancelled)
            throw ce
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            appendLog("[$logTag] ERROR: ${e.message}")
            val reason = e.message ?: "Plan execution failed"
            archiveAttempt(attemptId, SetupOutcome.Failed(stage = _state.value.currentStage?.name, reason = reason))
        } finally {
            _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
            publishJournalState()
        }
    }

    /**
     * The home folder of the user in [distro]: the current environment's when it is that distro, else asked from
     * WSL.
     */
    private suspend fun resolveHome(distro: String): String = currentEnvironment?.takeIf { it.distro == distro }?.home
        ?: runCatchingCancellable {
            val res =
                transport.execute(
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
        val env =
            active.environment?.toDomain()
                ?: currentEnvironment?.takeIf { it.distro == active.environmentKey }
        if (env == null) {
            return SetupOutcome.Interrupted(
                "Attempt '${active.attemptId}' has no recorded environment; run the environment check again.",
            )
        }
        currentEnvironment = env
        val isServerReady =
            _state.value.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
                ?.let { it.status == StepStatus.SUCCESS } == true
        val probeRes = resolveResumeProbeReport(env, isServerReady)
        val probeReport = probeRes.getOrNull()
        return if (probeReport == null) {
            SetupOutcome.Interrupted(probeRes.exceptionOrNull()?.message ?: "Failed to probe packages")
        } else {
            lastPrerequisiteReport = probeReport
            evaluateBootstrapReport(repo, active, handoff, planId, distro, env, probeReport)
        }
    }

    /** The system packages of [env], through the service when it is ready, else probed straight from WSL. */
    private suspend fun resolveResumeProbeReport(
        env: SetupEnvironment,
        isServerReady: Boolean,
    ): Result<PrerequisiteReport> = if (isServerReady) {
        val res = servicePreparation.verifySystemPackages(env)
        if (res is PackagesPreparationResult.Failure && res.prerequisiteReport.results.isEmpty()) {
            Result.failure(IllegalStateException("Couldn't check packages via build service: ${res.error}"))
        } else {
            Result.success(res.prerequisiteReport)
        }
    } else {
        val hostReport = hostPrerequisitePort.probe(env)
        Result.success(hostReport)
    }

    /**
     * Ends a package handoff by what [probeReport] found: still missing shows the handoff again, all present
     * archives the attempt.
     */
    private suspend fun evaluateBootstrapReport(
        repo: ToolchainSetupRepository,
        active: SetupAttemptRecord,
        handoff: PersistedUserHandoff,
        planId: String,
        distro: String,
        env: SetupEnvironment,
        probeReport: PrerequisiteReport,
    ): SetupOutcome {
        val checkFailure = probeReport.results.values.filterIsInstance<RequirementStatus.CheckFailed>().firstOrNull()
        val unavailable = probeReport.results.filterValues { it is RequirementStatus.Unavailable }.keys
        val missing = probeReport.missingPackages
        return when {
            checkFailure != null -> SetupOutcome.Interrupted("Couldn't check packages: ${checkFailure.reason}")

            unavailable.isNotEmpty() -> {
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

            else ->
                archiveAttempt(active.attemptId, SetupOutcome.Succeeded()).also { archived ->
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
        val persistedPlan =
            active.persistedPlan ?: PersistedSetupPlan(
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
            handoff =
            UserRepairHandoff(
                actionId = handoff.actionId,
                description = "Required packages are still missing",
                terminalCommand = newCommand,
                packages = missing,
                distro = distro,
            ),
        )
    }

    /** Rebuilds the journaled plan so a resumed attempt runs exactly the actions that were previewed. */
    override suspend fun resume(planId: String): SetupOutcome = withContext(dispatcher) {
        val distro = resolveEnvironmentKey()
        coordinator.withAdmission(
            environmentKey = distro,
            operationId = planId,
            kind = SetupOperationCoordinator.KIND_RESUME,
        ) {
            val repo =
                repository ?: return@withAdmission SetupOutcome.Failed(
                    stage = null,
                    reason = "No repository configured to resume setup.",
                )
            val active = repo.currentState.activeAttempt
            resumeRefusal(active, planId, distro)?.let { return@withAdmission it }
            // resumeRefusal answered for each of these.
            val attempt = checkNotNull(active)
            val handoff = checkNotNull(attempt.awaitingHandoff)
            val domainPlan = rebuildPlan(attempt).getOrElse {
                return@withAdmission SetupOutcome.Interrupted(it.message.orEmpty())
            }
            if (attempt.isBootstrap()) {
                return@withAdmission resumeBootstrap(repo, attempt, handoff, planId, distro)
            }
            packagesAfterHandoff(attempt, handoff, planId, distro)?.let { return@withAdmission it }

            // The handoff's action is done: the attempt runs again from the actions it has not completed.
            repo.recordPlanActionCompleted(attempt.attemptId, handoff.actionId).onFailure { failure ->
                return@withAdmission SetupOutcome.Interrupted("Failed to record action completion: ${failure.message}")
            }
            repo.updateAttemptStatus(attempt.attemptId, AttemptStatus.RUNNING).onFailure { failure ->
                return@withAdmission SetupOutcome.Interrupted("Failed to update attempt status: ${failure.message}")
            }
            setStepStatus(SetupStepStage.SYSTEM_PACKAGES, StepStatus.SUCCESS, "System packages verified.")
            val completedActionIds = (attempt.completedActionIds + handoff.actionId)
                .filter { it != "switch-toolchain" && !it.startsWith("prereq-") }
                .toSet()
            val journal = SetupJournalContext(
                environmentKey = distro,
                attemptId = attempt.attemptId,
                activity = logBuffer,
            )
            runAttempt(domainPlan, attempt.attemptId, attempt.planId, "Resume") {
                executePlan(domainPlan, distro, journal, actionsToSkip = completedActionIds)
            }
        }.also {
            _operationOutcome.value = it
        }
    }

    /**
     * The journaled plan of [attempt]; a failure's message says why it cannot be rebuilt safely (the handoff then
     * stays pending).
     */
    private fun rebuildPlan(attempt: SetupAttemptRecord): Result<SetupPlan> {
        val id = attempt.attemptId
        val persisted = attempt.persistedPlan
            ?: return refused("Attempt '$id' has no persisted plan; preview and confirm a new plan.")
        return try {
            val plan = persisted.toDomainPlan()
            // A plan with nothing left to run would archive the attempt as done while the handoff was never reviewed.
            if (plan.orderedActions.isNotEmpty()) {
                Result.success(plan)
            } else {
                refused(
                    "Attempt '$id' has no resumable actions in its persisted plan; the handoff remains pending and " +
                        "must be reviewed.",
                )
            }
        } catch (e: IllegalArgumentException) {
            refused("Attempt '$id' has an invalid persisted plan: ${e.message}")
        } catch (e: IllegalStateException) {
            refused("Attempt '$id' cannot safely reconstruct its plan: ${e.message}")
        }
    }

    /** A plan that cannot be rebuilt; [why] becomes the Interrupted outcome's reason. */
    private fun refused(why: String): Result<SetupPlan> = Result.failure(IllegalStateException(why))

    /**
     * After the user ran the handoff's command: the packages are checked again in the attempt's environment. Still
     * missing: the handoff is shown again; another package missing now: the environment broke while paused and the
     * resume stops. Null when everything is there.
     */
    private suspend fun packagesAfterHandoff(
        attempt: SetupAttemptRecord,
        handoff: PersistedUserHandoff,
        planId: String,
        distro: String,
    ): SetupOutcome? {
        val env = currentEnvironment
            ?: attempt.environment?.toDomain()
            ?: (detector.classify(distro) as? DistroStatus.Usable)?.environment
            ?: return SetupOutcome.Failed(SetupStepStage.SYSTEM_PACKAGES.name, "No active WSL environment found")
        currentEnvironment = env
        val missing = hostPrerequisitePort.probe(env).missingPackages
        val stillMissing = missing.filter { it in handoff.packages }
        val otherMissing = missing.filterNot { it in handoff.packages }
        return when {
            stillMissing.isNotEmpty() -> {
                appendLog("[Resume] Required packages still missing: ${stillMissing.joinToString()}")
                SetupOutcome.AwaitingUserAction(
                    pendingPlanId = planId,
                    stage = SetupStepStage.SYSTEM_PACKAGES,
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

            otherMissing.isNotEmpty() -> {
                val names = otherMissing.joinToString()
                appendLog("[Resume] ERROR: Environment became unhealthy while paused: $names")
                val reason = "Environment check failed before resumption: $names"
                setStepStatus(SetupStepStage.SYSTEM_PACKAGES, StepStatus.FAILED, desc = reason, error = names)
                SetupOutcome.Failed(stage = SetupStepStage.SYSTEM_PACKAGES.name, reason = reason)
            }

            else -> null
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
    private suspend fun archiveAttempt(attemptId: String, outcome: SetupOutcome): SetupOutcome =
        // Called from a cancel's catch: in the cancelled context the journal write (withContext(IO)) would be refused
        // before it ran, leaving the attempt RUNNING and blocking the next operation until recovery.
        withContext(NonCancellable) { archiveAttemptNow(attemptId, outcome) }

    @Suppress("ReturnCount") // no-journal, foreign attempt, unproven children and write failure are distinct verdicts
    private suspend fun archiveAttemptNow(attemptId: String, outcome: SetupOutcome): SetupOutcome {
        val repo = repository ?: return outcome
        val active = repo.currentState.activeAttempt
        if (active == null || active.attemptId != attemptId) return outcome
        if (outcome is SetupOutcome.AwaitingUserAction) {
            return outcome
        }
        if (active.unprovenIntents.isNotEmpty()) {
            val reason =
                "Attempt $attemptId ended (${outcome::class.simpleName}) while " +
                    "${active.unprovenIntents.size} journaled command(s) lack terminal proof; reconnect to reconcile."
            repo.setRecoveryBlocked(true, reason)
            return SetupOutcome.Interrupted(reason)
        }
        val status =
            when (outcome) {
                is SetupOutcome.Succeeded -> AttemptStatus.SUCCEEDED

                is SetupOutcome.Cancelled ->
                    if (active.status == AttemptStatus.AWAITING_USER_ACTION &&
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
        publishJournalState()
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
        val record =
            SetupAttemptRecord(
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

    /** The journal's id for this action, as the plan records completed ones. */
    private fun SetupPlanAction.actionId(): String = when (this) {
        is SetupPlanAction.InstallPackages -> "packages"
        is SetupPlanAction.SwitchToolchain -> "switch-toolchain"
    }

    /** Remembers [runId] as the run [stage] started, so its output can be followed. */
    private fun recordRunStarted(stage: SetupStepStage, runId: String) {
        _state.update { s ->
            s.copy(
                stepRunIds = s.stepRunIds + (stage to runId),
                steps = s.steps.map { if (it.stage == stage) it.copy(runId = runId) else it },
            )
        }
    }

    /**
     * A repair rebuilds one tool's recipe group: refused when the tool has no recipe; otherwise every tool of that
     * group loses its verification (FR-009), the target shows as running and the others as pending.
     */
    private suspend fun prepareRepair(targetTool: String?): SetupOutcome? {
        if (targetTool == null || !ToolCapabilities.isRecipeSupported(targetTool)) {
            val reason = ToolCapabilities.unavailableReasonFor(targetTool ?: "")
                ?: ToolCapabilities.REPAIR_UNAVAILABLE_REASON
            appendLog("[Tool Matrix] Repair unavailable for '$targetTool': $reason")
            return SetupOutcome.Failed(stage = SetupStepStage.TOOLCHAIN_COMPILATION.name, reason = reason)
        }
        ToolCapabilities.recipeGroupFor(targetTool)?.let(ToolCapabilities::toolsForRecipeGroup)
            ?.let { invalidateGroup(targetTool, it) }
        return null
    }

    /** [affected] lose their verification; [targetTool] shows as running and the rest of its group as pending. */
    private suspend fun invalidateGroup(targetTool: String, affected: Set<String>) {
        for (toolId in affected) repository?.updateTool(toolId) { it.copy(isVerified = false) }
        _state.update { s ->
            s.copy(
                toolsMatrix = s.toolsMatrix.map {
                    if (it.id !in affected) {
                        it
                    } else {
                        it.copy(
                            status = if (it.id == targetTool) StepStatus.RUNNING else StepStatus.PENDING,
                            lastTested = null,
                        )
                    }
                },
            )
        }
    }

    /**
     * Executes the confirmed plan's actions in order. All mutations go through the daemon service
     * transport ([TransportCliExecutor]) - never the raw host `wsl.exe` executor - and every step's
     * status comes from a verified fact (re-run diagnostics, engine result, real listing), never from
     * a discarded Boolean or a command that merely returned.
     */
    @Suppress("ReturnCount") // a refused repair and a failed plan end it before the installed evidence
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

        if (currentPlan.kind == SetupPlanKind.REPAIR_TOOL) {
            prepareRepair(currentPlan.targetStageOrToolId)?.let { return it }
        }

        val operationScope =
            OperationScope(
                environmentKey = distro,
                operationId = journal?.attemptId ?: "plan-${currentPlan.planId}",
                stateFlow = _state,
                activeOpId = { _state.value.activeOperationId },
            )
        val installedScope =
            InstalledScope(
                environmentKey = distro,
                stateFlow = _state,
            )
        val context =
            ExecutionContext(
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

        // Installed evidence: updates paths and installed status from component inventory or probe.
        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val now = System.currentTimeMillis()
        val inventory = toolManager?.refresh()?.getOrNull() ?: toolManager?.state?.value?.inventory
        val updatedTools = if (inventory != null) {
            toolsFromInventory(currentTools, inventory, home)
        } else {
            currentTools.map { tool ->
                val path = toolBinary(home, tool.id, tool.binaryName).path
                val exists = transport.execute(
                    ToolExecutionRequest(
                        toolId = "test",
                        arguments = listOf("-f", path),
                        workingDirectory = "/",
                        purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
                    ),
                ).exitCode == 0
                if (exists) repository?.updateTool(tool.id) { t -> t.copy(isCompiled = true, binaryPath = path) }
                toolFromFile(tool, path, exists)
            }
        }
        if (toolManager != null) {
            for (tool in updatedTools) {
                repository?.updateTool(tool.id) {
                    it.copy(
                        isCompiled = tool.path != null,
                        isVerified = tool.status == StepStatus.SUCCESS,
                        binaryPath = tool.path,
                    )
                }
            }
        }
        _state.update { it.copy(toolsMatrix = updatedTools, lastVerifiedTimestamp = now) }
        if (currentPlan.kind == SetupPlanKind.FULL_SETUP) {
            repository?.markSetupCompleted(true, now)
        }

        val evidenceMap = planEvidence(updatedTools, binDir, now)
        appendLog("=== Plan ${currentPlan.planId} (${currentPlan.kind}) completed ===")
        return SetupOutcome.Succeeded(evidence = evidenceMap)
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

    /** Sets one step's status, description, error and failure category. */
    private fun setStepStatus(
        stage: SetupStepStage,
        status: StepStatus,
        desc: String? = null,
        error: String? = null,
        failureCategory: DaemonFailureCategory? = null,
        toolchainFailureCategory: ToolchainFailureCategory? = null,
    ) {
        _state.update { it.withStepStatus(stage, status, desc, error, failureCategory, toolchainFailureCategory) }
    }

    /**
     * Provisioner-originated narration (not server run output). Streamed run output reaches
     * [logBuffer] through [SetupJournalContext.activity] with its real journal identity instead.
     */
    override fun log(line: String) {
        appendLog(line, SetupLogKind.CHECK)
    }

    /** Adds [line] to the activity log under the open attempt (or the local one). */
    private fun appendLog(line: String, kind: SetupLogKind = SetupLogKind.RUN) {
        val attemptId = repository?.currentState?.activeAttempt?.attemptId ?: LOCAL_ATTEMPT_ID
        logBuffer.append(text = line, attemptId = attemptId, childRunId = PROVISIONER_CHILD_ID, kind = kind)
        publishLogTail(line)
    }

    /** Keeps only a short tail on the immutable snapshot; the full bounded history lives in [logBuffer]. */
    private fun publishLogTail(line: String) {
        _state.update { it.withLogLine(line, STATE_LOG_TAIL_LINES) }
    }
}
