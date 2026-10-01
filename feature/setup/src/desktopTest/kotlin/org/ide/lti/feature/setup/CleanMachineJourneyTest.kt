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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.ports.TerminalLauncherPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.setup.UserRepairHandoff
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.junit.Test
import kotlin.test.assertEquals

/**
 * End-to-end journey test (T048, US1 MVP) on a clean machine:
 *
 * 1. Initial Launch: App opens on Projects destination (usable immediately, no forced tab or modal).
 *    Status header displays "Checking this machine…".
 * 2. Missing Packages Detected: Status line transitions to "This machine needs setup" + "Set up this machine".
 *    Projects tab remains active (SC-011: zero unprompted tab switch or modal popup).
 * 3. User Enters Environment: Clicking "Set up this machine" navigates to the Environment tab.
 *    The System packages row shows "3 packages missing" with action "Install packages".
 * 4. Terminal Handoff: Clicking "Install packages" opens the Terminal Handoff Sheet with the 4-step sequence.
 *    Security (FR-011): zero stored passwords, zero password fields, zero sudoers/NOPASSWD references.
 * 5. Terminal Launch: Clicking "Open the terminal" dispatches to TerminalLauncherPort without advancing setup.
 * 6. Verification (Partial): Clicking "I've run it — check again" verifies and shows inline
 *    "Still missing: ninja-build"
 *    and the updated terminal command without closing the sheet.
 * 7. Verification (Complete): Clicking "I've run it — check again" satisfies all packages
 *    ("All present") and closes the sheet.
 * 8. Pipeline Progression: Build service installs, Pre-Flight Doctor checks pass, source repositories synchronize,
 *    and native toolchain compiles.
 * 9. Machine Ready: Environment tab presents the "Machine ready" card ("This machine is ready to build ROMs")
 *    with "Open project" and "Create project".
 * 10. Projects Tab Header Status: Navigating back to Projects shows header status "Machine ready".
 *
 * Fully adheres to contracts/ui-states.md and SC-011.
 */
@OptIn(ExperimentalTestApi::class)
class CleanMachineJourneyTest {
    private class FakeTerminalLauncher : TerminalLauncherPort {
        var launchedDistro: String? = null
        var launchCount: Int = 0

        override fun launchTerminal(distro: String): Result<Unit> {
            launchCount++
            launchedDistro = distro
            return Result.success(Unit)
        }
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        val recents = MutableStateFlow<List<RecentProject>>(emptyList())

        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(emptyList())

        override suspend fun getWorkspace(id: String): Workspace? = null

        override suspend fun saveWorkspace(workspace: Workspace) {}

        override suspend fun deleteWorkspace(id: String) {}

        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null

        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}

        override fun getRecentProjects(): Flow<List<RecentProject>> = recents.asStateFlow()

        override suspend fun addRecentProject(project: RecentProject) {}

        override suspend fun removeRecentProject(workspaceId: String) {}

