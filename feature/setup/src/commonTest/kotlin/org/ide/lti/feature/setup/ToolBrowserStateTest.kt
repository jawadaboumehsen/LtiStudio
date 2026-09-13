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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.setup.SetupOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolBrowserStateTest {

    private fun runToolBrowserTest(
        testBody: suspend TestScope.(scope: CoroutineScope) -> Unit
    ) = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        val toolScope = CoroutineScope(testDispatcher + SupervisorJob())
        try {
            testBody(toolScope)
        } finally {
            toolScope.cancel()
            Dispatchers.resetMain()
        }
    }

    private class SpyToolchainService(
        initialState: ToolchainSetupState = ToolchainSetupState()
    ) : ToolchainProvisioningService {
        val _state = MutableStateFlow(initialState)
        override val state: StateFlow<ToolchainSetupState> = _state.asStateFlow()

        var testToolCallCount = 0
            private set
        var lastTestedToolId: String? = null
            private set
        var repairToolCallCount = 0
            private set
        var lastRepairedToolId: String? = null
            private set
        var repairToolResult: Boolean = true

        override suspend fun verifyEnvironment(): ToolchainSetupState = _state.value
        override suspend fun checkStatus(): ToolchainSetupState = _state.value
        override suspend fun provisionAvbKey(): Boolean = true

        // 003 plan contract: fakes never synthesize success for behaviour the test does not drive.
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan =
            SetupPlan(planId = "fake-plan", revisionHash = "fake-rev", environmentKey = "fake-env", kind = kind,
                targetStageOrToolId = targetId, autoDoctorEnabled = autoDoctorEnabled)
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service does not execute plans")
        override fun observe(): Flow<SetupOutcome?> = flowOf(null)
        override suspend fun recover(attemptId: String?): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has no journal")
        override suspend fun cancel(): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has nothing to cancel")

        override suspend fun testTool(toolId: String): SetupOutcome {
            testToolCallCount++
            lastTestedToolId = toolId
            _state.value = _state.value.copy(
                toolsMatrix = _state.value.toolsMatrix.map {
                    if (it.id == toolId) it.copy(status = StepStatus.SUCCESS, lastTested = "Just now") else it
                }
            )
            return SetupOutcome.Succeeded()
        }

        override suspend fun repairTool(toolId: String): Boolean {
            repairToolCallCount++
            lastRepairedToolId = toolId
            if (repairToolResult) {
                _state.value = _state.value.copy(
                    toolsMatrix = _state.value.toolsMatrix.map {
                        if (it.id == toolId) it.copy(status = StepStatus.SUCCESS, path = "~/LtiRomTools/bin/$toolId") else it
                    }
                )
            }
            return repairToolResult
        }
    }

    @Test
    fun testCategoryAndSearchFiltering() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        // Initial state: all tools present
        val initialCount = browserState.filteredTools.value.size
        assertTrue(initialCount > 0, "Initial tools matrix should not be empty")

        // Filter by category: Dynamic Partitions
        browserState.selectCategory(ToolCategory.DYNAMIC_PARTITIONS.displayName)
        val dynamicTools = browserState.filteredTools.value
        assertTrue(dynamicTools.isNotEmpty())
        assertTrue(dynamicTools.all { it.tool.category == ToolCategory.DYNAMIC_PARTITIONS })

        // Search within category
        browserState.onSearchQueryChange("lpunpack")
        val filtered = browserState.filteredTools.value
        assertEquals(1, filtered.size)
        assertEquals("lpunpack", filtered.first().tool.id)

        // Reset category to "All" and search by capability
        browserState.selectCategory("All")
        browserState.onSearchQueryChange("lz4_compression")
        val lz4Tools = browserState.filteredTools.value
        assertTrue(lz4Tools.any { it.tool.id == "mkfs.erofs" })

        // Clear search
        browserState.onSearchQueryChange("")
        assertEquals(initialCount, browserState.filteredTools.value.size)
    }

    @Test
    fun testRepairRequiresSupportedRecipeOtherwiseUnavailableWithReason() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        val supportedToolPresentation = browserState.filteredTools.value.first { it.tool.id == "lpmake" }
        assertTrue(supportedToolPresentation.isRepairSupported, "lpmake has a supported rebuild recipe")
        assertNull(supportedToolPresentation.repairUnavailableReason, "Supported tool must not have an unavailable reason")

        // Unsupported recipe tool: "gh" (or external binary without recipe)
        val unsupportedToolPresentation = browserState.filteredTools.value.first { it.tool.id == "gh" }
        assertFalse(unsupportedToolPresentation.isRepairSupported, "gh has no supported recipe")
        assertEquals(
            "Repair requires a supported recipe; otherwise the action is unavailable with a reason.",
            unsupportedToolPresentation.repairUnavailableReason,
            "Enforces exact specification phrase for unavailable repair",
        )

        // Invoking repair on unsupported tool must not execute service repair
        val repairAttempt = browserState.repairTool("gh")
        assertFalse(repairAttempt, "Repair on unsupported recipe must be rejected")
        assertEquals(0, service.repairToolCallCount, "Service repair must not be called for unsupported tool")

        // Invoking repair on supported tool invokes service repair
        val supportedRepairAttempt = browserState.repairTool("lpmake")
        assertTrue(supportedRepairAttempt, "Repair on supported recipe must succeed")
        assertEquals(1, service.repairToolCallCount)
        assertEquals("lpmake", service.lastRepairedToolId)
    }

    @Test
    fun testTestToolInvokesServiceVerification() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        browserState.testTool("mke2fs")
        assertEquals(1, service.testToolCallCount)
        assertEquals("mke2fs", service.lastTestedToolId)

        val tool = browserState.filteredTools.value.first { it.tool.id == "mke2fs" }
        assertEquals(StepStatus.SUCCESS, tool.tool.status)
        assertEquals("Just now", tool.tool.lastTested)
    }

    @Test
    fun testSingleFlightMutationBlocksConcurrentRepair() = runToolBrowserTest { scope ->
        val service = SpyToolchainService(
            initialState = ToolchainSetupState(isRunning = true)
        )
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        // When environment is already busy with a mutation, tool repair must be blocked
        val result = browserState.repairTool("lpmake")
        assertFalse(result, "Repair should be rejected when environment mutation is active")
        assertEquals(0, service.repairToolCallCount, "Service repair must not run concurrently")
    }

    @Test
    fun testSessionStatePreservationAcrossDestinations() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        browserState.onSearchQueryChange("erofs")
        browserState.selectCategory(ToolCategory.FILESYSTEM_AND_IMAGES.displayName)
        browserState.selectTool("mkfs.erofs")
        browserState.setListPosition(3, 120)

        // Session state must retain all filter and navigation values
        assertEquals("erofs", browserState.searchQuery.value)
        assertEquals(ToolCategory.FILESYSTEM_AND_IMAGES.displayName, browserState.selectedCategory.value)
        assertEquals("mkfs.erofs", browserState.selectedToolId.value)
        assertEquals(Pair(3, 120), browserState.getListPosition())
    }
}
