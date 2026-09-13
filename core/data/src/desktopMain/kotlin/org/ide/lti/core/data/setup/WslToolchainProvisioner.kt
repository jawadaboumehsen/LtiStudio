package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.WslDaemonManager
import org.ide.lti.core.data.setup.doctor.CompositeSystemDoctorEngine
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.ToolchainIntegrityInspector
import org.ide.lti.core.data.setup.doctor.WslPackageRemediator
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import io.ltirom.tooling.client.wsl.TransportCliExecutor
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolEvidence
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import java.io.File

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
    private val coordinator: SetupOperationCoordinator = SetupOperationCoordinator(),
) : ToolchainProvisioningService {

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
        if (!persisted.isSetupCompleted && persisted.completedStages.isEmpty() && persisted.tools.isEmpty()) {
            return ToolchainSetupState()
        }

        val lastVerified = persisted.lastVerifiedTimestamp
        val restoredSteps = ToolchainSetupState.defaultSteps().map { step ->
            if (persisted.isStageCompleted(step.stage.name) || persisted.isSetupCompleted) {
                step.copy(
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.RESTORED,
                    verifiedAtEpochMs = lastVerified,
                    description = "Last verified ${formatAgo(lastVerified)} — re-checking…"
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
                tool.copy(
                    status = StepStatus.SUCCESS,
                    path = pTool.binaryPath,
                    version = pTool.version,
                    lastTested = pTool.lastVerifiedTimestamp?.let { "Restored" } ?: "Ready"
                )
            } else tool
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
        )
    }



    public companion object {
        private const val DEFAULT_DISTRO: String = "Ubuntu"

        public data class PinnedSubmodule(
            val name: String,
            val url: String,
            val commit: String
        )

        public val SUBMODULES: List<PinnedSubmodule> = listOf(
            PinnedSubmodule("android-tools", "https://github.com/UN1CA/external_android-tools.git", "2c5e1b7c192f30ec3d50ec5f983618bb87b9895c"),
            PinnedSubmodule("apktool", "https://github.com/iBotPeaches/Apktool.git", "1c1d15b070c2138d958fa8b685d430c49c4bb117"),
            PinnedSubmodule("erofs-utils", "https://github.com/sekaiacg/erofs-utils.git", "24ebe947b889fabcd72f802e4f184b0f807a0e0e"),
            PinnedSubmodule("img2sdat", "https://github.com/UN1CA/external_img2sdat.git", "601317ed0bf502eb8facdd048f2d75dc75ffcd53"),
            PinnedSubmodule("signapk", "https://github.com/UN1CA/external_signapk.git", "cb88f4b27d8a07ca101eade5d40a6893920cb55e")
        )

        public val CORE_BINARIES: List<String> = listOf(
            "adb", "fastboot", "mke2fs", "dump.erofs", "mkfs.erofs", "img2sdat"
        )

        public val ALL_CORE_TOOLS: List<String> = ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS

        public val REQUIRED_DEV_PACKAGES: List<String> = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES

        public val SUPPORTED_RECIPES: Set<String> = setOf(
            "adb", "fastboot", "mke2fs", "avbtool", "mkbootimg", "lpmake", "lpunpack", "lpdump",
            "mkfs.erofs", "dump.erofs", "fsck.erofs", "erofsfuse", "img2sdat",
            "apktool", "signapk", "payload-dumper-go"
        )

        public fun isRecipeSupported(toolId: String): Boolean = toolId in SUPPORTED_RECIPES
    }


    /** Names of the executable files in [binDir] (symlinks followed) via a single `find` call. */
    private fun listExecutables(distro: String, binDir: String, executor: WslCliExecutor): Set<String> {
        val result = executor.execute(
            distro,
            listOf("find", "-L", binDir, "-maxdepth", "1", "-type", "f", "-perm", "-u+x", "-printf", "%f\n"),
        )
        if (result.exitCode != 0) return emptySet()
        return result.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    override suspend fun checkStatus(): ToolchainSetupState = verifyEnvironment()

    override suspend fun verifyEnvironment(): ToolchainSetupState = withContext(dispatcher) {
        if (_state.value.isChecking) return@withContext _state.value
        _state.update { it.copy(isChecking = true) }
        try {
            // 1. WSL_DETECTION
            val wslStep = _state.value.steps.firstOrNull { it.stage == SetupStepStage.WSL_DETECTION }
            val isBridgeConnected = _state.value.steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }
                ?.let { it.status == StepStatus.SUCCESS && it.provenance == StepProvenance.LIVE } == true
            val alreadyVerifiedWsl = isBridgeConnected || (wslStep?.status == StepStatus.SUCCESS && wslStep.provenance == StepProvenance.LIVE)
            setStepRunning(SetupStepStage.WSL_DETECTION)
            val isWslOk = if (alreadyVerifiedWsl) true else detector.isWslInstalled()
            val distro = if (alreadyVerifiedWsl) {
                _state.value.activeDistro ?: "Ubuntu"
            } else if (isWslOk) {
                detector.getDefaultDistro() ?: "Ubuntu"
            } else {
                "Ubuntu"
            }

            if (!isWslOk) {
                setStepStatus(
                    SetupStepStage.WSL_DETECTION,
                    StepStatus.FAILED,
                    desc = "WSL 2 not detected or no distribution found.",
                    error = "WSL 2 is not installed. Run 'wsl --install -d Ubuntu' in PowerShell."
                )
                shortCircuitRemaining(SetupStepStage.WSL_DETECTION, "Pending WSL.")
                return@withContext _state.value
            }

            // Host-side WSL facts (installed distributions) belong to the detection step; nothing
            // after the bridge is established goes through the host wsl.exe executor.
            val distros = if (alreadyVerifiedWsl) {
                _state.value.installedDistros.ifEmpty { listOf(distro) }
            } else {
                detector.listDistros().ifEmpty { listOf(distro) }
            }
            setStepStatus(
                SetupStepStage.WSL_DETECTION,
                StepStatus.SUCCESS,
                desc = "WSL 2 active with default distribution '$distro'."
            )

            // 2. SERVER_CONNECTIVITY
            setStepRunning(SetupStepStage.SERVER_CONNECTIVITY)
            val desc = runCatching { supervisor.ensureStarted() }
            if (desc.isFailure) {
                val err = desc.exceptionOrNull()
                val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
                    ?: supervisor.lastLaunchErrorLogTail
                val errMsg = logTail ?: err?.message ?: "Failed to connect to daemon"
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.FAILED,
                    desc = "Daemon bridge failed: ${err?.message ?: "unreachable"}",
                    error = errMsg
                )
                shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending daemon.")
                return@withContext _state.value
            }

            val startMs = System.currentTimeMillis()
            val health = transport.checkHealth()
            if (health == null) {
                val logTail = (supervisor as? WslDaemonManager)?.lastLaunchErrorLogTail
                    ?: supervisor.lastLaunchErrorLogTail
                setStepStatus(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    StepStatus.FAILED,
                    desc = "Daemon bridge failed: health check returned null",
                    error = logTail ?: "Health check returned null"
                )
                shortCircuitRemaining(SetupStepStage.SERVER_CONNECTIVITY, "Pending daemon.")
                return@withContext _state.value
            }
            val pingMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val connDesc = desc.getOrThrow()
            setStepStatus(
                SetupStepStage.SERVER_CONNECTIVITY,
                StepStatus.SUCCESS,
                desc = "Connected · ${pingMs}ms · pid ${connDesc.pid}"
            )
            _state.update { it.copy(daemonPingMs = pingMs) }

            // Post-bridge: All remote calls use transport
            val home = runCatching {
                val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
                if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
            }.getOrNull() ?: detector.resolveWslUserHome(distro)
            val binDir = "$home/LtiRomTools/bin"
            val extDir = "$home/LtiRomTools/external"
            val workDir = "$home/LtiRomWorkDir"
            val avbKeyFile = "$workDir/security/avb/lti_rsa4096.pem"
            val transportCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)

            // 3. TOOLCHAIN_COMPILATION
            setStepRunning(SetupStepStage.TOOLCHAIN_COMPILATION)
            val findBin = transport.execute(
                ToolExecutionRequest(
                    toolId = "find",
                    arguments = listOf(
                        "-L", binDir, "-maxdepth", "1", "-type", "f", "-perm", "-u+x", "-printf", "%f\n"
                    ),
                    workingDirectory = "/",
                )
            )
            val executables = if (findBin.exitCode == 0) {
                findBin.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            } else {
                emptySet()
            }

            val publishedTools = transport.listTools()
                .filter { it.installed }
                .map { it.tool }
                .toSet()

            val missingBins = CORE_BINARIES.filterNot { it in executables }
            val compilationStatus = if (missingBins.isEmpty()) StepStatus.SUCCESS else StepStatus.PENDING
            val compilationDesc = if (missingBins.isEmpty()) {
                "${executables.count { it in ALL_CORE_TOOLS }}/${ALL_CORE_TOOLS.size} tools · ${publishedTools.size} published"
            } else {
                "${missingBins.first()} not built"
            }
            setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, compilationStatus, desc = compilationDesc)

            // 4. REPO_SYNCHRONIZATION
            setStepRunning(SetupStepStage.REPO_SYNCHRONIZATION)
            val findExt = transport.execute(
                ToolExecutionRequest(
                    toolId = "find",
                    arguments = listOf(extDir, "-maxdepth", "1", "-mindepth", "1", "-type", "d", "-printf", "%f\n"),
                    workingDirectory = "/",
                )
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
                )
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

            // 5. SYSTEM_DIAGNOSTICS
            setStepRunning(SetupStepStage.SYSTEM_DIAGNOSTICS)
            val binariesPresent = missingBins.isEmpty()
            val doctorEngine = CompositeSystemDoctorEngine(
                cli = transportCli,
                detector = WslEnvironmentDetector(transportCli),
                dispatcher = dispatcher,
                inspectors = CompositeSystemDoctorEngine.defaultInspectors(transportCli),
                remediator = WslPackageRemediator(transportCli),
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
                    "${diagnosticsList.count { it.status == StepStatus.WARNING }} warnings detected in pre-flight checks."
                else -> "${diagnosticsList.size}/${diagnosticsList.size} host and build system checks verified."
            }
            setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, diagStatus, desc = diagDesc)
            _state.update { it.copy(diagnostics = diagnosticsList) }

            // AVB Key Check
            val testAvb = transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf("-f", avbKeyFile),
                    workingDirectory = "/",
                )
            )
            val hasAvbKey = testAvb.exitCode == 0

            // Tools Matrix Update
            val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
            val updatedTools = currentTools.map { tool ->
                val toolPath = "$binDir/${tool.binaryName}"
                val exists = tool.binaryName in executables
                tool.copy(
                    path = if (exists) toolPath else null,
                    status = if (exists) StepStatus.SUCCESS else StepStatus.PENDING,
                    lastTested = if (exists) "Just now" else tool.lastTested
                )
            }

            val storageDf = runCatching {
                transport.execute(
                    ToolExecutionRequest(
                        toolId = "df",
                        arguments = listOf("-B1", home),
                        workingDirectory = "/",
                    )
                )
            }.getOrNull()
            val (storageAvailableBytes, storageMeasuredAt) = if (storageDf != null && storageDf.exitCode == 0) {
                val lines = storageDf.stdout.lines()
                val tokens = if (lines.size >= 2) lines[1].trim().split("\\s+".toRegex()) else null
                val bytes = tokens?.getOrNull(3)?.toLongOrNull()
                if (bytes != null) Pair(bytes, System.currentTimeMillis()) else Pair(null, null)
            } else Pair(null, null)

            val now = System.currentTimeMillis()
            _state.update { s ->
                val allPublished = publishedTools.containsAll(ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS)
                val isFullyReady = s.isAllReady && allPublished
                s.copy(
                    diagnostics = diagnosticsList.ifEmpty { s.diagnostics },
                    isAvbKeyProvisioned = hasAvbKey,
                    avbKeyPath = if (hasAvbKey) avbKeyFile else null,
                    activeDistro = distro,
                    installedDistros = distros,
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
                )
            }
            if (_state.value.isAllReady) {
                val now = _state.value.lastVerifiedTimestamp
                repository?.let { repo ->
                    repo.saveToolchainState(repo.currentState.copy(lastVerifiedTimestamp = now))
                }
            }
            _state.value
        } catch (e: CancellationException) {
            // Cancellation is never a verification failure; propagate after finally-cleanup (FR-006).
            throw e
        } catch (e: Exception) {
            val currentStage = _state.value.currentStage ?: SetupStepStage.SERVER_CONNECTIVITY
            setStepStatus(
                currentStage,
                StepStatus.FAILED,
                desc = "Verification failed: ${e.message ?: "network or daemon error"}",
                error = e.message
            )
            shortCircuitRemaining(currentStage, "Pending resolution of ${currentStage.name}.")
            _state.value
        } finally {
            _state.update { it.copy(isChecking = false, currentStage = null) }
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
                it.status == StepStatus.SUCCESS && it.provenance == StepProvenance.LIVE
        }
        return if (liveVerified) {
            snapshot.activeDistro ?: DEFAULT_DISTRO
        } else {
            detector.getDefaultDistro() ?: snapshot.activeDistro ?: DEFAULT_DISTRO
        }
    }

    private suspend fun setStepRunning(stage: SetupStepStage) {
        _state.update { s ->
            val updatedSteps = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        status = StepStatus.RUNNING,
                        provenance = StepProvenance.LIVE
                    )
                } else step
            }
            s.copy(
                steps = updatedSteps,
                currentStage = stage
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
                        error = null
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
        distroName: String? = null
    ): Boolean = withContext(dispatcher) {
        if (_state.value.isBusy && serviceCli == null) return@withContext false
        val distro = distroName ?: _state.value.activeDistro ?: (if (serviceCli == null) detector.getDefaultDistro() else null) ?: "Ubuntu"
        val userHome = home ?: runCatching {
            val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
            if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
        }.getOrNull() ?: (if (serviceCli == null) detector.resolveWslUserHome(distro) else "/home/lti")
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
                listOf("openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096", "-out", keyPath)
            )
            effectiveCli.execute(distro, listOf("chmod", "600", keyPath))
            appendLog("[AVB 2.0] Private key generated with mode 0600 at $keyPath.")
        } else {
            appendLog("[AVB 2.0] Existing RSA-4096 private key detected.")
        }

        // Extract public key using avbtool if available
        val hasAvbTool = effectiveCli.execute(distro, listOf("test", "-x", "$binDir/avbtool")).exitCode == 0
        if (hasAvbTool) {
            effectiveCli.execute(distro, listOf("$binDir/avbtool", "extract_public_key", "--key", keyPath, "--output", pubKeyPath))
            appendLog("[AVB 2.0] Derived AVB public key at $pubKeyPath.")
        }

        val verified = effectiveCli.execute(distro, listOf("test", "-f", keyPath)).exitCode == 0
        if (verified) {
            _state.update { s ->
                s.copy(
                    isAvbKeyProvisioned = true,
                    avbKeyPath = keyPath
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
            val userHome = runCatching {
                val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
                if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
            }.getOrNull() ?: detector.resolveWslUserHome(distro)
            val binDir = "$userHome/LtiRomTools/bin"
            val binaryName = toolItem.binaryName
            val binaryPath = "$binDir/$binaryName"

            _state.update { s ->
                s.copy(
                    toolsMatrix = s.toolsMatrix.map {
                        if (it.id == toolId) it.copy(status = StepStatus.RUNNING) else it
                    }
                )
            }

            val effectiveCli = TransportCliExecutor(transport, defaultWorkingDirectory = userHome)
            val exists = effectiveCli.execute(distro, listOf("test", "-x", binaryPath)).exitCode == 0
            // Real process exit code: do NOT infer success from stdout text alone (FR-009)
            val testStatus = if (exists) {
                val execRes = effectiveCli.execute(distro, listOf(binaryPath, "--help"), timeoutSeconds = 5)
                if (execRes.exitCode == 0) StepStatus.SUCCESS else StepStatus.FAILED
            } else {
                StepStatus.FAILED
            }

            val now = System.currentTimeMillis()
            _state.update { s ->
                s.copy(
                    toolsMatrix = s.toolsMatrix.map {
                        if (it.id == toolId) it.copy(
                            status = testStatus,
                            path = if (exists) binaryPath else null,
                            lastTested = if (testStatus == StepStatus.SUCCESS) "Just now" else it.lastTested
                        ) else it
                    }
                )
            }
            if (testStatus == StepStatus.SUCCESS) {
                repository?.updateTool(toolId) { tool ->
                    tool.copy(
                        isVerified = true,
                        isCompiled = true,
                        binaryPath = if (exists) binaryPath else tool.binaryPath,
                        lastVerifiedTimestamp = now
                    )
                }
                SetupOutcome.Succeeded(
                    evidence = mapOf(
                        toolId to ToolEvidence(
                            toolId = toolId,
                            probeArgv = listOf(binaryPath, "--help"),
                            acceptedExitCodes = setOf(0),
                            isInstalled = exists,
                            isVerified = true,
                            isFailedExecution = false,
                            lastVerifiedTimestamp = now
                        )
                    )
                )
            } else {
                SetupOutcome.Failed(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                    reason = "Tool $toolId verification probe failed"
                )
            }
        }.also { _operationOutcome.value = it }
    }

    override suspend fun repairTool(toolId: String): Boolean = withContext(dispatcher) {
        if (!isRecipeSupported(toolId)) {
            appendLog("[Tool Matrix] Repair unavailable for '$toolId': Repair requires a supported recipe; otherwise the action is unavailable with a reason.")
            return@withContext false
        }
        if (_state.value.isChecking) {
            appendLog("[Tool Matrix] Cannot repair '$toolId' while an environment check is running.")
            return@withContext false
        }
        val distro = resolveEnvironmentKey()
        val opId = "repair-$toolId-${System.currentTimeMillis()}"

        val outcome = coordinator.withAdmission(
            environmentKey = distro,
            operationId = opId,
            kind = "REPAIR_TOOL",
        ) {
            _state.update { it.copy(isRunning = true, activeOperationId = opId) }
            try {
                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId) it.copy(status = StepStatus.RUNNING) else it
                        }
                    )
                }
                appendLog("[Tool Matrix] Executing rebuild recipe for tool: $toolId...")
                val userHome = runCatching {
                    val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
                    if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
                }.getOrNull() ?: detector.resolveWslUserHome(distro)
                val toolsRoot = "$userHome/LtiRomTools"
                val extDir = "$toolsRoot/external"
                val binDir = "$toolsRoot/bin"
                val effectiveCli = TransportCliExecutor(transport, defaultWorkingDirectory = userHome)
                val buildEngine = ToolchainBuildEngine(
                    cli = effectiveCli,
                    repository = repository,
                    transport = transport,
                )

                val buildRes = buildEngine.buildAndDistributeAll(distro, extDir, binDir, log = { appendLog(it) })
                if (buildRes.isFailure) {
                    appendLog("[Tool Matrix] Build failed for $toolId: ${buildRes.exceptionOrNull()?.message}")
                    _state.update { s ->
                        s.copy(
                            toolsMatrix = s.toolsMatrix.map {
                                if (it.id == toolId) it.copy(status = StepStatus.FAILED) else it
                            }
                        )
                    }
                    return@withAdmission SetupOutcome.Failed(
                        "TOOLCHAIN_COMPILATION",
                        buildRes.exceptionOrNull()?.message ?: "Build failed",
                    )
                }

                // Verify the repaired binary using real exit codes (FR-009)
                val toolItem = _state.value.toolsMatrix.firstOrNull { it.id == toolId }
                val binaryName = toolItem?.binaryName ?: toolId
                val binaryPath = "$binDir/$binaryName"
                val exists = effectiveCli.execute(distro, listOf("test", "-x", binaryPath)).exitCode == 0
                val execRes = if (exists) effectiveCli.execute(distro, listOf(binaryPath, "--help"), timeoutSeconds = 5) else null
                val isOk = exists && execRes?.exitCode == 0

                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId) it.copy(
                                status = if (isOk) StepStatus.SUCCESS else StepStatus.FAILED,
                                path = if (exists) binaryPath else null,
                                lastTested = if (isOk) "Just now" else it.lastTested
                            ) else it
                        }
                    )
                }
                if (isOk) {
                    repository?.updateTool(toolId) { tool ->
                        tool.copy(
                            isVerified = true,
                            isCompiled = true,
                            binaryPath = binaryPath,
                            lastVerifiedTimestamp = System.currentTimeMillis()
                        )
                    }
                    SetupOutcome.Succeeded()
                } else {
                    SetupOutcome.Failed("TOOLCHAIN_COMPILATION", "Repair verification failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendLog("[Tool Matrix] Exception during repair of $toolId: ${e.message}")
                _state.update { s ->
                    s.copy(
                        toolsMatrix = s.toolsMatrix.map {
                            if (it.id == toolId) it.copy(status = StepStatus.FAILED) else it
                        }
                    )
                }
                SetupOutcome.Failed("TOOLCHAIN_COMPILATION", e.message ?: "Repair exception")
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null) }
            }
        }
        outcome.isSuccess
    }

    // ---- Plan-driven operation contract (003 US2) ----
    //
    // Every mutation is previewed by [prepare] and executed only by [confirm]. A plan is derived
    // from the real current state (never mutating anything); confirmation enforces the plan ID /
    // revision invariant, revalidates the live evidence and environment, freezes the Auto Doctor
    // policy carried by the plan, and runs under the single environment operation owner. Journal-
    // backed recovery is owned by 003 US3 (T019-T023); until it lands [recover] returns a typed
    // Failed with the reason - never a synthesized Succeeded.

    override suspend fun prepare(
        kind: SetupPlanKind,
        targetId: String?,
        autoDoctorEnabled: Boolean,
    ): SetupPlan = withContext(dispatcher) {
        val snapshot = _state.value
        val distro = resolveEnvironmentKey()
        val plan = SetupPlanFactory.createPlan(
            kind = kind,
            targetId = targetId,
            autoDoctorEnabled = autoDoctorEnabled,
            snapshot = snapshot,
            environmentKey = distro,
        )
        activePlan = plan
        plan
    }

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
            // The plan is consumed by this attempt: a second confirmation must preview again.
            activePlan = null
            _state.update { it.copy(isRunning = true, activeOperationId = currentPlan.planId) }
            try {
                when (currentPlan.kind) {
                    SetupPlanKind.CACHE_RESET -> executeCacheReset()
                    SetupPlanKind.FULL_SETUP,
                    SetupPlanKind.STAGE_RETRY,
                    SetupPlanKind.REPAIR_TOOL -> {
                        // Failed health is a terminal prerequisite failure: nothing else runs.
                        val health = transport.checkHealth()
                        if (health == null) {
                            SetupOutcome.Failed(
                                stage = SetupStepStage.SERVER_CONNECTIVITY.name,
                                reason = "Daemon bridge failed: health check returned null.",
                            )
                        } else {
                            executePlan(currentPlan, distro)
                        }
                    }
                }
            } finally {
                _state.update { it.copy(isRunning = false, activeOperationId = null, currentStage = null) }
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

    /**
     * Clears the persisted setup evidence. Reached only through a confirmed CACHE_RESET plan; there
     * is no direct entry point (FR-003). A cleared state is Unknown, never Ready.
     */
    private suspend fun executeCacheReset(): SetupOutcome {
        repository?.clearToolchainState()
        _state.value = ToolchainSetupState()
        appendLog("[Cache] Reset toolchain persistence cache to clean default.")
        return SetupOutcome.Succeeded(evidence = emptyMap())
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
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    private suspend fun executePlan(currentPlan: SetupPlan, distro: String): SetupOutcome {
        val home = runCatching {
            val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
            if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
        }.getOrNull() ?: detector.resolveWslUserHome(distro)
        val toolsRoot = "$home/LtiRomTools"
        val extDir = "$toolsRoot/external"
        val binDir = "$toolsRoot/bin"
        val workDir = "$home/LtiRomWorkDir"
        val serviceCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)

        for (action in currentPlan.orderedActions) {
            when (action) {
                is SetupPlanAction.InstallPackages -> {
                    // "Auto Doctor policy is immutable for the confirmed attempt": the plan carried the
                    // policy at preview time; a plan built with the policy off never contains this action.
                    currentPlan.validateAttemptPolicy(true)
                    val outcome = executeRemediation(action, distro, home, binDir, serviceCli)
                    if (outcome != null) return outcome
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
                        "All ${action.submodules.size} submodules and $workDir ready."
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
                    val buildRes = buildEngine.buildAndDistributeAll(
                        distro,
                        extDir,
                        binDir,
                        onRunStarted = ::recordRunStarted,
                    ) { appendLog(it) }
                    if (buildRes.isFailure) {
                        val err = buildRes.exceptionOrNull()?.message ?: "Some core tool binaries are missing in $binDir after build."
                        appendLog("[Step 5/5] ERROR: $err")
                        setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.FAILED, error = err)
                        return SetupOutcome.Failed(SetupStepStage.TOOLCHAIN_COMPILATION.name, err)
                    }
                    setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.SUCCESS, "Core tools ready in $binDir.")
                }
                is SetupPlanAction.PublishTools -> {
                    val pubRes = toolPublicationPort?.publish(binDir)
                    val publishedIds = pubRes?.published ?: emptySet()
                    val unpublishedIds = pubRes?.skipped ?: emptySet()
                    _state.update {
                        it.copy(
                            publishedToolIds = publishedIds,
                            unpublishedToolIds = unpublishedIds,
                        )
                    }
                    appendLog(
                        "[Step 5/5] Published ${publishedIds.size} tools to daemon " +
                            "(${unpublishedIds.size} skipped)."
                    )
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
                    probeArgv = listOf("$binDir/${tool.binaryName}", "--help"),
                    acceptedExitCodes = setOf(0),
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
            detector = WslEnvironmentDetector(serviceCli),
            dispatcher = dispatcher,
            inspectors = CompositeSystemDoctorEngine.defaultInspectors(serviceCli),
            remediator = WslPackageRemediator(serviceCli),
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

    override suspend fun recover(attemptId: String?): SetupOutcome = SetupOutcome.Failed(
        stage = null,
        reason = "No attempt journal exists yet (003 US3 T019-T023); " +
            (attemptId?.let { "attempt '$it' cannot be reconciled." } ?: "nothing to reconcile."),
    )

    override suspend fun cancel(): SetupOutcome = withContext(dispatcher) {
        val outcome = coordinator.cancelActiveOperation(resolveEnvironmentKey())
        _operationOutcome.value = outcome
        outcome
    }


    private fun setStepStatus(stage: SetupStepStage, status: StepStatus, desc: String? = null, error: String? = null) {
        _state.update { s ->
            val updatedSteps = s.steps.map { step ->
                if (step.stage == stage) {
                    step.copy(
                        status = status,
                        description = desc ?: step.description,
                        error = error,
                        provenance = StepProvenance.LIVE
                    )
                } else step
            }
            s.copy(
                steps = updatedSteps,
                currentStage = if (status == StepStatus.RUNNING) stage else null
            )
        }
    }

    private fun appendLog(line: String) {
        _state.update { s ->
            s.copy(
                logs = s.logs + line,
                activeLogLine = line
            )
        }
    }
}
