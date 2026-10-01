package org.ide.lti.desktop.di

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.shared.di.KoinModules
import org.ide.lti.shared.di.toolingModule
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class ToolingDiResolutionTest {

    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(toolingModule)
            modules(KoinModules.allModules)
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testToolchainProvisioningServiceResolves() {
        val koin = org.koin.core.context.GlobalContext.get()
        val service: ToolchainProvisioningService? = koin.getOrNull()
        println("ToolchainProvisioningService resolved: $service")
        assertNotNull(service, "ToolchainProvisioningService must resolve from toolingModule")
    }

    @Test
    fun testToolingModuleComponentsResolve() {
        val koin = org.koin.core.context.GlobalContext.get()
        assertNotNull(koin.getOrNull<org.ide.lti.core.domain.ports.WslPresencePort>())
        assertNotNull(koin.getOrNull<io.ltirom.tooling.core.ports.DaemonSupervisorPort>())
        assertNotNull(koin.getOrNull<io.ltirom.tooling.core.ports.RemoteTransportPort>())
        assertNotNull(koin.getOrNull<org.ide.lti.core.domain.setup.doctor.SystemDoctorPort>())
        assertNotNull(koin.getOrNull<ToolchainProvisioningService>())
    }
}