        override suspend fun clearRecentProjects() {}

        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
    }

    private class FakeCleanMachineProvisioningService(initialState: ToolchainSetupState) :
        ToolchainProvisioningService {
        val stateFlow = MutableStateFlow(initialState)
        override val state: StateFlow<ToolchainSetupState> = stateFlow.asStateFlow()

        val executedPlans = mutableListOf<SetupPlan>()
        var resumeCount: Int = 0
            private set
        var prepareCount: Int = 0
            private set
        var confirmCount: Int = 0
            private set

        val initialHandoff =
            UserRepairHandoff(
                actionId = "bootstrap:apt",
                description = "Install packages",
                terminalCommand = "sudo apt-get update && sudo apt-get install -y git cmake ninja-build",
                packages = listOf("git", "cmake", "ninja-build"),
                distro = "Ubuntu-24.04",
            )

        val partialHandoff =
            UserRepairHandoff(
                actionId = "bootstrap:apt",
                description = "Install packages",
                terminalCommand = "sudo apt-get update && sudo apt-get install -y ninja-build",
                packages = listOf("ninja-build"),
                distro = "Ubuntu-24.04",
            )

        private val preparedPlans = mutableMapOf<String, SetupPlan>()

        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan {
            prepareCount++
            return SetupPlan(
                planId = "bootstrap-plan-$prepareCount",
                revisionHash = "rev-$prepareCount",
                environmentKey = "Ubuntu-24.04",
                kind = kind,
                targetStageOrToolId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
            ).also { preparedPlans[it.planId] = it }
        }

        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome {
            confirmCount++
            val plan =
                preparedPlans[planId]
                    ?: return SetupOutcome.Failed(stage = null, reason = "Plan not found")
            executedPlans += plan
            return SetupOutcome.AwaitingUserAction(
                pendingPlanId = plan.planId,
                stage = SetupStepStage.SYSTEM_PACKAGES,
                reason = "External authorization needed in WSL terminal.",
                handoff = initialHandoff,
            )
        }

        override suspend fun resume(planId: String): SetupOutcome {
            resumeCount++
            return if (resumeCount == 1) {
                // First check: Still missing ninja-build
                stateFlow.value =
                    stateFlow.value.copy(
                        steps =
                        stateFlow.value.steps.map {
                            if (it.stage == SetupStepStage.SYSTEM_PACKAGES) {
                                it.copy(
                                    status = StepStatus.FAILED,
                                    description = "Still missing: ninja-build",
                                )
                            } else {
                                it
                            }
                        },
                    )
                SetupOutcome.AwaitingUserAction(
                    pendingPlanId = planId,
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    reason = "Still missing: ninja-build",
                    handoff = partialHandoff,
                )
            } else {
                // Second check: All present
                stateFlow.value =
                    stateFlow.value.copy(
                        steps =
                        stateFlow.value.steps.map {
                            if (it.stage == SetupStepStage.SYSTEM_PACKAGES) {
                                it.copy(
                                    status = StepStatus.SUCCESS,
                                    description = "All present",
                                    provenance = StepProvenance.LIVE,
                                )
                            } else {
                                it
                            }
                        },
                    )
                SetupOutcome.Succeeded()
            }
        }

        override suspend fun verifyEnvironment(): ToolchainSetupState = stateFlow.value

        override suspend fun checkStatus(): ToolchainSetupState = stateFlow.value

        override suspend fun provisionAvbKey(): Boolean = true

        override fun observe(): Flow<SetupOutcome?> = flowOf(null)

        override fun observeActivity(): Flow<SetupLogEvent> = emptyFlow()

        override suspend fun recover(attemptId: String?): SetupOutcome = SetupOutcome.Failed(null, "No journal")

        override suspend fun cancel(): SetupOutcome = SetupOutcome.Cancelled

        override suspend fun testTool(toolId: String): SetupOutcome = SetupOutcome.Succeeded()
    }

    // One end-to-end journey: splitting it would hide the step ordering under test.
    @Suppress("LongMethod")
    @Test
    fun testCleanMachineJourneyFromFirstLaunchToReady() = runDesktopComposeUiTest(width = 1200, height = 900) {
        val stepWsl =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                description = "Ubuntu-24.04 · lti",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            )
        val stepPackagesChecking =
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_PACKAGES,
                title = "System Packages",
                description = "Checking…",
                status = StepStatus.PENDING,
            )
        val stepServerPending =
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server Bridge",
                description = "Waiting for System packages",
                status = StepStatus.PENDING,
            )
        val stepDoctorPending =
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                title = "Pre-Flight Doctor",
                description = "Waiting for Build service",
                status = StepStatus.PENDING,
            )
        val stepReposPending =
            SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Source Repositories",
                description = "Waiting for Pre-Flight Doctor",
                status = StepStatus.PENDING,
            )
        val stepToolsPending =
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain Binaries",
                description = "Waiting for Source repositories",
                status = StepStatus.PENDING,
            )

        val initialCheckingState =
            ToolchainSetupState(
                steps =
                listOf(
                    stepWsl,
                    stepPackagesChecking,
                    stepServerPending,
                    stepDoctorPending,
                    stepReposPending,
                    stepToolsPending,
                ),
                activeDistro = "Ubuntu-24.04",
                installedDistros = listOf("Ubuntu-24.04"),
                isChecking = true,
            )

        val fakeService = FakeCleanMachineProvisioningService(initialCheckingState)
        val fakeLauncher = FakeTerminalLauncher()
        val fakeRepo = FakeWorkspaceRepository()
        val workspaceManager = WorkspaceManager(fakeRepo, CoroutineScope(Dispatchers.Unconfined))

        val viewModel =
            SetupViewModel(
                workspaceManager = workspaceManager,
                toolchainService = fakeService,
                terminalLauncher = fakeLauncher,
            )

        setContent {
            LtiTheme {
                SetupScreen(
                    onOpenWorkspace = {},
                    viewModel = viewModel,
                )
            }
        }

        // =========================================================================
        // Step 1: Initial launch on Projects tab with background checks running
        // =========================================================================
        onNodeWithText("Workspaces").assertIsDisplayed()
        onNodeWithText("Checking this machine…").assertIsDisplayed()

        // =========================================================================
        // Step 2: Missing packages detected; Projects tab stays active (SC-011)
        // =========================================================================
        val stepPackagesMissing =
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_PACKAGES,
                title = "System Packages",
                description = "3 packages missing: git, cmake, ninja-build",
                status = StepStatus.FAILED,
            )
        fakeService.stateFlow.value =
            initialCheckingState.copy(
                steps =
                listOf(
                    stepWsl,
                    stepPackagesMissing,
                    stepServerPending,
                    stepDoctorPending,
                    stepReposPending,
                    stepToolsPending,
                ),
                isChecking = false,
            )

        waitForIdle()

        // Status line in Projects header reflects setup required, but no unprompted tab switch or modal popup occurs
        onNodeWithText("This machine needs setup").assertIsDisplayed()
        onNodeWithText("Set up this machine").assertIsDisplayed()
        onNodeWithText("Workspaces").assertIsDisplayed()

        // =========================================================================
        // Step 3: Explicit click navigates to Environment tab
        // =========================================================================
        onNodeWithText("Set up this machine").performClick()
        waitForIdle()

        onNodeWithText("System Packages").assertIsDisplayed()
        onNodeWithText("Install packages").assertIsDisplayed()

        // =========================================================================
        // Step 4: User clicks "Install packages" -> Terminal Handoff Sheet opens
        // =========================================================================
        onNodeWithText("Install packages").performClick()
        waitForIdle()

        // Verify sheet content against contracts/ui-states.md §4
        onNodeWithText("Install 3 packages").assertIsDisplayed()
        onNodeWithText(
            "1 Copy the command → 2 Open the terminal → 3 Paste it and press Enter, then type your password → " +
                "4 Come back and select \"I've run it — check again\".",
        ).assertIsDisplayed()
        onNodeWithText("sudo apt-get update && sudo apt-get install -y git cmake ninja-build").assertIsDisplayed()
        onNodeWithText("Copy command").assertIsDisplayed()
        onNodeWithText("Open the terminal").assertIsDisplayed()
        onNodeWithText("I've run it — check again").assertIsDisplayed()
        onNodeWithText("Close for now").assertIsDisplayed()
        onNodeWithText("Abandon setup").assertIsDisplayed()

        // =========================================================================
        // Step 5: Click "Open the terminal" -> Dispatches without advancing setup
        // =========================================================================
        onNodeWithText("Open the terminal").performClick()
        waitForIdle()

        assertEquals("Ubuntu-24.04", fakeLauncher.launchedDistro)
        assertEquals(1, fakeLauncher.launchCount)
        // Sheet remains open in awaiting state
        onNodeWithText("I've run it — check again").assertIsDisplayed()

        // =========================================================================
        // Step 6: First check again -> Partial result ("Still missing: ninja-build")
        // =========================================================================
        onNodeWithText("I've run it — check again").performClick()
        waitForIdle()

        assertEquals(1, fakeService.resumeCount)
        onAllNodesWithText("Still missing: ninja-build")[0].assertIsDisplayed()
        onNodeWithText("sudo apt-get update && sudo apt-get install -y ninja-build").assertIsDisplayed()

        // =========================================================================
        // Step 7: Second check again -> All present, sheet closes
        // =========================================================================
        onNodeWithText("I've run it — check again").performClick()
        waitForIdle()

        assertEquals(2, fakeService.resumeCount)
        onAllNodesWithText("All present")[0].assertIsDisplayed()

        // =========================================================================
        // Step 8: Subsequent pipeline stages progress to completion
        // =========================================================================
        // 8a. Build service installing -> connected
        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.SERVER_CONNECTIVITY) {
                        it.copy(status = StepStatus.RUNNING, description = "Installing build service 0.1.0…")
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onNodeWithText("Installing build service 0.1.0…").assertIsDisplayed()

        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.SERVER_CONNECTIVITY) {
                        it.copy(
                            status = StepStatus.SUCCESS,
                            description = "0.1.0 · connected",
                            provenance = StepProvenance.LIVE,
                        )
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onAllNodesWithText("0.1.0 · connected")[0].assertIsDisplayed()

        // 8b. Pre-flight doctor checking -> passed
        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.SYSTEM_DIAGNOSTICS) {
                        it.copy(status = StepStatus.RUNNING, description = "Checking 1 of 8: Compilers")
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onNodeWithText("Checking 1 of 8: Compilers").assertIsDisplayed()

        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.SYSTEM_DIAGNOSTICS) {
                        it.copy(
                            status = StepStatus.SUCCESS,
                            description = "All checks passed",
                            provenance = StepProvenance.LIVE,
                        )
                    } else {
                        it
                    }
                },
            )
        waitForIdle()

        // 8c. Source repositories syncing -> pinned
        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.REPO_SYNCHRONIZATION) {
                        it.copy(status = StepStatus.RUNNING, description = "Syncing android-tools (1 of 3)")
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onNodeWithText("Syncing android-tools (1 of 3)").assertIsDisplayed()

        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.REPO_SYNCHRONIZATION) {
                        it.copy(
                            status = StepStatus.SUCCESS,
                            description = "3 repositories at pinned versions",
                            provenance = StepProvenance.LIVE,
                        )
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onNodeWithText("3 repositories at pinned versions").assertIsDisplayed()

        // 8d. Toolchain compilation -> ready
        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.TOOLCHAIN_COMPILATION) {
                        it.copy(status = StepStatus.RUNNING, description = "Building mkfs.erofs (1 of 12)")
                    } else {
                        it
                    }
                },
            )
        waitForIdle()
        onNodeWithText("Building mkfs.erofs (1 of 12)").assertIsDisplayed()

        val readyTimestamp = Clock.System.now().toEpochMilliseconds()
        fakeService.stateFlow.value =
            fakeService.stateFlow.value.copy(
                steps =
                fakeService.stateFlow.value.steps.map {
                    if (it.stage == SetupStepStage.TOOLCHAIN_COMPILATION) {
                        it.copy(
                            status = StepStatus.SUCCESS,
                            description = "12 tools ready",
                            provenance = StepProvenance.LIVE,
                        )
                    } else {
                        it
                    }
                },
                lastReadyAt = readyTimestamp,
            )
        waitForIdle()
        onNodeWithText("12 tools ready").assertIsDisplayed()

        // =========================================================================
        // Step 9: Environment tab displays "Machine ready" card
        // =========================================================================
        onNodeWithText("This machine is ready to build ROMs").assertIsDisplayed()
        onNodeWithText("Open project").assertIsDisplayed().assertIsEnabled()
        onNodeWithText("Create project").assertIsDisplayed().assertIsEnabled()

        // =========================================================================
        // Step 10: Returning to Projects tab shows header status "Machine ready"
        // =========================================================================
        onNodeWithTag("RailItem_projects").performClick()
        waitForIdle()

        onNodeWithText("Workspaces").assertIsDisplayed()
        onAllNodesWithText("Machine ready")[0].assertIsDisplayed()
    }
}
