package org.ide.lti.core.data.setup.install

import org.ide.lti.core.data.setup.FakeWslCliExecutor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CleanupTest {
    private lateinit var cli: FakeWslCliExecutor
    private lateinit var manager: InstallationManager

    @BeforeTest
    fun setUp() {
        cli = FakeWslCliExecutor()
        manager = InstallationManager(cli, "Ubuntu")
    }

    @Test
    fun `cleanup deletes unreferenced installs and artifacts`() {
        cli.existingPaths.add("/home/lti/LtiRomTools/installs/i-1")
        cli.existingPaths.add("/home/lti/LtiRomTools/installs/i-old")
        val res = manager.cleanup(activeInstallId = "i-1", previousInstallId = null)
        assertEquals(listOf("i-old"), res.deletedInstalls)
        assertTrue(res.deletedArtifacts.isEmpty())
    }
}
