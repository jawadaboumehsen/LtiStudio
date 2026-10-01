/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceType
import org.ide.lti.feature.setup.SETUP_ROUTE
import org.ide.lti.shared.di.initKoin
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RootNavGraphAdmissionGateTest {

    private class TestToolchainService(initialReady: Boolean = false) : ToolchainProvisioningService {
        val stateFlow = MutableStateFlow(
            ToolchainSetupState(
                isChecking = false,
                steps = if (initialReady) {
                    SetupStepStage.entries.map {
                        SetupStepDetail(
                            stage = it,
                            title = it.displayName,
                            description = "OK",
                            status = StepStatus.SUCCESS,
                        )
                    }
                } else {
                    listOf(
                        SetupStepDetail(
                            stage = SetupStepStage.SERVER_CONNECTIVITY,
                            title = "Server Bridge",
                            description = "Offline",
                            status = StepStatus.FAILED,
                        ),
                    )
                },
            ),
        )

        override val state: StateFlow<ToolchainSetupState> = stateFlow.asStateFlow()
        override suspend fun verifyEnvironment(): ToolchainSetupState = state.value
        override suspend fun provisionAvbKey(): Boolean = false
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan =
            error("Unused")
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome = error("Unused")
        override suspend fun resume(planId: String): SetupOutcome = error("Unused")
        override fun observe(): Flow<SetupOutcome?> = emptyFlow()
        override fun observeActivity(): Flow<SetupLogEvent> = emptyFlow()
        override suspend fun recover(attemptId: String?): SetupOutcome = error("Unused")
        override suspend fun cancel(): SetupOutcome = SetupOutcome.Cancelled
        override suspend fun testTool(binaryName: String): SetupOutcome = error("Unused")
    }

    @Before
    fun setUp() {
        stopKoin()
        initKoin()
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testRootNavGraphBlocksStudioNavigationWhenReadinessGateClosed() = runDesktopComposeUiTest(
        width = 1120,
        height = 700,
    ) {
        val workspaceManager = GlobalContext.get().get<WorkspaceManager>()
        workspaceManager.setCurrentWorkspace(null)
        val toolchainService = TestToolchainService(initialReady = false)
        lateinit var navController: NavHostController

        setContent {
            navController = rememberNavController()
            org.ide.lti.core.designsystem.theme.LtiTheme(effectsEnabled = false) {
                RootNavGraph(
                    navHostController = navController,
                    startDestination = SETUP_ROUTE,
                    workspaceManager = workspaceManager,
                    toolchainService = toolchainService,
                )
            }
        }

        // Set active workspace while environment readiness is FALSE
        val sampleWorkspace = Workspace(
            id = "ws-test",
            name = "Test Project",
            path = "/tmp/test",
            type = WorkspaceType.LOCAL,
            lastOpened = Clock.System.now(),
        )
        workspaceManager.setCurrentWorkspace(sampleWorkspace)
        waitForIdle()

        // Root admission gate MUST prevent transition to Studio and hold on SETUP_ROUTE
        assertEquals(
            expected = SETUP_ROUTE,
            actual = navController.currentDestination?.route,
            message = "RootNavGraph must keep route on SETUP_ROUTE when canLaunchWorkspace is false",
        )

        // Now open the gate
        toolchainService.stateFlow.value = ToolchainSetupState(
            isChecking = false,
            steps = SetupStepStage.entries.map {
                SetupStepDetail(stage = it, title = it.displayName, description = "OK", status = StepStatus.SUCCESS)
            },
        )
        waitForIdle()

        // Gate opened: RootNavGraph navigates to Studio
        assertTrue(
            navController.currentDestination?.route?.startsWith("studio/") == true,
            "RootNavGraph must navigate to Studio once canLaunchWorkspace becomes true, but was " +
                "${navController.currentDestination?.route}",
        )
    }
}
