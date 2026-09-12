/*
 * Copyright 2025-2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.SelectAndOpenDirectoryUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace

/**
 * Presentation ViewModel for the Setup / Welcome Screen.
 *
 * Adheres strictly to:
 * - Single Responsibility: Manages UI state for host environment provisioning, doctor diagnostics,
 *   machine-local AVB key generation, REDMAGIC Astra hardware target, workspace discovery, and launch transition.
 * - Dependency Inversion: Depends exclusively on domain use cases rather than concrete managers.
 */
class SetupViewModel(
    private val getRecentWorkspacesUseCase: GetRecentWorkspacesUseCase,
    private val openWorkspaceUseCase: OpenWorkspaceUseCase,
    private val openWorkspaceByPathUseCase: OpenWorkspaceByPathUseCase,
    private val selectAndOpenDirectoryUseCase: SelectAndOpenDirectoryUseCase,
    private val removeRecentWorkspaceUseCase: RemoveRecentWorkspaceUseCase,
    private val discoverWorkdirWorkspacesUseCase: DiscoverWorkdirWorkspacesUseCase? = null,
    private val toolchainService: ToolchainProvisioningService? = null,
    private val provisionAvbKeyUseCase: ProvisionAvbKeyUseCase? = null,
    private val getAvailableTargetsUseCase: GetAvailableTargetsUseCase? = null,
    private val getSelectedTargetUseCase: GetSelectedTargetUseCase? = null,
    private val selectTargetUseCase: SelectTargetUseCase? = null,
) : ViewModel() {

    /**
     * Backward-compatible convenience constructor for testing and callers with [WorkspaceManager].
     */
    constructor(
        workspaceManager: WorkspaceManager,
        toolchainService: ToolchainProvisioningService? = null,
        provisionAvbKeyUseCase: ProvisionAvbKeyUseCase? = null,
        getAvailableTargetsUseCase: GetAvailableTargetsUseCase? = null,
        getSelectedTargetUseCase: GetSelectedTargetUseCase? = null,
        selectTargetUseCase: SelectTargetUseCase? = null,
        getRecentWorkspacesUseCase: GetRecentWorkspacesUseCase? = null,
        discoverWorkdirWorkspacesUseCase: DiscoverWorkdirWorkspacesUseCase? = null,
        openWorkspaceUseCase: OpenWorkspaceUseCase? = null,
        removeRecentWorkspaceUseCase: RemoveRecentWorkspaceUseCase? = null,
        openWorkspaceByPathUseCase: OpenWorkspaceByPathUseCase =
            OpenWorkspaceByPathUseCase(workspaceManager.repository, workspaceManager),
    ) : this(
        getRecentWorkspacesUseCase = getRecentWorkspacesUseCase ?:
            GetRecentWorkspacesUseCase(workspaceManager.repository),
        openWorkspaceUseCase = openWorkspaceUseCase ?:
            OpenWorkspaceUseCase(workspaceManager.repository, workspaceManager),
        openWorkspaceByPathUseCase = openWorkspaceByPathUseCase,
        selectAndOpenDirectoryUseCase = SelectAndOpenDirectoryUseCase(openWorkspaceByPathUseCase),
        removeRecentWorkspaceUseCase = removeRecentWorkspaceUseCase ?:
            RemoveRecentWorkspaceUseCase(workspaceManager.repository),
        discoverWorkdirWorkspacesUseCase = discoverWorkdirWorkspacesUseCase,
        toolchainService = toolchainService,
        provisionAvbKeyUseCase = provisionAvbKeyUseCase,
        getAvailableTargetsUseCase = getAvailableTargetsUseCase,
        getSelectedTargetUseCase = getSelectedTargetUseCase,
        selectTargetUseCase = selectTargetUseCase,
    )

    companion object {
        val DEFAULT_OFFICIAL_TARGETS = listOf(DefaultTargetCatalog.PQ84P01_DEFAULT)
    }

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

    val toolchainSetupState: StateFlow<ToolchainSetupState> = toolchainService?.state
        ?: _fallbackToolchainState.asStateFlow()

    private var autoRunJob: Job? = null

    private val _fallbackSelectedTarget = MutableStateFlow<TargetDevice?>(DEFAULT_OFFICIAL_TARGETS.firstOrNull())

    val availableTargets: StateFlow<List<TargetDevice>> = (getAvailableTargetsUseCase?.invoke()
        ?: MutableStateFlow(DEFAULT_OFFICIAL_TARGETS))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_OFFICIAL_TARGETS,
        )

    val selectedTarget: StateFlow<TargetDevice?> = (getSelectedTargetUseCase?.invoke()
        ?: _fallbackSelectedTarget.asStateFlow())
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_OFFICIAL_TARGETS.firstOrNull(),
        )

    internal val _isAutoDoctorEnabled = MutableStateFlow(true)
    val isAutoDoctorEnabled: StateFlow<Boolean> = _isAutoDoctorEnabled.asStateFlow()

    init {
        autoRunEnvironmentSetup(autoFix = true)
    }

    val recentProjects: StateFlow<List<RecentProject>> = getRecentWorkspacesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    internal val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredRecentProjects: StateFlow<List<RecentProject>> = combine(
        recentProjects,
        searchQuery,
    ) { projects, query ->
        if (query.isBlank()) {
            projects
        } else {
            projects.filter { project ->
                project.name.contains(query, ignoreCase = true) ||
                    project.path.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    internal val _selectedTab = MutableStateFlow(SetupTab.PROJECTS)
    val selectedTab: StateFlow<SetupTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: SetupTab) {
        _selectedTab.value = tab
    }

    internal val _environmentNotice = MutableStateFlow<String?>(null)
    val environmentNotice: StateFlow<String?> = _environmentNotice.asStateFlow()

    fun dismissEnvironmentNotice() {
        _environmentNotice.value = null
    }

    fun navigateToEnvironment() {
        _selectedTab.value = SetupTab.ENVIRONMENT
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            autoRunEnvironmentSetup(autoFix = true)
        }
    }

    internal val _selectedCockpitTab = MutableStateFlow("doctor")
    val selectedCockpitTab: StateFlow<String> = _selectedCockpitTab.asStateFlow()

    fun selectCockpitTab(tab: String) {
        _selectedCockpitTab.value = tab
    }

    internal val _toolSearchQuery = MutableStateFlow("")
    val toolSearchQuery: StateFlow<String> = _toolSearchQuery.asStateFlow()

    fun onToolSearchQueryChange(query: String) {
        _toolSearchQuery.value = query
    }

    internal val _selectedToolCategory = MutableStateFlow("All")
    val selectedToolCategory: StateFlow<String> = _selectedToolCategory.asStateFlow()

    fun selectToolCategory(category: String) {
        _selectedToolCategory.value = category
    }

    val filteredToolsMatrix: StateFlow<List<ToolComponentItem>> = combine(
        toolchainSetupState,
        toolSearchQuery,
        selectedToolCategory,
    ) { state, query, category ->
        var list = state.toolsMatrix
        if (category != "All") {
            list = list.filter { it.category.displayName.equals(category, ignoreCase = true) }
        }
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.id.contains(query, ignoreCase = true) ||
                    it.binaryName.contains(query, ignoreCase = true) ||
                    it.capabilities.any { cap -> cap.contains(query, ignoreCase = true) }
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ToolchainSetupState.defaultToolsMatrix(),
    )

    fun testTool(toolId: String) {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        viewModelScope.launch {
            toolchainService.testIndividualTool(toolId)
        }
    }

    fun recompileTool(toolId: String) {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        viewModelScope.launch {
            toolchainService.recompileTool(toolId)
        }
    }

    fun removeRecentProject(workspaceId: String) {
        viewModelScope.launch {
            removeRecentWorkspaceUseCase(workspaceId)
        }
    }

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit) {
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            _environmentNotice.value = "LtiRomServer connection and native toolchain compilation must be " +
                "verified before opening a workspace."
            navigateToEnvironment()
            return
        }
        viewModelScope.launch {
            val result = selectAndOpenDirectoryUseCase()
            result.onSuccess { workspace ->
                if (workspace != null) {
                    onWorkspaceOpened(workspace)
                }
            }
        }
    }

    /**
     * Initiates Git clone workflow or opens destination workspace.
     */
    fun cloneGitRepository(url: String? = null, onWorkspaceOpened: (Workspace) -> Unit) {
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            _environmentNotice.value = "LtiRomServer connection and native toolchain compilation must be " +
                "verified before cloning a workspace."
            navigateToEnvironment()
            return
        }
        viewModelScope.launch {
            val targetPath = if (url != null) {
                val projectName = url.substringAfterLast('/').removeSuffix(".git")
                "~/LtiRomWorkDir/projects/$projectName"
            } else {
                null
            }
            val result = if (targetPath != null) {
                openWorkspaceByPathUseCase(targetPath)
            } else {
                selectAndOpenDirectoryUseCase()
            }
            result.onSuccess { workspace ->
                if (workspace != null) {
                    onWorkspaceOpened(workspace)
                }
            }
        }
    }

    fun openRecentProject(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit) {
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            _environmentNotice.value = "LtiRomServer connection and native toolchain compilation must be " +
                "verified before opening a recent workspace."
            navigateToEnvironment()
            return
        }
        viewModelScope.launch {
            val result = openWorkspaceUseCase(project.workspaceId)
            result.onSuccess { workspace ->
                onWorkspaceOpened(workspace)
            }
        }
    }

    fun discoverWorkspaces() {
        viewModelScope.launch {
            discoverWorkdirWorkspacesUseCase?.invoke()
        }
    }

    private fun fallbackTargetWorkspace(target: TargetDevice, targetPath: String): Workspace = Workspace(
        id = "target_${target.targetKey.lowercase()}",
        name = target.friendlyTitle,
        path = targetPath,
    )

    /**
     * Opens the target device workspace (~/LtiRomWorkDir/target/<targetKey>) once setup is complete.
     */
    fun openDefaultWorkspace(onWorkspaceOpened: (Workspace) -> Unit) {
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            _environmentNotice.value = "LtiRomServer connection and native toolchain compilation must be " +
                "verified before launching the workspace."
            navigateToEnvironment()
            return
        }
        viewModelScope.launch {
            val target = selectedTarget.value ?: DEFAULT_OFFICIAL_TARGETS.first()
            val targetPath = "~/LtiRomWorkDir/target/${target.targetKey}"
            val result = openWorkspaceByPathUseCase(targetPath)
            result.onSuccess { workspace ->
                onWorkspaceOpened(workspace)
            }.onFailure {
                onWorkspaceOpened(fallbackTargetWorkspace(target, targetPath))
            }
        }
    }


    /**
     * Selects an official target hardware device.
     */
    fun selectTarget(target: TargetDevice) {
        _fallbackSelectedTarget.value = target
        viewModelScope.launch {
            selectTargetUseCase?.invoke(target.id)
        }
    }

    /**
     * Selects an official target hardware device and launches directly into the workspace.
     */
    fun selectTargetAndLaunch(target: TargetDevice, onWorkspaceOpened: (Workspace) -> Unit) {
        _fallbackSelectedTarget.value = target
        viewModelScope.launch {
            selectTargetUseCase?.invoke(target.id)
            val targetPath = "~/LtiRomWorkDir/target/${target.targetKey}"
            val result = openWorkspaceByPathUseCase(targetPath)
            result.onSuccess { workspace ->
                onWorkspaceOpened(workspace)
            }.onFailure {
                onWorkspaceOpened(fallbackTargetWorkspace(target, targetPath))
            }
        }
    }

    /**
     * Provisions or verifies the machine-local AVB 2.0 RSA-4096 signing key.
     */
    fun onProvisionAvbKey() {
        if (toolchainSetupState.value.isBusy) return
        viewModelScope.launch {
            provisionAvbKeyUseCase?.invoke()
        }
    }

    /**
     * Re-runs live environment verification without modifying the filesystem.
     */
    fun reverify() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy || autoRunJob?.isActive == true) return
        viewModelScope.launch {
            toolchainService.verifyEnvironment()
        }
    }

    /**
     * Probes environment & toolchain status without modifying the filesystem.
     */
    fun checkEnvironmentStatus() {
        reverify()
    }

    /**
     * Executes end-to-end environment & toolchain setup pipeline (WSL, Server, Repos, Build).
     */
    fun runFullEnvironmentSetup() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy) return
        viewModelScope.launch {
            toolchainService.runFullSetup()
        }
    }

    /**
     * Retries the current setup step that failed or is pending.
     */
    fun retrySetupStep() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy) return
        viewModelScope.launch {
            toolchainService.retryCurrentStep()
        }
    }

    /**
     * Runs automated package remediation via root elevation to resolve doctor diagnostic notices.
     */
    fun autoRemediateDoctorIssues() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy) return
        viewModelScope.launch {
            toolchainService.autoRemediateDoctor()
        }
    }

    fun setAutoDoctorEnabled(enabled: Boolean) {
        _isAutoDoctorEnabled.value = enabled
    }

    /**
     * Automatically coordinates environment verification, doctor remediation, and toolchain provisioning:
     * 1. Probes current environment status via [ToolchainProvisioningService.verifyEnvironment].
     * 2. If any doctor diagnostic issues or prerequisites fail, automatically runs root package remediation.
     * 3. Runs full setup pipeline only if bridge is connected and toolchain compilation or submodules
     *    synchronization is PENDING.
     */
    fun autoRunEnvironmentSetup(autoFix: Boolean = true) {
        if (autoRunJob?.isActive == true ||
            toolchainSetupState.value.isRunning ||
            toolchainSetupState.value.isFixing
        ) {
            return
        }
        autoRunJob = viewModelScope.launch {
            val status = toolchainService?.verifyEnvironment() ?: toolchainSetupState.value
            val isBridgeReady = status.isServerConnected
            val needsSetup = isBridgeReady && status.steps.any { step ->
                (step.stage == SetupStepStage.TOOLCHAIN_COMPILATION ||
                    step.stage == SetupStepStage.REPO_SYNCHRONIZATION) &&
                    step.status == StepStatus.PENDING
            }
            if (needsSetup) {
                val hasDoctorIssues = status.diagnostics.any {
                    it.status == StepStatus.FAILED || (it.status == StepStatus.WARNING && !it.isOptionalForRuntime)
                }
                if (autoFix && _isAutoDoctorEnabled.value && hasDoctorIssues) {
                    toolchainService?.autoRemediateDoctor()
                }
                toolchainService?.runFullSetup()
            }
        }
    }

    /**
     * Purges the persistent setup cache and executes a clean rebuild from scratch.
     */
    fun resetToolchainCacheAndRecheck() {
        if (toolchainService == null) {
            _environmentNotice.value = "Toolchain service is not available in this session."
            return
        }
        if (toolchainSetupState.value.isBusy) return
        viewModelScope.launch {
            toolchainService.resetSetupCache()
            autoRunEnvironmentSetup(autoFix = true)
        }
    }
}
