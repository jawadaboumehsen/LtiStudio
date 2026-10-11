/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationKind
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.Selection
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolOperation
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.components.ToolchainPipelineTracker
import org.ide.lti.feature.setup.steps.EnvironmentStepContent
import org.ide.lti.feature.setup.steps.ToolsStepContent
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ToolsScreenshotTest {
    private val themes = listOf(AppTheme.Light, AppTheme.Dark, AppTheme.Blue)

    private class FakeToolStorePort : ToolStorePort {
        var inventory = ToolInventory(emptyMap())

        override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = Result.success(inventory)

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> =
            Result.success(RefListing(listOf(TagRefInfo("v1.8.9", "a".repeat(40))), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = "a".repeat(40)))

        override fun install(request: InstallRequest): Flow<InstallEvent> = emptyFlow()

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
            Result.success(GroupLinks(versionId, null))

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(
            PinnedTools(emptyMap(), emptyMap()),
        )
    }

    private class FakeSelectionRepo : ToolchainSelectionRepository {
        override suspend fun recordTrust(repoUrl: String): Result<Unit> = Result.success(Unit)

        override suspend fun isTrusted(repoUrl: String): Boolean = true
    }

    private fun installedVersion(
        group: ToolGroupId,
        id: String,
        label: String,
        intact: Boolean = true,
    ): InstalledVersion = InstalledVersion(
        group = group,
        id = id,
        label = label,
        source =
        ResolvedSource.Git(
            "https://github.com/erofs/erofs-utils",
            ToolRef.Tag(id),
            "3fa1c2d".padEnd(40, '0'),
        ),
        recipe = RecipeSummary("cmake", 1),
        outputs = listOf(InstalledOutput("mkfs.erofs", 1024L, "755"), InstalledOutput("dump.erofs", 1024L, "755")),
        builtAt = "2026-10-01",
        intact = intact,
    )

    private fun runScreenshot(name: String, content: @Composable () -> Unit) {
        for (theme in themes) {
            runDesktopComposeUiTest(width = 900, height = 600) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = 1.0f, fontScale = 2.0f),
                    ) {
                        LtiTheme(appTheme = theme, effectsEnabled = false) {
                            Box(Modifier.fillMaxSize()) {
                                GlassBackdrop()
                                content()
                            }
                        }
                    }
                }
                val roots = onAllNodes(androidx.compose.ui.test.isRoot())
                val nodeToCapture = if (roots.fetchSemanticsNodes().size > 1) roots[1] else onRoot()
                nodeToCapture.assertIsDisplayed()
                val image = nodeToCapture.captureToImage()
                saveCandidateScreenshot("${name}_${theme.id}", image)
            }
        }
    }

    private fun saveCandidateScreenshot(name: String, bitmap: ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/tools")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "$name.png")
        val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val bytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) {
            file.writeBytes(bytes)
        }
    }

    @Test
    fun screenshotEnvironmentStepMissingPackages() {
        val missingPackagesState =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "Ubuntu (WSL)",
                        description = "Ubuntu-24.04 · mohammad",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Build service",
                        description = "Running · v1.4.0 · 12 ms",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_PACKAGES,
                        title = "System packages",
                        description = "3 missing: cmake, clang, libfuse3-dev",
                        status = StepStatus.FAILED,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "Waiting for System packages",
                        status = StepStatus.PENDING,
                    ),
                ),
            )
        runScreenshot("env_step_missing_packages") {
            EnvironmentStepContent(
                state = SetupUiState(toolchainSetupState = missingPackagesState),
                actions = SetupUiActions(),
            )
        }
    }

    @Test
    fun screenshotEnvironmentStepAutomaticToolsInProgress() {
        val inProgressState =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "Ubuntu (WSL)",
                        description = "Ubuntu-24.04 · mohammad",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Build service",
                        description = "Running · v1.4.0 · 12 ms",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_PACKAGES,
                        title = "System packages",
                        description = "All packages present",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "Building erofs-utils (2 of 7)",
                        status = StepStatus.RUNNING,
                    ),
                ),
            )
        runScreenshot("env_step_automatic_tools_in_progress") {
            EnvironmentStepContent(
                state = SetupUiState(toolchainSetupState = inProgressState),
                actions = SetupUiActions(),
            )
        }
    }

    @Test
    fun screenshotEnvironmentStepPartialFailure() {
        val partialFailState =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "Ubuntu (WSL)",
                        description = "Ubuntu-24.04 · mohammad",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Build service",
                        description = "Running · v1.4.0 · 12 ms",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_PACKAGES,
                        title = "System packages",
                        description = "All packages present",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "5 ready · 1 failed · 1 not attempted",
                        status = StepStatus.FAILED,
                    ),
                ),
            )
        runScreenshot("env_step_partial_failure") {
            EnvironmentStepContent(
                state = SetupUiState(toolchainSetupState = partialFailState),
                actions = SetupUiActions(),
            )
        }
    }

    @Test
    fun screenshotToolsPage() {
        val port = FakeToolStorePort()
        val vAndroid = installedVersion(ToolGroupId("android-tools"), "sixteen-f9bef94", "sixteen")
        val vErofs = installedVersion(ToolGroupId("erofs-utils"), "v1.8.9", "v1.8.9")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("android-tools") to
                        GroupState(ToolGroupId("android-tools"), vAndroid, null, listOf(vAndroid)),
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            ToolGroupId("erofs-utils"),
                            vErofs,
                            Selection("v1.8.9", vErofs.source),
                            listOf(vErofs),
                        ),
                ),
            )
        val manager = ToolManager(port, selectionRepository = FakeSelectionRepo())
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))

        runScreenshot("tools_page") {
            ToolsStepContent(
                state = SetupUiState(),
                toolsPageState = state,
            )
        }
    }

    @Test
    fun screenshotInspectorInstalled() {
        val port = FakeToolStorePort()
        val vErofs1 = installedVersion(ToolGroupId("erofs-utils"), "v1.8.9", "v1.8.9")
        val vErofs2 = installedVersion(ToolGroupId("erofs-utils"), "v1.8.10-251217", "v1.8.10-251217")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to
                        GroupState(
                            ToolGroupId("erofs-utils"),
                            vErofs1,
                            Selection("v1.8.9", vErofs1.source),
                            listOf(vErofs1, vErofs2),
                        ),
                ),
            )
        val manager = ToolManager(port, selectionRepository = FakeSelectionRepo())
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(ToolGroupId("erofs-utils"))

        runScreenshot("tools_inspector_installed") {
            ToolInspector(state = state, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun screenshotInspectorAvailableToInstall() {
        val port = FakeToolStorePort()
        val group = ToolGroupId("apktool")
        port.inventory =
            ToolInventory(
                mapOf(
                    group to GroupState(group, null, null, emptyList()),
                ),
            )
        val manager = ToolManager(port, selectionRepository = FakeSelectionRepo())
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(group)

        runScreenshot("tools_inspector_available_to_install") {
            ToolInspector(state = state, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun screenshotInspectorError() {
        val port = FakeToolStorePort()
        val group = ToolGroupId("apktool")
        val failedOp =
            ToolOperation(
                opId = "op-error",
                group = group,
                kind = OperationKind.Install,
                phase = OperationPhase.Building,
                result =
                OperationResult.Failed(
                    error = ToolError.BuildFailed("Gradle build failed: compilation error in shadowJar"),
                    message = "Build failed",
                ),
            )
        port.inventory =
            ToolInventory(
                mapOf(
                    group to
                        GroupState(
                            group = group,
                            current = null,
                            selection = null,
                            installed = emptyList(),
                            operation = failedOp,
                        ),
                ),
            )
        val manager = ToolManager(port, selectionRepository = FakeSelectionRepo())
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(group)

        runScreenshot("tools_inspector_error") {
            ToolInspector(state = state, modifier = Modifier.fillMaxSize())
        }
    }

    @Test
    fun screenshotTrustDialog() {
        runScreenshot("trust_dialog") {
            TrustRepositoryDialog(
                repoUrl = "https://github.com/custom-fork/erofs-utils",
                onConfirm = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun screenshotDistroActionInstallUbuntu() {
        val step =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                status = StepStatus.FAILED,
                description = "No Linux distribution installed.",
            )
        runScreenshot("distro_action_install_ubuntu") {
            ToolchainPipelineTracker(
                steps = listOf(step),
                onRetryStep = {},
                distroStatuses = listOf(DistroStatus.NoDistro),
                onInstallUbuntu = {},
            )
        }
    }

    @Test
    fun screenshotDistroActionUseDistro() {
        val step =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                status = StepStatus.FAILED,
                description = "Unsupported distribution",
            )
        val unsupported = DistroStatus.Unsupported("Debian", "Debian 12 is unsupported")
        val usable =
            DistroStatus.Usable(
                SetupEnvironment("Ubuntu-22.04", 2, "ubuntu", "22.04", "testuser", "/home/testuser"),
            )
        runScreenshot("distro_action_use_distro") {
            ToolchainPipelineTracker(
                steps = listOf(step),
                onRetryStep = {},
                distroStatuses = listOf(unsupported, usable),
                activeDistro = "Debian",
                onSelectDistro = {},
            )
        }
    }

    @Test
    fun screenshotDistroActionOpenUbuntu() {
        val step =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                status = StepStatus.FAILED,
                description = "Ubuntu-24.04 needs a user account",
            )
        val noUser = DistroStatus.NoUsableUser("Ubuntu-24.04", "Default user is root")
        runScreenshot("distro_action_open_ubuntu") {
            ToolchainPipelineTracker(
                steps = listOf(step),
                onRetryStep = {},
                distroStatuses = listOf(noUser),
                onOpenDistro = {},
            )
        }
    }
}
