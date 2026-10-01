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
import org.ide.lti.core.data.setup.ServerArtifactAdapter
import org.ide.lti.core.data.setup.WslHostPrerequisiteProbe
import org.ide.lti.core.data.setup.WslToolchainProvisioner
import org.ide.lti.core.data.setup.doctor.CompositeSystemDoctorEngine
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.RemoteFileUploaderPort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.doctor.RunSystemDiagnosticsUseCase
import org.ide.lti.core.domain.setup.doctor.SystemDoctorPort
import org.ide.lti.core.domain.usecase.setup.CheckEnvironmentStatusUseCase
import org.ide.lti.core.domain.usecase.setup.TestIndividualToolUseCase
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Composition Root DI Module for the tooling subsystem.
 * Exposes [ToolRepository], [StreamingToolRepository], [DaemonSupervisorPort], [ToolEventBus],
 * [SystemDoctorPort], [ToolchainProvisioningService], [ToolMetadataRegistry], and adapter families
 * for dependency injection across presentation components.
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
    single<ToolchainProvisioningService> {
        WslToolchainProvisioner(
            supervisor = get<DaemonSupervisorPort>(),
            transport = get<RemoteTransportPort>(),
            repository = get<org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository>(),
            toolPublicationPort = getOrNull<ToolPublicationPort>(),
            hostPrerequisitePort = get<HostPrerequisitePort>(),
            serverArtifactPort = get<ServerArtifactPort>(),
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
