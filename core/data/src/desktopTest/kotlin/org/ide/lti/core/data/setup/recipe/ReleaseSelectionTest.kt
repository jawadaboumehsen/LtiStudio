/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.recipe

import com.russhwolf.settings.MapSettings
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.handlers.SwitchToolchainHandler
import org.ide.lti.core.data.setup.install.ArtifactFileEntry
import org.ide.lti.core.data.setup.install.ArtifactManifest
import org.ide.lti.core.data.setup.install.ArtifactRecipeInfo
import org.ide.lti.core.data.setup.install.ArtifactSourceInfo
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.data.setup.source.GitSourceResolver
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleaseSelectionTest {

    private lateinit var tempDir: File
    private lateinit var toolsDir: File
    private lateinit var artifactStore: ArtifactStore
    private lateinit var installationManager: InstallationManager
    private lateinit var fakeInstallationPort: FakeInstallationPort
    private lateinit var preferencesDataSource: ToolchainPreferencesDataSource
    private lateinit var repository: ToolchainSetupRepositoryImpl
    private lateinit var fakeVerifier: ToolVerifier
    private lateinit var gitCache: GitCache
    private lateinit var fakeCli: TestCli

    private class TestCli(var tamperedSha: Boolean = false) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands.add(command)
            return when {
                command.firstOrNull() == "sha256sum" -> {
                    val hash = if (tamperedSha) {
                        "0000000000000000000000000000000000000000000000000000000000000000"
                    } else {
                        "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409"
                    }
                    CliExecutionResult(0, "$hash  file", "")
                }
                command.firstOrNull() == "test" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "curl" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "tar" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "install" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "mv" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "rm" -> CliExecutionResult(0, "", "")
                command.firstOrNull() == "mkdir" -> CliExecutionResult(0, "", "")
                else -> {
                    val path = command.firstOrNull() ?: ""
                    val toolId = path.substringAfterLast('/').removeSuffix(".jar")
                    val accepted = ToolCapabilities.acceptedExitCodesFor(toolId)
                    val exitCode = if (0 in accepted) 0 else (accepted.firstOrNull() ?: 0)
                    CliExecutionResult(exitCode, "ok", "")
                }
            }
        }

        override suspend fun run(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            user: String?,
        ): CliExecutionResult = execute(distro, command, timeoutSeconds, Charsets.UTF_8, user)
    }

    private class FakeInstallationPort(private val manager: InstallationManager) : ToolchainInstallationPort {
        var activeId: InstallId? = null
        var previousId: InstallId? = null

        override suspend fun state(): InstalledToolchain = InstalledToolchain(
            activeInstallId = activeId,
            previousInstallId = previousId,
        )

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId {
            val candidate = manager.assembleCandidate(
                distro = "Ubuntu",
                arch = "x86_64",
                groups = artifacts.mapKeys { it.key.value }.mapValues { it.value.value },
                outputs = ToolGroupCatalog.allOutputs.map { out ->
                    org.ide.lti.core.data.setup.install.InstallOutputDescriptor(
                        toolId = out.toolId,
                        file = out.file,
                        kind = out.kind.name,
                        requiredForProduct = out.requiredForProduct,
                    )
                },
            )
            return InstallId(candidate.installId)
        }

        override suspend fun activate(request: ActivationRequest): ActivationOutcome {
            previousId = activeId
            activeId = request.targetInstallId
            return ActivationOutcome.Committed(request.targetInstallId)
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null

        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("release-selection-test").toFile()
        toolsDir = File(tempDir, "LtiRomTools").apply { mkdirs() }
        artifactStore = ArtifactStore(File(toolsDir, "artifacts"))
        installationManager = InstallationManager(toolsDir)
        fakeInstallationPort = FakeInstallationPort(installationManager)
        preferencesDataSource = ToolchainPreferencesDataSource(settings = MapSettings())
        repository = ToolchainSetupRepositoryImpl(preferencesDataSource)
        fakeCli = TestCli()
        fakeVerifier = ToolVerifier(fakeCli)
        gitCache = GitCache(cacheBaseDir = File(toolsDir, "cache/git").absolutePath, cli = fakeCli)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun onlyCatalogVersionsOfferedAndNonCatalogRefused() = runTest {
        val resolver = GitSourceResolver(fakeCli, gitCache, catalog = ToolGroupCatalog)

        val ghListing = resolver.listRefs(ToolGroupId("gh"), repoUrl = null)
        val ghRecipe = ToolGroupCatalog.GH.recipe as RecipeConfig.Release
        val expectedGhVersions = ghRecipe.versions.map { it.version }
        assertEquals(expectedGhVersions, ghListing.tags)
        assertTrue(ghListing.branches.isEmpty(), "Release groups have no branches")

        val payloadListing = resolver.listRefs(ToolGroupId("payload-dumper-go"), repoUrl = null)
        val payloadRecipe = ToolGroupCatalog.PAYLOAD_DUMPER_GO.recipe as RecipeConfig.Release
        val expectedPayloadVersions = payloadRecipe.versions.map { it.version }
        assertEquals(expectedPayloadVersions, payloadListing.tags)
        assertTrue(payloadListing.branches.isEmpty(), "Release groups have no branches")

        val nonCatalogReleaseOutcome = resolver.resolve(
            ToolSelection("gh", null, ToolRef.ReleaseVersion("9.99.9")),
        )
        assertIs<ResolveOutcome.NotFound>(nonCatalogReleaseOutcome)

        val nonCatalogTagOutcome = resolver.resolve(
            ToolSelection("gh", null, ToolRef.Tag("v9.99.9")),
        )
        assertIs<ResolveOutcome.NotFound>(nonCatalogTagOutcome)
    }

    @Test
    fun resolvedInputReleaseFrozenWithExactFields() = runTest {
        val resolver = GitSourceResolver(fakeCli, gitCache, catalog = ToolGroupCatalog)

        val outcome = resolver.resolve(
            ToolSelection("gh", null, ToolRef.ReleaseVersion("2.97.0")),
        )
        assertIs<ResolveOutcome.Resolved>(outcome)
        val input = outcome.input
        assertIs<ResolvedInput.Release>(input)

        assertEquals(ToolGroupId("gh"), input.group)
        assertEquals("2.97.0", input.version)
        assertEquals(
            "https://github.com/cli/cli/releases/download/v2.97.0/gh_2.97.0_linux_amd64.tar.gz",
            input.url,
        )
        assertEquals("gh_2.97.0_linux_amd64/bin/gh", input.archiveMember)
        assertEquals(
            "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409",
            input.sha256,
        )
        assertEquals("linux-x86_64", input.platform)
    }

    @Test
    fun tamperedArchiveRefusedAndPreviousInstallRemainsActive() = runTest {
        val initialArtifacts = publishBaseArtifactsForAllGroups("1111111111111111111111111111111111111111")
        val initialInstallId = fakeInstallationPort.assembleCandidate(initialArtifacts)
        fakeInstallationPort.activate(ActivationRequest("init-req", null, initialInstallId))
        assertEquals(initialInstallId, fakeInstallationPort.state().activeInstallId)

        fakeCli.tamperedSha = true

        val handler = SwitchToolchainHandler(
            artifactStore = artifactStore,
            installationManager = installationManager,
            installationPort = fakeInstallationPort,
            toolVerifier = fakeVerifier,
            recipeDispatcher = BuildRecipeDispatcher(),
            missingPackageProber = { _, _, _ -> emptyList() },
        )

        val inputs = ToolGroupCatalog.DEFAULT_GROUPS.associate { group ->
            if (group.id.value == "gh") {
                group.id to ResolvedInput.Release(
                    group = group.id,
                    version = "2.97.0",
                    url = "https://github.com/cli/cli/releases/download/v2.97.0/gh_2.97.0_linux_amd64.tar.gz",
                    archiveMember = "gh_2.97.0_linux_amd64/bin/gh",
                    sha256 = "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409",
                    platform = "linux-x86_64",
                )
            } else {
                group.id to ResolvedInput.Git(
                    group = group.id,
                    repoUrl = null,
                    ref = ToolRef.Tag("v1.0"),
                    commit = "1111111111111111111111111111111111111111",
                    resolvedAt = 1000L,
                )
            }
        }

        val action = SetupPlanAction.SwitchToolchain(
            inputs = inputs,
            selectionRevision = 1L,
            forceRebuildGroups = setOf(ToolGroupId("gh")),
        )
        val context = createTestExecutionContext(action)

        val outcome = handler.execute(action, context)
        assertNotNull(outcome, "Tampered archive must result in non-null failure outcome")

        // Previous active installation MUST remain active!
        assertEquals(
            initialInstallId,
            fakeInstallationPort.state().activeInstallId,
            "Previous installation must remain active when download is tampered",
        )
    }

    private fun createTestExecutionContext(action: SetupPlanAction.SwitchToolchain): ExecutionContext {
        val plan = SetupPlan(
            planId = "test-plan",
            revisionHash = "test-hash",
            environmentKey = "test-distro",
            kind = SetupPlanKind.FULL_SETUP,
            targetStageOrToolId = null,
            orderedActions = listOf(action),
        )
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = OperationScope("test-distro", "op-1", stateFlow) { "op-1" }
        return ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = File(toolsDir, "bin").absolutePath,
            extDir = File(toolsDir, "ext").absolutePath,
            workDir = File(toolsDir, "work").absolutePath,
            plan = plan,
            scope = scope,
            journal = SetupJournalContext("test-distro", "test-attempt"),
            repository = repository,
            serviceCli = fakeCli,
        )
    }

    private fun publishBaseArtifactsForAllGroups(commit: String): Map<ToolGroupId, ArtifactId> {
        val result = mutableMapOf<ToolGroupId, ArtifactId>()
        for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
            val candidateDir = File(tempDir, "cand-${group.id.value}").apply { mkdirs() }
            val binDir = File(candidateDir, "bin").apply { mkdirs() }
            val files = group.outputs.map { out ->
                val f = File(binDir, out.file).apply { writeText("dummy-bin-${out.file}") }
                ArtifactFileEntry("bin/${out.file}", ArtifactStore.sha256(f), "755")
            }

            val template = ArtifactManifest(
                schema = 1,
                group = group.id.value,
                artifactId = "art-${group.id.value}-initial",
                fingerprint = ArtifactStore.sha256("${group.id.value}-$commit"),
                source = ArtifactSourceInfo(kind = "git", commit = commit),
                recipe = ArtifactRecipeInfo(type = "Mock", revision = 1),
                files = files,
                outputs = group.outputs.map { it.file },
                verifiedAt = 1000L,
            )
            val published = artifactStore.publish(group.id.value, candidateDir, template, "attempt-1")
            result[group.id] = ArtifactId(published.artifactId)
            candidateDir.deleteRecursively()
        }
        return result
    }
}
