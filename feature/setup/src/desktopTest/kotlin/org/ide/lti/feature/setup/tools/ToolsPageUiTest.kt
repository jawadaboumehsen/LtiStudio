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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationKind
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.TagRefInfo
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolOperation
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ToolsPageUiTest {
    private class FakePort : ToolStorePort {
        var inventory = ToolInventory(emptyMap())
        var customRefs: RefListing? = null

        override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = Result.success(inventory)

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> = Result.success(
            customRefs ?: RefListing(listOf(TagRefInfo("v1.8.2", "2".repeat(40))), emptyList()),
        )

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = "2".repeat(40)))

        override fun install(request: InstallRequest): Flow<InstallEvent> = emptyFlow()

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
            Result.success(GroupLinks(versionId, null))

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(
            PinnedTools(emptyMap(), emptyMap()),
        )
    }

    private class InMemorySelectionRepo : ToolchainSelectionRepository {
        private val trusted = mutableSetOf<String>()

        override suspend fun recordTrust(repoUrl: String): Result<Unit> {
            trusted += repoUrl
            return Result.success(Unit)
        }

        override suspend fun isTrusted(repoUrl: String): Boolean = repoUrl in trusted
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
        source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1".repeat(40)), "1".repeat(40)),
        recipe = RecipeSummary("cmake", 1),
        outputs = emptyList(),
        builtAt = "2026-10-01",
        intact = intact,
    )

    @Test
    fun toolsTableRendersAndHandlesClicks() = runDesktopComposeUiTest {
        val rows =
            listOf(
                ToolRowPresentation(
                    groupId = "android-tools",
                    displayName = "android-tools",
                    inUseLabel = "sixteen",
                    statusLabel = "Ready",
                    statusKind = ToolRowStatusKind.Ready,
                ),
            )
        var selected: ToolGroupId? = null

        setContent {
            LtiTheme {
                ToolsTable(
                    rows = rows,
                    selectedGroupId = "android-tools",
                    onSelectGroup = { selected = it },
                )
            }
        }

        onNodeWithText("android-tools").assertIsDisplayed()
        onNodeWithText("android-tools").performClick()
        assertEquals(ToolGroupId("android-tools"), selected)
    }

    @Test
    fun keyboardNavigationMovesThroughRowsAndOpensClosesInspector() = runDesktopComposeUiTest {
        val port = FakePort()
        val vAndroid = installedVersion(ToolGroupId("android-tools"), "sixteen-f9bef94", "sixteen")
        val vErofs = installedVersion(ToolGroupId("erofs-utils"), "v1.8.10-251217", "v1.8.10")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("android-tools") to
                        GroupState(ToolGroupId("android-tools"), vAndroid, null, listOf(vAndroid)),
                    ToolGroupId("erofs-utils") to GroupState(ToolGroupId("erofs-utils"), vErofs, null, listOf(vErofs)),
                ),
            )
        val manager = ToolManager(port)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))

        setContent {
            LtiTheme {
                val rows by state.rows.collectAsState()
                val selectedGroup by state.selectedGroup.collectAsState()
                val isInspectorOpen by state.isInspectorOpen.collectAsState()

                Row(modifier = Modifier.fillMaxSize()) {
                    ToolsTable(
                        rows = rows,
                        selectedGroupId = selectedGroup.value,
                        onSelectGroup = { state.selectGroup(it) },
                        onMoveUp = { state.selectPreviousGroup() },
                        onMoveDown = { state.selectNextGroup() },
                        onOpenInspector = { state.openInspector() },
                        onCloseInspector = { state.closeInspector() },
                        modifier = Modifier.weight(1f),
                    )
                    if (isInspectorOpen) {
                        ToolInspector(state = state)
                    }
                }
            }
        }

        assertEquals(ToolGroupId("android-tools"), state.selectedGroup.value)

        val androidRow = onNode(hasText("android-tools") and hasClickAction())
        val erofsRow = onNode(hasText("erofs-utils") and hasClickAction())

        // Down arrow moves selection to erofs-utils
        androidRow.performClick()
        androidRow.performKeyInput {
            pressKey(Key.DirectionDown)
        }
        assertEquals(ToolGroupId("erofs-utils"), state.selectedGroup.value)

        // Up arrow moves selection back to android-tools
        erofsRow.performClick()
        erofsRow.performKeyInput {
            pressKey(Key.DirectionUp)
        }
        assertEquals(ToolGroupId("android-tools"), state.selectedGroup.value)

        // Esc closes inspector
        androidRow.performKeyInput {
            pressKey(Key.Escape)
        }
        assertFalse(state.isInspectorOpen.value)

        // Enter opens inspector
        androidRow.performKeyInput {
            pressKey(Key.Enter)
        }
        assertTrue(state.isInspectorOpen.value)
    }

    @Test
    fun inspectorIsBoundedSiblingWithoutInfiniteHeightCrashInScrollingPage() = runDesktopComposeUiTest {
        val port = FakePort()
        val vAndroid = installedVersion(ToolGroupId("android-tools"), "sixteen-f9bef94", "sixteen")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("android-tools") to
                        GroupState(ToolGroupId("android-tools"), vAndroid, null, listOf(vAndroid)),
                ),
            )
        val manager = ToolManager(port)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))

        setContent {
            LtiTheme {
                Column(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(ComponentSize.MaxPanelHeight)) {
                        val rows by state.rows.collectAsState()
                        ToolsTable(
                            rows = rows,
                            selectedGroupId = "android-tools",
                            onSelectGroup = { state.selectGroup(it) },
                            modifier = Modifier.weight(1f),
                        )
                        ToolInspector(
                            state = state,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            }
        }

        onNode(hasText("android-tools") and hasClickAction()).assertIsDisplayed()
        onNodeWithText("Installed").assertIsDisplayed()
    }

    @Test
    fun trustDialogAppearsOncePerUrl() = runDesktopComposeUiTest {
        val port = FakePort()
        val repo = InMemorySelectionRepo()
        val manager = ToolManager(port = port, selectionRepository = repo)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        val customUrl = "https://github.com/custom-fork/android-tools.git"

        setContent {
            LtiTheme {
                val showTrust by state.showTrustDialog.collectAsState()
                val untrustedUrl by state.untrustedRepoUrl.collectAsState()

                if (showTrust && untrustedUrl != null) {
                    TrustRepositoryDialog(
                        repoUrl = untrustedUrl!!,
                        onConfirm = { state.confirmTrustAndProceed() },
                        onDismiss = { state.dismissTrustDialog() },
                    )
                }
            }
        }

        var executedCount = 0
        runBlocking {
            state.requestWithTrustCheck(customUrl) {
                executedCount++
            }
        }

        onNodeWithText("Trust External Repository?").assertIsDisplayed()
        onNodeWithText(customUrl).assertIsDisplayed()
        assertEquals(0, executedCount)

        onNodeWithText("Trust and proceed").performClick()
        assertFalse(state.showTrustDialog.value)
        assertEquals(1, executedCount, "agreeing carries on with the request that asked for the trust")

        runBlocking {
            state.requestWithTrustCheck(customUrl) {
                executedCount++
            }
        }

        assertEquals(2, executedCount, "a trusted URL asks no second time")
        assertFalse(state.showTrustDialog.value)
    }

    @Test
    fun releaseGroupsShowOnlyCuratedVersionsWithNoBranchCommitOrCustom() = runDesktopComposeUiTest {
        val port = FakePort()
        val vGh = installedVersion(ToolGroupId("gh"), "2.97.0", "v2.97.0")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("gh") to GroupState(ToolGroupId("gh"), vGh, null, listOf(vGh)),
                ),
            )
        val manager = ToolManager(port)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(ToolGroupId("gh"))

        setContent {
            LtiTheme {
                ToolInspector(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        onAllNodesWithText("Checksum verified")[0].assertIsDisplayed()
        onAllNodesWithText("2.97.0")[0].assertIsDisplayed()

        onNodeWithText("Branch").assertDoesNotExist()
        onNodeWithText("Commit").assertDoesNotExist()
        onNodeWithText("Custom URL").assertDoesNotExist()
    }

    @Test
    fun tagListIsLazyWith500TagsScrollingInsideBounds() = runDesktopComposeUiTest {
        val port = FakePort()
        val tags500 = (1..500).map { TagRefInfo("v1.0.$it", "a".repeat(40)) }
        port.customRefs = RefListing(tags = tags500, branches = emptyList())

        val vErofs = installedVersion(ToolGroupId("erofs-utils"), "v1.8.10", "v1.8.10")
        port.inventory =
            ToolInventory(
                mapOf(
                    ToolGroupId("erofs-utils") to GroupState(ToolGroupId("erofs-utils"), vErofs, null, listOf(vErofs)),
                ),
            )
        val manager = ToolManager(port)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(ToolGroupId("erofs-utils"))
        state.loadRefs()

        setContent {
            LtiTheme {
                ToolInspector(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        onNodeWithText("v1.0.1").assertIsDisplayed()
        onNodeWithText("v1.0.499").assertDoesNotExist()
    }

    @Test
    fun withReducedMotionInstallProgressShowsAsStaticTextAndNoInfiniteAnimationRuns() = runDesktopComposeUiTest {
        val port = FakePort()
        val group = ToolGroupId("erofs-utils")
        val vErofs = installedVersion(group, "v1.8.10", "v1.8.10")
        val op =
            ToolOperation(
                opId = "op-1",
                group = group,
                kind = OperationKind.Install,
                phase = OperationPhase.Building,
            )
        port.inventory =
            ToolInventory(
                mapOf(
                    group to GroupState(group, vErofs, null, listOf(vErofs), operation = op),
                ),
            )
        val manager = ToolManager(port)
        runBlocking { manager.refresh() }
        val state = ToolsPageState(manager, CoroutineScope(Dispatchers.Unconfined))
        state.selectGroup(group)

        setContent {
            LtiTheme(reducedMotion = true) {
                ToolInspector(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        onNodeWithText("Installing · Building").assertIsDisplayed()
        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }
}
