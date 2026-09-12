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
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.RecentProject

/**
 * Immutable presentation UI state for the Setup / Welcome Screen.
 *
 * Bundles related presentation state properties into a cohesive parameter object
 * to keep composable signatures short and maintainable.
 */
@Immutable
data class SetupUiState(
    val toolchainSetupState: ToolchainSetupState = ToolchainSetupState(),
    val selectedTarget: TargetDevice? = null,
    val availableTargets: List<TargetDevice> = emptyList(),
    val recentProjects: List<RecentProject> = emptyList(),
    val filteredProjects: List<RecentProject> = emptyList(),
    val searchQuery: String = "",
    val selectedWorkspaceId: String? = null,
    val selectedCockpitTab: String = "doctor",
    val toolSearchQuery: String = "",
    val selectedToolCategory: String = "All",
    val filteredToolsMatrix: List<ToolComponentItem> = emptyList(),
    val isAutoDoctorEnabled: Boolean = true,
    val selectedTab: SetupTab = SetupTab.PROJECTS,
    val selectedDestination: SetupDestination = SetupDestination.WORKSPACES,
    val environmentNotice: String? = null,
    val environmentReadiness: EnvironmentReadiness? = null,
    val activeOperation: SetupOperation? = null,
)

/**
 * Cohesive bundle of user actions and navigation events dispatched from the Setup Screen.
 *
 * Bundles related user actions into a cohesive parameter object to keep composable signatures
 * short and maintain clean separation between presentation state and events.
 */
@Immutable
data class SetupUiActions(
    val onCheckEnvironment: () -> Unit = {},
    val onRunFullSetup: () -> Unit = {},
    val onRetrySetupStep: () -> Unit = {},
    val onProvisionAvbKey: () -> Unit = {},
    val onSelectTarget: (TargetDevice) -> Unit = {},
    val onAutoRemediateDoctor: () -> Unit = {},
    val onAutoRunEnvironmentSetup: () -> Unit = {},
    val onNavigateToEnvironment: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onRemoveRecentProject: (String) -> Unit = {},
    val onOpenFolder: () -> Unit = {},
    val onRecentProjectClick: (RecentProject) -> Unit = {},
    val onSelectWorkspace: (String?) -> Unit = {},
    val onSelectCockpitTab: (String) -> Unit = {},
    val onToolSearchQueryChange: (String) -> Unit = {},
    val onSelectToolCategory: (String) -> Unit = {},
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
    val onPreviewSetup: (org.ide.lti.core.domain.setup.SetupStepStage?) -> Unit = {},
    val onConfirmOperation: () -> Unit = {},
    val onDismissPreview: () -> Unit = {},
    val onRetryStage: (org.ide.lti.core.domain.setup.SetupStepStage) -> Unit = {},
)
