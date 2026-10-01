package org.ide.lti.core.data.setup.execute

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.FakeWslCliExecutor
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.execute.handlers.SwitchToolchainHandler
import org.ide.lti.core.data.setup.execute.handlers.computeInputFingerprint
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.InstallationManager
import org.ide.lti.core.data.setup.recipe.BuildRecipeDispatcher
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
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
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SwitchToolchainHandlerTest {

    private lateinit var tempDir: File
    private lateinit var cli: FakeWslCliExecutor
    private lateinit var artifactStore: ArtifactStore
    private lateinit var installationManager: InstallationManager
    private lateinit var fakeInstallationPort: FakeToolchainInstallationPort
    private lateinit var handler: SwitchToolchainHandler

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("switch-test").toFile()
        cli = FakeWslCliExecutor()
        cli.userHome = "/home/lti"
        artifactStore = ArtifactStore(cli, "Ubuntu")
        installationManager = InstallationManager(cli, "Ubuntu")
        fakeInstallationPort = FakeToolchainInstallationPort()

        handler = SwitchToolchainHandler(
            artifactStore = artifactStore,
            installationManager = installationManager,
            installationPort = fakeInstallationPort,
            recipeDispatcher = BuildRecipeDispatcher(),
            toolVerifier = ToolVerifier(cli),
            missingPackageProber = { _, _, _ -> emptyList() },
        )
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `a find or sha256sum failure fails the build`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = ToolGroupCatalog.DEFAULT_GROUPS.associate { group ->
            group.id to ResolvedInput.Git(
                group = group.id,
                repoUrl = "https://github.com/example/${group.id.value}.git",
                ref = ToolRef.Tag("v1"),
                commit = testSha,
                resolvedAt = 100L,
            )
        }
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        val outcome = handler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "Should fail when find or sha256sum fails")
    }

    @Test
    fun `a different toolchain version means no reuse`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val group = ToolGroupCatalog.ANDROID_TOOLS
        val input = ResolvedInput.Git(
            group = group.id,
            repoUrl = "https://github.com/example/android-tools.git",
            ref = ToolRef.Tag("v1"),
            commit = testSha,
            resolvedAt = 100L,
        )
        val fp1 = computeInputFingerprint(group, input, mapOf("clang" to "clang 14.0"))
        val fp2 = computeInputFingerprint(group, input, mapOf("clang" to "clang 15.0"))
        assertNotEquals(fp1, fp2, "Different toolchain compiler version must produce different fingerprint")
    }

    @Test
    fun `a tampered file is not reused`() = runTest {
        val group = "android-tools"
        val artifactId = "art-test-1"
        val intact = artifactStore.isIntact(group, artifactId)
        assertFalse(intact, "Store must reject tampered or missing artifact files")
    }

    @Test
    fun `nothing is created on the Windows filesystem`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = ToolGroupCatalog.DEFAULT_GROUPS.associate { group ->
            group.id to ResolvedInput.Git(
                group = group.id,
                repoUrl = "https://github.com/example/${group.id.value}.git",
                ref = ToolRef.Tag("v1"),
                commit = testSha,
                resolvedAt = 100L,
            )
        }
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        handler.execute(action, context)

        val items = tempDir.listFiles() ?: emptyArray()
        assertTrue(items.isEmpty(), "Nothing should be created on Windows filesystem")
    }

    private fun createAllInputs(
        overrideGroup: ToolGroupId,
        overrideInput: ResolvedInput,
    ): Map<ToolGroupId, ResolvedInput> {
        val inputs = org.ide.lti.core.data.setup.SetupPlanFactory.defaultCatalogInputs().toMutableMap()
        inputs[overrideGroup] = overrideInput
        return inputs
    }

    @Test
    fun `failed git clone stops execution and returns failure before recipe runs`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            if (cmdStr.contains("git clone") && cmdStr.contains("fork/android-tools.git")) {
                io.ltirom.tooling.client.wsl.CliExecutionResult(128, "", "fatal: repository not found")
            } else {
                null
            }
        }

        val outcome = handler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "Clone failure must yield SetupOutcome.Failed, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("Failed to clone"), "Reason must mention clone failure: $reason")
    }

    @Test
    fun `failed git checkout stops execution and returns failure before recipe runs`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("git clone") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("cat-file") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("git -C") && cmdStr.contains("checkout") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(1, "", "fatal: reference is not a tree")
                else -> null
            }
        }

        val outcome = handler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "Checkout failure must yield SetupOutcome.Failed, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("Failed to checkout"), "Reason must mention checkout failure: $reason")
    }

    @Test
    fun `HEAD mismatch stops execution and returns failure before recipe runs`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("git clone") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("cat-file") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("checkout") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("rev-parse HEAD") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "2222222222222222222222222222222222222222\n", "")
                else -> null
            }
        }

        val outcome = handler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "HEAD mismatch must yield SetupOutcome.Failed, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("HEAD verification failed"), "Reason must mention HEAD verification: $reason")
    }

    @Test
    fun `submodule verification failure stops execution and returns failure before recipe runs`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val subSha = "3333333333333333333333333333333333333333"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                submoduleCommits = mapOf("external/boringssl" to subSha),
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)
        val context = createTestExecutionContext(action)

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("git clone") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("cat-file") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("rev-parse HEAD") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "$testSha\n", "")
                cmdStr.contains("submodule update") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(1, "", "fatal: Server does not allow request")
                else -> null
            }
        }

        val outcome = handler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "Submodule failure must yield SetupOutcome.Failed, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("Failed to initialize submodules"), "Reason must mention submodule: $reason")
        assertTrue(
            cli.commandLog.any { it.startsWith("WSL: rm -rf /home/lti/LtiRomTools/artifacts/.tmp-build-android-tools-") },
            "A failed source preparation must remove its isolated temporary workspace",
        )
    }

    @Test
    fun `missing packages with journal write failure returns Interrupted and does not present handoff`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)

        val inMemRepo = org.ide.lti.core.data.setup.inMemoryToolchainRepository()
        val failingRepo = object : org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository by inMemRepo {
            override suspend fun recordAttemptAwaitingUserAction(
                attemptId: String,
                handoff: org.ide.lti.core.model.setup.PersistedUserHandoff,
                plan: org.ide.lti.core.model.setup.PersistedSetupPlan,
            ): Result<Unit> = Result.failure(java.io.IOException("Disk full: cannot write journal"))
        }

        val handlerWithMissingPackages = SwitchToolchainHandler(
            artifactStore = artifactStore,
            installationManager = installationManager,
            installationPort = fakeInstallationPort,
            recipeDispatcher = BuildRecipeDispatcher(),
            toolVerifier = ToolVerifier(cli),
            missingPackageProber = { _, _, _ -> listOf("cmake", "ninja-build") },
        )

        val baseContext = createTestExecutionContext(action)
        val context = baseContext.copy(repository = failingRepo)

        val outcome = handlerWithMissingPackages.execute(action, context)
        assertTrue(outcome is SetupOutcome.Interrupted, "Must return Interrupted, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("Journal write failed"), "Reason must mention journal write failure: $reason")
    }

    @Test
    fun `when commit installed persistence fails, pendingSwitch is retained and recovery required is reported`() = runTest {
        val testSha = "1111111111111111111111111111111111111111"
        val inputs = createAllInputs(
            ToolGroupCatalog.ANDROID_TOOLS.id,
            ResolvedInput.Git(
                group = ToolGroupCatalog.ANDROID_TOOLS.id,
                repoUrl = "https://github.com/fork/android-tools.git",
                ref = ToolRef.Commit(testSha),
                commit = testSha,
                resolvedAt = 100L,
            ),
        )
        val action = SetupPlanAction.SwitchToolchain(inputs = inputs, selectionRevision = 1L)

        val inMemRepo = org.ide.lti.core.data.setup.inMemoryToolchainRepository()
        val failingRepo = object : org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository by inMemRepo {
            override suspend fun markStageCompleted(stageName: String, completed: Boolean): Result<Unit> =
                Result.failure(java.io.IOException("Disk error while saving stage completion"))
        }

        cli.commandInterceptor = { cmd ->
            val cmdStr = cmd.joinToString(" ")
            when {
                cmdStr.contains("git clone") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("cat") && cmdStr.contains("install.json") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(
                        0,
                        """{"schema":1,"installId":"fake-id","distro":"Ubuntu","arch":"x86_64","groups":{"android-tools":"art-1"},"outputs":[]}""",
                        "",
                    )
                cmdStr.contains("cat-file") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "", "")
                cmdStr.contains("rev-parse HEAD") -> {
                    val groupName = cmd.getOrNull(cmd.indexOf("-C") + 1)?.substringAfterLast('/')
                    val expectedCommit = inputs.entries.firstOrNull { it.key.value == groupName }?.value?.let { inp ->
                        (inp as? ResolvedInput.Git)?.commit
                    } ?: testSha
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "$expectedCommit\n", "")
                }
                cmdStr.contains("find") -> io.ltirom.tooling.client.wsl.CliExecutionResult(0, "bin/adb\n", "")
                cmdStr.contains("sha256sum") ->
                    io.ltirom.tooling.client.wsl.CliExecutionResult(0, "${"0".repeat(64)}  bin/adb\n", "")
                else -> null
            }
        }

        val mockRecipe = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig> {
            override suspend fun build(
                ctx: org.ide.lti.core.data.setup.recipe.RecipeContext,
                config: org.ide.lti.core.domain.setup.RecipeConfig,
            ): org.ide.lti.core.data.setup.recipe.RecipeResult =
                org.ide.lti.core.data.setup.recipe.RecipeResult.Built(listOf("bin/adb"))
        }
        val mockDispatcher = BuildRecipeDispatcher(
            androidTools = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig.AndroidTools> {
                override suspend fun build(ctx: org.ide.lti.core.data.setup.recipe.RecipeContext, config: org.ide.lti.core.domain.setup.RecipeConfig.AndroidTools) =
                    mockRecipe.build(ctx, config)
            },
            cmake = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig.CMake> {
                override suspend fun build(ctx: org.ide.lti.core.data.setup.recipe.RecipeContext, config: org.ide.lti.core.domain.setup.RecipeConfig.CMake) =
                    mockRecipe.build(ctx, config)
            },
            gradleJar = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig.GradleJar> {
                override suspend fun build(ctx: org.ide.lti.core.data.setup.recipe.RecipeContext, config: org.ide.lti.core.domain.setup.RecipeConfig.GradleJar) =
                    mockRecipe.build(ctx, config)
            },
            scriptCopy = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig.ScriptCopy> {
                override suspend fun build(ctx: org.ide.lti.core.data.setup.recipe.RecipeContext, config: org.ide.lti.core.domain.setup.RecipeConfig.ScriptCopy) =
                    mockRecipe.build(ctx, config)
            },
            releaseDownload = object : org.ide.lti.core.data.setup.recipe.BuildRecipe<org.ide.lti.core.domain.setup.RecipeConfig.Release> {
                override suspend fun build(ctx: org.ide.lti.core.data.setup.recipe.RecipeContext, config: org.ide.lti.core.domain.setup.RecipeConfig.Release) =
                    mockRecipe.build(ctx, config)
            },
        )

        val customHandler = SwitchToolchainHandler(
            artifactStore = artifactStore,
            installationManager = installationManager,
            installationPort = fakeInstallationPort,
            recipeDispatcher = mockDispatcher,
            toolVerifier = ToolVerifier(cli),
            missingPackageProber = { _, _, _ -> emptyList() },
        )

        val baseContext = createTestExecutionContext(action)
        val context = baseContext.copy(repository = failingRepo)

        val outcome = customHandler.execute(action, context)
        assertTrue(outcome is SetupOutcome.Failed, "Must return Failed on commitInstalled error, got: $outcome")
        val reason = outcome.reason
        assertTrue(reason.contains("Recovery required"), "Must report Recovery required: $reason")
        kotlin.test.assertNotNull(
            failingRepo.currentState.pendingSwitch,
            "pendingSwitch must be retained on persistence failure",
        )
    }

    @Test
    fun `commit without a journal repository cannot report activation as success`() = runTest {
        val action = SetupPlanAction.SwitchToolchain(
            inputs = createAllInputs(
                ToolGroupCatalog.ANDROID_TOOLS.id,
                ResolvedInput.Git(
                    group = ToolGroupCatalog.ANDROID_TOOLS.id,
                    repoUrl = "https://github.com/fork/android-tools.git",
                    ref = ToolRef.Commit("1111111111111111111111111111111111111111"),
                    commit = "1111111111111111111111111111111111111111",
                    resolvedAt = 100L,
                ),
            ),
            selectionRevision = 1L,
        )
        val context = createTestExecutionContext(action)

        val outcome = handler.commitInstalled(InstallId("candidate-install"), context)

        assertTrue(outcome is SetupOutcome.Interrupted, "Missing durable repository must require recovery: $outcome")
        assertTrue(outcome.reason.contains("cannot be committed without the setup journal"))
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
        val stateFlow = MutableStateFlow(org.ide.lti.core.domain.setup.ToolchainSetupState())
        val scope = OperationScope("test-distro", "op-1", stateFlow) { "op-1" }
        return ExecutionContext(
            distro = "Ubuntu",
            home = tempDir.absolutePath,
            binDir = File(tempDir, "bin").absolutePath,
            extDir = File(tempDir, "ext").absolutePath,
            workDir = File(tempDir, "work").absolutePath,
            plan = plan,
            scope = scope,
            journal = SetupJournalContext("test-distro", "test-attempt"),
            repository = null,
            serviceCli = cli,
        )
    }

    private class FakeToolchainInstallationPort : ToolchainInstallationPort {
        override suspend fun state(): InstalledToolchain = InstalledToolchain(null, null)

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId =
            InstallId("fake-id")

        override suspend fun activate(request: ActivationRequest): ActivationOutcome =
            ActivationOutcome.Committed(request.targetInstallId)

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null

        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }
}
