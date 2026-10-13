/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
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
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.Selection
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.feature.setup.tools.ToolsPageState
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * Automated Glass Verification & Roborazzi Desktop Multi-Window-Size Screenshot Test Suite.
 *
 * Validates:
 * 1. Compact Window Profile (900 x 600 dp) - Minimum desktop window boundary (SetupTokens.MinWindowWidth),
 *    single-column layout, unclipped action buttons, icon-only pipeline rail.
 * 2. Standard Default Window Profile (1120 x 700 dp) - Balanced two-pane workspace, search inputs,
 *    activity panels, and status bars.
 * 3. Wide Desktop Window Profile (1440 x 900 dp) - Right-hand persistent summary panel auto-expansion
 *    at the 1100 dp breakpoint with specular vertical dividers.
 * 4. Maximized / 4K Window Profile (1920 x 1080 dp) - Content max-width clamping (SetupTokens.ContentMaxWidth)
 *    preventing card stretching, preserving centered margins and card aspect ratios.
 * 5. Accessibility Scaled Profile (900 x 600 dp @ fontScale = 2.0f) - High-density text scaling without
 *    component clipping or layout failure.
 * 6. Solid Surface Fallback - Verifies effectsEnabled = false renders clean solid opaque surfaces without
 *    transparency glitches.
 * 7. Modal Overlays - Verifies Terminal Handoff Sheet over glass backdrop.
 */
@OptIn(ExperimentalTestApi::class)
class SetupWindowSizeScreenshotTest {

    private val testThemes = listOf(AppTheme.Dark, AppTheme.Light, AppTheme.Blue)

