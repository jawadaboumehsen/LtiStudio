package org.ide.lti.core.data.setup

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectionRevisionTest {

    private lateinit var settings: MapSettings
    private lateinit var preferencesDataSource: ToolchainPreferencesDataSource
    private lateinit var repository: ToolchainSetupRepositoryImpl

    @BeforeTest
    fun setUp() {
        settings = MapSettings()
        preferencesDataSource = ToolchainPreferencesDataSource(settings = settings)
        repository = ToolchainSetupRepositoryImpl(preferencesDataSource)
    }

    @Test
    fun `save with stale revision is rejected`() = runTest {
        val distro = "Ubuntu-24.04"
        val initial = repository.desiredSelections(distro)
        assertEquals(0L, initial.revision)
        assertTrue(initial.desired.isEmpty())

        val selection1 = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Tag("v1.0"),
        )
        val saveResult1 = repository.saveSelections(
            distro = distro,
            expectedRevision = 0L,
            desired = mapOf(ToolGroupId("android-tools") to selection1),
        )
        assertTrue(saveResult1.isSuccess)
        assertEquals(1L, saveResult1.getOrThrow())

        // Attempting to save with stale expectedRevision 0L must fail
        val selection2 = ToolSelection(
            group = "android-tools",
            repoUrl = "https://github.com/example/repo.git",
            ref = ToolRef.Tag("v2.0"),
        )
        val staleResult = repository.saveSelections(
            distro = distro,
            expectedRevision = 0L,
            desired = mapOf(ToolGroupId("android-tools") to selection2),
        )
        assertTrue(staleResult.isFailure, "Stale revision must be rejected")
        assertTrue(staleResult.exceptionOrNull()?.message?.contains("Stale selection revision") == true)

        // Saving with expectedRevision 1L succeeds
        val saveResult2 = repository.saveSelections(
            distro = distro,
            expectedRevision = 1L,
            desired = mapOf(ToolGroupId("android-tools") to selection2),
        )
        assertTrue(saveResult2.isSuccess)
        assertEquals(2L, saveResult2.getOrThrow())

        val updated = repository.desiredSelections(distro)
        assertEquals(2L, updated.revision)
        assertEquals(selection2, updated.desired["android-tools"])
    }

    @Test
    fun `two distros with different selections stay isolated`() = runTest {
        val ubuntu = "Ubuntu-24.04"
        val debian = "Debian-12"

        val ubuntuSelection = ToolSelection(
            group = "erofs-utils",
            repoUrl = "https://github.com/example/erofs.git",
            ref = ToolRef.Tag("v1.7.1"),
        )
        val debianSelection = ToolSelection(
            group = "erofs-utils",
            repoUrl = "https://github.com/example/erofs.git",
            ref = ToolRef.Tag("v1.6.0"),
        )

        val uResult = repository.saveSelections(
            distro = ubuntu,
            expectedRevision = 0L,
            desired = mapOf(ToolGroupId("erofs-utils") to ubuntuSelection),
        )
        assertTrue(uResult.isSuccess)

        val dResult = repository.saveSelections(
            distro = debian,
            expectedRevision = 0L,
            desired = mapOf(ToolGroupId("erofs-utils") to debianSelection),
        )
        assertTrue(dResult.isSuccess)

        // Verify each distro has its own selections and revision
        val uSelections = repository.desiredSelections(ubuntu)
        val dSelections = repository.desiredSelections(debian)

        assertEquals(1L, uSelections.revision)
        assertEquals(ubuntuSelection, uSelections.desired["erofs-utils"])

        assertEquals(1L, dSelections.revision)
        assertEquals(debianSelection, dSelections.desired["erofs-utils"])

        // Updating Ubuntu never mutates Debian
        val ubuntuUpdate = ToolSelection(
            group = "erofs-utils",
            repoUrl = "https://github.com/example/erofs.git",
            ref = ToolRef.Tag("v1.8.0"),
        )
        val uUpdateResult = repository.saveSelections(
            distro = ubuntu,
            expectedRevision = 1L,
            desired = mapOf(ToolGroupId("erofs-utils") to ubuntuUpdate),
        )
        assertTrue(uUpdateResult.isSuccess)

        val uSelectionsAfter = repository.desiredSelections(ubuntu)
        val dSelectionsAfter = repository.desiredSelections(debian)

        assertEquals(2L, uSelectionsAfter.revision)
        assertEquals(ubuntuUpdate, uSelectionsAfter.desired["erofs-utils"])

        assertEquals(1L, dSelectionsAfter.revision)
        assertEquals(debianSelection, dSelectionsAfter.desired["erofs-utils"])
    }

    @Test
    fun `desired selections remain preserved across build and switch`() = runTest {
        val distro = "Ubuntu-24.04"
        val selB = ToolSelection(
            group = "apktool",
            repoUrl = "https://github.com/example/apktool.git",
            ref = ToolRef.Tag("v2.9.0"),
        )
        val rev1 = repository.saveSelections(
            distro = distro,
            expectedRevision = 0L,
            desired = mapOf(ToolGroupId("apktool") to selB),
        ).getOrThrow()
        assertEquals(1L, rev1)

        // User changes desired to C while build B is in flight
        val selC = ToolSelection(
            group = "apktool",
            repoUrl = "https://github.com/example/apktool.git",
            ref = ToolRef.Tag("v2.10.0"),
        )
        val rev2 = repository.saveSelections(
            distro = distro,
            expectedRevision = rev1,
            desired = mapOf(ToolGroupId("apktool") to selC),
        ).getOrThrow()
        assertEquals(2L, rev2)

        // Desired remains C at revision 2
        val currentDesired = repository.desiredSelections(distro)
        assertEquals(2L, currentDesired.revision)
        assertEquals(selC, currentDesired.desired["apktool"])
    }
}
