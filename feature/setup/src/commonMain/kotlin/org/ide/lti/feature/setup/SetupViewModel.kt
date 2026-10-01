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
import io.github.vinceglb.filekit.core.FileKit
import kotlinx.coroutines.CancellationException
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
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.RestoreCoordinatorPort
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.setup.versions.ToolVersionsState
import org.ide.lti.core.model.setup.ToolSelection as VersionToolSelection

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
    private val removeRecentWorkspaceUseCase: RemoveRecentWorkspaceUseCase,
    private val discoverWorkdirWorkspacesUseCase: DiscoverWorkdirWorkspacesUseCase? = null,
    private val toolchainService: ToolchainProvisioningService? = null,
    private val provisionAvbKeyUseCase: ProvisionAvbKeyUseCase? = null,
    private val getAvailableTargetsUseCase: GetAvailableTargetsUseCase? = null,
    private val getSelectedTargetUseCase: GetSelectedTargetUseCase? = null,
    private val selectTargetUseCase: SelectTargetUseCase? = null,
    private val environmentReadinessPort: EnvironmentReadinessPort? = null,
    private val terminalLauncher: TerminalLauncherPort? = null,
    private val toolchainSelectionRepository: ToolchainSelectionRepository? = null,
    private val sourceResolver: SourceResolverPort? = null,
    private val toolchainInstallationPort: ToolchainInstallationPort? = null,
    private val restoreCoordinator: RestoreCoordinatorPort? = null,
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
        terminalLauncher: TerminalLauncherPort? = null,
        toolchainSelectionRepository: ToolchainSelectionRepository? = null,
        sourceResolver: SourceResolverPort? = null,
        toolchainInstallationPort: ToolchainInstallationPort? = null,
        restoreCoordinator: RestoreCoordinatorPort? = null,
    ) : this(
        getRecentWorkspacesUseCase = getRecentWorkspacesUseCase
            ?: GetRecentWorkspacesUseCase(workspaceManager.repository),
        openWorkspaceUseCase = openWorkspaceUseCase
            ?: OpenWorkspaceUseCase(workspaceManager.repository, workspaceManager),
        openWorkspaceByPathUseCase = openWorkspaceByPathUseCase,
        removeRecentWorkspaceUseCase = removeRecentWorkspaceUseCase
            ?: RemoveRecentWorkspaceUseCase(workspaceManager.repository),
        discoverWorkdirWorkspacesUseCase = discoverWorkdirWorkspacesUseCase,
        toolchainService = toolchainService,
        provisionAvbKeyUseCase = provisionAvbKeyUseCase,
        getAvailableTargetsUseCase = getAvailableTargetsUseCase,
        getSelectedTargetUseCase = getSelectedTargetUseCase,
        selectTargetUseCase = selectTargetUseCase,
        environmentReadinessPort = environmentReadinessPort,
        terminalLauncher = terminalLauncher,
        toolchainSelectionRepository = toolchainSelectionRepository,
        sourceResolver = sourceResolver,
        toolchainInstallationPort = toolchainInstallationPort,
        restoreCoordinator = restoreCoordinator,
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

    val toolVersionsState = ToolVersionsState(
        scope = viewModelScope,
        repository = toolchainSelectionRepository ?: InMemoryToolchainSelectionRepository(),
        sourceResolver = sourceResolver,
        installationPort = toolchainInstallationPort,
        restoreCoordinator = restoreCoordinator,
        isBuildingProvider = {
            val step = toolchainSetupState.value.steps
                .firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
            step?.status == StepStatus.RUNNING
        },
    )

    val isAutoDoctorEnabled: StateFlow<Boolean> = environmentSetupState.isAutoDoctorEnabled
    val activeOperation: StateFlow<SetupOperation?> = environmentSetupState.activeOperation
    val isToolchainBound: Boolean get() = environmentSetupState.isToolchainBound

    init {
        toolVersionsState.onBuildAndSwitch = { executeBuildAndSwitch() }
        environmentSetupState.checkEnvironmentStatus()
        viewModelScope.launch {
            toolchainSetupState.collect { state ->
                val compilationStep = state.steps.firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
                if (compilationStep?.status == StepStatus.RUNNING || compilationStep?.status == StepStatus.SUCCESS) {
                    toolVersionsState.refresh()
                }
            }
        }
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

    suspend fun executeBuildAndSwitch(): SetupOutcome {
        val service = toolchainService
            ?: return SetupOutcome.Failed(null, "Toolchain service is not available in this session.")
        return try {
            val plan = service.prepare(
                kind = SetupPlanKind.FULL_SETUP,
                targetId = null,
                autoDoctorEnabled = isAutoDoctorEnabled.value,
            )
            val outcome = service.confirm(plan.planId, plan.revisionHash)
            when (outcome) {
                is SetupOutcome.Succeeded -> {
                    environmentSetupState.checkEnvironmentStatus()
                }
                is SetupOutcome.Failed -> {
                    environmentSetupState.setEnvironmentNotice("Build & switch failed: ${outcome.reason}")
                }
                is SetupOutcome.Interrupted -> {
                    environmentSetupState.setEnvironmentNotice("Build & switch was interrupted: ${outcome.reason}")
                }
                is SetupOutcome.Busy -> {
                    environmentSetupState.setEnvironmentNotice("Another operation is running (${outcome.ownerId}).")
                }
                SetupOutcome.Cancelled -> {
                    environmentSetupState.setEnvironmentNotice("Build & switch was cancelled.")
                }
                else -> {}
            }
            outcome
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val failed = SetupOutcome.Failed(null, e.message ?: "Build & switch failed")
            environmentSetupState.setEnvironmentNotice("Build & switch error: ${e.message}")
            failed
        }
    }

    val workspaceBrowserState = WorkspaceBrowserState(
        scope = viewModelScope,
        getRecentWorkspacesUseCase = getRecentWorkspacesUseCase,
        openWorkspaceUseCase = openWorkspaceUseCase,
        removeRecentWorkspaceUseCase = removeRecentWorkspaceUseCase,
        openWorkspaceByPathUseCase = openWorkspaceByPathUseCase,
        discoverWorkdirWorkspacesUseCase = discoverWorkdirWorkspacesUseCase,
        environmentReadinessPort = environmentReadinessPort,
        toolchainSetupState = toolchainSetupState,
        onFailingCheckFocus = { selectTab(SetupTab.ENVIRONMENT) },
    )

    val recentProjects: StateFlow<List<RecentProject>> = workspaceBrowserState.recentProjects
    val searchQuery: StateFlow<String> = workspaceBrowserState.searchQuery
    val filteredRecentProjects: StateFlow<List<RecentProject>> = workspaceBrowserState.filteredProjects
    val selectedWorkspaceId: StateFlow<String?> = workspaceBrowserState.selectedWorkspaceId
    val workspaceError: StateFlow<WorkspaceError?> = workspaceBrowserState.workspaceError
    val pendingWorkspaceRequest: StateFlow<PendingWorkspaceRequest?> = workspaceBrowserState.pendingWorkspaceRequest

    fun onSearchQueryChange(query: String) {
        workspaceBrowserState.onSearchQueryChange(query)
    }

    fun selectWorkspace(workspaceId: String?) {
        workspaceBrowserState.selectWorkspace(workspaceId)
    }

    fun dismissWorkspaceError() {
        workspaceBrowserState.clearWorkspaceError()
    }

    fun cancelPendingWorkspaceRequest() {
        workspaceBrowserState.cancelPendingWorkspaceRequest()
    }

    private val _selectedTab = MutableStateFlow(SetupTab.PROJECTS)
    val selectedTab: StateFlow<SetupTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: SetupTab) {
        _selectedTab.value = tab
    }

    val environmentNotice: StateFlow<String?> = environmentSetupState.environmentNotice

    fun showEnvironmentNotice(message: String) {
        environmentSetupState.setEnvironmentNotice(message)
    }

    fun dismissEnvironmentNotice() {
        environmentSetupState.dismissEnvironmentNotice()
    }

    fun navigateToEnvironment() {
        _selectedTab.value = SetupTab.ENVIRONMENT
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

    val toolSelection: StateFlow<ToolSelection> = toolBrowserState.toolSelection

    fun selectToolCategory(category: ToolCategory?) {
        toolBrowserState.selectCategory(category)
    }

    fun selectToolPackage(pkg: ToolNavPackage) {
        toolBrowserState.selectPackage(pkg)
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
        viewModelScope.launch { reportToolOutcome("Test", toolId, toolBrowserState.testTool(toolId)) }
    }

    fun recompileTool(toolId: String) {
        if (toolchainService == null) {
            environmentSetupState.setEnvironmentNotice("Toolchain service is not available in this session.")
            return
        }
        viewModelScope.launch { reportToolOutcome("Rebuild", toolId, toolBrowserState.repairTool(toolId)) }
    }

    /** Every outcome but success says why, so a busy, interrupted or refused action never ends silently. */
    private fun reportToolOutcome(action: String, toolId: String, outcome: SetupOutcome) {
        val notice = when (outcome) {
            is SetupOutcome.Succeeded -> return
            is SetupOutcome.Failed -> "$action $toolId failed: ${outcome.reason}"
            is SetupOutcome.Busy -> "$action $toolId did not start: another operation is running (${outcome.ownerId})."
            is SetupOutcome.Interrupted -> "$action $toolId was interrupted: ${outcome.reason}"
            SetupOutcome.Cancelled -> "$action $toolId was cancelled."
            else -> "$action $toolId did not finish: ${outcome::class.simpleName}."
        }
        environmentSetupState.setEnvironmentNotice(notice)
    }

    fun removeRecentProject(workspaceId: String) {
        workspaceBrowserState.removeRecentProject(workspaceId)
    }

    fun openFolder(onWorkspaceOpened: (Workspace) -> Unit) {
        workspaceBrowserState.openFolder(onWorkspaceOpened)
    }

    fun openPath(path: String, onWorkspaceOpened: (Workspace) -> Unit) {
        workspaceBrowserState.openPath(path, onWorkspaceOpened)
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

    fun resumeAwaitingOperation() {
        environmentSetupState.resumeAwaitingOperation()
    }

    fun openTerminal(distro: String) {
        val launcher = terminalLauncher
        if (launcher != null) {
            launcher.launchTerminal(distro).onFailure {
                environmentSetupState.setTerminalLaunchFailure(
                    it.message ?: "Unknown error",
                )
            }
        } else {
            environmentSetupState.setTerminalLaunchFailure(
                "No terminal launcher available on this platform",
            )
        }
    }

    val isHandoffSheetVisible: StateFlow<Boolean> = environmentSetupState.isHandoffSheetVisible
    val handoffSheetState: StateFlow<TerminalHandoffState> = environmentSetupState.handoffSheetState

    fun showHandoffSheet() {
        environmentSetupState.showHandoffSheet()
    }

    fun installPackages() {
        environmentSetupState.installPackages()
    }

    fun abandonAwaitingOperation() {
        environmentSetupState.abandonAwaitingOperation()
    }

    fun dismissHandoff() {
        environmentSetupState.dismissHandoff()
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

    fun selectDistro(distro: String) {
        environmentSetupState.selectDistro(distro)
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

    /**
     * Cancels the currently running setup operation (FR-023, T064).
     */
    fun cancelOperation() {
        environmentSetupState.cancelOperation()
    }

    /**
     * Exports the persisted attempt log to a file via native save dialog (T067, T068).
     */
    fun exportLogs() {
        viewModelScope.launch {
            // Only the persisted log is exported: the in-memory tail is truncated by design (T068).
            val content = environmentSetupState.getPersistedAttemptLog()
            if (content == null) {
                environmentSetupState.setEnvironmentNotice("There is no setup log to export yet.")
                return@launch
            }
            try {
                FileKit.saveFile(
                    bytes = content.encodeToByteArray(),
                    baseName = "setup-${System.currentTimeMillis()}",
                    extension = "log",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                environmentSetupState.setEnvironmentNotice("Couldn't export the setup log: ${e.message}")
            }
        }
    }
}

private class InMemoryToolchainSelectionRepository : ToolchainSelectionRepository {
    private var selections = DistroToolSelections()

    override suspend fun desiredSelections(distro: String): DistroToolSelections = selections

    override suspend fun saveSelections(
        distro: String,
        expectedRevision: Long,
        desired: Map<ToolGroupId, VersionToolSelection>,
    ): Result<Long> {
        if (expectedRevision != selections.revision) {
            return Result.failure(IllegalStateException("Revision mismatch"))
        }
        val next = selections.revision + 1
        selections = DistroToolSelections(desired = desired.mapKeys { it.key.value }, revision = next)
        return Result.success(next)
    }

    private val trustedRepos = mutableSetOf<String>()

    override suspend fun recordTrust(repoUrl: String): Result<Unit> {
        trustedRepos.add(VersionToolSelection.normalizeRepoUrl(repoUrl))
        return Result.success(Unit)
    }

    override suspend fun isTrusted(repoUrl: String): Boolean =
        VersionToolSelection.normalizeRepoUrl(repoUrl) in trustedRepos
}
