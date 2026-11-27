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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.workspace.DiscoverWorkdirWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.GetRecentWorkspacesUseCase
import org.ide.lti.core.domain.usecase.workspace.OpenWorkspaceUseCase
import org.ide.lti.core.domain.usecase.workspace.RemoveRecentWorkspaceUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelWorkspaceTest {
    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())
        val workspaces = mutableMapOf<String, Workspace>()
        var shouldThrowOnGet = false
        var throwMessage = "Disk I/O error"

        override fun getWorkspaces(): Flow<List<Workspace>> = MutableStateFlow(workspaces.values.toList())

        override suspend fun getWorkspace(id: String): Workspace? {
            if (shouldThrowOnGet) throw IllegalStateException(throwMessage)
            return workspaces[id]
        }

        override suspend fun saveWorkspace(workspace: Workspace) {
            workspaces[workspace.id] = workspace
        }

        override suspend fun deleteWorkspace(id: String) {
            workspaces.remove(id)
        }

        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null

        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}

        override fun getRecentProjects(): Flow<List<RecentProject>> = recents.asStateFlow()

        override suspend fun addRecentProject(project: RecentProject) {
            recents.value = listOf(project) + recents.value.filter { it.workspaceId != project.workspaceId }
        }

        override suspend fun removeRecentProject(workspaceId: String) {
            recents.value = recents.value.filter { it.workspaceId != workspaceId }
        }

        override suspend fun clearRecentProjects() {
            recents.value = emptyList()
        }

        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> {
            val defaults =
                listOf(
                    RecentProject(
                        workspaceId = "ltirom_staging_workdir",
                        name = "LtiRom Staging WorkDir",
                        path = "~/LtiRomWorkDir",
                        lastOpened = Clock.System.now(),
                    ),
                    RecentProject(
                        workspaceId = "target_pq84p01",
                        name = "REDMAGIC Astra (PQ84P01)",
                        path = "~/LtiRomWorkDir/workspaces/target_pq84p01",
                        lastOpened = Clock.System.now(),
                    ),
                )
            val current = recents.value
            val combined = (current + defaults).distinctBy { it.workspaceId }
            recents.value = combined
            return combined
        }
    }

    @Test
    fun testSearchFilteringByProjectNameAndPath() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val project1 =
                RecentProject(
                    workspaceId = "p1",
                    name = "LtiRomProject",
                    path = "/Users/dev/LtiRomProject",
                    lastOpened = Clock.System.now(),
                )
            val project2 =
                RecentProject(
                    workspaceId = "p2",
                    name = "KmpTemplate",
                    path = "/Users/dev/KmpTemplate",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project1, project2)

            val viewModel = SetupViewModel(manager)
            val collectJob =
                backgroundScope.launch(testDispatcher) {
                    viewModel.filteredRecentProjects.collect()
                }

            // When query is empty, all projects returned
            assertEquals(2, viewModel.filteredRecentProjects.value.size)

            // Filter by name "Lti"
            viewModel.onSearchQueryChange("Lti")
            assertEquals(1, viewModel.filteredRecentProjects.value.size)
            assertEquals("LtiRomProject", viewModel.filteredRecentProjects.value.first().name)

            // Filter by path "Template"
            viewModel.onSearchQueryChange("Template")
            assertEquals(1, viewModel.filteredRecentProjects.value.size)
            assertEquals("KmpTemplate", viewModel.filteredRecentProjects.value.first().name)

            // Filter with no matches
            viewModel.onSearchQueryChange("NonExistent")
            assertEquals(0, viewModel.filteredRecentProjects.value.size)

            // Clear query
            viewModel.onSearchQueryChange("")
            assertEquals(2, viewModel.filteredRecentProjects.value.size)

            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testRemoveRecentProject() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val project =
                RecentProject(
                    workspaceId = "p1",
                    name = "LtiRomProject",
                    path = "/Users/dev/LtiRomProject",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(manager)
            assertEquals(1, repo.recents.value.size)

            viewModel.removeRecentProject("p1")
            testScheduler.advanceUntilIdle()

            assertEquals(0, repo.recents.value.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWorkdirWorkspacesDiscoveryAndUseCases() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val manager = WorkspaceManager(repo, this)
            val getRecents = GetRecentWorkspacesUseCase(repo)
            val discoverWorkdir = DiscoverWorkdirWorkspacesUseCase(repo)
            val openWorkspace = OpenWorkspaceUseCase(repo)
            val removeRecent = RemoveRecentWorkspaceUseCase(repo)

            val viewModel =
                SetupViewModel(
                    workspaceManager = manager,
                    getRecentWorkspacesUseCase = getRecents,
                    discoverWorkdirWorkspacesUseCase = discoverWorkdir,
                    openWorkspaceUseCase = openWorkspace,
                    removeRecentWorkspaceUseCase = removeRecent,
                )
            val collectJob =
                backgroundScope.launch(testDispatcher) {
                    viewModel.recentProjects.collect()
                }
            testScheduler.advanceUntilIdle()

            // Without a ready build service, discovery adds nothing (DiscoverWorkdirWorkspacesUseCaseTest covers it)
            assertEquals(0, viewModel.recentProjects.value.size)

            repo.discoverWorkspacesInWorkDir(null) // seeds two recents
            testScheduler.advanceUntilIdle()

            assertEquals(2, viewModel.recentProjects.value.size)
            assertTrue(viewModel.recentProjects.value.any { it.path == "~/LtiRomWorkDir" })
            assertTrue(viewModel.recentProjects.value.any { it.name == "REDMAGIC Astra (PQ84P01)" })

            // Test removal via use case
            viewModel.removeRecentProject("target_pq84p01")
            testScheduler.advanceUntilIdle()

            assertEquals(1, viewModel.recentProjects.value.size)
            assertEquals("ltirom_staging_workdir", viewModel.recentProjects.value.first().workspaceId)

            collectJob.cancel()
            testScheduler.advanceUntilIdle()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testContextualOpenFailureSetsWorkspaceErrorWithContextAndRetry() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            repo.shouldThrowOnGet = true
            repo.throwMessage = "Corrupted workspace XML on disk"
            val manager = WorkspaceManager(repo, this)
            val project =
                RecentProject(
                    workspaceId = "p_corrupt",
                    name = "CorruptedProject",
                    path = "/Users/dev/CorruptedProject",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(manager)
            var openedWorkspace: Workspace? = null

            viewModel.openRecentProject(project) { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            // Strict assertion: failure never invokes success callback
            assertEquals(null, openedWorkspace, "Failure must never invoke onWorkspaceOpened callback")

            // Contextual error must be recorded
            val error = viewModel.workspaceError.value
            assertTrue(error != null, "workspaceError must be populated on open failure")
            assertEquals(WorkspaceAction.OPEN, error.action)
            assertEquals("p_corrupt", error.workspaceId)
            assertEquals("/Users/dev/CorruptedProject", error.workspacePath)
            assertTrue(
                error.reason.contains("Corrupted workspace XML on disk"),
                "Error reason must preserve root cause: ${error.reason}",
            )
            assertTrue(error.retryAction != null, "Contextual retry action must be provided")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testRemovedOrMissingWorkspacePathFailsContextuallyWithoutSuccessNavigation() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            // Workspace is NOT present in repo.workspaces (simulating removed/deleted workspace directory)
            val manager = WorkspaceManager(repo, this)
            val project =
                RecentProject(
                    workspaceId = "p_missing",
                    name = "DeletedFolderProject",
                    path = "/Users/dev/DeletedFolderProject",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(manager)
            var navigationCalled = false

            viewModel.openRecentProject(project) {
                navigationCalled = true
            }
            testScheduler.advanceUntilIdle()

            assertFalse(navigationCalled, "Opening missing workspace directory must never trigger navigation")
            val error = viewModel.workspaceError.value
            assertTrue(error != null, "Missing workspace must set workspaceError")
            assertEquals(WorkspaceAction.OPEN, error.action)
            assertEquals("p_missing", error.workspaceId)
            assertEquals("/Users/dev/DeletedFolderProject", error.workspacePath)
            assertTrue(
                error.reason.contains("not found", ignoreCase = true),
                "Reason must explain workspace was not found: ${error.reason}",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWorkspaceErrorRetryActionSucceedsWhenErrorConditionResolves() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            repo.shouldThrowOnGet = true
            repo.throwMessage = "Transient lock timeout"
            val manager = WorkspaceManager(repo, this)
            val targetWorkspace =
                Workspace(
                    id = "p_retry",
                    name = "RetryableProject",
                    path = "/Users/dev/RetryableProject",
                )
            repo.workspaces[targetWorkspace.id] = targetWorkspace
            val project =
                RecentProject(
                    workspaceId = "p_retry",
                    name = "RetryableProject",
                    path = "/Users/dev/RetryableProject",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project)

            val viewModel = SetupViewModel(manager)
            var openedWorkspace: Workspace? = null

            viewModel.openRecentProject(project) { ws ->
                openedWorkspace = ws
            }
            testScheduler.advanceUntilIdle()

            assertEquals(null, openedWorkspace)
            val error = viewModel.workspaceError.value
            assertTrue(error != null, "Initial failure must set workspaceError")

            // Resolve error condition and invoke retryAction
            repo.shouldThrowOnGet = false
            error.retryAction?.invoke()
            testScheduler.advanceUntilIdle()

            assertEquals(targetWorkspace, openedWorkspace, "Successful retry must invoke onWorkspaceOpened")
            assertEquals(null, viewModel.workspaceError.value, "workspaceError must be cleared on success")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testOfflineCachedBrowsingWorksWhenReadinessPortUnavailable() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val repo = FakeWorkspaceRepository()
            val project1 =
                RecentProject(
                    workspaceId = "cached_1",
                    name = "AlphaRom",
                    path = "/data/AlphaRom",
                    lastOpened = Clock.System.now(),
                )
            val project2 =
                RecentProject(
                    workspaceId = "cached_2",
                    name = "BetaRom",
                    path = "/data/BetaRom",
                    lastOpened = Clock.System.now(),
                )
            repo.recents.value = listOf(project1, project2)
            val manager = WorkspaceManager(repo, this)

            val failingReadinessPort =
                object : EnvironmentReadinessPort {
                    override fun observe(): Flow<EnvironmentReadiness> = emptyFlow()

                    override suspend fun refresh(): EnvironmentReadiness = EnvironmentReadiness(
                        state = EnvironmentReadinessState.SERVICE_UNREACHABLE,
                        failingCheck = "WSL2 daemon bridge unreachable",
                    )

                    override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = refresh()
                }

            val viewModel =
                SetupViewModel(
                    workspaceManager = manager,
                    environmentReadinessPort = failingReadinessPort,
                )
            val collectJob =
                backgroundScope.launch(testDispatcher) {
                    viewModel.filteredRecentProjects.collect()
                }
            testScheduler.advanceUntilIdle()

            // Cached browsing works offline:
            assertEquals(2, viewModel.filteredRecentProjects.value.size)

            // Filtering works offline:
            viewModel.onSearchQueryChange("Alpha")
            assertEquals(1, viewModel.filteredRecentProjects.value.size)
            assertEquals("cached_1", viewModel.filteredRecentProjects.value.first().workspaceId)

            // Selection works offline:
            viewModel.selectWorkspace("cached_1")
            assertEquals("cached_1", viewModel.selectedWorkspaceId.value)

            // Creation prerequisites check fails indicating live daemon is required:
            val prereqResult = viewModel.workspaceBrowserState.checkCreationPrerequisites()
            assertTrue(prereqResult.isFailure, "Creation prerequisite must fail when environment is offline")
            assertTrue(prereqResult.exceptionOrNull()?.message?.contains("WSL2 daemon bridge unreachable") == true)

            collectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
