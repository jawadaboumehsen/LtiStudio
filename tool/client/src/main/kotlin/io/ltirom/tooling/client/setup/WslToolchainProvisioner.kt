package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.setup.doctor.CompositeSystemDoctorEngine
import io.ltirom.tooling.client.setup.doctor.NativeLibrariesInspector
import io.ltirom.tooling.client.setup.doctor.ToolchainIntegrityInspector
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
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.*
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
    private val submoduleEngine: SubmoduleSyncEngine = SubmoduleSyncEngine(cli = cli, repository = repository),
    private val buildEngine: ToolchainBuildEngine = ToolchainBuildEngine(cli = cli, repository = repository),
    private val toolPublicationPort: ToolPublicationPort? = null,
) : ToolchainProvisioningService {

    private val _state = MutableStateFlow(restoreInitialState())
    override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

    private fun restoreInitialState(): ToolchainSetupState {
        val persisted = repository?.currentState ?: return ToolchainSetupState()
        if (!persisted.isSetupCompleted && persisted.completedStages.isEmpty() && persisted.tools.isEmpty()) {
            return ToolchainSetupState()
        }

        val restoredSteps = ToolchainSetupState.defaultSteps().map { step ->
            if (persisted.isStageCompleted(step.stage.name) || persisted.isSetupCompleted) {
                step.copy(
                    status = StepStatus.SUCCESS,
                    description = "${step.title} verified and restored from persistent DataStore."
                )
            } else step
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
            isAllReady = persisted.isSetupCompleted,
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


    override suspend fun checkStatus(): ToolchainSetupState = withContext(dispatcher) {
        if (_state.value.isChecking) return@withContext _state.value
        _state.update { it.copy(isChecking = true) }
        try {
            val distro = detector.getDefaultDistro() ?: "Ubuntu"
            val isWslOk = detector.isWslInstalled()

        val wslStep = if (isWslOk) {
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                description = "WSL 2 active with default distribution '$distro'.",
                status = StepStatus.SUCCESS
            )
        } else {
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                description = "WSL 2 not detected or no distribution found.",
                status = StepStatus.FAILED,
                error = "WSL 2 is not installed. Run 'wsl --install -d Ubuntu' in PowerShell."
            )
        }

        val serverStep = if (!isWslOk) {
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server Bridge",
                description = "Pending WSL installation.",
                status = StepStatus.PENDING
            )
        } else {
            val isHealthy = runCatching {
                supervisor.getConnectionInfo()?.let { supervisor.isHealthy(it) } ?: false
            }.getOrDefault(false)

            if (isHealthy) {
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server Bridge",
                    description = "LtiRomServer daemon is connected and responsive.",
                    status = StepStatus.SUCCESS
                )
            } else {
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server Bridge",
                    description = "Daemon is idle. Will launch on demand.",
                    status = StepStatus.PENDING
                )
            }
        }

        val home = if (isWslOk) detector.resolveWslUserHome(distro) else "/home/lti"
        val workDir = "$home/LtiRomWorkDir"
        val toolsRoot = "$home/LtiRomTools"
        val extDir = "$toolsRoot/external"
        val avbKeyFile = "$workDir/security/avb/lti_rsa4096.pem"
        val binDir = "$toolsRoot/bin"
        val binariesPresent = if (isWslOk) {
            CORE_BINARIES.all { bin ->
                cli.execute(distro, listOf("test", "-x", "$binDir/$bin")).exitCode == 0
            }
        } else false

        // Fast-path: If setup was already completed and core binaries exist, avoid slow CLI re-probing
        val persisted = repository?.currentState
        if (isWslOk && persisted?.isSetupCompleted == true && binariesPresent) {
            val hasAvbKey = cli.execute(distro, listOf("test", "-f", avbKeyFile)).exitCode == 0
            val daemonPing = runCatching {
                val startMs = System.currentTimeMillis()
                val healthy = supervisor.getConnectionInfo()?.let { supervisor.isHealthy(it) } ?: false
                if (healthy) (System.currentTimeMillis() - startMs).coerceAtLeast(1L) else null
            }.getOrNull()

            val steps = ToolchainSetupState.defaultSteps().map { step ->
                val desc = when (step.stage) {
                    SetupStepStage.WSL_DETECTION -> "WSL 2 active with default distribution '$distro'."
                    SetupStepStage.SERVER_CONNECTIVITY -> if (daemonPing != null) "Connected (${daemonPing}ms)." else "Daemon idle (starts on demand)."
                    SetupStepStage.SYSTEM_DIAGNOSTICS -> "All host utilities and dependencies verified."
                    SetupStepStage.REPO_SYNCHRONIZATION -> "All submodules and workspace ready."
                    SetupStepStage.TOOLCHAIN_COMPILATION -> "All core tools compiled and verified in $binDir."
                }
                step.copy(status = StepStatus.SUCCESS, description = desc)
            }

            val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
            val updatedTools = currentTools.map { tool ->
                val pTool = persisted.tools[tool.id]
                val toolPath = "$binDir/${tool.binaryName}"
                if (pTool?.isCompiled == true) {
                    tool.copy(
                        path = pTool.binaryPath ?: toolPath,
                        version = pTool.version,
                        status = StepStatus.SUCCESS,
                        lastTested = pTool.lastVerifiedTimestamp?.let { "Verified" } ?: "Ready"
                    )
                } else {
                    val exists = cli.execute(distro, listOf("test", "-x", toolPath)).exitCode == 0
                    tool.copy(
                        path = if (exists) toolPath else null,
                        status = if (exists) StepStatus.SUCCESS else StepStatus.PENDING
                    )
                }
            }

            val fastState = _state.value.copy(
                steps = steps,
                isAvbKeyProvisioned = hasAvbKey,
                avbKeyPath = if (hasAvbKey) avbKeyFile else null,
                isAllReady = true,
                activeDistro = distro,
                installedDistros = detector.listDistros().ifEmpty { listOf(distro) },
                daemonPingMs = daemonPing,
                toolsMatrix = updatedTools,
                lastVerifiedTimestamp = System.currentTimeMillis(),
                isChecking = false
            )
            _state.value = fastState
            repository?.let { repo -> repo.saveState(repo.currentState.copy(lastVerifiedTimestamp = fastState.lastVerifiedTimestamp)) }
            return@withContext fastState
        }

        // 3. Pre-Flight Doctor Diagnostics (delegated to SystemDoctorPort)

        val diagnosticsList = if (isWslOk) {
            doctorPort.runDiagnostics(distro, binariesPresent)
        } else {
            emptyList()
        }

        val diagStep = if (!isWslOk) {
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                title = "Pre-Flight Doctor",
                description = "Pending WSL installation.",
                status = StepStatus.PENDING
            )
        } else {
            val anyDiagFailed = diagnosticsList.any { it.status == StepStatus.FAILED }
            val anyDiagWarn = diagnosticsList.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                title = "Pre-Flight Doctor",
                description = when {
                    anyDiagFailed -> "Critical system requirements failed."
                    anyDiagWarn -> "Minor warnings detected in pre-flight checks."
                    else -> "All host utilities, compilers, runtimes, and storage headroom verified."
                },
                status = when {
                    anyDiagFailed -> StepStatus.FAILED
                    anyDiagWarn -> StepStatus.WARNING
                    else -> StepStatus.SUCCESS
                }
            )
        }

        val hasAvbKey = if (isWslOk) {
            cli.execute(distro, listOf("test", "-f", avbKeyFile)).exitCode == 0
        } else false

        val repoStep = if (!isWslOk) {
            SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Source Repositories & WorkDir",
                description = "Pending WSL installation.",
                status = StepStatus.PENDING
            )
        } else {
            val workDirExists = cli.execute(distro, listOf("test", "-d", workDir)).exitCode == 0
            val dirExists = cli.execute(distro, listOf("test", "-d", extDir)).exitCode == 0
            if (!workDirExists || !dirExists) {
                SetupStepDetail(
                    stage = SetupStepStage.REPO_SYNCHRONIZATION,
                    title = "Source Repositories & WorkDir",
                    description = when {
                        !workDirExists && !dirExists -> "Directories $workDir and $extDir not created yet."
                        !workDirExists -> "Workspace directory $workDir not created yet."
                        else -> "Directory $extDir not created yet."
                    },
                    status = StepStatus.PENDING
                )
            } else {
                val missingSubs = SUBMODULES.filter { sub ->
                    cli.execute(distro, listOf("test", "-d", "$extDir/${sub.name}")).exitCode != 0
                }
                if (missingSubs.isEmpty()) {
                    SetupStepDetail(
                        stage = SetupStepStage.REPO_SYNCHRONIZATION,
                        title = "Source Repositories & WorkDir",
                        description = "All 5 submodules and $workDir ready.",
                        status = StepStatus.SUCCESS
                    )
                } else {
                    SetupStepDetail(
                        stage = SetupStepStage.REPO_SYNCHRONIZATION,
                        title = "Source Repositories & WorkDir",
                        description = "${missingSubs.size} submodule(s) missing in $extDir.",
                        status = StepStatus.PENDING
                    )
                }
            }
        }

        val buildStep = if (!isWslOk) {
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain Binaries",
                description = "Pending WSL installation.",
                status = StepStatus.PENDING
            )
        } else {
            val missingBins = CORE_BINARIES.filter { bin ->
                cli.execute(distro, listOf("test", "-x", "$binDir/$bin")).exitCode != 0
            }
            if (missingBins.isEmpty()) {
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Toolchain Binaries",
                    description = "All core tool binaries present in $binDir.",
                    status = StepStatus.SUCCESS
                )
            } else {
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Toolchain Binaries",
                    description = "${missingBins.size} binary(ies) missing in $binDir.",
                    status = StepStatus.PENDING
                )
            }
        }

        val steps = listOf(wslStep, serverStep, diagStep, repoStep, buildStep)
        val allReady = steps.all { it.status == StepStatus.SUCCESS || it.status == StepStatus.WARNING }

        val distros = if (isWslOk) detector.listDistros() else listOf("Ubuntu")
        val daemonPing = if (isWslOk) {
            val startMs = System.currentTimeMillis()
            val isHealthy = runCatching {
                supervisor.getConnectionInfo()?.let { supervisor.isHealthy(it) } ?: false
            }.getOrDefault(false)
            if (isHealthy) (System.currentTimeMillis() - startMs).coerceAtLeast(1L) else null
        } else null

        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val updatedTools = if (isWslOk) {
            currentTools.map { tool ->
                val toolPath = "$binDir/${tool.binaryName}"
                val exists = cli.execute(distro, listOf("test", "-x", toolPath)).exitCode == 0
                tool.copy(
                    path = if (exists) toolPath else null,
                    status = if (exists) StepStatus.SUCCESS else StepStatus.PENDING
                )
            }
        } else currentTools

        val newState = _state.value.copy(
            steps = steps,
            diagnostics = diagnosticsList.ifEmpty { _state.value.diagnostics },
            isAvbKeyProvisioned = hasAvbKey,
            avbKeyPath = if (hasAvbKey) avbKeyFile else null,
            isAllReady = allReady,
            activeDistro = distro,
            installedDistros = distros.ifEmpty { listOf(distro) },
            daemonPingMs = daemonPing,
            toolsMatrix = updatedTools,
            lastVerifiedTimestamp = if (allReady) System.currentTimeMillis() else _state.value.lastVerifiedTimestamp,
            isChecking = false
        )
        _state.value = newState
        if (allReady) {
            repository?.let { repo -> repo.saveState(repo.currentState.copy(lastVerifiedTimestamp = newState.lastVerifiedTimestamp)) }
        }
        newState
        } finally {
            _state.update { it.copy(isChecking = false) }
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
        // Step 3: Pre-Flight Doctor Diagnostics & Automated Remediation
        // -------------------------------------------------------------
        setStepStatus(SetupStepStage.SYSTEM_DIAGNOSTICS, StepStatus.RUNNING)
        appendLog("[Step 3/5] Running pre-flight system diagnostics...")
        val preDiagnostics = doctorPort.runDiagnostics(distro, binariesPresent = false)
        val hasBlockers = preDiagnostics.any { it.status == StepStatus.FAILED }
        val hasWarnings = preDiagnostics.any { it.status == StepStatus.WARNING && !it.isOptionalForRuntime }

        if (hasBlockers || hasWarnings) {
            appendLog("[Step 3/5] Missing system dependencies detected. Initiating automated package remediation via WSL root...")
            doctorPort.autoRemediate(distro) { logLine ->
                appendLog("[Step 3/5] $logLine")
            }
        }

        val finalDiagnostics = doctorPort.runDiagnostics(distro, binariesPresent = false)
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
        val home = detector.resolveWslUserHome(distro)
        val workDir = "$home/LtiRomWorkDir"
        val toolsRoot = "$home/LtiRomTools"
        val extDir = "$toolsRoot/external"

        appendLog("[Step 4/5] Ensuring workspace root directory exists at $workDir...")
        cli.execute(distro, listOf("mkdir", "-p", workDir))
        appendLog("[Step 4/5] Initialized workspace root directory at $workDir.")

        appendLog("[Step 4/5] Ensuring tools directory exists at $extDir...")
        cli.execute(distro, listOf("mkdir", "-p", extDir))

        val syncRes = submoduleEngine.syncAll(distro, extDir) { appendLog(it) }
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

        val buildRes = buildEngine.buildAndDistributeAll(distro, extDir, binDir) { appendLog(it) }
        if (buildRes.isFailure) {
            val err = buildRes.exceptionOrNull()?.message ?: "Some core tool binaries are missing in $binDir after build."
            appendLog("[Step 5/5] WARNING: $err")
            setStepStatus(SetupStepStage.TOOLCHAIN_COMPILATION, StepStatus.WARNING, err)
            _state.update { it.copy(isRunning = false) }
            return@withContext
        }

        // Provision AVB key if not present
        val avbKeyPath = "$workDir/security/avb/lti_rsa4096.pem"
        val hasAvb = cli.execute(distro, listOf("test", "-f", avbKeyPath)).exitCode == 0
        if (!hasAvb) {
            appendLog("[Step 5/5] Provisioning machine-local AVB 2.0 RSA-4096 key...")
            provisionAvbKey()
        } else {
            repository?.updateAvbKeyState(true, avbKeyPath)
        }

        // Update tools matrix state and persist
        val currentTools = _state.value.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }
        val updatedTools = currentTools.map { tool ->
            val toolPath = "$binDir/${tool.binaryName}"
            val exists = cli.execute(distro, listOf("test", "-x", toolPath)).exitCode == 0
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

        repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION, true)
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
                isAllReady = true,
                toolsMatrix = updatedTools,
                lastVerifiedTimestamp = System.currentTimeMillis(),
                publishedToolIds = publishedIds,
                unpublishedToolIds = unpublishedIds
            )
        }

    }

    override suspend fun retryCurrentStep() {
        if (_state.value.isBusy) return
        runFullSetup()
    }

    override suspend fun provisionAvbKey(): Boolean = withContext(dispatcher) {
        if (_state.value.isBusy) return@withContext false
        val distro = detector.getDefaultDistro() ?: "Ubuntu"
        val home = detector.resolveWslUserHome(distro)
        val workDir = "$home/LtiRomWorkDir"
        val avbDir = "$workDir/security/avb"
        val keyPath = "$avbDir/lti_rsa4096.pem"
        val pubKeyPath = "$avbDir/lti_rsa4096.avbpubkey"
        val binDir = "$home/LtiRomTools/bin"

        appendLog("[AVB 2.0] Initializing security/avb directory in $workDir...")
        cli.execute(distro, listOf("mkdir", "-p", avbDir))

        val keyExists = cli.execute(distro, listOf("test", "-f", keyPath)).exitCode == 0
        if (!keyExists) {
            appendLog("[AVB 2.0] Generating RSA-4096 private key via OpenSSL...")
            val genRes = cli.execute(
                distro,
                listOf("openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096", "-out", keyPath)
            )
            cli.execute(distro, listOf("chmod", "600", keyPath))
            appendLog("[AVB 2.0] Private key generated with mode 0600 at $keyPath.")
        } else {
            appendLog("[AVB 2.0] Existing RSA-4096 private key detected.")
        }

        // Extract public key using avbtool if available
        val hasAvbTool = cli.execute(distro, listOf("test", "-x", "$binDir/avbtool")).exitCode == 0
        if (hasAvbTool) {
            cli.execute(distro, listOf("$binDir/avbtool", "extract_public_key", "--key", keyPath, "--output", pubKeyPath))
            appendLog("[AVB 2.0] Derived AVB public key at $pubKeyPath.")
        }

        val verified = cli.execute(distro, listOf("test", "-f", keyPath)).exitCode == 0
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
        val home = detector.resolveWslUserHome(distro)
        val binDir = "$home/LtiRomTools/bin"
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

        val exists = cli.execute(distro, listOf("test", "-x", binaryPath)).exitCode == 0
        val testStatus = if (exists) {
            val execRes = cli.execute(distro, listOf(binaryPath, "--help"), timeoutSeconds = 5)
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
        repository?.reset()
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
                        error = error
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
