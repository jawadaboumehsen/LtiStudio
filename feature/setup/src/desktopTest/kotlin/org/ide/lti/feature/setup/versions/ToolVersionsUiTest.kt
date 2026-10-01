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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.UpdateAvailability
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.ToolSelection
import org.ide.lti.feature.setup.versions.components.ToolVersionsInspector
import org.ide.lti.feature.setup.versions.components.ToolVersionsSection
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Desktop UI tests for Tool Versions Section, Edit Panel, and Review Dialog (008 T072).
 */
@OptIn(ExperimentalTestApi::class)
class ToolVersionsUiTest {
    private class UiTestSelectionRepository(initialSelections: DistroToolSelections = DistroToolSelections()) :
        ToolchainSelectionRepository {
        private var selections = initialSelections

        override suspend fun desiredSelections(distro: String): DistroToolSelections = selections

        override suspend fun saveSelections(
            distro: String,
            expectedRevision: Long,
            desired: Map<ToolGroupId, ToolSelection>,
        ): Result<Long> {
            val next = selections.revision + 1
            selections = DistroToolSelections(desired = desired.mapKeys { it.key.value }, revision = next)
            return Result.success(next)
        }

        override suspend fun recordTrust(repoUrl: String): Result<Unit> = Result.success(Unit)

        override suspend fun isTrusted(repoUrl: String): Boolean = false
    }

    private class UiTestSourceResolver : SourceResolverPort {
        val availableListing =
            RefListing(
                tags = listOf("v1.7.0", "v1.8.0", "v2.0.0"),
                branches = listOf("master", "develop"),
            )

        override suspend fun listRefs(group: ToolGroupId, repoUrl: String?): RefListing = availableListing

        override suspend fun resolve(selection: ToolSelection): ResolveOutcome = ResolveOutcome.Resolved(
            ResolvedInput.Git(
                group = ToolGroupId(selection.group),
                repoUrl = selection.repoUrl,
                ref = selection.ref,
                commit = "0123456789abcdef0123456789abcdef01234567",
                resolvedAt = 1000L,
            ),
        )

        override suspend fun checkLayout(input: ResolvedInput.Git): CompatibilityResult =
            CompatibilityResult.LayoutCompatible

        override suspend fun updateAvailability(frozen: ResolvedInput.Git): UpdateAvailability =
            UpdateAvailability.UpToDate
    }

    private class UiTestInstallationPort(val activeId: String? = "install-1") : ToolchainInstallationPort {
        override suspend fun state(): InstalledToolchain = InstalledToolchain(
            activeInstallId = activeId?.let { InstallId(it) },
            previousInstallId = null,
        )

        override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId = InstallId("cand-1")

        override suspend fun activate(request: ActivationRequest): ActivationOutcome =
            ActivationOutcome.Committed(request.targetInstallId)

        override suspend fun activationOutcome(requestId: String): ActivationOutcome? = null

        override suspend fun cleanup(references: Set<InstallId>): CleanupReport = CleanupReport()
    }

    @Test
    fun testRowStatusesAndHeadersDisplayed() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val repo = UiTestSelectionRepository()
        val resolver = UiTestSourceResolver()
        val installPort = UiTestInstallationPort()
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = repo,
                sourceResolver = resolver,
                installationPort = installPort,
            )

        setContent {
            LtiTheme {
                VersionsPage(state)
            }
        }

        onNodeWithText("Tool Build Groups & Versions").assertIsDisplayed()
        onNodeWithText(
            "Configure source versions and track release updates for native build groups",
        ).assertIsDisplayed()
        onNodeWithText("erofs-utils").assertIsDisplayed()
        onNodeWithText("android-tools").assertIsDisplayed()
    }

    @Test
    fun testEditPanelFlowOpenFilterAndCancel() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val repo = UiTestSelectionRepository()
        val resolver = UiTestSourceResolver()
        val installPort = UiTestInstallationPort()
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = repo,
                sourceResolver = resolver,
                installationPort = installPort,
            )

        setContent {
            LtiTheme {
                VersionsPage(state)
            }
        }

        assertNull(state.selectedGroupId.value)
        state.startEdit(ToolGroupId("erofs-utils"))

        onNodeWithText("Edit erofs-utils").assertIsDisplayed()
        onNodeWithText("Currently Installed").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        assertNull(state.selectedGroupId.value)
    }

    @Test
    fun testInspectorBesideScrollingPageDoesNotCrash() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = UiTestSelectionRepository(),
                sourceResolver = UiTestSourceResolver(),
                installationPort = UiTestInstallationPort(),
            )

        setContent {
            LtiTheme {
                VersionsPage(state)
            }
        }

        state.startEdit(ToolGroupId("erofs-utils"))

        onNodeWithText("Edit erofs-utils").assertIsDisplayed()
    }

    @Test
    fun testEditedRowIsMarkedSelectedAndEscapeClosesInspector() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = UiTestSelectionRepository(),
                sourceResolver = UiTestSourceResolver(),
                installationPort = UiTestInstallationPort(),
            )
        setContent { LtiTheme { VersionsPage(state) } }

        state.startEdit(ToolGroupId("erofs-utils"))
        waitForIdle()

        onNode(hasContentDescription("Tool group erofs-utils", substring = true) and isSelected()).assertExists()
        onNode(hasContentDescription("Tool group android-tools", substring = true) and isSelected())
            .assertDoesNotExist()

        onNodeWithText("Edit erofs-utils").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertNull(state.selectedGroupId.value)
    }

    @Test
    fun testReviewDialogContentDisplayed() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val repo = UiTestSelectionRepository()
        val resolver = UiTestSourceResolver()
        val installPort = UiTestInstallationPort()
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = repo,
                sourceResolver = resolver,
                installationPort = installPort,
            )

        setContent {
            LtiTheme {
                VersionsPage(state)
            }
        }

        state.openReview()

        onNodeWithText("Review Toolchain Switch").assertIsDisplayed()
        onNodeWithText(
            "Your current tools stay active until the new toolchain is verified.",
        ).assertIsDisplayed()
        onNodeWithText("Unchanged groups are reused from your artifact cache.").assertIsDisplayed()
        onNodeWithText("Build & switch").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        assertFalse(state.isReviewOpen.value)
    }

    @Test
    fun testEscapeKeyDismissesEditPanel() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val repo = UiTestSelectionRepository()
        val resolver = UiTestSourceResolver()
        val installPort = UiTestInstallationPort()
        val state =
            ToolVersionsState(
                scope = CoroutineScope(Dispatchers.Unconfined),
                repository = repo,
                sourceResolver = resolver,
                installationPort = installPort,
            )

        setContent {
            LtiTheme {
                VersionsPage(state)
            }
        }

        state.startEdit(ToolGroupId("erofs-utils"))
        assertNotNull(state.selectedGroupId.value)
        onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(Key.Escape) }
        assertNull(state.selectedGroupId.value)
    }
}

/** Same layout as ToolsStepContent: a scrolling page with the inspector as its sibling. */
@Composable
private fun VersionsPage(state: ToolVersionsState) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ToolVersionsSection(state = state)
        }
        ToolVersionsInspector(state = state)
    }
}
