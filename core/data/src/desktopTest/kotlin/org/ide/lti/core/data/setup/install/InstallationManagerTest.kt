package org.ide.lti.core.data.setup.install

import org.ide.lti.core.data.setup.FakeWslCliExecutor
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class InstallationManagerTest {

    private lateinit var cli: FakeWslCliExecutor
    private lateinit var manager: InstallationManager

    @BeforeTest
    fun setUp() {
        cli = FakeWslCliExecutor()
        manager = InstallationManager(cli, "Ubuntu")
    }

    @Test
    fun `installId calculation is deterministic and changes with content`() {
        val groups1 = mapOf("erofs-utils" to "art-1", "android-tools" to "art-2")
        val outputs1 = listOf(
            InstallOutputDescriptor("mkfs.erofs", "mkfs.erofs"),
            InstallOutputDescriptor("fastboot", "fastboot"),
        )

        val id1 = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 1,
        )

        val id2 = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 1,
        )

        assertEquals(id1, id2, "Identical inputs must yield identical installId")
        assertTrue(id1.startsWith("i-"), "installId must start with i-")

        val idDiffRevision = InstallationManager.computeInstallId(
            schema = 1,
            distro = "Ubuntu",
            arch = "x86_64",
            groups = groups1,
            outputs = outputs1,
            catalogRevision = 2,
        )
        assertNotEquals(id1, idDiffRevision, "Changed catalogRevision must yield new installId")
    }

    @Test
    fun `assembleCandidate throws when a required catalog group is missing`() {
        val incompleteGroups = mapOf("erofs-utils" to "art-1")
        val outputs = listOf(InstallOutputDescriptor("mkfs.erofs", "mkfs.erofs"))

        assertFailsWith<IllegalArgumentException> {
            manager.assembleCandidate(
                distro = "Ubuntu",
                arch = "x86_64",
                groups = incompleteGroups,
                outputs = outputs,
                catalogRevision = 1,
            )
        }
    }

    private fun assertTrue(b: Boolean, msg: String) {
        kotlin.test.assertTrue(b, msg)
    }
}
