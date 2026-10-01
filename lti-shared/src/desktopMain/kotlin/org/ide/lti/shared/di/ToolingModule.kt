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
import io.ltirom.tooling.client.wsl.WslCliExecutor
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
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.WslHostPrerequisiteProbe
import org.ide.lti.core.data.setup.WslToolchainProvisioner
import org.ide.lti.core.data.setup.check.EnvironmentCheck
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.doctor.CompositeSystemDoctorEngine
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.ToolchainRestoreCoordinator
import org.ide.lti.core.data.setup.execute.handlers.SwitchToolchainHandler
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.data.setup.install.WslToolchainInstallationAdapter
import org.ide.lti.core.data.setup.recipe.BuildRecipeDispatcher
import org.ide.lti.core.data.setup.source.GitSourceResolver
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.RemoteFileUploaderPort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.domain.setup.RestoreCoordinatorPort
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.doctor.RunSystemDiagnosticsUseCase
import org.ide.lti.core.domain.setup.doctor.SystemDoctorPort
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.domain.usecase.setup.CheckEnvironmentStatusUseCase
import org.ide.lti.core.domain.usecase.setup.TestIndividualToolUseCase
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Composition Root DI Module for the tooling subsystem (008 layout, always active).
 *
 * Required bindings:
 * - [SourceResolverPort] → [GitSourceResolver] (live network resolution)
 * - [ArtifactStore] (WSL-side, rootDir = null)
 * - [InstallationManager] (WSL-side, toolsDir = null)
 * - [ToolchainInstallationPort] → [WslToolchainInstallationAdapter]
 * - [SwitchToolchainHandler] (registered into [PlanExecutor])
 * - [ToolchainRestoreCoordinator] → [RestoreCoordinatorPort]
 *
 * [ToolPublicationPort] is required (not optional): missing it is a wiring error caught by
 * [KoinGraphResolutionTest].
 */
public val toolingModule = module {
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
    single<SystemDoctorPort> {
        CompositeSystemDoctorEngine()
    }

    // ------------------------------------------------------------------
    // 008 required bindings — all four must resolve; getOrNull is banned
    // ------------------------------------------------------------------

    // Git ref resolver for tags, branches, and commits (live network calls via WslCliExecutor)
    single {
        val dm = get<WslDaemonManager>()
        GitSourceResolver(
            cli = WslCliExecutor(),
            gitCache = get<GitCache>(),
            distroProvider = { dm.activeDistro ?: "Ubuntu" },
        )
    } bind SourceResolverPort::class

    // Git mirror cache for commit resolution and layout checks
    single {
        GitCache(cli = WslCliExecutor())
    }

    // Content-addressable artifact store (WSL-side: rootDir = null, uses WslCliExecutor)
    single {
        val dm = get<WslDaemonManager>()
        ArtifactStore(
            cli = WslCliExecutor(),
            distroProvider = { dm.activeDistro ?: "Ubuntu" },
        )
    }

    // Installation manager (WSL-side: toolsDir = null)
    single {
        val dm = get<WslDaemonManager>()
        InstallationManager(
            cli = WslCliExecutor(),
            distroProvider = { dm.activeDistro ?: "Ubuntu" },
        )
    }

    // Toolchain installation port: assembles/activates/queries over RemoteTransportPort + InstallationManager
    single<ToolchainInstallationPort> {
        val dm = get<WslDaemonManager>()
        WslToolchainInstallationAdapter(
            installationManager = get<InstallationManager>(),
            transport = get<RemoteTransportPort>(),
            distroProvider = { dm.activeDistro ?: "Ubuntu" },
        )
    }

    // Switch handler: artifact build + activation orchestration
    single {
        SwitchToolchainHandler(
            artifactStore = get<ArtifactStore>(),
            installationManager = get<InstallationManager>(),
            installationPort = get<ToolchainInstallationPort>(),
            repository = get<org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository>(),
        )
    }

    // Recipe dispatcher for build recipes
    single { BuildRecipeDispatcher() }

    // Tool verifier
    single { ToolVerifier(WslCliExecutor()) }

    // Plan executor with SwitchToolchainHandler registered — KoinGraphResolutionTest asserts non-null
    single {
        PlanExecutor(
            switchToolchainHandler = get<SwitchToolchainHandler>(),
        )
    }

    // Restore coordinator (rollback to previous install without rebuilding)
    single {
        ToolchainRestoreCoordinator(
            installationManager = get<InstallationManager>(),
            artifactStore = get<ArtifactStore>(),
            installationPort = get<ToolchainInstallationPort>(),
            selectionRepository = get(),
        )
    } bind RestoreCoordinatorPort::class

    // Setup-run recovery coordinator (separate from ToolchainRestoreCoordinator)
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

    // WslToolchainProvisioner: ToolPublicationPort is REQUIRED (not optional)
    single<ToolchainProvisioningService> {
        WslToolchainProvisioner(
            supervisor = get<DaemonSupervisorPort>(),
            transport = get<RemoteTransportPort>(),
            repository = get<org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository>(),
            toolPublicationPort = get<ToolPublicationPort>(),
            hostPrerequisitePort = get<HostPrerequisitePort>(),
            serverArtifactPort = get<ServerArtifactPort>(),
            planExecutor = get<PlanExecutor>(),
            sourceResolver = get<SourceResolverPort>(),
        )
    }
    single<TerminalLauncherPort> { DesktopTerminalLauncher() }

    single { ConnectionStateHolder() }
    single<ToolEventBus> { DefaultToolEventBus() }
    single {
        val resolver = DefaultToolResolver(
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
    single { RunSystemDiagnosticsUseCase(get<SystemDoctorPort>()) }

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
