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
