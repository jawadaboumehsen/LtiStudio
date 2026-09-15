/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.AuthorProject
import org.ide.lti.core.domain.plugin.AuthorProjectTrustPort
import org.ide.lti.core.domain.plugin.AuthorProjectTrustUseCase
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFailure
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.InstallPluginUseCase
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.MarketplaceIndexDocument
import org.ide.lti.core.domain.plugin.MarketplaceIndexPort
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.PluginLifecycleUseCase
import org.ide.lti.core.domain.plugin.TrustApproval
import org.ide.lti.core.domain.plugin.TrustCheck
import org.ide.lti.core.domain.plugin.TrustDecision
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PluginManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeInstallPort: FakePluginInstallPort
    private lateinit var fakeMarketplacePort: FakeMarketplaceIndexPort
    private lateinit var fakeAuthorPort: FakeAuthorProjectTrustPort
    private lateinit var viewModel: PluginManagerViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeInstallPort = FakePluginInstallPort()
        fakeMarketplacePort = FakeMarketplaceIndexPort()
        fakeAuthorPort = FakeAuthorProjectTrustPort()

        val installUseCase = InstallPluginUseCase(fakeInstallPort)
        val lifecycleUseCase = PluginLifecycleUseCase(fakeInstallPort)
        val authorUseCase = AuthorProjectTrustUseCase(fakeAuthorPort)

        viewModel = PluginManagerViewModel(
            installPluginUseCase = installUseCase,
            pluginLifecycleUseCase = lifecycleUseCase,
            authorProjectTrustUseCase = authorUseCase,
            marketplaceIndexPort = fakeMarketplacePort,
            pluginInstallPort = fakeInstallPort,
            initialDestination = "installed",
            initialWorkspaceId = "ws-test",
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialDestinationAndWorkspaceArePreserved() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(PluginDestination.INSTALLED, state.destination)
        assertEquals("ws-test", state.activeWorkspaceId)
    }

    @Test
    fun selectDestinationUpdatesState() = runTest(testDispatcher) {
        viewModel.selectDestination(PluginDestination.MARKETPLACE)
        assertEquals(PluginDestination.MARKETPLACE, viewModel.uiState.value.destination)

        viewModel.selectDestination(PluginDestination.IMPORT)
        assertEquals(PluginDestination.IMPORT, viewModel.uiState.value.destination)

        viewModel.selectDestination(PluginDestination.AUTHOR_TOOLS)
        assertEquals(PluginDestination.AUTHOR_TOOLS, viewModel.uiState.value.destination)
    }

    @Test
    fun enableAndDisableWorkspaceUpdatesInstalledRecord() = runTest(testDispatcher) {
        val record = InstalledRecord(
            identity = PackageIdentity("org.ide.lti", "sample-mod", "1.0.0", "digest123"),
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        )
        fakeInstallPort.records.add(record)
        viewModel.refreshInstalled()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.enableForWorkspace(record, "ws-test")
        testDispatcher.scheduler.advanceUntilIdle()
        val updated = fakeInstallPort.records.first { it.identity == record.identity }
        assertTrue(updated.enabledInWorkspaces.contains("ws-test"))

        viewModel.disableForWorkspace(updated, "ws-test")
        testDispatcher.scheduler.advanceUntilIdle()
        val disabled = fakeInstallPort.records.first { it.identity == record.identity }
        assertFalse(disabled.enabledInWorkspaces.contains("ws-test"))
    }

    @Test
    fun uninstallRefusesWhenReferencedByWorkspace() = runTest(testDispatcher) {
        val record = InstalledRecord(
            identity = PackageIdentity("org.ide.lti", "sample-mod", "1.0.0", "digest123"),
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = listOf("ws-test"),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        )
        fakeInstallPort.records.add(record)
        viewModel.refreshInstalled()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uninstallPackage(record)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not be removed from repository because it is referenced by ws-test
        assertEquals(1, fakeInstallPort.records.size)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun legacyShellScriptImportRoutesToUnsupportedFormatPanel() = runTest(testDispatcher) {
        viewModel.inspectImportPath("C:/legacy/customize.sh")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLegacyUnsupportedFormat)
        assertEquals("customize.sh", state.legacyFormatName)
        assertEquals(0, fakeInstallPort.quarantinedCalls.size)
    }

    @Test
    fun marketplaceConfigRejectsInsecureHttpUrl() = runTest(testDispatcher) {
        viewModel.configureMarketplaceSource("Insecure", "http://insecure.example.com/index.json")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<IndexFetchResult.Failed>(state.marketplaceFetchResult)
        assertEquals(IndexFailure.InsecureSource, (state.marketplaceFetchResult as IndexFetchResult.Failed).reason)
    }

    @Test
    fun marketplaceOfflineModePreservesObservedAtAge() = runTest(testDispatcher) {
        val observedAt = 1700000000000L
        fakeMarketplacePort.stubbedResult = IndexFetchResult.Cached(
            doc = MarketplaceIndexDocument(
                entries = listOf(
                    IndexEntry(
                        publisher = "org.ide.lti",
                        id = "offline-mod",
                        version = "2.0.0",
                        sdkApiRange = "1.0..2.0",
                        targetSummary = "Summary",
                        packageUrl = "https://example.com/pkg.lti-mod.zip",
                        contentDigest = "digest-offline",
                        signatureIdentity = "LtiDev",
                    ),
                ),
            ),
            observedAtEpochMs = observedAt,
        )

        viewModel.configureMarketplaceSource("Repo", "https://example.com/index.json")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertIs<IndexFetchResult.Cached>(state.marketplaceFetchResult)
        assertEquals(observedAt, (state.marketplaceFetchResult as IndexFetchResult.Cached).observedAtEpochMs)
        assertEquals(1, state.marketplaceEntries.size)
    }

    @Test
    fun authorProjectTrustCheckAndTaskExecution() = runTest(testDispatcher) {
        val path = "C:/projects/test-mod"
        viewModel.checkAuthorProject(path)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TrustCheck.NotApproved, viewModel.uiState.value.authorTrustCheck)

        viewModel.approveAuthorProject()
        testDispatcher.scheduler.advanceUntilIdle()
        assertIs<TrustCheck.Approved>(viewModel.uiState.value.authorTrustCheck)

        viewModel.runAuthorTask("testMod")
        testDispatcher.scheduler.advanceUntilIdle()
        assertIs<AuthorTaskResult.Success>(viewModel.uiState.value.authorTaskResult)
    }

    private class FakePluginInstallPort : PluginInstallPort {
        val records = mutableListOf<InstalledRecord>()
        val quarantinedCalls = mutableListOf<String>()

        override suspend fun quarantine(sourceLabel: String): Path? {
            quarantinedCalls.add(sourceLabel)
            return Path.of(sourceLabel)
        }

        override suspend fun inspect(quarantined: Path): PackageInspection = PackageInspection(
            identity = PackageIdentity("test.pub", "test-pkg", "1.0.0", "digest-test"),
            hasSignature = true,
            report = ValidationReport(),
        )

        override suspend fun promote(quarantined: Path, identity: PackageIdentity): Boolean = true

        override suspend fun installed(): List<InstalledRecord> = records.toList()

        override suspend fun remove(identity: PackageIdentity): Boolean = records.removeAll { it.identity == identity }

        override suspend fun updateRecord(record: InstalledRecord): Boolean {
            val idx = records.indexOfFirst { it.identity == record.identity }
            if (idx >= 0) {
                records[idx] = record
            } else {
                records.add(record)
            }
            return true
        }
    }

    private class FakeMarketplaceIndexPort : MarketplaceIndexPort {
        var stubbedResult: IndexFetchResult = IndexFetchResult.Fetched(
            doc = MarketplaceIndexDocument(entries = emptyList()),
            observedAtEpochMs = System.currentTimeMillis(),
        )

        override suspend fun fetch(source: IndexSource, allowCache: Boolean): IndexFetchResult = stubbedResult
    }

    private class FakeAuthorProjectTrustPort : AuthorProjectTrustPort {
        private var storedApproval: TrustApproval? = null

        override suspend fun fingerprint(projectPath: String): String = "fingerprint-123"

        override suspend fun approval(projectPath: String): TrustApproval? = storedApproval

        override suspend fun approve(project: AuthorProject): TrustApproval {
            val approval = TrustApproval(project.path, "fingerprint-123", System.currentTimeMillis())
            storedApproval = approval
            return approval
        }

        override suspend fun revoke(projectPath: String) {
            storedApproval = null
        }

        override suspend fun runTask(project: AuthorProject, task: String, timeoutMs: Long): AuthorTaskResult =
            AuthorTaskResult.Success(stdout = "Task $task succeeded", stderr = "")
    }
}
