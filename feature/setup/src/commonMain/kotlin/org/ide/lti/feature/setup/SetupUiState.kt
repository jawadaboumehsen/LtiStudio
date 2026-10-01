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

import androidx.compose.runtime.Immutable
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.SetupAttemptRecord
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject

/**
 * Immutable presentation UI state for the Setup / Welcome Screen.
 *
 * Presentation properties (environmentPresentation, environmentSummary, statusBarPresentation)
 * are calculated getters derived directly from raw facts to guarantee that state copies never
 * desynchronize or retain stale presentation copies.
 */
@Immutable
data class SetupUiState(
    val toolchainSetupState: ToolchainSetupState = ToolchainSetupState(),
    val isToolchainBound: Boolean = true,
    val selectedTarget: TargetDevice? = null,
    val availableTargets: List<TargetDevice> = emptyList(),
    val recentProjects: List<RecentProject> = emptyList(),
    val filteredProjects: List<RecentProject> = emptyList(),
    val searchQuery: String = "",
    val selectedWorkspaceId: String? = null,
    val selectedCockpitTab: String = "doctor",
    val toolSearchQuery: String = "",
    val toolSelection: ToolSelection = ToolSelection(),
    val filteredToolsMatrix: List<ToolComponentItem> = emptyList(),
    val isAutoDoctorEnabled: Boolean = true,
    val selectedTab: SetupTab = SetupTab.PROJECTS,
    val selectedDestination: SetupDestination = SetupDestination.WORKSPACES,
    val environmentNotice: String? = null,
    val environmentReadiness: EnvironmentReadiness? = null,
    val activeOperation: SetupOperation? = null,
    val workspaceError: WorkspaceError? = null,
    val pendingWorkspaceRequest: PendingWorkspaceRequest? = null,
    val isHandoffSheetVisible: Boolean = false,
    val handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    val attemptRecord: SetupAttemptRecord? = null,
) {
    val environmentPresentation: EnvironmentPresentation
        get() = SetupPresentationMapper.mapEnvironment(
            state = toolchainSetupState,
            isToolchainBound = isToolchainBound,
            activeOperation = activeOperation,
        )

    val environmentSummary: EnvironmentSummaryPresentation
        get() = SetupPresentationMapper.mapEnvironmentSummary(toolchainSetupState)

    val statusBarPresentation: SetupStatusBarPresentation
        get() = SetupPresentationMapper.mapStatusBar(
            activeOperation = activeOperation,
            toolchainSetupState = toolchainSetupState,
        )

    val recoveryPresentation: RecoveryPresentation
        get() = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = toolchainSetupState,
            activeOperation = activeOperation,
            attemptRecord = attemptRecord,
        )
}

/**
 * Cohesive bundle of user actions and navigation events dispatched from the Setup Screen.
 *
 * Defaults to no-ops to allow test harnesses to instantiate SetupUiActions without boilerplate,
 * while maintaining a single typed callback per action.
 */
@Immutable
data class SetupUiActions(
    val onCheckEnvironment: () -> Unit = {},
    val onReconnect: () -> Unit = {},
    val onRetrySetupStep: () -> Unit = {},
    val onProvisionAvbKey: () -> Unit = {},
    val onSelectTarget: (TargetDevice) -> Unit = {},
    val onAutoRemediateDoctor: () -> Unit = {},
    val onNavigateToEnvironment: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onRemoveRecentProject: (String) -> Unit = {},
    val onOpenFolder: () -> Unit = {},
    val onRecentProjectClick: (RecentProject) -> Unit = {},
    val onSelectWorkspace: (String?) -> Unit = {},
    val onSelectCockpitTab: (String) -> Unit = {},
    val onToolSearchQueryChange: (String) -> Unit = {},
    val onSelectToolCategory: (ToolCategory?) -> Unit = {},
    val onSelectToolPackage: (ToolNavPackage) -> Unit = {},
    val onTestTool: (String) -> Unit = {},
    val onRecompileTool: (String) -> Unit = {},
    val onSelectTab: (SetupTab) -> Unit = {},
    val onSelectDestination: (SetupDestination) -> Unit = {},
    val onDismissEnvironmentNotice: () -> Unit = {},
    val onSetAutoDoctorEnabled: (Boolean) -> Unit = {},
    val onResetToolchainCache: () -> Unit = {},
    val onCreateWorkspace: () -> Unit = {},
    val onOpenManageTargets: () -> Unit = {},
    val onOpenHelp: () -> Unit = {},
    val onPreviewSetup: (SetupStepStage?) -> Unit = {},
    val onConfirmOperation: () -> Unit = {},
    val onDismissPreview: () -> Unit = {},
    val onRetryStage: (SetupStepStage) -> Unit = {},
    val onDismissWorkspaceError: () -> Unit = {},
    val onLaunchStudio: () -> Unit = {},
    val onCancelPendingWorkspaceRequest: () -> Unit = {},
    val onResumeAwaitingOperation: () -> Unit = {},
    val onDismissHandoff: () -> Unit = {},
    val onAbandonAwaitingOperation: () -> Unit = {},
    val onShowHandoffSheet: () -> Unit = {},
    val onOpenTerminal: (String) -> Unit = {},
    val onSelectDistro: (String) -> Unit = {},
    val onInstallPackages: () -> Unit = {},
    val onCancelOperation: () -> Unit = {},
    val onExportLogs: () -> Unit = {},
)
