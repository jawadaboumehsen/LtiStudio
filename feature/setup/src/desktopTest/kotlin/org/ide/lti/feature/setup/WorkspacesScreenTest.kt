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

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.datetime.Clock
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.feature.setup.steps.ProjectsStepContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop Compose UI tests for the Workspaces destination (FR-001, FR-002, FR-012).
 *
 * Fully DI-free fixtures validating:
 * - Empty state ("No Recent Workspaces") with New/Open actions.
 * - No-match state ("No Matching Workspaces") when filter produces 0 results.
 * - Virtualized recents display with titles, paths, and target chips.
 * - Keyboard shortcuts (Ctrl+N, Ctrl+O).
 * - Safe legacy route resolution ("launchpad", "projects" -> WORKSPACES).
 */
@OptIn(ExperimentalTestApi::class)
class WorkspacesScreenTest {

    @Test
    fun testEmptyStateShowsNewAndOpenWorkspaceActions() = runDesktopComposeUiTest(width = 1120, height = 700) {
        var createCalled = false
        var openCalled = false

        setContent {
            LtiTheme {
                ProjectsStepContent(
                    state = SetupUiState(
                        recentProjects = emptyList(),
                        filteredProjects = emptyList(),
                        searchQuery = "",
                    ),
                    actions = SetupUiActions(
                        onCreateWorkspace = { createCalled = true },
                        onOpenFolder = { openCalled = true },
                    ),
                )
            }
        }

        onNodeWithText("No Recent Workspaces").assertIsDisplayed()
        onNodeWithText("New Workspace").assertIsDisplayed().performClick()
        assertTrue(createCalled, "Clicking New Workspace must invoke onCreateWorkspace")

        onNodeWithText("Open Workspace").assertIsDisplayed().performClick()
        assertTrue(openCalled, "Clicking Open Workspace must invoke onOpenFolder")
    }

    @Test
    fun testNoMatchStateShowsDistinctMessage() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val existingProject = RecentProject(
            workspaceId = "ws_1",
            name = "AOSP_Build",
            path = "C:/Workspaces/AOSP_Build",
            lastOpened = Clock.System.now(),
        )

        setContent {
            LtiTheme {
                ProjectsStepContent(
                    state = SetupUiState(
                        recentProjects = listOf(existingProject),
                        filteredProjects = emptyList(),
                        searchQuery = "UnknownROM",
                    ),
                    actions = SetupUiActions(),
                )
            }
        }

        onNodeWithText("No Matching Workspaces").assertIsDisplayed()
        onNodeWithText("No workspace matches 'UnknownROM'. Try refining your search query.").assertIsDisplayed()
    }

    @Test
    fun testRecentsListDisplaysWorkspaceEntries() = runDesktopComposeUiTest(width = 1120, height = 700) {
        val now = Clock.System.now()
        val p1 = RecentProject(
            workspaceId = "p1",
            name = "RedMagicKernel",
            path = "C:/RomDev/RedMagicKernel",
            lastOpened = now,
            targetDisplayName = "REDMAGIC Astra",
            targetBinding = TargetBinding("pq84p01", 1),
        )
        val p2 = RecentProject(
            workspaceId = "p2",
            name = "LineageOS_Port",
            path = "D:/LineageOS_Port",
            lastOpened = now,
        )

        var openedProject: RecentProject? = null

        setContent {
            LtiTheme {
                ProjectsStepContent(
                    state = SetupUiState(
                        recentProjects = listOf(p1, p2),
                        filteredProjects = listOf(p1, p2),
                        searchQuery = "",
                    ),
                    actions = SetupUiActions(
                        onRecentProjectClick = { openedProject = it },
                    ),
                )
            }
        }

        onNodeWithText("RedMagicKernel").assertIsDisplayed()
        onNodeWithText("C:/RomDev/RedMagicKernel").assertIsDisplayed()
        onNodeWithText("REDMAGIC Astra").assertIsDisplayed()
        onNodeWithText("LineageOS_Port").assertIsDisplayed()
        onNodeWithText("Needs target").assertIsDisplayed()

        onNodeWithText("RedMagicKernel").performClick()
        assertEquals("p1", openedProject?.workspaceId)
    }

    @Test
    fun testKeyboardShortcutsCtrlNAndCtrlOTriggerActions() = runDesktopComposeUiTest(width = 1120, height = 700) {
        var createCalled = false
        var openCalled = false

        setContent {
            LtiTheme {
                ProjectsStepContent(
                    state = SetupUiState(
                        recentProjects = emptyList(),
                        filteredProjects = emptyList(),
                    ),
                    actions = SetupUiActions(
                        onCreateWorkspace = { createCalled = true },
                        onOpenFolder = { openCalled = true },
                    ),
                )
            }
        }

        // Trigger Ctrl+N
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.N)
            keyUp(Key.N)
            keyUp(Key.CtrlLeft)
        }
        assertTrue(createCalled, "Ctrl+N keyboard shortcut must trigger onCreateWorkspace (FR-012)")

        // Trigger Ctrl+O
        onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.O)
            keyUp(Key.O)
            keyUp(Key.CtrlLeft)
        }
        assertTrue(openCalled, "Ctrl+O keyboard shortcut must trigger onOpenFolder (FR-012)")
    }

    @Test
    fun testLegacyRouteResolvesToWorkspaces() = runDesktopComposeUiTest(width = 1120, height = 700) {
        // Contract assertion: legacy routes map to WORKSPACES (FR-001, FR-004)
        assertEquals(SetupDestination.WORKSPACES, SetupDestination.fromId("launchpad"))
        assertEquals(SetupDestination.WORKSPACES, SetupDestination.fromId("projects"))
        assertEquals(SetupDestination.WORKSPACES, SetupDestination.fromId("unknown_route"))

        // The legacy "launchpad" route remains accepted and lands on the Workspaces list;
        // the old overview page it used to show was removed.
        setContent {
            LtiTheme {
                SetupScreenContent(
                    state = SetupUiState(),
                    actions = SetupUiActions(),
                    initialTab = SetupTab.PROJECTS,
                )
            }
        }

        onNodeWithText("Workspaces").assertIsDisplayed()
    }
}
