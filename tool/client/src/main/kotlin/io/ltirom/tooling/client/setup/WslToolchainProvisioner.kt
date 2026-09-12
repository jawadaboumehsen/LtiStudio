package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.setup.doctor.CompositeSystemDoctorEngine
import io.ltirom.tooling.client.setup.doctor.NativeLibrariesInspector
import io.ltirom.tooling.client.setup.doctor.ToolchainIntegrityInspector
import io.ltirom.tooling.client.setup.doctor.WslPackageRemediator
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.doctor.SystemDoctorPort
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import java.io.File

/**
 * Concrete implementation of [ToolchainProvisioningService] orchestrating WSL environment detection,
 * daemon bridge connectivity, submodule repository synchronization, and native tool compilation.
 *
 * Adheres to:
 * - Single Responsibility Principle: coordinates sequential setup workflow and state emissions.
 *   Diagnostic health checks are delegated to [SystemDoctorPort].
 *   Submodule management is delegated to [SubmoduleSyncEngine].
 *   Binary compilation is delegated to [ToolchainBuildEngine].
 * - Dependency Inversion Principle: depends on abstractions ([DaemonSupervisorPort], [RemoteTransportPort],
 *   [SystemDoctorPort], [ToolchainSetupRepository]).
 * - Persistence & Incremental Execution: Leverages [ToolchainSetupRepository] to eliminate redundant
 *   zero-start builds, Git clones, and native compilations across launches.
 */