    private fun createReadyToolchainState(): ToolchainSetupState = ToolchainSetupState(
        steps = listOf(
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL Detection",
                description = "Ubuntu-24.04",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Build Server",
                description = "Connected",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_PACKAGES,
                title = "Packages",
                description = "All installed",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain",
                description = "Verified",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            ),
        ),
        lastVerifiedTimestamp = 1_700_000_000_000L,
    )

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

    private fun createTestToolsPageState(): ToolsPageState {
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
        return ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun saveScreenshotReport(name: String, bitmap: androidx.compose.ui.graphics.ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/setup_windows")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val file = File(reportsDir, "$name.png")
        val skiaImage = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        val bytes = skiaImage.encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) {
            file.writeBytes(bytes)
        }

        val targetPath = "src/desktopTest/resources/screenshots/$name.png"
        val targetFile = File(targetPath)
        val isUpdating = System.getProperty("updateScreenshots")?.toBoolean() == true ||
            System.getenv("UPDATE_SCREENSHOTS")?.toBoolean() == true
        if ((!targetFile.exists() || isUpdating) && bytes != null) {
            targetFile.parentFile?.mkdirs()
            targetFile.writeBytes(bytes)
        }

        bitmap.captureRoboImage(
            filePath = targetPath,
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(
                    changeThreshold = 0.005f,
                ),
                recordOptions = RoborazziOptions.RecordOptions(
                    resizeScale = 1.0,
                ),
            ),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // 1. Compact Window Profile (900 x 600 dp) - Minimum Desktop Boundary
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testCompact900x600_ProjectsTab_AllThemes() = runDesktopComposeUiTest(width = 900, height = 600) {
        val testProject = RecentProject(
            workspaceId = "ws_test_compact",
            name = "Kernel_AOSP",
            path = "C:/RomDev/Kernel_AOSP",
            lastOpened = Clock.System.now(),
        )
        val state = SetupUiState(
            recentProjects = listOf(testProject),
            filteredProjects = listOf(testProject),
            selectedTab = SetupTab.PROJECTS,
            toolchainSetupState = createReadyToolchainState(),
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("Kernel_AOSP").assertIsDisplayed()
            onNodeWithText("New Workspace").assertIsDisplayed()
            onNodeWithText("Open Workspace").assertIsDisplayed()

            val image = onRoot().captureToImage()
            assertEquals(900, image.width)
            assertEquals(600, image.height)
            saveScreenshotReport("setup_compact_900x600_projects_${appTheme.id}", image)
            setContent {}
        }
    }

    @Test
    fun testCompact900x600_EnvironmentTab_EffectsDisabled() = runDesktopComposeUiTest(width = 900, height = 600) {
        val state = SetupUiState(
            toolchainSetupState = createReadyToolchainState(),
            selectedTab = SetupTab.ENVIRONMENT,
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = false) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("WSL Detection").assertIsDisplayed()
            val image = onRoot().captureToImage()
            assertEquals(900, image.width)
            assertEquals(600, image.height)
            saveScreenshotReport("setup_compact_900x600_environment_solid_${appTheme.id}", image)
            setContent {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Standard Default Window Profile (1120 x 700 dp)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testStandard1120x700_ProjectsTab_AllThemes() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val testProject = RecentProject(
            workspaceId = "ws_test_standard",
            name = "Android_Vendor",
            path = "C:/RomDev/Android_Vendor",
            lastOpened = Clock.System.now(),
        )
        val state = SetupUiState(
            recentProjects = listOf(testProject),
            filteredProjects = listOf(testProject),
            selectedTab = SetupTab.PROJECTS,
            toolchainSetupState = createReadyToolchainState(),
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("Android_Vendor").assertIsDisplayed()
            onNodeWithText("New Workspace").assertIsDisplayed()
            onNodeWithText("Open Workspace").assertIsDisplayed()

            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("setup_standard_1120x700_projects_${appTheme.id}", image)
            setContent {}
        }
    }

    @Test
    fun testStandard1120x700_ToolsTab_AllThemes() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val toolsPageState = createTestToolsPageState()
        val state = SetupUiState(
            toolchainSetupState = createReadyToolchainState(),
            selectedTab = SetupTab.TOOLS,
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            toolsPageState = toolsPageState,
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("Toolchain & Native Binaries").assertIsDisplayed()
            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("setup_standard_1120x700_tools_${appTheme.id}", image)
            setContent {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 3. Wide Desktop Window Profile (1440 x 900 dp) - Auto-Expanded Summary Panel
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testWide1440x900_EnvironmentWithSummaryPanel_AllThemes() = runDesktopComposeUiTest(width = 1440, height = 900) {
        val state = SetupUiState(
            toolchainSetupState = createReadyToolchainState(),
            selectedTab = SetupTab.ENVIRONMENT,
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithTag("SetupRightRail").assertExists()
            val image = onRoot().captureToImage()
            assertEquals(1440, image.width)
            assertEquals(900, image.height)
            saveScreenshotReport("setup_wide_1440x900_environment_${appTheme.id}", image)
            setContent {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 4. Maximized / 4K Window Profile (1920 x 1080 dp) - Content Clamping
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testMaximized1920x1080_ContentClamping_AllThemes() = runDesktopComposeUiTest(width = 1920, height = 1080) {
        val testProjects = (1..5).map { index ->
            RecentProject(
                workspaceId = "ws_max_$index",
                name = "AOSP_Build_$index",
                path = "C:/RomDev/AOSP_Build_$index",
                lastOpened = Clock.System.now(),
            )
        }
        val state = SetupUiState(
            recentProjects = testProjects,
            filteredProjects = testProjects,
            selectedTab = SetupTab.PROJECTS,
            toolchainSetupState = createReadyToolchainState(),
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("AOSP_Build_1").assertIsDisplayed()
            val image = onRoot().captureToImage()
            assertEquals(1920, image.width)
            assertEquals(1080, image.height)
            saveScreenshotReport("setup_maximized_1920x1080_projects_${appTheme.id}", image)
            setContent {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 5. Accessibility Scaled Profile (900 x 600 dp @ 2.0x Font Scale)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testAccessibilityScaled900x600_2xFontScale_AllThemes() = runDesktopComposeUiTest(width = 900, height = 600) {
        val testProject = RecentProject(
            workspaceId = "ws_scaled",
            name = "Scaled_Workspace",
            path = "C:/RomDev/Scaled_Workspace",
            lastOpened = Clock.System.now(),
        )
        val state = SetupUiState(
            recentProjects = listOf(testProject),
            filteredProjects = listOf(testProject),
            selectedTab = SetupTab.PROJECTS,
            toolchainSetupState = createReadyToolchainState(),
        )

        for (appTheme in testThemes) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1.0f, fontScale = 2.0f),
                ) {
                    LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                        Box(Modifier.fillMaxSize()) {
                            GlassBackdrop()
                            SetupScreenContent(
                                state = state,
                                actions = SetupUiActions(),
                                iconsOnly = true,
                            )
                        }
                    }
                }
            }
            onNodeWithText("New Workspace").assertIsDisplayed()
            onNodeWithText("Open Workspace").assertIsDisplayed()

            val image = onRoot().captureToImage()
            assertEquals(900, image.width)
            assertEquals(600, image.height)
            saveScreenshotReport("setup_a11y_2x_900x600_projects_${appTheme.id}", image)
            setContent {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 6. Modal Overlays & Bottom Sheets (1120 x 700 dp)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testStandard1120x700_TerminalHandoffSheet_AllThemes() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val handoff = UserRepairHandoff(
            actionId = "bootstrap:apt",
            description = "Root privileges required for package installation",
            terminalCommand = "sudo apt-get update && sudo apt-get install -y cmake build-essential",
            packages = listOf("cmake", "build-essential"),
            distro = "Ubuntu-24.04",
        )
        val state = SetupUiState(
            isHandoffSheetVisible = true,
            handoffSheetState = TerminalHandoffState.Idle,
            activeOperation = SetupOperation(
                executionState = SetupOperationState.AWAITING_USER_ACTION,
                awaitingHandoff = handoff,
            ),
            toolchainSetupState = createReadyToolchainState(),
        )

        for (appTheme in testThemes) {
            setContent {
                LtiTheme(appTheme = appTheme, effectsEnabled = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        SetupScreenContent(
                            state = state,
                            actions = SetupUiActions(),
                            iconsOnly = true,
                        )
                    }
                }
            }
            onNodeWithText("Copy command").assertIsDisplayed()
            onNodeWithText("Close for now").assertIsDisplayed()
            val image = onRoot().captureToImage()
            assertEquals(1120, image.width)
            assertEquals(700, image.height)
            saveScreenshotReport("setup_overlay_handoff_sheet_${appTheme.id}", image)
            setContent {}
        }
    }
}
