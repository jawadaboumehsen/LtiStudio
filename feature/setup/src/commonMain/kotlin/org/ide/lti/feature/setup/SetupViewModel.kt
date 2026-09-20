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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
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
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
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
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
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
        environmentReadinessPort: EnvironmentReadinessPort? = null,
    ) : this(
        getRecentWorkspacesUseCase = getRecentWorkspacesUseCase
            ?: GetRecentWorkspacesUseCase(workspaceManager.repository),
        openWorkspaceUseCase = openWorkspaceUseCase
            ?: OpenWorkspaceUseCase(workspaceManager.repository, workspaceManager),
        openWorkspaceByPathUseCase = openWorkspaceByPathUseCase,
        selectAndOpenDirectoryUseCase = SelectAndOpenDirectoryUseCase(openWorkspaceByPathUseCase),
        removeRecentWorkspaceUseCase = removeRecentWorkspaceUseCase
            ?: RemoveRecentWorkspaceUseCase(workspaceManager.repository),
        discoverWorkdirWorkspacesUseCase = discoverWorkdirWorkspacesUseCase,
        toolchainService = toolchainService,
        provisionAvbKeyUseCase = provisionAvbKeyUseCase,
        getAvailableTargetsUseCase = getAvailableTargetsUseCase,
        getSelectedTargetUseCase = getSelectedTargetUseCase,
        selectTargetUseCase = selectTargetUseCase,
        environmentReadinessPort = environmentReadinessPort,
    )

    companion object {
        val DEFAULT_OFFICIAL_TARGETS = listOf(DefaultTargetCatalog.PQ84P01_DEFAULT)
    }

    private val fallbackToolchainState = MutableStateFlow(
        ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map { step ->
                if (toolchainService == null) {
                    step.copy(
                        status = StepStatus.WARNING,
                        description = "Toolchain service not bound in DI. Running in preview/host mode.",
                    )
                } else {
                    step
                }
            },
        ),
    )

    val environmentReadiness: StateFlow<EnvironmentReadiness?> =
        environmentReadinessPort?.observe()
            ?.stateIn(viewModelScope, SharingStarted.Eagerly, null)
            ?: MutableStateFlow<EnvironmentReadiness?>(null).asStateFlow()

    val toolchainSetupState: StateFlow<ToolchainSetupState> = toolchainService?.state
        ?: fallbackToolchainState.asStateFlow()

    private var autoRunJob: Job? = null

    private val fallbackSelectedTarget = MutableStateFlow<TargetDevice?>(DEFAULT_OFFICIAL_TARGETS.firstOrNull())

    val availableTargets: StateFlow<List<TargetDevice>> = (
        getAvailableTargetsUseCase?.invoke()
            ?: MutableStateFlow(DEFAULT_OFFICIAL_TARGETS)
        )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_OFFICIAL_TARGETS,
        )

    val selectedTarget: StateFlow<TargetDevice?> = (
        getSelectedTargetUseCase?.invoke()
            ?: fallbackSelectedTarget.asStateFlow()
        )
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_OFFICIAL_TARGETS.firstOrNull(),
        )

    val environmentSetupState = EnvironmentSetupState(
        scope = viewModelScope,
        toolchainService = toolchainService,
        environmentReadinessPort = environmentReadinessPort,
    )

    val toolBrowserState = ToolBrowserState(
        scope = viewModelScope,
        toolchainService = toolchainService,
        environmentSetupState = environmentSetupState,
    )

    val setupActivityState = SetupActivityState(
        scope = viewModelScope,
    )

    val isAutoDoctorEnabled: StateFlow<Boolean> = environmentSetupState.isAutoDoctorEnabled
    val activeOperation: StateFlow<SetupOperation?> = environmentSetupState.activeOperation
    val environmentPresentation: StateFlow<EnvironmentPresentation> = environmentSetupState.environmentPresentation

    init {
        environmentSetupState.checkEnvironmentStatus()
        viewModelScope.launch {
            // The single activity subscription (FR-012). Events already queued are drained into one
            // batch so a burst costs one snapshot publish, not one per line.
            val events = toolchainService?.observeActivity()?.produceIn(this) ?: return@launch
            for (first in events) {
                val batch = ArrayList<SetupLogEvent>().apply { add(first) }
                while (true) {
                    batch += events.tryReceive().getOrNull() ?: break
                }
                setupActivityState.appendEvents(batch)
            }
        }
    }

    val workspaceBrowserState = WorkspaceBrowserState(
        scope = viewModelScope,
        getRecentWorkspacesUseCase = getRecentWorkspacesUseCase,
        openWorkspaceUseCase = openWorkspaceUseCase,
        removeRecentWorkspaceUseCase = removeRecentWorkspaceUseCase,
        openWorkspaceByPathUseCase = openWorkspaceByPathUseCase,
        selectAndOpenDirectoryUseCase = selectAndOpenDirectoryUseCase,
        discoverWorkdirWorkspacesUseCase = discoverWorkdirWorkspacesUseCase,
        environmentReadinessPort = environmentReadinessPort,
    )

    val recentProjects: StateFlow<List<RecentProject>> = workspaceBrowserState.recentProjects
    val searchQuery: StateFlow<String> = workspaceBrowserState.searchQuery
    val filteredRecentProjects: StateFlow<List<RecentProject>> = workspaceBrowserState.filteredProjects
    val selectedWorkspaceId: StateFlow<String?> = workspaceBrowserState.selectedWorkspaceId
    val workspaceError: StateFlow<WorkspaceError?> = workspaceBrowserState.workspaceError

    fun onSearchQueryChange(query: String) {
        workspaceBrowserState.onSearchQueryChange(query)
    }

    fun selectWorkspace(workspaceId: String?) {
        workspaceBrowserState.selectWorkspace(workspaceId)
    }

    fun dismissWorkspaceError() {
        workspaceBrowserState.clearWorkspaceError()
    }

    private val _selectedTab = MutableStateFlow(SetupTab.PROJECTS)
    val selectedTab: StateFlow<SetupTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: SetupTab) {
        _selectedTab.value = tab
    }

    val environmentNotice: StateFlow<String?> = environmentSetupState.environmentNotice

    fun dismissEnvironmentNotice() {
        environmentSetupState.dismissEnvironmentNotice()
    }

    fun navigateToEnvironment() {
        _selectedTab.value = SetupTab.ENVIRONMENT
        if (!toolchainSetupState.value.canLaunchWorkspace) {
            checkEnvironmentStatus()
        }
    }

    internal val _selectedCockpitTab = MutableStateFlow("doctor")
    val selectedCockpitTab: StateFlow<String> = _selectedCockpitTab.asStateFlow()

    fun selectCockpitTab(tab: String) {
        _selectedCockpitTab.value = tab
    }

    val toolSearchQuery: StateFlow<String> = toolBrowserState.searchQuery

    fun onToolSearchQueryChange(query: String) {
        toolBrowserState.onSearchQueryChange(query)
    }

    val selectedToolCategory: StateFlow<String> = toolBrowserState.selectedCategory

    fun selectToolCategory(category: String) {
        toolBrowserState.selectCategory(category)
    }

    val filteredToolsMatrix: StateFlow<List<ToolComponentItem>> = toolBrowserState.filteredTools
        .map { list -> list.map { it.tool } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ToolchainSetupState.defaultToolsMatrix(),
        )

    fun testTool(toolId: String) {
        if (toolchainService == null) {
            environmentSetupState.setEnvironmentNotice("Toolchain service is not available in this session.")
            return
        }
        toolBrowserState.launchTestTool(toolId)
    }

    fun recompileTool(toolId: String) {
        if (toolchainService == null) {
            environmentSetupState.setEnvironmentNotice("Toolchain service is not available in this session.")
            return
        }
        viewModelScope.launch {
            val outcome = toolBrowserState.repairTool(toolId)
            if (outcome is SetupOutcome.Failed && !ToolCapabilities.isRecipeSupported(toolId)) {
                environmentSetupState.setEnvironmentNotice(
                    ToolCapabilities.unavailableReasonFor(toolId) ?: ToolCapabilities.REPAIR_UNAVAILABLE_REASON,
                )
            }
        }
    }

    fun removeRecentProject(workspaceId: String) {
        workspaceBrowserState.removeRecentProject(workspaceId)
    }

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit) {
        workspaceBrowserState.openFolder(onWorkspaceOpened)
    }

    fun openRecentProject(project: RecentProject, onWorkspaceOpened: (Workspace) -> Unit) {
        workspaceBrowserState.openRecentProject(project, onWorkspaceOpened)
    }

    fun discoverWorkspaces() {
        workspaceBrowserState.discoverWorkspaces()
    }

    /**
     * Selects an official target hardware device.
     */
    fun selectTarget(target: TargetDevice) {
        fallbackSelectedTarget.value = target
        viewModelScope.launch {
            selectTargetUseCase?.invoke(target.id)
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

    fun previewSetup(stage: SetupStepStage? = null) {
        environmentSetupState.previewSetup(stage)
    }

    fun confirmSetupOperation() {
        environmentSetupState.confirmOperation()
    }

    fun confirmOperation() {
        confirmSetupOperation()
    }

    fun dismissPreview() {
        environmentSetupState.dismissPreview()
    }

    fun retryStage(stage: SetupStepStage) {
        environmentSetupState.retryStage(stage)
    }

    /**
     * Re-runs live environment verification without modifying the filesystem.
     */
    fun reverify() {
        environmentSetupState.reverify()
    }

    /**
     * Probes environment & toolchain status without modifying the filesystem.
     */
    fun checkEnvironmentStatus() {
        environmentSetupState.checkEnvironmentStatus()
    }

    /**
     * Reconciles the journaled pending attempt against server truth (US3). Reconnect reuses the
     * attemptId; it never resubmits and never starts new work.
     */
    fun reconnectPendingAttempt() {
        environmentSetupState.reconcilePendingAttempt()
    }

    /**
     * Retries the current failed/pending setup step. Retry is a mutation: it opens a preview that the
     * user confirms explicitly; it never executes directly (FR-003).
     */
    fun retrySetupStep() {
        val stage = toolchainSetupState.value.steps.firstOrNull { it.status == StepStatus.FAILED }?.stage
            ?: toolchainSetupState.value.steps.firstOrNull { it.status == StepStatus.PENDING }?.stage
            ?: toolchainSetupState.value.steps.lastOrNull()?.stage
            ?: return
        environmentSetupState.retryStage(stage)
    }

    /**
     * Previews Auto-Fix (doctor remediation) as a confirmable plan; runs nothing until confirmed and
     * never when Auto Doctor is disabled (FR-003, FR-004).
     */
    fun autoRemediateDoctorIssues() {
        environmentSetupState.previewAutoFix()
    }

    fun setAutoDoctorEnabled(enabled: Boolean) {
        environmentSetupState.setAutoDoctorEnabled(enabled)
    }

    /**
     * Previews a cache reset as a confirmable plan. Reset is a mutation: nothing is purged until the
     * user confirms the preview, and the environment is re-probed afterwards (FR-003, FR-006).
     */
    fun previewResetToolchainCache() {
        environmentSetupState.previewResetToolchainCache()
    }
}
