/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.di

import io.ltirom.tooling.client.RemoteWslToolRepository
import io.ltirom.tooling.client.SmartToolRepository
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.resilience.ConnectionStateHolder
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.client.wsl.TransportCliExecutor
import io.ltirom.tooling.client.wsl.WslPresenceAdapter
import io.ltirom.tooling.core.DefaultToolResolver
import io.ltirom.tooling.core.ProcessEnvironment
import io.ltirom.tooling.core.ProcessToolRepository
import io.ltirom.tooling.core.Redactor
import io.ltirom.tooling.core.StreamingToolRepository
import io.ltirom.tooling.core.ToolMetadataRegistry
import io.ltirom.tooling.core.ToolRepository
import io.ltirom.tooling.core.ToolSafetyPolicy
import io.ltirom.tooling.core.events.DefaultToolEventBus
import io.ltirom.tooling.core.events.ToolEventBus
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.data.setup.DesktopTerminalLauncher
import org.ide.lti.core.data.setup.RecoveryCoordinator
import org.ide.lti.core.data.setup.ServerArtifactAdapter
import org.ide.lti.core.data.setup.WslHostPrerequisiteProbe
import org.ide.lti.core.data.setup.WslToolchainProvisioner
import org.ide.lti.core.data.setup.check.EnvironmentCheck
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.handlers.ComponentToolsHandler
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.RemoteFileUploaderPort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.domain.usecase.setup.CheckEnvironmentStatusUseCase
import org.ide.lti.core.domain.usecase.setup.TestIndividualToolUseCase
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Composition root for component tools and authenticated WSL command execution.
 *
 * Required bindings:
 * - [ToolStorePort] → [KotlinToolStoreAdapter]
 * - [ComponentToolsHandler] (registered into [PlanExecutor])
 */
public val toolingModule =
    module {
        single<WslPresencePort> { WslPresenceAdapter() }
        single<HostPrerequisitePort> { WslHostPrerequisiteProbe() }
        single { WslDaemonManager() } bind DaemonSupervisorPort::class
        single<ServerArtifactPort> {
            ServerArtifactAdapter(
                daemonManager = get<WslDaemonManager>(),
            )
        }
        single<RemoteTransportPort> {
            val dm = get<WslDaemonManager>()
            KtorRemoteTransportAdapter(dm, dm.httpClient)
        }
        single<RemoteFileUploaderPort> {
            val transport = get<RemoteTransportPort>()
            RemoteFileUploaderPort { remotePath, content ->
                transport.uploadFile(remotePath, content)
            }
        }
        single { TransportCliExecutor(transport = get()) }

        // 009 ToolStorePort: component-based tool manager store adapter
        single<ToolStorePort> {
            val dm = get<WslDaemonManager>()
            KotlinToolStoreAdapter(
                transport = get<RemoteTransportPort>(),
                heartbeatMonitor = get(),
                serverDirProvider = { dm.resolveActiveHome() + "/.ltirom/server/current" },
            )
        }

        single { ComponentToolsHandler(manager = get<ToolManager>()) }

        single { PlanExecutor(componentToolsHandler = get<ComponentToolsHandler>()) }

        // Durable setup-run recovery coordinator
        single {
            RecoveryCoordinator(
                transport = get<RemoteTransportPort>(),
                supervisor = get<DaemonSupervisorPort>(),
                repository = getOrNull<org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository>(),
            )
        }

        // EnvironmentCheck uses HostPrerequisitePort + ServicePreparation
        single {
            ServicePreparation(
                hostPrerequisitePort = get<HostPrerequisitePort>(),
                supervisor = get<DaemonSupervisorPort>(),
                transport = get<RemoteTransportPort>(),
                serverArtifactPort = get<ServerArtifactPort>(),
            )
        }
        single {
            EnvironmentCheck()
        }

        // 009 ToolManager: SDK Manager style component tool management
        single {
            ToolManager(
                port = get<ToolStorePort>(),
                catalog = ToolGroupCatalog,
                selectionRepository = get(),
                usageCoordinator = get(),
            )
        }

        // 009 ToolUsageCoordinator: App-scoped coordination between ROM jobs and tool removal
        single { ToolUsageCoordinator() }

        // 009 AppShutdownCoordinator: Coordinates graceful stop across all exit surfaces
        single {
            org.ide.lti.core.data.AppShutdownCoordinator(
                transport = get<RemoteTransportPort>(),
                heartbeatMonitor = getOrNull<io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor>(),
                toolManager = getOrNull<org.ide.lti.core.domain.tools.ToolManager>(),
                toolStore = getOrNull<org.ide.lti.core.domain.tools.ToolStorePort>(),
            )
        } bind org.ide.lti.core.domain.tools.AppShutdownPort::class

        single<ToolchainProvisioningService> {
            WslToolchainProvisioner(
                supervisor = get<DaemonSupervisorPort>(),
                transport = get<RemoteTransportPort>(),
                repository = get<org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository>(),
                hostPrerequisitePort = get<HostPrerequisitePort>(),
                serverArtifactPort = get<ServerArtifactPort>(),
                planExecutor = get<PlanExecutor>(),
                toolManager = get<ToolManager>(),
                heartbeatMonitor = get<io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor>(),
            )
        }
        single<TerminalLauncherPort> { DesktopTerminalLauncher() }

        single { ConnectionStateHolder() }
        single {
            io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor(
                supervisor = get<DaemonSupervisorPort>(),
                stateHolder = get(),
                transport = get<RemoteTransportPort>(),
            )
        }
        single<ToolEventBus> { DefaultToolEventBus() }
        single {
            val resolver =
                DefaultToolResolver(
                    toolsBinDir = File(System.getProperty("user.home"), "LtiRomTools/bin"),
                    allowPathFallback = true,
                )
            ProcessToolRepository(
                binaryResolver = resolver::resolve,
                environment = ProcessEnvironment(cwd = File(System.getProperty("user.home"))),
                safetyPolicy = ToolSafetyPolicy(),
                redactor = Redactor(),
            )
        }
        single {
            RemoteWslToolRepository(
                transport = get<RemoteTransportPort>(),
                eventBus = get<ToolEventBus>(),
            )
        }
        single {
            SmartToolRepository(
                localRepository = get<ProcessToolRepository>(),
                remoteWslRepository = get<RemoteWslToolRepository>(),
            )
        }
        single<ToolRepository> {
            get<SmartToolRepository>()
        }
        single<StreamingToolRepository> {
            get<SmartToolRepository>()
        }

        // Toolchain-backed domain use cases
        single { ProvisionAvbKeyUseCase(get<ToolchainProvisioningService>()) }

        // Setup & Toolchain lifecycle use cases
        single { CheckEnvironmentStatusUseCase(get<ToolchainProvisioningService>()) }
        single { TestIndividualToolUseCase(get<ToolchainProvisioningService>()) }

        // Tool Metadata Registry integration
        single { ToolMetadataRegistry }

        // Tool Family Adapters integration
        single { io.ltirom.tooling.adapters.adb.DevicesUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.fastboot.DevicesUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.aapt2.VersionUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.apktool.VersionUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.mkfserofs.ExecuteUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.lpmake.ExecuteUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.avbtool.ExtractPublicKeyUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.signapk.ExecuteUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.zipalign.AlignUseCase(get<ToolRepository>()) }
        single { io.ltirom.tooling.adapters.gh.ReleaseUseCase(get<ToolRepository>()) }
    }
