/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.RevertingGroup
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.versions.components.EditVersionPanel
import org.ide.lti.feature.setup.versions.components.RestorePreviousDialog
import org.ide.lti.feature.setup.versions.components.ReviewSwitchDialog
import org.ide.lti.feature.setup.versions.components.ToolGroupRow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

private const val WIDTH_STANDARD: Int = 1120
private const val HEIGHT_STANDARD: Int = 700
private const val WIDTH_COMPACT: Int = 900
private const val HEIGHT_COMPACT: Int = 600
private const val WIDTH_DIALOG: Int = 800
private const val HEIGHT_DIALOG: Int = 600

@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ToolVersionsScreenshotTest {

    private val sampleRows = listOf(
        ToolGroupRowModel("EROFS_UTILS", "v1.7.1", "v1.7.1", ToolGroupStatus.UP_TO_DATE),
        ToolGroupRowModel("F2FS_TOOLS", "v1.16.0", "v1.16.0-custom", ToolGroupStatus.CHANGE_PENDING),
        ToolGroupRowModel("GH", "2.96.0", "2.97.0", ToolGroupStatus.UPDATE_AVAILABLE),
        ToolGroupRowModel("CUSTOM_MOD", "1.0.0", "1.0.0", ToolGroupStatus.UNSUPPORTED),
        ToolGroupRowModel("PAYLOAD_DUMPER_GO", "1.2.2", "1.3.0", ToolGroupStatus.BUILDING),
        ToolGroupRowModel("RECOVERY_TEST", "corrupted", "none", ToolGroupStatus.NEEDS_RECOVERY),
    )

    private val sampleDraft = ToolGroupDraft(
        group = ToolGroupId("EROFS_UTILS"),
        repoUrl = null,
        ref = ToolRef.Tag("v1.8.0"),
        refQuery = "",
        isAdvanced = false,
    )

    private val sampleRefs = RefListing(
        tags = listOf("v1.8.0", "v1.7.1", "v1.7.0", "v1.6.0"),
        branches = listOf("master", "dev"),
    )

    private val sampleResolution = ResolutionState.Resolved(
        input = ResolvedInput.Git(
            group = ToolGroupId("EROFS_UTILS"),
            repoUrl = "https://git.kernel.org/pub/scm/linux/kernel/git/jaegeuk/erofs-utils.git",
            ref = ToolRef.Tag("v1.8.0"),
            commit = "e9a12c4019bd37fa20391d1e4e1a0b3297a5b3cd",
            submoduleCommits = emptyMap(),
            resolvedAt = 1727730000000L,
        ),
        compatibility = CompatibilityResult.LayoutCompatible,
    )

    private val sampleReviewModel = ReviewSwitchModel(
        changedGroups = listOf(
            ReviewGroupChange(
                groupId = ToolGroupId("EROFS_UTILS"),
                currentLabel = "v1.7.1",
                currentCommit = "abc1234",
                newLabel = "v1.8.0",
                newCommit = "def5678",
                affectedTools = listOf("mkfs.erofs", "fsck.erofs"),
            ),
        ),
        unchangedGroups = listOf(
            ToolGroupId("F2FS_TOOLS"),
            ToolGroupId("GH"),
            ToolGroupId("PAYLOAD_DUMPER_GO"),
        ),
        requiresTrustConfirmation = false,
        untrustedRepoUrl = null,
    )

    private val sampleRestoreModel = RestorePreviousModel(
        activeInstallId = "c-1727730000",
        previousInstallId = "c-1727720000",
        revertingGroups = listOf(
            RevertingGroup(
                groupId = ToolGroupId("EROFS_UTILS"),
                currentArtifactId = "art-1",
                previousArtifactId = "art-0",
                currentVersion = "v1.8.0",
                previousVersion = "v1.7.1",
                affectedTools = listOf("mkfs.erofs"),
            ),
            RevertingGroup(
                groupId = ToolGroupId("GH"),
                currentArtifactId = "art-3",
                previousArtifactId = "art-2",
                currentVersion = "2.97.0",
                previousVersion = "2.96.0",
                affectedTools = listOf("gh"),
            ),
        ),
    )

    @Test
    fun testRowsDark() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyRowsScreenshot("tool_versions_rows_dark", AppTheme.Dark)
    }

    @Test
    fun testRowsLight() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyRowsScreenshot("tool_versions_rows_light", AppTheme.Light)
    }

    @Test
    fun testRowsBlue() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyRowsScreenshot("tool_versions_rows_blue", AppTheme.Blue)
    }

    @Test
    fun testRowsEffectsOff() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyRowsScreenshot("tool_versions_rows_effects_off", AppTheme.Dark, effectsEnabled = false)
    }

    @Test
    fun testRowsCompact() = runDesktopComposeUiTest(width = WIDTH_COMPACT, height = HEIGHT_COMPACT) {
        verifyRowsScreenshot(
            name = "tool_versions_rows_compact",
            theme = AppTheme.Blue,
            width = WIDTH_COMPACT,
            height = HEIGHT_COMPACT,
        )
    }

    @Test
    fun testRowsFontScale200() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyRowsScreenshot("tool_versions_rows_font_scale_200", AppTheme.Dark, fontScale = 2.0f)
    }

    @Test
    fun testEditorDark() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyEditorScreenshot("tool_versions_editor_dark", AppTheme.Dark)
    }

    @Test
    fun testEditorLight() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyEditorScreenshot("tool_versions_editor_light", AppTheme.Light)
    }

    @Test
    fun testReviewDialogDark() = runDesktopComposeUiTest(width = WIDTH_DIALOG, height = HEIGHT_DIALOG) {
        verifyReviewDialogScreenshot("tool_versions_review_dialog_dark", AppTheme.Dark)
    }

    @Test
    fun testRestoreDialogDark() = runDesktopComposeUiTest(width = WIDTH_DIALOG, height = HEIGHT_DIALOG) {
        verifyRestoreDialogScreenshot("tool_versions_restore_dialog_dark", AppTheme.Dark)
    }

    @Test
    fun testProgressStateDark() = runDesktopComposeUiTest(width = WIDTH_STANDARD, height = HEIGHT_STANDARD) {
        verifyProgressScreenshot("tool_versions_progress_dark", AppTheme.Dark)
    }

    private fun DesktopComposeUiTest.verifyRowsScreenshot(
        name: String,
        theme: AppTheme,
        effectsEnabled: Boolean = true,
        width: Int = WIDTH_STANDARD,
        height: Int = HEIGHT_STANDARD,
        fontScale: Float = 1.0f,
    ) {
        verifyBaseScreenshot(name, theme, effectsEnabled, width, height, fontScale) {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                sampleRows.forEach { rowModel ->
                    ToolGroupRow(model = rowModel, onActionClick = {})
                }
            }
        }
    }

    private fun DesktopComposeUiTest.verifyEditorScreenshot(
        name: String,
        theme: AppTheme,
        width: Int = WIDTH_STANDARD,
        height: Int = HEIGHT_STANDARD,
    ) {
        verifyBaseScreenshot(name, theme, true, width, height, 1.0f) {
            Box(modifier = Modifier.padding(Spacing.Large)) {
                EditVersionPanel(
                    draft = sampleDraft,
                    installedVersion = "v1.7.1",
                    availableRefs = sampleRefs,
                    isLoadingRefs = false,
                    resolutionState = sampleResolution,
                    onRefSelected = {},
                    onRepoUrlChanged = {},
                    onQueryChanged = {},
                    onAdvancedToggled = {},
                    onSave = {},
                    onReviewAndBuild = {},
                    onCancel = {},
                )
            }
        }
    }

    private fun DesktopComposeUiTest.verifyReviewDialogScreenshot(name: String, theme: AppTheme) {
        setContent {
            LtiTheme(appTheme = theme) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop()
                    ReviewSwitchDialog(
                        model = sampleReviewModel,
                        onConfirmBuildAndSwitch = {},
                        onDismiss = {},
                    )
                }
            }
        }
        captureRootOrDialog(name, WIDTH_DIALOG, HEIGHT_DIALOG)
    }

    private fun DesktopComposeUiTest.verifyRestoreDialogScreenshot(name: String, theme: AppTheme) {
        setContent {
            LtiTheme(appTheme = theme) {
                Box(Modifier.fillMaxSize()) {
                    GlassBackdrop()
                    RestorePreviousDialog(
                        model = sampleRestoreModel,
                        onConfirmRestore = {},
                        onDismiss = {},
                    )
                }
            }
        }
        captureRootOrDialog(name, WIDTH_DIALOG, HEIGHT_DIALOG)
    }

    private fun DesktopComposeUiTest.verifyProgressScreenshot(name: String, theme: AppTheme) {
        verifyBaseScreenshot(name, theme, true, WIDTH_STANDARD, HEIGHT_STANDARD, 1.0f) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(modifier = Modifier.width(ComponentSize.PluginInspectorWidth)) {
                    Column(
                        modifier = Modifier.padding(Spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        Text(
                            text = "Toolchain Switch in Progress",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(IconSize.Medium))
                            Spacer(modifier = Modifier.width(Spacing.Small))
                            Text(
                                text = "Building tools… (EROFS_UTILS compiling native binaries)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun DesktopComposeUiTest.verifyBaseScreenshot(
        name: String,
        theme: AppTheme,
        effectsEnabled: Boolean,
        width: Int,
        height: Int,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1.0f, fontScale = fontScale),
            ) {
                LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop()
                        content()
                    }
                }
            }
        }
        onRoot().assertIsDisplayed()
        onRoot().captureToImage().also { image ->
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            saveScreenshot(name, image)
        }
    }

    private fun DesktopComposeUiTest.captureRootOrDialog(name: String, width: Int, height: Int) {
        val roots = onAllNodes(isRoot())
        val nodeToCapture = if (roots.fetchSemanticsNodes().size > 1) roots[1] else onRoot()
        nodeToCapture.assertIsDisplayed()
        nodeToCapture.captureToImage().also { image ->
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            saveScreenshot(name, image)
        }
    }

    private fun saveScreenshot(name: String, bitmap: ImageBitmap) {
        val reportsDir = File("build/reports/screenshots/setup-versions")
        if (!reportsDir.exists()) reportsDir.mkdirs()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes != null) File(reportsDir, "$name.png").writeBytes(bytes)

        val targetFile = File("src/desktopTest/resources/screenshots/$name.png")
        if (targetFile.exists() || System.getProperty("updateScreenshots") == "true") {
            bitmap.captureRoboImage(
                filePath = targetFile.path,
                roborazziOptions = RoborazziOptions(
                    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.005f),
                    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 1.0),
                ),
            )
        }
    }
}
