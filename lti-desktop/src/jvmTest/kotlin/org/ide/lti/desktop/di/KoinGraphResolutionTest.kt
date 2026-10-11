package org.ide.lti.desktop.di

import org.ide.lti.core.data.AppShutdownCoordinator
import org.ide.lti.core.data.setup.check.EnvironmentCheck
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.domain.pipeline.PipelineOrchestrator
import org.ide.lti.core.domain.plugin.AuthorProjectTrustPort
import org.ide.lti.core.domain.plugin.AuthorProjectTrustUseCase
import org.ide.lti.core.domain.plugin.InstallPluginUseCase
import org.ide.lti.core.domain.plugin.MarketplaceIndexPort
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.PluginLifecycleUseCase
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.ServerArtifactPort
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.tools.AppShutdownPort
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.usecase.build.StartPipelineRunUseCase
import org.ide.lti.core.domain.usecase.workspace.CreateWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.SaveConfigurationSnapshotUseCase
import org.ide.lti.shared.di.KoinModules
import org.ide.lti.shared.di.toolingModule
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Resolves every definition the desktop app reaches at startup and from the Setup / Workspace
 * screens. Catches wiring regressions that unit tests with fakes cannot see, e.g. a `singleOf`
 * whose constructor has a defaulted parameter Koin then tries (and fails) to resolve.
 */
class KoinGraphResolutionTest {
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        stopKoin()
        koin =
            startKoin {
                modules(toolingModule)
                modules(KoinModules.allModules)
            }.koin
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun portsAndRepositoriesResolve() {
        assertNotNull(koin.get<HostPrerequisitePort>())
        assertNotNull(koin.get<ServerArtifactPort>())
        assertNotNull(koin.get<EnvironmentReadinessPort>())
        assertNotNull(koin.get<io.ltirom.tooling.client.wsl.TransportCliExecutor>())
        assertNotNull(koin.get<WorkspaceProvisioningPort>())
        assertNotNull(koin.get<PipelineExecutionPort>())
        assertNotNull(koin.get<SnapshotRepository>())
        assertNotNull(koin.get<RunRepository>())
        assertNotNull(koin.get<ToolchainProvisioningService>())
        assertNotNull(koin.get<PluginInstallPort>())
        assertNotNull(koin.get<MarketplaceIndexPort>())
        assertNotNull(koin.get<AuthorProjectTrustPort>())
        assertNotNull(koin.get<PluginCatalogPort>())
        assertNotNull(koin.get<org.ide.lti.core.domain.ports.WorkspaceAdmissionPort>())
        assertNotNull(koin.get<org.ide.lti.core.domain.ports.SnapshotMaterializerPort>())
        val toolStore = koin.get<ToolStorePort>()
        assertNotNull(toolStore)
        assertTrue(toolStore is KotlinToolStoreAdapter)
        assertNotNull(koin.get<ToolManager>())
        assertNotNull(koin.get<ToolUsageCoordinator>())
        assertNotNull(koin.get<AppShutdownPort>())
        assertNotNull(koin.get<AppShutdownCoordinator>())
        assertNotNull(koin.get<TerminalLauncherPort>())
        assertNotNull(koin.get<EnvironmentCheck>())
        val planExecutor = koin.get<org.ide.lti.core.data.setup.execute.PlanExecutor>()
        assertNotNull(planExecutor.componentToolsHandler, "PlanExecutor must use the component tools handler")

        listOf(
            "org.ide.lti.core.data.setup.check.EnvironmentCheck",
            "org.ide.lti.core.data.setup.check.ServicePreparation",
            "org.ide.lti.core.data.setup.execute.PlanExecutor",
            "org.ide.lti.core.data.setup.execute.handlers.ComponentToolsHandler",
            "org.ide.lti.core.data.setup.RecoveryCoordinator",
        ).forEach { className ->
            val clazz = Class.forName(className).kotlin
            assertNotNull(koin.get<Any>(clazz, qualifier = null, parameters = null), className)
        }
    }

    @Test
    fun workspaceUseCasesResolve() {
        assertNotNull(koin.get<CreateWorkspaceUseCase>())
        assertNotNull(koin.get<SaveConfigurationSnapshotUseCase>())
        assertNotNull(koin.get<VendoredFilePort>())
        assertNotNull(koin.get<PipelineOrchestrator>())
        assertNotNull(koin.get<StartPipelineRunUseCase>())
        assertNotNull(koin.get<ObserveRunUseCase>())
        assertNotNull(koin.get<InstallPluginUseCase>())
        assertNotNull(koin.get<PluginLifecycleUseCase>())
        assertNotNull(koin.get<AuthorProjectTrustUseCase>())
    }

    @Test
    fun screenViewModelsResolve() {
        // Feature modules are runtime-only dependencies of lti-desktop (via lti-shared), so the
        // ViewModels are resolved by name; each must construct with the production graph.
        SCREEN_VIEW_MODELS.forEach { className ->
            val clazz = Class.forName(className).kotlin
            assertNotNull(koin.get<Any>(clazz, qualifier = null, parameters = null), className)
        }
    }

    @Test
    fun setupViewModelWiredWithComponentTools() {
        val vmClazz = Class.forName("org.ide.lti.feature.setup.SetupViewModel").kotlin
        val vm = koin.get<Any>(vmClazz, qualifier = null, parameters = null)
        assertNotNull(vm, "SetupViewModel must resolve from DI")
    }

    private companion object {
        val SCREEN_VIEW_MODELS =
            listOf(
                "org.ide.lti.feature.setup.SetupViewModel",
                "org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationViewModel",
                "org.ide.lti.feature.workspace.creation.CreateWorkspaceViewModel",
                "org.ide.lti.feature.workspace.panels.run.BuildRunViewModel",
                "org.ide.lti.feature.configuration.ConfigurationViewModel",
                "org.ide.lti.feature.plugins.PluginManagerViewModel",
            )
    }
}
