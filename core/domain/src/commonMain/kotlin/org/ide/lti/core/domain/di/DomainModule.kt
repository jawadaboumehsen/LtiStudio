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
import org.ide.lti.core.domain.pipeline.session.DiscardPipelineDraftChangesUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveAcquisitionConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveAssemblyConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveBuildConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveCustomizationConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveDebloatConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveExtractionConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObservePipelineDraftSessionUseCase
import org.ide.lti.core.domain.pipeline.session.ObservePublishConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ObserveReleaseConfigUseCase
import org.ide.lti.core.domain.pipeline.session.PipelineDraftSessionRepository
import org.ide.lti.core.domain.pipeline.session.SavePipelineDraftUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateAcquisitionConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateAssemblyConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateBuildConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateCustomizationConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateDebloatConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateExtractionConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdatePublishConfigUseCase
import org.ide.lti.core.domain.pipeline.session.UpdateReleaseConfigUseCase
import org.ide.lti.core.domain.pipeline.session.ValidatePipelineDraftUseCase
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

    // Pipeline Draft Session & Stage Configuration
    singleOf(::PipelineDraftSessionRepository)
    singleOf(::ObservePipelineDraftSessionUseCase)
    singleOf(::SavePipelineDraftUseCase)
    singleOf(::ValidatePipelineDraftUseCase)
    singleOf(::DiscardPipelineDraftChangesUseCase)
    singleOf(::ObserveAcquisitionConfigUseCase)
    singleOf(::UpdateAcquisitionConfigUseCase)
    singleOf(::ObserveExtractionConfigUseCase)
    singleOf(::UpdateExtractionConfigUseCase)
    singleOf(::ObserveAssemblyConfigUseCase)
    singleOf(::UpdateAssemblyConfigUseCase)
    singleOf(::ObserveDebloatConfigUseCase)
    singleOf(::UpdateDebloatConfigUseCase)
    singleOf(::ObserveCustomizationConfigUseCase)
    singleOf(::UpdateCustomizationConfigUseCase)
    singleOf(::ObserveBuildConfigUseCase)
    singleOf(::UpdateBuildConfigUseCase)
    singleOf(::ObserveReleaseConfigUseCase)
    singleOf(::UpdateReleaseConfigUseCase)
    singleOf(::ObservePublishConfigUseCase)
    singleOf(::UpdatePublishConfigUseCase)
}