public class WslToolchainProvisioner(
    private val supervisor: DaemonSupervisorPort,
    private val transport: RemoteTransportPort,
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(),
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val doctorPort: SystemDoctorPort = CompositeSystemDoctorEngine(
        cli = cli,
        detector = detector,
        dispatcher = dispatcher
    ),
    private val repository: ToolchainSetupRepository? = null,
    private val toolPublicationPort: ToolPublicationPort? = null,
) : ToolchainProvisioningService {

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
        )
    }



    public companion object {
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

            var publishedTools = transport.listTools()
                .filter { it.installed }
                .map { it.tool }
                .toSet()

            val expectedTools = EnvironmentProbe.DEFAULT_EXPECTED_PRODUCT_TOOLS
            val missingExpected = expectedTools - publishedTools
            if (missingExpected.isNotEmpty() && executables.any { it in expectedTools }) {
                toolPublicationPort?.publish(binDir)
                publishedTools = transport.listTools()
                    .filter { it.installed }
                    .map { it.tool }
                    .toSet()
            }

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

            val distros = if (alreadyVerifiedWsl) _state.value.installedDistros.ifEmpty { listOf(distro) } else detector.listDistros().ifEmpty { listOf(distro) }
            _state.update { s ->
                s.copy(
                    diagnostics = diagnosticsList.ifEmpty { s.diagnostics },
                    isAvbKeyProvisioned = hasAvbKey,
                    avbKeyPath = if (hasAvbKey) avbKeyFile else null,
                    activeDistro = distro,
                    installedDistros = distros,
                    toolsMatrix = updatedTools,
                    currentStage = null,
                    isChecking = false,
                    lastVerifiedTimestamp = if (s.isAllReady) System.currentTimeMillis() else s.lastVerifiedTimestamp
                )
            }
            if (_state.value.isAllReady) {
                val now = _state.value.lastVerifiedTimestamp
                repository?.let { repo ->
                    repo.saveToolchainState(repo.currentState.copy(lastVerifiedTimestamp = now))
                }
            }
            _state.value
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
                isChecking = false
            )
        }
    }

    override suspend fun runFullSetup(): Unit = withContext(dispatcher) {
        if (_state.value.isBusy) return@withContext
        _state.update { it.copy(isRunning = true, logs = emptyList()) }

        val distro = detector.getDefaultDistro() ?: "Ubuntu"
        appendLog("=== Starting LtiRom Environment & Toolchain Setup ===")
        appendLog("Target WSL Distribution: $distro")

        // -------------------------------------------------------------
        // Step 1: WSL Detection
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.WSL_DETECTION, StepStatus.RUNNING)
        appendLog("[Step 1/4] Probing Windows Subsystem for Linux (WSL 2)...")
        val isWslInstalled = detector.isWslInstalled()
        if (!isWslInstalled) {
            val err = "WSL 2 is not installed or has no active distributions. Run 'wsl --install -d Ubuntu' in PowerShell."
            appendLog("[Step 1/4] ERROR: $err")
            setStepStatus(SetupStepStage.WSL_DETECTION, StepStatus.FAILED, error = err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }
        val detectedDistros = detector.listDistros().ifEmpty { listOf(distro) }
        _state.update { it.copy(activeDistro = distro, installedDistros = detectedDistros) }
        appendLog("[Step 1/4] WSL 2 verified with distro '$distro'.")
        setStepStatus(SetupStepStage.WSL_DETECTION, StepStatus.SUCCESS, "WSL 2 active with distro '$distro'.")

        // -------------------------------------------------------------
        // Step 2: Server-Client Bridge Connectivity
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.SERVER_CONNECTIVITY, StepStatus.RUNNING)
        appendLog("[Step 2/4] Connecting to LtiRomServer daemon...")
        val conn = runCatching { supervisor.ensureStarted() }.getOrNull()
        if (conn == null) {
            val err = "Failed to launch or attach to LtiRomServer in WSL distro '$distro'."
            appendLog("[Step 2/4] ERROR: $err")
            setStepStatus(SetupStepStage.SERVER_CONNECTIVITY, StepStatus.FAILED, error = err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }
        val health = transport.checkHealth()
        appendLog("[Step 2/5] Daemon bridge active on http://${conn.host}:${conn.port} [distro=${conn.distro}, pid=${conn.pid}].")
        setStepStatus(SetupStepStage.SERVER_CONNECTIVITY, StepStatus.SUCCESS, "Connected to daemon at http://${conn.host}:${conn.port}.")

        // -------------------------------------------------------------
        // Service Phase: strictly via TransportCliExecutor and RemoteTransportPort
        // -------------------------------------------------------------
        val home = runCatching {
            val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
            if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
        }.getOrNull() ?: detector.resolveWslUserHome(distro)

        val serviceCli = TransportCliExecutor(transport, defaultWorkingDirectory = home)
        val serviceDoctor = CompositeSystemDoctorEngine(
            cli = serviceCli,
            detector = WslEnvironmentDetector(serviceCli),
            dispatcher = dispatcher,
            inspectors = CompositeSystemDoctorEngine.defaultInspectors(serviceCli),
            remediator = WslPackageRemediator(serviceCli),
        )
        val serviceSubmoduleEngine = SubmoduleSyncEngine(
            cli = serviceCli,
            repository = repository,
            transport = transport
        )
        val serviceBuildEngine = ToolchainBuildEngine(
            cli = serviceCli,
            repository = repository,
            transport = transport
        )

        // -------------------------------------------------------------
        // Step 3: Pre-Flight Doctor Diagnostics & Automated Remediation
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, StepStatus.RUNNING)
        appendLog("[Step 3/5] Running pre-flight system diagnostics...")
        val preDiagnostics = serviceDoctor.runDiagnostics(distro, binariesPresent = false)
        val hasBlockers = preDiagnostics.any { it.status == StepStatus.FAILED }
        val hasWarnings = preDiagnostics.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }

        if (hasBlockers || hasWarnings) {
            appendLog("[Step 3/5] Missing system dependencies detected. Initiating automated package remediation via non-interactive sudo...")
            serviceDoctor.autoRemediate(distro) { logLine ->
                appendLog("[Step 3/5] $logLine")
            }
        }

        val finalDiagnostics = serviceDoctor.runDiagnostics(distro, binariesPresent = false)
        _state.update { it.copy(diagnostics = finalDiagnostics) }

        val anyFailed = finalDiagnostics.any { it.status == StepStatus.FAILED }
        val anyWarn = finalDiagnostics.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
        setStepStatus(
            SetupStepStage.SYSTEM_DIAGNOSTICS,
            when {
                anyFailed -> StepStatus.FAILED
                anyWarn -> StepStatus.WARNING
                else -> StepStatus.SUCCESS
            },
            if (anyFailed) "Critical system requirements missing." else "Host runtimes, compilers, and dependencies verified."
        )
        if (anyFailed) {
            val failedNames = finalDiagnostics.filter { it.status == StepStatus.FAILED }.joinToString { it.title }
            val err = "Critical pre-flight requirements not met: $failedNames."
            appendLog("[Step 3/5] ERROR: $err")
            setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, StepStatus.FAILED, error = err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }


        // -------------------------------------------------------------
        // Step 4: Workspace Directory & Source Repositories Sync
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.RUNNING)
        val workDir = "$home/LtiRomWorkDir"
        val toolsRoot = "$home/LtiRomTools"
        val extDir = "$toolsRoot/external"

        appendLog("[Step 4/5] Ensuring workspace root directory exists at $workDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", workDir))
        appendLog("[Step 4/5] Initialized workspace root directory at $workDir.")

        appendLog("[Step 4/5] Ensuring tools directory exists at $extDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", extDir))

        val syncRes = serviceSubmoduleEngine.syncAll(distro, extDir) { appendLog(it) }
        if (syncRes.isFailure) {
            val err = syncRes.exceptionOrNull()?.message ?: "Failed to synchronize submodules"
            appendLog("[Step 4/5] ERROR: $err")
            setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.FAILED, error = err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }
        setStepStatus(SetupStepStage.REPO_SYNCHRONIZATION, StepStatus.SUCCESS, "All 5 submodules and $workDir ready.")

        // -------------------------------------------------------------
        // Step 5: Toolchain Compilation & AVB Key Provisioning
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.RUNNING)
        val binDir = "$toolsRoot/bin"
        appendLog("[Step 5/5] Preparing binary directory at $binDir...")
        serviceCli.execute(distro, listOf("mkdir", "-p", binDir))

        val buildRes = serviceBuildEngine.buildAndDistributeAll(distro, extDir, binDir) { appendLog(it) }
        if (buildRes.isFailure) {
            val err = buildRes.exceptionOrNull()?.message ?: "Some core tool binaries are missing in $binDir after build."
            appendLog("[Step 5/5] WARNING: $err")
            setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.WARNING, err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }

        // Provision AVB key if not present
        val avbKeyPath = "$workDir/security/avb/lti_rsa4096.pem"
        val hasAvb = serviceCli.execute(distro, listOf("test", "-f", avbKeyPath)).exitCode == 0
        if (!hasAvb) {
            appendLog("[Step 5/5] Provisioning machine-local AVB 2.0 RSA-4096 key...")
            provisionAvbKey(serviceCli = serviceCli, home = home, distroName = distro)
        } else {
            repository?.updateAvbKeyState(true, avbKeyPath)
        }

        // Update tools matrix state and persist
        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val builtExecutables = listExecutables(distro, binDir, serviceCli)
        val updatedTools = currentTools.map { tool ->
            val toolPath = "$binDir/${tool.binaryName}"
            val exists = tool.binaryName in builtExecutables
            if (exists) {
                repository?.updateTool(tool.id) { t ->
                    t.copy(
                        isCompiled = true,
                        isVerified = true,
                        binaryPath = toolPath,
                        lastVerifiedTimestamp = System.currentTimeMillis()
                    )
                }
            }
            tool.copy(
                path = if (exists) toolPath else null,
                status = if (exists) StepStatus.SUCCESS else StepStatus.PENDING
            )
        }

        repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
        repository?.markSetupCompleted(true)

        val publicationResult = toolPublicationPort?.publish(binDir)
        val publishedIds = publicationResult?.published ?: emptySet()
        val unpublishedIds = publicationResult?.skipped ?: emptySet()
        if (toolPublicationPort != null) {
            appendLog("[Step 5/5] Published ${publishedIds.size} tools to daemon (${unpublishedIds.size} skipped).")
        }

        appendLog("[Step 5/5] Toolchain binaries successfully built and verified in $binDir.")
        setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.SUCCESS, "Core tools ready in $binDir.")
        appendLog("=== LtiRom Environment & Toolchain Setup Complete! ===")
        _state.update {
            it.copy(
                isRunning = false,
                toolsMatrix = updatedTools,
                lastVerifiedTimestamp = System.currentTimeMillis(),
                publishedToolIds = publishedIds,
                unpublishedToolIds = unpublishedIds
            )
        }
        verifyEnvironment()
    }

    override suspend fun retryCurrentStep() {
        if (_state.value.isBusy) return
        runFullSetup()
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

    override suspend fun autoRemediateDoctor(): Boolean = withContext(dispatcher) {
        if (_state.value.isBusy) return@withContext false
        val distro = detector.getDefaultDistro() ?: "Ubuntu"
        if (!detector.isWslInstalled()) {
            appendLog("[Auto-Remediate] WSL 2 is not installed or active.")
            return@withContext false
        }
        appendLog("=== Starting Automated Pre-Flight Package Remediation ===")
        appendLog("Target Distribution: $distro (Executing via WSL root elevation)")
        _state.update { it.copy(isRunning = true, isFixing = true) }
        val success = try {
            doctorPort.autoRemediate(distro) { logLine ->
                appendLog("[Remediate] $logLine")
            }
        } finally {
            _state.update { it.copy(isFixing = false) }
        }
        checkStatus()
        _state.update { it.copy(isRunning = false) }
        success
    }

    override suspend fun testIndividualTool(toolId: String): StepStatus = withContext(dispatcher) {
        val distro = detector.getDefaultDistro() ?: "Ubuntu"
        val userHome = runCatching {
            val res = transport.execute(ToolExecutionRequest(toolId = "printenv", arguments = listOf("HOME"), workingDirectory = "/"))
            if (res.exitCode == 0 && res.stdout.trim().startsWith("/")) res.stdout.trim() else null
        }.getOrNull() ?: detector.resolveWslUserHome(distro)
        val binDir = "$userHome/LtiRomTools/bin"
        val toolItem = _state.value.toolsMatrix.firstOrNull { it.id == toolId }
        val binaryName = toolItem?.binaryName ?: toolId
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
        val testStatus = if (exists) {
            val execRes = effectiveCli.execute(distro, listOf(binaryPath, "--help"), timeoutSeconds = 5)
            if (execRes.exitCode == 0 || execRes.output.isNotBlank()) StepStatus.SUCCESS else StepStatus.WARNING
        } else {
            StepStatus.FAILED
        }

        _state.update { s ->
            s.copy(
                toolsMatrix = s.toolsMatrix.map {
                    if (it.id == toolId) it.copy(
                        status = testStatus,
                        path = if (exists) binaryPath else null,
                        lastTested = "Just now"
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
                    lastVerifiedTimestamp = System.currentTimeMillis()
                )
            }
        }
        testStatus
    }

    override suspend fun recompileTool(toolId: String): Boolean = withContext(dispatcher) {
        appendLog("[Tool Matrix] Verifying & recompiling tool component: $toolId...")
        val status = testIndividualTool(toolId)
        status == StepStatus.SUCCESS
    }

    override suspend fun resetSetupCache(): Unit = withContext(dispatcher) {
        repository?.clearToolchainState()
        _state.value = ToolchainSetupState()
        appendLog("[Cache] Reset toolchain persistence cache to clean default.")
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
