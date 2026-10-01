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
import org.ide.lti.core.data.setup.SetupPlanFactory
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanExecutorTest {

    private class RecordingActionHandler<A : SetupPlanAction>(var shouldFail: Boolean = false) : PlanActionHandler<A> {
        val executedActions = mutableListOf<A>()

        override suspend fun execute(action: A, context: ExecutionContext): SetupOutcome? {
            executedActions.add(action)
            return if (shouldFail) {
                SetupOutcome.Failed(stage = "TEST", reason = "Forced failure on ${action.actionId()}")
            } else {
                null
            }
        }
    }

    private fun makeContext(plan: SetupPlan): ExecutionContext {
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val operationScope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }
        return ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = "/home/test/bin",
            extDir = "/home/test/ext",
            workDir = "/home/test/work",
            plan = plan,
            scope = operationScope,
        )
    }

    // -----------------------------------------------------------------------
    // InstallPackages dispatch
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorDispatchesInstallPackages() = runTest {
        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>()
        val executor = PlanExecutor(installPackagesHandler = pkgHandler)

        val plan = SetupPlan(
            planId = "plan-pkg",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.BOOTSTRAP_PACKAGES,
            orderedActions = listOf(SetupPlanAction.InstallPackages(listOf("git", "cmake"))),
        )

        val outcome = executor.execute(makeContext(plan))
        assertIs<SetupOutcome.Succeeded>(outcome)
        assertEquals(1, pkgHandler.executedActions.size)
        assertEquals(listOf("git", "cmake"), pkgHandler.executedActions.first().packages)
    }

    // -----------------------------------------------------------------------
    // SwitchToolchain — no handler registered → typed Failed, not a crash
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorWithNoSwitchHandlerProducesTypedFailure() = runTest {
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val plan = SetupPlan(
            planId = "plan-switch",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(SetupPlanAction.SwitchToolchain(inputs, selectionRevision = 1L)),
        )
        val executor = PlanExecutor(switchToolchainHandler = null)
        val outcome = executor.execute(makeContext(plan))
        val failure = assertIs<SetupOutcome.Failed>(outcome)
        assertTrue(failure.reason.contains("SwitchToolchainHandler"), "Failure must mention the missing handler")
    }

    // -----------------------------------------------------------------------
    // SwitchToolchain dispatched when handler is registered
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorDispatchesSwitchToolchain() = runTest {
        val switchHandler = RecordingActionHandler<SetupPlanAction.SwitchToolchain>()
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val executor = PlanExecutor(switchToolchainHandler = switchHandler)

        val plan = SetupPlan(
            planId = "plan-switch",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(SetupPlanAction.SwitchToolchain(inputs, selectionRevision = 42L)),
        )

        val outcome = executor.execute(makeContext(plan))
        assertIs<SetupOutcome.Succeeded>(outcome)
        assertEquals(1, switchHandler.executedActions.size)
        assertEquals(42L, switchHandler.executedActions.first().selectionRevision)
    }

    // -----------------------------------------------------------------------
    // Skip-set honours completed actions
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorSkipsActionsInSkipSet() = runTest {
        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>()
        val switchHandler = RecordingActionHandler<SetupPlanAction.SwitchToolchain>()
        val executor = PlanExecutor(
            installPackagesHandler = pkgHandler,
            switchToolchainHandler = switchHandler,
        )

        val pkgAction = SetupPlanAction.InstallPackages(listOf("git"))
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val switchAction = SetupPlanAction.SwitchToolchain(inputs, selectionRevision = 1L)

        val plan = SetupPlan(
            planId = "plan-skip",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(pkgAction, switchAction),
        )

        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = OperationScope("Ubuntu", "op-1", stateFlow) { "op-1" }
        val context = ExecutionContext(
            distro = "Ubuntu",
            home = "/home/test",
            binDir = "/home/test/bin",
            extDir = "/home/test/ext",
            workDir = "/home/test/work",
            plan = plan,
            scope = scope,
            actionsToSkip = setOf(pkgAction.actionId()),
        )

        val outcome = executor.execute(context)
        assertIs<SetupOutcome.Succeeded>(outcome)
        assertTrue(pkgHandler.executedActions.isEmpty(), "Skipped action must not execute")
        assertEquals(1, switchHandler.executedActions.size)
    }

    // -----------------------------------------------------------------------
    // Halt immediately on action failure
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorHaltsImmediatelyOnInstallPackagesFailure() = runTest {
        val pkgHandler = RecordingActionHandler<SetupPlanAction.InstallPackages>(shouldFail = true)
        val switchHandler = RecordingActionHandler<SetupPlanAction.SwitchToolchain>()
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val executor = PlanExecutor(
            installPackagesHandler = pkgHandler,
            switchToolchainHandler = switchHandler,
        )

        val plan = SetupPlan(
            planId = "plan-fail",
            revisionHash = "rev-hash",
            environmentKey = "Ubuntu",
            kind = SetupPlanKind.FULL_SETUP,
            orderedActions = listOf(
                SetupPlanAction.InstallPackages(listOf("git")),
                SetupPlanAction.SwitchToolchain(inputs, selectionRevision = 1L),
            ),
        )

        val outcome = executor.execute(makeContext(plan))
        val failure = assertIs<SetupOutcome.Failed>(outcome)
        assertTrue(failure.reason.contains("Forced failure"))
        assertTrue(switchHandler.executedActions.isEmpty(), "SwitchToolchain must not execute after failure")
    }

    // -----------------------------------------------------------------------
    // PlanExecutor.switchToolchainHandler is accessible for DI assertion
    // -----------------------------------------------------------------------

    @Test
    fun planExecutorExposesSwitchToolchainHandler() {
        val switchHandler = RecordingActionHandler<SetupPlanAction.SwitchToolchain>()
        val executor = PlanExecutor(switchToolchainHandler = switchHandler)
        assertNotNull(executor.switchToolchainHandler, "switchToolchainHandler must be exposed for DI gate")
    }
}
