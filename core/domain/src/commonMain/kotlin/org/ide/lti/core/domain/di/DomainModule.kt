/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ide.lti.core.domain.ai.AIService
import org.ide.lti.core.domain.ai.AIServiceImpl
import org.ide.lti.core.domain.connection.ConnectionManager
import org.ide.lti.core.domain.panels.PanelManager
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.plugin.AuthorProjectTrustUseCase
import org.ide.lti.core.domain.plugin.InstallPluginUseCase
import org.ide.lti.core.domain.plugin.PluginLifecycleUseCase
import org.ide.lti.core.domain.terminal.TerminalManager
import org.ide.lti.core.domain.usecase.build.CancelRunUseCase
import org.ide.lti.core.domain.usecase.build.GetRunHistoryUseCase
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.ReattachRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.usecase.device.ConnectWirelessDeviceUseCase
import org.ide.lti.core.domain.usecase.device.DisconnectDeviceUseCase
import org.ide.lti.core.domain.usecase.device.GetConnectedDevicesUseCase
import org.ide.lti.core.domain.usecase.device.GetSelectedConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.device.PairWirelessDeviceUseCase
import org.ide.lti.core.domain.usecase.device.RebootConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.device.RefreshConnectedDevicesUseCase
import org.ide.lti.core.domain.usecase.device.SelectConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.target.AddTargetUseCase
import org.ide.lti.core.domain.usecase.target.CreateTargetScaffoldUseCase
import org.ide.lti.core.domain.usecase.target.DeleteTargetUseCase
import org.ide.lti.core.domain.usecase.target.DuplicateTargetUseCase
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.usecase.target.SaveTargetConfigurationUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.usecase.target.UpdateTargetUseCase
import org.ide.lti.core.domain.usecase.target.ValidateTargetConfigurationUseCase
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceByPathUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.core.domain.usecase.workspace.SelectAndOpenDirectoryUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val DomainModule = module {
    // Coroutine scope for managers
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Managers and Services
    singleOf(::WorkspaceManager)
    singleOf(::PanelManager)
    singleOf(::TerminalManager)
    singleOf(::ConnectionManager)
    singleOf(::AIServiceImpl) { bind<AIService>() }

    // Workspace Use Cases
    singleOf(::GetRecentWorkspacesUseCase)
    singleOf(::DiscoverWorkdirWorkspacesUseCase)
    singleOf(::OpenWorkspaceUseCase)
    singleOf(::OpenWorkspaceByPathUseCase)
    singleOf(::SelectAndOpenDirectoryUseCase)
    // Explicit: `singleOf` would try to resolve the defaulted `appVersion: String` from Koin.
    single {
        CreateWorkspaceUseCase(
            readinessPort = get(),
            provisioningPort = get(),
            workspaceRepository = get(),
            snapshotRepository = get(),
        )
    }
    single {
        SaveConfigurationSnapshotUseCase(
            workspaceRepository = get(),
            snapshotRepository = get(),
            runRepository = get(),
            provisioningPort = get(),
            admissionPort = get(),
            materializerPort = get(),
        )
    }

    singleOf(::RemoveRecentWorkspaceUseCase)

    // Pipeline & Build Use Cases
    single {
        PipelineOrchestrator(
            executionPort = get(),
            runRepository = get(),
            vendoredFiles = get(),
        )
    }
    single {
        StartPipelineRunUseCase(
            workspaceRepository = get(),
            snapshotRepository = get(),
            targetRepository = get(),
            readinessPort = get(),
            executionPort = get(),
            runRepository = get(),
            orchestrator = get(),
            scope = getOrNull(),
        )
    }
    single {
        CancelRunUseCase(
            runRepository = get(),
            executionPort = get(),
            workspaceRepository = getOrNull(),
        )
    }
    single {
        ReattachRunUseCase(
            workspaceRepository = get(),
            snapshotRepository = get(),
            targetRepository = get(),
            runRepository = get(),
            orchestrator = get(),
            scope = getOrNull(),
        )
    }
    singleOf(::ObserveRunUseCase)
    single {
        GetRunHistoryUseCase(
            runRepository = get(),
            workspaceRepository = getOrNull(),
            executionPort = getOrNull(),
        )
    }

    // Target Use Cases (common)
    singleOf(::GetAvailableTargetsUseCase)
    singleOf(::GetSelectedTargetUseCase)
    singleOf(::AddTargetUseCase)
    singleOf(::UpdateTargetUseCase)
    singleOf(::DeleteTargetUseCase)
    singleOf(::SelectTargetUseCase)
    singleOf(::ValidateTargetConfigurationUseCase)
    singleOf(::CreateTargetScaffoldUseCase)
    singleOf(::DuplicateTargetUseCase)
    single {
        SaveTargetConfigurationUseCase(
            validateTargetConfigurationUseCase = get(),
            addTargetUseCase = get(),
            updateTargetUseCase = get(),
            selectTargetUseCase = get(),
        )
    }

    // Connected ADB & Fastboot Device Use Cases
    singleOf(::GetConnectedDevicesUseCase)
    singleOf(::GetSelectedConnectedDeviceUseCase)
    singleOf(::SelectConnectedDeviceUseCase)
    singleOf(::RefreshConnectedDevicesUseCase)
    singleOf(::RebootConnectedDeviceUseCase)
    singleOf(::PairWirelessDeviceUseCase)
    singleOf(::ConnectWirelessDeviceUseCase)
    singleOf(::DisconnectDeviceUseCase)

    // Plugin Use Cases
    single { InstallPluginUseCase(port = get()) }
    single { PluginLifecycleUseCase(port = get()) }
    single { AuthorProjectTrustUseCase(port = get()) }
}

