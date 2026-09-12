/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Kind of setup mutation operation.
 */
enum class SetupOperationKind {
    FULL_SETUP,
    RETRY_STAGE,
    REPAIR_TOOL,
    TEST_TOOL,
}

/**
 * Lifecycle states of an environment setup operation.
 */
enum class SetupOperationState {
    PREVIEW,
    RUNNING,
    RECONNECTING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * Planned changes and elevation requirements surfaced to the user before confirmation.
 */
data class PlannedSetupChanges(
    val packagesToInstall: List<String> = emptyList(),
    val submodulesToSync: List<String> = emptyList(),
    val toolsToCompile: List<String> = emptyList(),
    val toolsToPublish: List<String> = emptyList(),
    val requiresElevation: Boolean = false,
    val elevationReason: String? = null,
    val targetDistro: String = "Ubuntu",
    val autoDoctorRemediation: Boolean = true,
)

/**
 * Encapsulation of an active or previewed setup operation.
 */
data class SetupOperation(
    val operationId: String? = null,
    val kind: SetupOperationKind = SetupOperationKind.FULL_SETUP,
    val stageId: SetupStepStage? = null,
    val toolId: String? = null,
    val plannedChanges: PlannedSetupChanges = PlannedSetupChanges(),
    val executionState: SetupOperationState = SetupOperationState.PREVIEW,
    val autoDoctorEnabled: Boolean = true,
    val failure: String? = null,
)

/**
 * Feature-local state holder for Environment setup and toolchain operations (US2).
 *
 * Responsibilities:
 * - Performs initial inspection only (no mutation on initial check; FR-005).
 * - Previews planned changes and required elevation before mutating the environment (FR-005).
 * - Enforces single-flight operation serialization (at most one active setup mutation per environment; FR-007, SC-002).
 * - Honors Auto Doctor toggle in all paths (FR-007).
 * - Attaches to existing durable operation IDs on reconnection (FR-007).
 * - Cleans up busy/mutation state in finally blocks on error/cancellation (FR-007).
 * - Preserves execution state across Setup destination navigation during a session (FR-004, FR-007).
 */
class EnvironmentSetupState(
    private val scope: CoroutineScope,
    private val toolchainService: ToolchainProvisioningService? = null,
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
) {
    private val _fallbackToolchainState = MutableStateFlow(
        ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map { step ->
                if (toolchainService == null) {
                    step.copy(
                        status = StepStatus.WARNING,
                        description = "Toolchain service not bound in DI. Running in preview/host mode.",
                    )
                } else step
            }
        )
    )

    val toolchainSetupState: StateFlow<ToolchainSetupState> =
        toolchainService?.state ?: _fallbackToolchainState.asStateFlow()

