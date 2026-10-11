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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.SetupPlanFactory
import org.ide.lti.core.data.setup.execute.handlers.ComponentToolsHandler
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComponentToolsHandlerTest {
    private open class Store : ToolStorePort {
        val requests = mutableListOf<InstallRequest>()
        var result: (InstallRequest) -> OperationResult = { OperationResult.Installed("version") }

        override suspend fun init() = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog) = Result.success(ToolInventory(emptyMap()))

        override suspend fun refs(group: ToolGroupId, repoUrl: String) =
            Result.success(RefListing(emptyList(), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef) =
            Result.success(ResolvedSourceCheck("1".repeat(40)))

        override fun install(request: InstallRequest) = flowOf(
            InstallEvent.Started("run-${request.group.value}"),
            InstallEvent.Output("compiler output"),
            InstallEvent.Finished(result(request)),
        ).also {
            requests += request
        }

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate) =
            Result.success(GroupLinks(versionId, null))

        override suspend fun remove(group: ToolGroupId, versionId: String) = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog) = Result.success(PinnedTools(emptyMap(), emptyMap()))
    }

    private fun context(kind: SetupPlanKind, action: SetupPlanAction.SwitchToolchain): ExecutionContext {
        val plan =
            SetupPlan(
                planId = "plan",
                revisionHash = "revision",
                environmentKey = "Ubuntu",
                kind = kind,
                orderedActions = listOf(action),
            )
        val scope = OperationScope("Ubuntu", "operation", MutableStateFlow(ToolchainSetupState())) { "operation" }
        return ExecutionContext(
            "Ubuntu",
            "/home/test",
            "/home/test/bin",
            "/home/test/ext",
            "/home/test/work",
            plan,
            scope,
        )
    }

    @Test
    fun stageRetryUsesExactlyTheFrozenComponentSource() = runTest {
        val store = Store()
        val group = ToolGroupCatalog.EROFS_UTILS.id
        val input = SetupPlanFactory.defaultCatalogInputs().getValue(group)
        val action = SetupPlanAction.SwitchToolchain(mapOf(group to input), selectionRevision = 1L)
        val outcome =
            ComponentToolsHandler(ToolManager(store)).execute(
                action,
                context(SetupPlanKind.STAGE_RETRY, action),
            )

        assertNull(outcome)
        assertEquals(listOf(group), store.requests.map { it.group })
        val source = assertIs<ResolvedSource.Git>(store.requests.single().source)
        assertEquals((input as org.ide.lti.core.domain.setup.ResolvedInput.Git).commit, source.commit)
        assertEquals(input.submoduleCommits, source.submodules)
    }

    @Test
    fun cleanSetupContinuesOtherComponentsAfterOneBuildFailureAndReportsFailure() = runTest {
        val store = Store()
        store.result = {
            if (it.group == ToolGroupCatalog.ANDROID_TOOLS.id) {
                OperationResult.Failed(ToolError.BuildFailed("compiler failed"), "compiler failed")
            } else {
                OperationResult.Installed("version")
            }
        }
        val action =
            SetupPlanAction.SwitchToolchain(
                SetupPlanFactory.defaultCatalogInputs(),
                selectionRevision = 1L,
            )
        val outcome =
            ComponentToolsHandler(ToolManager(store)).execute(
                action,
                context(SetupPlanKind.FULL_SETUP, action),
            )

        assertIs<SetupOutcome.Failed>(outcome)
        assertTrue(outcome.reason.contains("android-tools"))
        assertEquals(ToolGroupCatalog.allGroups.map { it.id }, store.requests.map { it.group })
    }

    @Test
    fun streamedComponentEventsReachSetupConsoleAndRunReceipt() = runTest {
        val store = Store()
        val group = ToolGroupCatalog.EROFS_UTILS.id
        val action =
            SetupPlanAction.SwitchToolchain(
                mapOf(group to SetupPlanFactory.defaultCatalogInputs().getValue(group)),
                selectionRevision = 1L,
            )
        val logs = mutableListOf<String>()
        val runs = mutableListOf<String>()
        val execution =
            context(SetupPlanKind.STAGE_RETRY, action).copy(
                appendLog = { logs += it },
                recordRunStarted = { _, runId -> runs += runId },
            )

        assertNull(ComponentToolsHandler(ToolManager(store)).execute(action, execution))
        assertEquals(listOf("run-erofs-utils"), runs)
        assertTrue("compiler output" in logs)
        assertTrue(logs.any { it.startsWith("[Tools]") })
    }

    @Test
    fun cancellationDoesNotBecomeSetupSuccess() = runTest {
        val store = Store()
        store.result = { OperationResult.Cancelled }
        val action =
            SetupPlanAction.SwitchToolchain(
                SetupPlanFactory.defaultCatalogInputs(),
                selectionRevision = 1L,
            )
        val outcome =
            ComponentToolsHandler(ToolManager(store)).execute(
                action,
                context(SetupPlanKind.FULL_SETUP, action),
            )

        assertEquals(SetupOutcome.Cancelled, outcome)
        assertEquals(1, store.requests.size)
    }

    @Test
    fun forceRebuildGroupsPassesForceNewToStore() = runTest {
        val store = Store()
        val group = ToolGroupCatalog.EROFS_UTILS.id
        val input = SetupPlanFactory.defaultCatalogInputs().getValue(group)
        val action =
            SetupPlanAction.SwitchToolchain(
                mapOf(group to input),
                forceRebuildGroups = setOf(group),
                selectionRevision = 1L,
            )
        val outcome =
            ComponentToolsHandler(ToolManager(store)).execute(
                action,
                context(SetupPlanKind.FULL_SETUP, action),
            )

        assertNull(outcome)
        assertEquals(1, store.requests.size)
        assertTrue(store.requests.single().forceNew, "Forced rebuild must set forceNew = true")
    }

    @Test
    fun inventoryRefreshFailureFailsSetup() = runTest {
        val store =
            object : Store() {
                override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> =
                    Result.failure(RuntimeException("WSL daemon unreachable"))
            }
        val group = ToolGroupCatalog.EROFS_UTILS.id
        val input = SetupPlanFactory.defaultCatalogInputs().getValue(group)
        val action = SetupPlanAction.SwitchToolchain(mapOf(group to input), selectionRevision = 1L)
        val outcome =
            ComponentToolsHandler(ToolManager(store)).execute(
                action,
                context(SetupPlanKind.FULL_SETUP, action),
            )

        assertIs<SetupOutcome.Failed>(outcome)
        assertTrue(outcome.reason.contains("Failed to inspect tools inventory"))
        assertTrue(store.requests.isEmpty(), "No install requests must be made when inventory refresh fails")
    }
}
