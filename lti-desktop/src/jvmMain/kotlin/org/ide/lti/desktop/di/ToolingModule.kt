package org.ide.lti.desktop.di

import io.ltirom.tooling.client.RemoteWslToolRepository
import io.ltirom.tooling.client.SmartToolRepository
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.resilience.ConnectionStateHolder
import io.ltirom.tooling.client.setup.WslToolchainProvisioner
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.core.DefaultToolResolver
import io.ltirom.tooling.core.ProcessEnvironment
import io.ltirom.tooling.core.ProcessToolRepository
import io.ltirom.tooling.core.Redactor
import io.ltirom.tooling.core.StreamingToolRepository
import io.ltirom.tooling.core.ToolRepository
import io.ltirom.tooling.core.ToolSafetyPolicy
import io.ltirom.tooling.core.events.DefaultToolEventBus
import io.ltirom.tooling.core.events.ToolEventBus
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.client.setup.EnvironmentProbe
import io.ltirom.tooling.client.setup.doctor.CompositeSystemDoctorEngine
import io.ltirom.tooling.client.wsl.TransportCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.client.wsl.WslPresenceAdapter
import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.domain.ports.EnvironmentProbePort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.WslPresencePort
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.doctor.AutoRemediateEnvironmentUseCase
import org.ide.lti.core.domain.setup.doctor.RunSystemDiagnosticsUseCase
import org.ide.lti.core.domain.setup.doctor.SystemDoctorPort
import org.ide.lti.core.domain.usecase.target.ProvisionAvbKeyUseCase
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.File

/**
 * Composition Root DI Module for the tooling subsystem.
 * Exposes [ToolRepository], [StreamingToolRepository], [DaemonSupervisorPort], [ToolEventBus],
 * [SystemDoctorPort], and [ToolchainProvisioningService] for dependency injection across presentation components.
 */
public val toolingModule = module {
    single<WslPresencePort> { WslPresenceAdapter() }
    single { WslDaemonManager() } bind DaemonSupervisorPort::class
    single<RemoteTransportPort> {
        val dm = get<WslDaemonManager>()
        KtorRemoteTransportAdapter(dm, dm.httpClient)
    }
    single { TransportCliExecutor(transport = get()) }
    single<EnvironmentProbePort> {
        EnvironmentProbe(
            detector = WslEnvironmentDetector(),
            supervisor = get<DaemonSupervisorPort>(),
            transport = get<RemoteTransportPort>(),
            toolPublicationPort = getOrNull<ToolPublicationPort>(),
        )
    }
    single<SystemDoctorPort> {
        CompositeSystemDoctorEngine()
    }
    single<ToolchainProvisioningService> {
        WslToolchainProvisioner(
            supervisor = get<DaemonSupervisorPort>(),
            transport = get<RemoteTransportPort>(),
            doctorPort = get<SystemDoctorPort>(),
            repository = getOrNull(),
            toolPublicationPort = getOrNull<ToolPublicationPort>(),
        )
    }

    // TODO: DaemonHeartbeatMonitor (tool:client/resilience) actively polls and updates this holder,
    // but nothing constructs/starts one here yet, and nothing reads this holder's state either --
    // wiring it up means both starting the monitor AND adding a real UI consumer of ConnectionState.
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
            eventBus = get<ToolEventBus>()
        )
    }
    single {
        SmartToolRepository(
            localRepository = get<ProcessToolRepository>(),
            remoteWslRepository = get<RemoteWslToolRepository>()
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
    single { AutoRemediateEnvironmentUseCase(get<SystemDoctorPort>()) }

    // Setup & Toolchain lifecycle use cases
    single { org.ide.lti.core.domain.usecase.setup.CheckEnvironmentStatusUseCase(get<ToolchainProvisioningService>()) }
    single { org.ide.lti.core.domain.usecase.setup.RunIncrementalSetupUseCase(get<ToolchainProvisioningService>()) }
    single { org.ide.lti.core.domain.usecase.setup.TestIndividualToolUseCase(get<ToolchainProvisioningService>()) }
    single { org.ide.lti.core.domain.usecase.setup.RecompileToolUseCase(get<ToolchainProvisioningService>()) }
    single { org.ide.lti.core.domain.usecase.setup.ResetToolchainSetupUseCase(get<ToolchainProvisioningService>(), getOrNull()) }
}