    val environmentPresentation: StateFlow<EnvironmentPresentation> =
        toolchainSetupState
            .map { SetupPresentationMapper.mapEnvironment(it) }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = SetupPresentationMapper.mapEnvironment(toolchainSetupState.value),
            )

    private val _activeOperation = MutableStateFlow<SetupOperation?>(null)
    val activeOperation: StateFlow<SetupOperation?> = _activeOperation.asStateFlow()

    private val _isAutoDoctorEnabled = MutableStateFlow(true)
    val isAutoDoctorEnabled: StateFlow<Boolean> = _isAutoDoctorEnabled.asStateFlow()

    private val _environmentNotice = MutableStateFlow<String?>(null)
    val environmentNotice: StateFlow<String?> = _environmentNotice.asStateFlow()

    fun setEnvironmentNotice(notice: String?) {
        _environmentNotice.value = notice
    }

    private val _selectedStage = MutableStateFlow<SetupStepStage?>(null)
    val selectedStage: StateFlow<SetupStepStage?> = _selectedStage.asStateFlow()

    private var currentExecutionJob: Job? = null

    init {
        // Reconnect to an already-running mutation on state-holder (re)creation, e.g. after
        // navigating away and back to the Environment destination during a session (FR-007).
        val initial = toolchainSetupState.value
        if (initial.isRunning) {
            val stage = initial.currentStage
            val runId = stage?.let { initial.stepRunIds[it] }
            if (runId != null) {
                attachToDurableRun(runId, stage)
            } else {
                _activeOperation.value = SetupOperation(
                    kind = if (stage != null) SetupOperationKind.RETRY_STAGE else SetupOperationKind.FULL_SETUP,
                    stageId = stage,
                    executionState = SetupOperationState.RECONNECTING,
                    autoDoctorEnabled = _isAutoDoctorEnabled.value,
                )
            }
        }
    }

    /**
     * Probes environment status without mutating filesystem or packages (FR-005).
     */
    fun checkEnvironmentStatus() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        currentExecutionJob = scope.launch {
            try {
                toolchainService.checkStatus()
            } catch (e: Exception) {
                _environmentNotice.value = "Environment verification notice: ${e.message}"
            } finally {
                currentExecutionJob = null
            }
        }
    }

    /**
     * Re-runs live environment verification without mutating the filesystem.
     */
    fun reverify() {
        checkEnvironmentStatus()
    }

    /**
     * Previews planned changes and required elevation without executing any mutation (FR-005).
     */
    fun previewSetup(stage: SetupStepStage? = null) {
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return

        val state = toolchainSetupState.value
        val distro = state.activeDistro ?: "Ubuntu"
        val planned = computePlannedChanges(stage, state, distro)

        _activeOperation.value = SetupOperation(
            operationId = null,
            kind = if (stage != null) SetupOperationKind.RETRY_STAGE else SetupOperationKind.FULL_SETUP,
            stageId = stage,
            plannedChanges = planned,
            executionState = SetupOperationState.PREVIEW,
            autoDoctorEnabled = _isAutoDoctorEnabled.value,
        )
        if (stage != null) {
            _selectedStage.value = stage
        }
    }

    /**
     * Dismisses the active preview without performing any mutations.
     */
    fun dismissPreview() {
        if (_activeOperation.value?.executionState == SetupOperationState.PREVIEW) {
            _activeOperation.value = null
        }
    }

    /**
     * Confirms and starts the active or requested setup operation (FR-005, FR-007).
     * Single-flight: repeated activations while running will not queue duplicate operations.
     */
    fun confirmOperation() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) {
            return
        }

        val currentOp = _activeOperation.value ?: run {
            val planned = computePlannedChanges(null, toolchainSetupState.value, toolchainSetupState.value.activeDistro ?: "Ubuntu")
            SetupOperation(
                kind = SetupOperationKind.FULL_SETUP,
                plannedChanges = planned,
                executionState = SetupOperationState.PREVIEW,
                autoDoctorEnabled = _isAutoDoctorEnabled.value,
            )
        }

        val opId = currentOp.operationId ?: "op-${System.currentTimeMillis()}"
        _activeOperation.value = currentOp.copy(
            operationId = opId,
            executionState = SetupOperationState.RUNNING,
            autoDoctorEnabled = _isAutoDoctorEnabled.value,
        )

        currentExecutionJob = scope.launch {
            try {
                when (currentOp.kind) {
                    SetupOperationKind.FULL_SETUP -> {
                        if (_isAutoDoctorEnabled.value) {
                            val status = toolchainSetupState.value
                            val hasDoctorIssues = status.diagnostics.any {
                                it.status == StepStatus.FAILED || (it.status == StepStatus.WARNING && !it.isOptionalForRuntime)
                            }
                            if (hasDoctorIssues) {
                                toolchainService.autoRemediateDoctor()
                            }
                        }
                        toolchainService.runFullSetup()
                    }
                    SetupOperationKind.RETRY_STAGE -> {
                        val stage = currentOp.stageId ?: SetupStepStage.WSL_DETECTION
                        if (stage == SetupStepStage.SYSTEM_DIAGNOSTICS && _isAutoDoctorEnabled.value) {
                            toolchainService.autoRemediateDoctor()
                        }
                        toolchainService.retryStep(stage)
                    }
                    SetupOperationKind.REPAIR_TOOL -> {
                        currentOp.toolId?.let { toolchainService.repairTool(it) }
                    }
                    SetupOperationKind.TEST_TOOL -> {
                        currentOp.toolId?.let { toolchainService.testTool(it) }
                    }
                }
                _activeOperation.value = _activeOperation.value?.copy(executionState = SetupOperationState.SUCCEEDED)
            } catch (e: Exception) {
                _activeOperation.value = _activeOperation.value?.copy(
                    executionState = SetupOperationState.FAILED,
                    failure = e.message,
                )
                _environmentNotice.value = "Operation failed: ${e.message}"
            } finally {
                currentExecutionJob = null
                if (_activeOperation.value?.executionState == SetupOperationState.SUCCEEDED) {
                    _activeOperation.value = null
                }
            }
        }
    }

    /**
     * Retries a specific setup stage with targeted execution (FR-006, FR-007).
     */
    fun retryStage(stage: SetupStepStage) {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        previewSetup(stage)
        confirmOperation()
    }

    /**
     * Attaches to an existing durable operation ID from feature-001 (FR-007).
     */
    fun attachToDurableRun(runId: String, stage: SetupStepStage? = null) {
        if (runId.isBlank()) return
        _activeOperation.value = SetupOperation(
            operationId = runId,
            kind = if (stage != null) SetupOperationKind.RETRY_STAGE else SetupOperationKind.FULL_SETUP,
            stageId = stage,
            executionState = SetupOperationState.RECONNECTING,
            autoDoctorEnabled = _isAutoDoctorEnabled.value,
        )
    }

    fun setAutoDoctorEnabled(enabled: Boolean) {
        _isAutoDoctorEnabled.value = enabled
        _activeOperation.value?.let { op ->
            if (op.executionState == SetupOperationState.PREVIEW) {
                _activeOperation.value = op.copy(
                    autoDoctorEnabled = enabled,
                    plannedChanges = op.plannedChanges.copy(autoDoctorRemediation = enabled),
                )
            }
        }
    }

    fun selectStage(stage: SetupStepStage?) {
        _selectedStage.value = stage
    }

    fun dismissEnvironmentNotice() {
        _environmentNotice.value = null
    }

    fun resetToolchainCacheAndRecheck() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || currentExecutionJob?.isActive == true) return
        scope.launch {
            try {
                toolchainService.resetSetupCache()
                checkEnvironmentStatus()
            } catch (e: Exception) {
                _environmentNotice.value = "Cache reset notice: ${e.message}"
            }
        }
    }

    private fun computePlannedChanges(
        stage: SetupStepStage?,
        state: ToolchainSetupState,
        distro: String,
    ): PlannedSetupChanges {
        val packages = mutableListOf<String>()
        val submodules = mutableListOf<String>()
        val tools = mutableListOf<String>()
        val publications = mutableListOf<String>()
        var requiresElevation = false
        var elevationReason: String? = null

        val autoDoc = _isAutoDoctorEnabled.value

        if (stage == null || stage == SetupStepStage.SYSTEM_DIAGNOSTICS) {
            if (autoDoc) {
                val issues = state.diagnostics.filter {
                    it.status == StepStatus.FAILED || (it.status == StepStatus.WARNING && !it.isOptionalForRuntime)
                }
                for (item in issues) {
                    when (item.id) {
                        "host_compilers" -> packages.addAll(listOf("build-essential", "cmake", "make", "clang"))
                        "host_libraries" -> packages.addAll(listOf("liblz4-dev", "libprotobuf-dev", "libzstd-dev"))
                        "python_crypto" -> packages.addAll(listOf("python3", "python3-pip", "cryptography"))
                        "jdk_dual" -> packages.addAll(listOf("openjdk-17-jdk", "openjdk-21-jdk"))
                        "selinux_attr" -> packages.add("attr")
                        "archive_tools" -> packages.addAll(listOf("zip", "unzip", "brotli"))
                        "transfer_tools" -> packages.addAll(listOf("curl", "git", "rsync", "tar"))
                        "dev_fuse" -> packages.addAll(listOf("fuse3", "libfuse3-dev"))
                        "openssl_tools" -> packages.add("openssl")
                        "build_cache" -> packages.add("ccache")
                    }
                }
                if (packages.isNotEmpty()) {
                    requiresElevation = true
                    elevationReason = "Automated package installation via passwordless non-interactive sudo (sudo -n apt-get)"
                }
            }
        }

        if (stage == null || stage == SetupStepStage.REPO_SYNCHRONIZATION) {
            val repoStep = state.steps.firstOrNull { it.stage == SetupStepStage.REPO_SYNCHRONIZATION }
            if (repoStep?.status != StepStatus.SUCCESS) {
                submodules.addAll(listOf("android-tools", "apktool", "erofs-utils", "img2sdat", "signapk"))
            }
        }

        if (stage == null || stage == SetupStepStage.TOOLCHAIN_COMPILATION) {
            val toolStep = state.steps.firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
            if (toolStep?.status != StepStatus.SUCCESS) {
                tools.addAll(listOf("adb", "fastboot", "mke2fs", "dump.erofs", "mkfs.erofs", "img2sdat"))
                publications.addAll(listOf("lpmake", "lpunpack", "avbtool", "unpack_bootimg", "payload-dumper-go"))
            }
        }

        return PlannedSetupChanges(
            packagesToInstall = packages.distinct(),
            submodulesToSync = submodules.distinct(),
            toolsToCompile = tools.distinct(),
            toolsToPublish = publications.distinct(),
            requiresElevation = requiresElevation,
            elevationReason = elevationReason,
            targetDistro = distro,
            autoDoctorRemediation = autoDoc,
        )
    }
}
