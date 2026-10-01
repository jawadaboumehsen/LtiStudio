/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.data.setup.install.ArtifactFileEntry
import org.ide.lti.core.data.setup.install.ArtifactManifest
import org.ide.lti.core.data.setup.install.ArtifactRecipeInfo
import org.ide.lti.core.data.setup.install.ArtifactSourceInfo
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.InstallOutputDescriptor
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.RestoreOutcome
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RestorePreviousTest {

    private lateinit var tempDir: File
    private lateinit var toolsDir: File
    private lateinit var artifactStore: ArtifactStore
    private lateinit var installationManager: InstallationManager
    private lateinit var preferencesDataSource: ToolchainPreferencesDataSource
    private lateinit var repository: ToolchainSetupRepositoryImpl
    private lateinit var fakeInstallationPort: FakeToolchainInstallationPort
    private lateinit var coordinator: ToolchainRestoreCoordinator

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("restore-test").toFile()
        toolsDir = File(tempDir, "LtiRomTools").apply { mkdirs() }
        artifactStore = ArtifactStore(File(toolsDir, "artifacts"))
        installationManager = InstallationManager(toolsDir)
        fakeInstallationPort = FakeToolchainInstallationPort()
        preferencesDataSource = ToolchainPreferencesDataSource(settings = MapSettings())
        repository = ToolchainSetupRepositoryImpl(preferencesDataSource)
        coordinator = ToolchainRestoreCoordinator(
            installationManager = installationManager,
            artifactStore = artifactStore,
            installationPort = fakeInstallationPort,
            selectionRepository = repository,
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `restore executes activation of previous install with no build`() = runTest {
        val (prevInstall, activeInstall) = createSampleInstalls()
        fakeInstallationPort.setActiveAndPrev(active = activeInstall, prev = prevInstall)

        var buildTriggered = false
        val outcome = coordinator.restore(distro = "Ubuntu", expectedRevision = null)

        assertIs<RestoreOutcome.Succeeded>(outcome)
        assertEquals(prevInstall, outcome.activeInstallId)
        assertFalse(buildTriggered, "Restore must not trigger a build")
        assertEquals(prevInstall, fakeInstallationPort.lastActivatedTarget)
        assertEquals(activeInstall, fakeInstallationPort.lastExpectedActive)
    }

    @Test
    fun `dialog list equals backend revert set`() = runTest {
        val (prevInstall, activeInstall) = createSampleInstalls()
        fakeInstallationPort.setActiveAndPrev(active = activeInstall, prev = prevInstall)

        val revertSet = coordinator.getRevertSet()

        assertEquals(1, revertSet.size)
        val revertingErofs = revertSet.first()
        assertEquals(ToolGroupId("erofs-utils"), revertingErofs.groupId)
        assertEquals("v1.9.0", revertingErofs.currentVersion)
        assertEquals("v1.8.0", revertingErofs.previousVersion)
        assertTrue(revertingErofs.affectedTools.contains("mkfs.erofs"))
    }

    @Test
    fun `desired set to restored only if unchanged since operations revision`() = runTest {
        val (prevInstall, activeInstall) = createSampleInstalls()
        fakeInstallationPort.setActiveAndPrev(active = activeInstall, prev = prevInstall)

        // Setup desired at revision 5
        val initialDesired = mapOf(
            ToolGroupId("erofs-utils") to ToolSelection("erofs-utils", null, ToolRef.Tag("v1.9.0")),
        )
        repository.saveSelections("Ubuntu", expectedRevision = 0L, desired = initialDesired)
        val initialRev = repository.desiredSelections("Ubuntu").revision

        // Scenario A: revision matches operation's revision -> desired updated to restored version (v1.8.0)
        val outcomeA = coordinator.restore(distro = "Ubuntu", expectedRevision = initialRev)
        assertIs<RestoreOutcome.Succeeded>(outcomeA)
        assertTrue(outcomeA.desiredUpdated)

        val updatedSelections = repository.desiredSelections("Ubuntu")
        assertEquals(initialRev + 1, updatedSelections.revision)
        val erofsDesired = updatedSelections.desired["erofs-utils"]
        assertEquals("v1.8.0", (erofsDesired?.ref as? ToolRef.Tag)?.name)

        // Scenario B: user modified desired to rev 7 after operation -> restore does NOT overwrite
        fakeInstallationPort.setActiveAndPrev(active = activeInstall, prev = prevInstall)
        repository.saveSelections(
            "Ubuntu",
            expectedRevision = updatedSelections.revision,
            desired = mapOf(
                ToolGroupId("erofs-utils") to ToolSelection("erofs-utils", null, ToolRef.Tag("v2.0.0")),
            ),
        )
        val revBeforeRestore = repository.desiredSelections("Ubuntu").revision

        val outcomeB = coordinator.restore(distro = "Ubuntu", expectedRevision = initialRev)
        assertIs<RestoreOutcome.Succeeded>(outcomeB)
        assertFalse(outcomeB.desiredUpdated, "Desired must not update when revision changed")
        assertEquals(revBeforeRestore, repository.desiredSelections("Ubuntu").revision)
        val preservedRef = repository.desiredSelections("Ubuntu").desired["erofs-utils"]?.ref
        assertEquals("v2.0.0", (preservedRef as? ToolRef.Tag)?.name)
    }

    @Test
    fun `reopen editor shows installed equals selected after restore`() = runTest {
        val (prevInstall, activeInstall) = createSampleInstalls()
        fakeInstallationPort.setActiveAndPrev(active = activeInstall, prev = prevInstall)

        repository.saveSelections(
            "Ubuntu",
            expectedRevision = 0L,
            desired = mapOf(
                ToolGroupId("erofs-utils") to ToolSelection("erofs-utils", null, ToolRef.Tag("v1.9.0")),
            ),
        )
        val rev = repository.desiredSelections("Ubuntu").revision

        coordinator.restore(distro = "Ubuntu", expectedRevision = rev)

        val desired = repository.desiredSelections("Ubuntu").desired["erofs-utils"]?.ref
        val desiredLabel = (desired as? ToolRef.Tag)?.name

        // In the restored install, erofs-utils was v1.8.0
        assertEquals("v1.8.0", desiredLabel)
    }

    private fun createSampleInstalls(): Pair<InstallId, InstallId> {
        val outputs = ToolGroupCatalog.allOutputs.map {
            InstallOutputDescriptor(it.toolId, it.file, it.kind.name, it.requiredForProduct)
        }

        // Common artifacts for non-erofs groups
        val commonGroups = mutableMapOf<String, String>()
        for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
            if (group.id.value == "erofs-utils") continue
            val art = publishFakeArtifact(group.id.value, "v1.0.0")
            commonGroups[group.id.value] = art.artifactId
        }

        // Install 1 (previous): erofs v1.8.0
        val artErofsPrev = publishFakeArtifact("erofs-utils", "v1.8.0")
        val prevGroups = commonGroups + ("erofs-utils" to artErofsPrev.artifactId)
        val prevCandidate = installationManager.assembleCandidate("Ubuntu", "x86_64", prevGroups, outputs)

        // Install 2 (active): erofs v1.9.0
        val artErofsActive = publishFakeArtifact("erofs-utils", "v1.9.0")
        val activeGroups = commonGroups + ("erofs-utils" to artErofsActive.artifactId)
        val activeCandidate = installationManager.assembleCandidate("Ubuntu", "x86_64", activeGroups, outputs)

        return Pair(InstallId(prevCandidate.installId), InstallId(activeCandidate.installId))
    }

    private fun publishFakeArtifact(group: String, tag: String): ArtifactManifest {
        val candidateDir = Files.createTempDirectory("art-$group").toFile()
        val binDir = File(candidateDir, "bin").apply { mkdirs() }
        val dummyTool = File(binDir, if (group == "erofs-utils") "mkfs.erofs" else "dummy")
        dummyTool.writeText("#!/bin/sh\necho $tag")

        val files = listOf(ArtifactFileEntry("bin/${dummyTool.name}", "sha-$tag", "755"))
        val template = ArtifactManifest(
            schema = 1,
            group = group,
            artifactId = "",
            fingerprint = "fp-$group-$tag",
            source = ArtifactSourceInfo(kind = "git", repoUrl = null, commit = "commit-$tag", version = tag),
            recipe = ArtifactRecipeInfo(type = "recipe", revision = 1),
            files = files,
            outputs = listOf(dummyTool.name),
            verifiedAt = System.currentTimeMillis(),
        )
        return artifactStore.publish(group, candidateDir, template, "inst-1")
    }

    private class FakeToolchainInstallationPort : ToolchainInstallationPort {
        var active: InstallId? = null
        var prev: InstallId? = null
        var lastActivatedTarget: InstallId? = null
        var lastExpectedActive: InstallId? = null

        fun setActiveAndPrev(active: InstallId, prev: InstallId) {
            this.active = active
            this.prev = prev
        }

        override suspend fun state(): InstalledToolchain =
            InstalledToolchain(activeInstallId = active, previousInstallId = prev)

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId =
            InstallId("assembled-candidate")

        override suspend fun activate(request: ActivationRequest): ActivationOutcome {
            lastActivatedTarget = request.targetInstallId
            lastExpectedActive = request.expectedActiveInstallId
            prev = active
            active = request.targetInstallId
            return ActivationOutcome.Committed(request.targetInstallId)
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null
        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }
}
