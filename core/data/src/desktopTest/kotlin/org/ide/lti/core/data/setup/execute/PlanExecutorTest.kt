/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanExecutorTest {

    private class RecordingActionHandler<A : SetupPlanAction>(
        var shouldFail: Boolean = false,
        var pauseUserAction: Boolean = false,
    ) : PlanActionHandler<A> {
        val executedActions = mutableListOf<A>()

        override suspend fun execute(action: A, context: ExecutionContext): SetupOutcome? {
            executedActions.add(action)
            return when {
                shouldFail -> SetupOutcome.Failed(stage = "TEST", reason = "Forced failure on ${action.actionId()}")
                pauseUserAction -> SetupOutcome.AwaitingUserAction(
                    pendingPlanId = "plan-1",
                    stage = org.ide.lti.core.domain.setup.SetupStepStage.SYSTEM_PACKAGES,
                    reason = "User action required",
                    handoff = org.ide.lti.core.domain.setup.UserRepairHandoff(
                        actionId = action.actionId(),
                        description = "Test handoff",
                        terminalCommand = "sudo apt install git",
                        packages = listOf("git"),
                        distro = context.distro,
                    ),
                )
                else -> null
            }
        }
    }

    @Test
    fun planExecutorExecutesActionsInOrder() = runTest {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val operationScope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }

        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>()
        val syncHandler = RecordingActionHandler<SetupPlanAction.SyncSources>()
        val buildHandler = RecordingActionHandler<SetupPlanAction.BuildRecipes>()
        val publishHandler = RecordingActionHandler<SetupPlanAction.PublishTools>()

        val executor = PlanExecutor(
            installPackagesHandler = pkgHandler,
            syncSourcesHandler = syncHandler,
            buildRecipesHandler = buildHandler,
            publishToolsHandler = publishHandler,
        )

        val plan = SetupPlan(
            planId = "plan-123",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(
                SetupPlanAction.InstallPackages(listOf("git")),
                SetupPlanAction.SyncSources(listOf("android-tools")),
                SetupPlanAction.BuildRecipes(listOf("android-tools")),
                SetupPlanAction.PublishTools(listOf("adb")),
            ),
        )

        val context = ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = "/home/test/bin",
            extDir = "/home/test/ext",
            workDir = "/home/test/work",
            plan = plan,
            scope = operationScope,
        )

        val outcome = executor.execute(context)
        assertIs<SetupOutcome.Succeeded>(outcome)
        assertEquals(1, pkgHandler.executedActions.size)
        assertEquals(1, syncHandler.executedActions.size)
        assertEquals(1, buildHandler.executedActions.size)
        assertEquals(1, publishHandler.executedActions.size)
    }

    @Test
    fun planExecutorSkipsActionsInSkipSet() = runTest {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val operationScope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }

        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>()
        val syncHandler = RecordingActionHandler<SetupPlanAction.SyncSources>()
        val buildHandler = RecordingActionHandler<SetupPlanAction.BuildRecipes>()
        val publishHandler = RecordingActionHandler<SetupPlanAction.PublishTools>()

        val executor = PlanExecutor(
            installPackagesHandler = pkgHandler,
            syncSourcesHandler = syncHandler,
            buildRecipesHandler = buildHandler,
            publishToolsHandler = publishHandler,
        )

        val pkgAction = SetupPlanAction.InstallPackages(listOf("git"))
        val syncAction = SetupPlanAction.SyncSources(listOf("android-tools"))

        val plan = SetupPlan(
            planId = "plan-123",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(pkgAction, syncAction),
        )

        val context = ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = "/home/test/bin",
            extDir = "/home/test/ext",
            workDir = "/home/test/work",
            plan = plan,
            scope = operationScope,
            actionsToSkip = setOf(pkgAction.actionId()),
        )

        val outcome = executor.execute(context)
        assertIs<SetupOutcome.Succeeded>(outcome)
        assertTrue(pkgHandler.executedActions.isEmpty(), "Skipped action must not execute")
        assertEquals(1, syncHandler.executedActions.size)
    }

    @Test
    fun planExecutorHaltsImmediatelyOnActionFailure() = runTest {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val operationScope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }

        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>()
        val syncHandler = RecordingActionHandler<SetupPlanAction.SyncSources>(shouldFail = true)
        val buildHandler = RecordingActionHandler<SetupPlanAction.BuildRecipes>()
        val publishHandler = RecordingActionHandler<SetupPlanAction.PublishTools>()

        val executor = PlanExecutor(
            installPackagesHandler = pkgHandler,
            syncSourcesHandler = syncHandler,
            buildRecipesHandler = buildHandler,
            publishToolsHandler = publishHandler,
        )

        val plan = SetupPlan(
            planId = "plan-123",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(
                SetupPlanAction.InstallPackages(listOf("git")),
                SetupPlanAction.SyncSources(listOf("android-tools")),
                SetupPlanAction.BuildRecipes(listOf("android-tools")),
            ),
        )

        val context = ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = "/home/test/bin",
            extDir = "/home/test/ext",
            workDir = "/home/test/work",
            plan = plan,
            scope = operationScope,
        )

        val outcome = executor.execute(context)
        val failure = assertIs<SetupOutcome.Failed>(outcome)
        assertTrue(failure.reason.contains("Forced failure"))
        assertTrue(buildHandler.executedActions.isEmpty(), "Subsequent actions must not execute after failure")
    }
}
