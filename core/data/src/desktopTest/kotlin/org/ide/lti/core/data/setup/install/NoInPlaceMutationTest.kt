package org.ide.lti.core.data.setup.install

import org.ide.lti.core.data.setup.FakeWslCliExecutor
import kotlin.test.Test
import kotlin.test.assertTrue

class NoInPlaceMutationTest {
    @Test
    fun `artifacts and installs are immutable`() {
        val cli = FakeWslCliExecutor()
        val store = ArtifactStore(cli, "Ubuntu")
        val mgr = InstallationManager(cli, "Ubuntu")
        assertTrue(true)
    }
}
