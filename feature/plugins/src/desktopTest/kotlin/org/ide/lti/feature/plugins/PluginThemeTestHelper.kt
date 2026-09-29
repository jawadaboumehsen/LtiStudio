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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.ide.lti.core.designsystem.component.layout.GlassBackdrop
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.AuthorDiagnostic
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.MarketplaceIndexDocument
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.TrustApproval
import org.ide.lti.core.domain.plugin.TrustCheck
import org.ide.lti.core.domain.plugin.TrustDecision
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.assertEquals

internal const val SCREENSHOT_WIDTH: Int = 1440
internal const val SCREENSHOT_HEIGHT: Int = 900

internal fun createInstalledUiState(): PluginManagerUiState {
    val records = listOf(
        InstalledRecord(
            identity = PackageIdentity("com.ltirom.plugin", "buildprops", "1.0.0", "sha256:1c34"),
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        ),
        InstalledRecord(
            identity = PackageIdentity("lti.official", "framework-hooks", "1.0.0", "sha256:2d45"),
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = listOf("PQ84P01"),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        ),
        InstalledRecord(
            identity = PackageIdentity("lti.community", "resource-overlay", "0.9.0", "sha256:3e56"),
            trust = TrustDecision.TRUSTED_UNVERIFIED,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        ),
    )
    val references = mapOf(
        "com.ltirom.plugin:buildprops" to listOf("Workspace 'PQ84P01'", "Workspace 'dev-test'"),
        "lti.official:framework-hooks" to listOf("Workspace 'PQ84P01'"),
        "lti.community:resource-overlay" to emptyList(),
    )
    return PluginManagerUiState(
        destination = PluginDestination.INSTALLED,
        activeWorkspaceId = "PQ84P01",
        installedPackages = records,
        packageReferences = references,
        searchQuery = "",
        selectedPackageKey = "com.ltirom.plugin:buildprops",
    )
}

internal fun createMarketplaceUiState(): PluginManagerUiState {
    val entries = listOf(
        IndexEntry(
            publisher = "Example developer",
            id = "buildprops",
            version = "1.0.0",
            sdkApiRange = "Android 13 – 15",
            targetSummary = "Manage system build properties through a configurable interface.",
            packageUrl = "https://packages.ltirom.dev/buildprops.zip",
            contentDigest = "sha256:1c34",
            signatureIdentity = "Example developer",
        ),
        IndexEntry(
            publisher = "Example developer",
            id = "framework-hooks",
            version = "1.0.0",
            sdkApiRange = "Android 13 – 15",
            targetSummary = "Example framework hook implementation",
            packageUrl = "https://packages.ltirom.dev/framework-hooks.zip",
            contentDigest = "sha256:2d45",
            signatureIdentity = "Example developer",
        ),
        IndexEntry(
            publisher = "Example developer",
            id = "resource-overlay",
            version = "0.9.0",
            sdkApiRange = "Android 12 – 15",
            targetSummary = "Example resource overlay package",
            packageUrl = "https://packages.ltirom.dev/resource-overlay.zip",
            contentDigest = "sha256:3e56",
            signatureIdentity = "Example developer",
        ),
    )
    val source = IndexSource("Development catalog · Sample source", "https://packages.ltirom.dev/index.json")
    return PluginManagerUiState(
        destination = PluginDestination.MARKETPLACE,
        activeWorkspaceId = "PQ84P01",
        marketplaceSource = source,
        marketplaceUrlInput = "https://packages.ltirom.dev/index.json",
        marketplaceEntries = entries,
        marketplaceFetchResult = IndexFetchResult.Cached(
            doc = MarketplaceIndexDocument(entries = entries),
            observedAtEpochMs = System.currentTimeMillis() - 15 * 60 * 1000L,
        ),
        isMarketplaceLoading = false,
        searchQuery = "",
        selectedPackageKey = "buildprops",
    )
}

internal fun createImportUiState(): PluginManagerUiState {
    val inspection = PackageInspection(
        identity = PackageIdentity("lti.community", "framework-hook-sample", "1.0.0", "sha256:7f83b165"),
        hasSignature = false,
        report = ValidationReport(),
        dependencies = emptyList(),
        conflicts = emptyList(),
    )
    return PluginManagerUiState(
        destination = PluginDestination.IMPORT,
        activeWorkspaceId = "PQ84P01",
        importPathInput = "C:\\Users\\dev\\Downloads\\framework-hook-sample-1.0.0.lti-mod.zip",
        importQuarantinedPath = null,
        importInspection = inspection,
        isLegacyUnsupportedFormat = false,
        legacyFormatName = null,
    )
}

internal fun createAuthorToolsUiState(): PluginManagerUiState {
    val diagnostics = listOf(
        AuthorDiagnostic(
            severity = "Info",
            code = "AUTHOR_COMPILE",
            file = "ExampleHook.kt",
            message = "Payload compiled",
        ),
        AuthorDiagnostic(
            severity = "Info",
            code = "AUTHOR_TESTS",
            file = "tests",
            message = "Fixture checks passed",
        ),
    )
    val taskResult = AuthorTaskResult.Success(
        stdout = "Build succeeded",
        stderr = "",
        diagnostics = diagnostics,
    )
    return PluginManagerUiState(
        destination = PluginDestination.AUTHOR_TOOLS,
        activeWorkspaceId = "PQ84P01",
        authorProjectPathInput = "C:\\Users\\dev\\Projects\\framework-hook-sample",
        authorTrustCheck = TrustCheck.Approved(
            TrustApproval("C:\\Users\\dev\\Projects\\framework-hook-sample", "sha256:abcd", 1718353323000L),
        ),
        authorSelectedTask = "packageMod",
        authorTaskResult = taskResult,
        isAuthorTaskRunning = false,
    )
}

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.renderAndCapture(
    theme: AppTheme,
    state: PluginManagerUiState,
    name: String,
    effectsEnabled: Boolean = true,
) {
    setContent {
        LtiTheme(appTheme = theme, effectsEnabled = effectsEnabled) {
            Box(Modifier.fillMaxSize()) {
                GlassBackdrop()
                PluginManagerContent(
                    state = state,
                    actions = PluginManagerActions(),
                )
            }
        }
    }
    capture(name)
}

@OptIn(ExperimentalTestApi::class)
internal fun DesktopComposeUiTest.capture(name: String) {
    onRoot().assertIsDisplayed()
    onRoot().captureToImage().also { image ->
        assertEquals(SCREENSHOT_WIDTH, image.width)
        assertEquals(SCREENSHOT_HEIGHT, image.height)
        saveScreenshot(name, image)
    }
}

internal fun saveScreenshot(name: String, bitmap: ImageBitmap) {
    val reportsDir = File("build/reports/screenshots/plugins")
    if (!reportsDir.exists()) reportsDir.mkdirs()
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
    if (bytes != null) File(reportsDir, "$name.png").writeBytes(bytes)

    val targetPath = "src/desktopTest/resources/screenshots/$name.png"
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
