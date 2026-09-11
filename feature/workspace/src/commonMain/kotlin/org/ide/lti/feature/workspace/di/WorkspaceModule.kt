/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.di

import org.ide.lti.core.cli.DesktopShellExecutor
import org.ide.lti.core.cli.ShellExecutor
import org.ide.lti.feature.workspace.WorkspaceViewModel
import org.ide.lti.feature.workspace.creation.CreateWorkspaceViewModel
import org.ide.lti.feature.workspace.panels.ai.AIAssistantViewModel
import org.ide.lti.feature.workspace.panels.editor.EditorViewModel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerViewModel
import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationViewModel
import org.ide.lti.feature.workspace.panels.terminal.TerminalViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val workspaceModule = module {
    // CLI & Terminal execution services
    single<ShellExecutor> { DesktopShellExecutor() }

    // Workspace ViewModels
    viewModel {
        WorkspaceViewModel(
            workspaceManager = get(),
            panelManager = get(),
            getAvailableTargetsUseCase = get(),
            getSelectedTargetUseCase = get(),
            selectTargetUseCase = get(),
            addTargetUseCase = getOrNull(),
            getConnectedDevicesUseCase = get(),
            getSelectedConnectedDeviceUseCase = get(),
            selectConnectedDeviceUseCase = get(),
            refreshConnectedDevicesUseCase = get(),
            rebootConnectedDeviceUseCase = get(),
            pairWirelessDeviceUseCase = get(),
            connectWirelessDeviceUseCase = get(),
            disconnectDeviceUseCase = get(),
        )
    }

    // Explicit: the defaulted file-picker/reader lambdas must not be resolved through Koin.
    viewModel {
        WorkspaceConfigurationViewModel(
            workspaceManager = get(),
            snapshotRepository = get(),
            targetRepository = get(),
            saveConfigurationSnapshotUseCase = get(),
            transport = getOrNull(),
            readinessPort = getOrNull(),
        )
    }
    viewModel {
        CreateWorkspaceViewModel(
            createWorkspaceUseCase = get(),
            targetRepository = get(),
            workspaceRepository = get(),
            workspaceManager = get(),
        )
    }

    // Panel ViewModels
    viewModelOf(::AIAssistantViewModel)
    viewModelOf(::EditorViewModel)
    viewModelOf(::ExplorerViewModel)
    viewModelOf(::TerminalViewModel)
    viewModel {
        BuildRunViewModel(
            workspaceManager = get(),
            startPipelineRunUseCase = get(),
            observeRunUseCase = get(),
            runRepository = getOrNull(),
        )
    }
}
