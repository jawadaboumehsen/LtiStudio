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
import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.execute.handlers.SwitchToolchainHandler
import org.ide.lti.core.data.setup.execute.handlers.computeInputFingerprint
import org.ide.lti.core.data.setup.install.ArtifactFileEntry
import org.ide.lti.core.data.setup.install.ArtifactManifest
import org.ide.lti.core.data.setup.install.ArtifactRecipeInfo
import org.ide.lti.core.data.setup.install.ArtifactSourceInfo
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.InstallOutputDescriptor
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.data.setup.recipe.BuildRecipe
import org.ide.lti.core.data.setup.recipe.BuildRecipeDispatcher
import org.ide.lti.core.data.setup.recipe.RecipeContext
import org.ide.lti.core.data.setup.recipe.RecipeResult
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainSetupState
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
import java.nio.charset.Charset
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepairInstalledTest {

    private lateinit var tempDir: File
    private lateinit var toolsDir: File
    private lateinit var artifactStore: ArtifactStore
    private lateinit var installationManager: InstallationManager
    private lateinit var fakeInstallationPort: FakeToolchainInstallationPort
    private lateinit var preferencesDataSource: ToolchainPreferencesDataSource
    private lateinit var repository: ToolchainSetupRepositoryImpl
    private lateinit var fakeVerifier: ToolVerifier

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("repair-test").toFile()
        toolsDir = File(tempDir, "LtiRomTools").apply { mkdirs() }
        artifactStore = ArtifactStore(File(toolsDir, "artifacts"))
        installationManager = InstallationManager(toolsDir)
        fakeInstallationPort = FakeToolchainInstallationPort(installationManager)
        preferencesDataSource = ToolchainPreferencesDataSource(settings = MapSettings())
        repository = ToolchainSetupRepositoryImpl(preferencesDataSource)

        val fakeCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val isSimg2imgProbe = command.any { it.contains("simg2img") } && !command.contains("test")
                val code = if (isSimg2imgProbe) 1 else 0
                return CliExecutionResult(code, "", "")
            }
        }
        fakeVerifier = ToolVerifier(fakeCli)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `repair rebuilds installed input into new artifact instance when desired differs`() = runTest {
        val erofsGroup = ToolGroupId("erofs-utils")
        val installedCommitA = "1111111111111111111111111111111111111111"
        val desiredCommitB = "2222222222222222222222222222222222222222"

        // Desired selection in repository is B
        repository.saveSelections(
            "Ubuntu",
            expectedRevision = 0L,
            desired = mapOf(erofsGroup to ToolSelection(erofsGroup.value, null, ToolRef.Commit(desiredCommitB))),
        )

        // Seed initial artifacts and install with installed input A
        val initialArtifacts = mutableMapOf<ToolGroupId, ArtifactId>()
        val installedInputs = mutableMapOf<ToolGroupId, ResolvedInput>()
        for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
            val commit = if (group.id == erofsGroup) installedCommitA else "0000000000000000000000000000000000000000"
            val input = ResolvedInput.Git(
                group = group.id,
                repoUrl = null,
                ref = ToolRef.Commit(commit),
                commit = commit,
                resolvedAt = System.currentTimeMillis(),
            )
            installedInputs[group.id] = input
            val art = publishArtifact(group.id.value, commit)
            initialArtifacts[group.id] = ArtifactId(art.artifactId)
        }

        val initialCandidate = fakeInstallationPort.assembleCandidate(initialArtifacts)
        fakeInstallationPort.activate(ActivationRequest("init", null, initialCandidate))
        val oldErofsArtifactId = initialArtifacts.getValue(erofsGroup).value

        // Pre-condition: Installed is A, Desired is B
        val preDesired = repository.desiredSelections("Ubuntu").desired[erofsGroup.value]?.ref
        assertEquals(desiredCommitB, (preDesired as? ToolRef.Commit)?.sha)
        assertNotEquals(installedCommitA, desiredCommitB)

        // Track what recipe gets executed
        var rebuiltCommit: String? = null
        val recipeDispatcher = createRecipeDispatcher { ctx, _ ->
            rebuiltCommit = installedCommitA
            val bin = File(ctx.binDir).apply { mkdirs() }
            File(bin, "mkfs.erofs").writeText("#!/bin/sh\n")
            RecipeResult.Built(listOf("mkfs.erofs"))
        }

        val handler = SwitchToolchainHandler(
            artifactStore = artifactStore,
            installationManager = installationManager,
            installationPort = fakeInstallationPort,
            toolVerifier = fakeVerifier,
            recipeDispatcher = recipeDispatcher,
            missingPackageProber = { _, _, _ -> emptyList() },
        )

        val switchAction = SetupPlanAction.SwitchToolchain(
            inputs = installedInputs,
            selectionRevision = repository.desiredSelections("Ubuntu").revision,
            forceRebuildGroups = setOf(erofsGroup),
        )
        val context = createTestExecutionContext(switchAction)

        val outcome = handler.execute(switchAction, context)
        assertNull(outcome, "Repair execution should succeed")

        // 1. Verify installed input A was rebuilt (not B)
        assertEquals(installedCommitA, rebuiltCommit)

        // 2. Verify new artifact instance was created (different artifactId)
        val activeInstallId = fakeInstallationPort.state().activeInstallId?.value
        val manifest = installationManager.getManifest(activeInstallId!!)
        val newErofsArtifactId = manifest?.groups?.get(erofsGroup.value)
        assertNotEquals(oldErofsArtifactId, newErofsArtifactId)

        // 3. Verify old artifact is still present on disk
        val oldDir = artifactStore.getArtifactDir(erofsGroup.value, oldErofsArtifactId)
        assertTrue(oldDir.exists(), "Old artifact must remain untouched while referenced")

        // 4. Verify desired in repository is STILL B
        val postDesired = repository.desiredSelections("Ubuntu").desired[erofsGroup.value]?.ref
        assertEquals(desiredCommitB, (postDesired as? ToolRef.Commit)?.sha)

        // 5. Verify installed artifact on candidate contains rebuilt commit A
        val postArtifact = artifactStore.getManifest(erofsGroup.value, newErofsArtifactId!!)
        assertEquals(installedCommitA, postArtifact?.source?.commit)
    }

    private fun publishArtifact(group: String, commit: String): ArtifactManifest {
        val candidateDir = Files.createTempDirectory("art-$group").toFile()
        val binDir = File(candidateDir, "bin").apply { mkdirs() }
        val dummy = File(binDir, if (group == "erofs-utils") "mkfs.erofs" else "tool-$group")
        dummy.writeText("#!/bin/sh\n")

        val files = listOf(ArtifactFileEntry("bin/${dummy.name}", "sha-$commit", "755"))
        val toolGroup = ToolGroupCatalog.DEFAULT_GROUPS.first { it.id.value == group }
        val input = ResolvedInput.Git(
            group = toolGroup.id,
            repoUrl = null,
            ref = ToolRef.Commit(commit),
            commit = commit,
            resolvedAt = 0L,
        )
        val fp = computeInputFingerprint(toolGroup, input, emptyMap())
        val template = ArtifactManifest(
            schema = 1,
            group = group,
            artifactId = "",
            fingerprint = fp,
            source = ArtifactSourceInfo(kind = "git", commit = commit),
            recipe = ArtifactRecipeInfo(type = "recipe", revision = 1),
            files = files,
            outputs = listOf(dummy.name),
            verifiedAt = System.currentTimeMillis(),
        )
        return artifactStore.publish(group, candidateDir, template, "inst-seed")
    }

    private fun createRecipeDispatcher(
        buildBlock: (RecipeContext, RecipeConfig) -> RecipeResult,
    ): BuildRecipeDispatcher {
        val mockRecipe = object : BuildRecipe<RecipeConfig> {
            override suspend fun build(ctx: RecipeContext, config: RecipeConfig): RecipeResult = buildBlock(ctx, config)
        }
        return BuildRecipeDispatcher(
            androidTools = object : BuildRecipe<RecipeConfig.AndroidTools> {
                override suspend fun build(ctx: RecipeContext, config: RecipeConfig.AndroidTools) =
                    mockRecipe.build(ctx, config)
            },
            cmake = object : BuildRecipe<RecipeConfig.CMake> {
                override suspend fun build(ctx: RecipeContext, config: RecipeConfig.CMake) =
                    mockRecipe.build(ctx, config)
            },
            gradleJar = object : BuildRecipe<RecipeConfig.GradleJar> {
                override suspend fun build(ctx: RecipeContext, config: RecipeConfig.GradleJar) =
                    mockRecipe.build(ctx, config)
            },
            scriptCopy = object : BuildRecipe<RecipeConfig.ScriptCopy> {
                override suspend fun build(ctx: RecipeContext, config: RecipeConfig.ScriptCopy) =
                    mockRecipe.build(ctx, config)
            },
            releaseDownload = object : BuildRecipe<RecipeConfig.Release> {
                override suspend fun build(ctx: RecipeContext, config: RecipeConfig.Release) =
                    mockRecipe.build(ctx, config)
            },
        )
    }

    private fun createTestExecutionContext(action: SetupPlanAction.SwitchToolchain): ExecutionContext {
        val plan = SetupPlan(
            planId = "repair-plan",
            revisionHash = "repair-hash",
            environmentKey = "test-distro",
            kind = SetupPlanKind.REPAIR_TOOL,
            targetStageOrToolId = "mkfs.erofs",
            orderedActions = listOf(action),
        )
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = OperationScope("test-distro", "op-repair", stateFlow) { "op-repair" }
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
        )
    }

    private class FakeToolchainInstallationPort(private val manager: InstallationManager) :
        ToolchainInstallationPort {
        private var active: InstallId? = null
        private var prev: InstallId? = null

        override suspend fun state(): InstalledToolchain =
            InstalledToolchain(activeInstallId = active, previousInstallId = prev)

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId {
            val candidate = manager.assembleCandidate(
                distro = "Ubuntu",
                arch = "x86_64",
                groups = artifacts.mapKeys { it.key.value }.mapValues { it.value.value },
                outputs = ToolGroupCatalog.allOutputs.map { out ->
                    InstallOutputDescriptor(
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
            prev = active
            active = request.targetInstallId
            return ActivationOutcome.Committed(request.targetInstallId)
        }

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null
        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }
}
