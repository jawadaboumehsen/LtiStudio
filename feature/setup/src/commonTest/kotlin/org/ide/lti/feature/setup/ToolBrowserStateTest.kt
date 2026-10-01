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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupOutcome
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolBrowserStateTest {

    private fun runToolBrowserTest(
        testBody: suspend TestScope.(scope: CoroutineScope) -> Unit,
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
        initialState: ToolchainSetupState = ToolchainSetupState(),
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
        override suspend fun prepare(kind: SetupPlanKind, targetId: String?, autoDoctorEnabled: Boolean): SetupPlan {
            if (kind == SetupPlanKind.REPAIR_TOOL) {
                repairToolCallCount++
                lastRepairedToolId = targetId
            }
            return SetupPlan(
                planId = "fake-plan",
                revisionHash = "fake-rev",
                environmentKey = "fake-env",
                kind = kind,
                targetStageOrToolId = targetId,
                autoDoctorEnabled = autoDoctorEnabled,
            )
        }

        @Suppress("ReturnCount")
        override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome {
            if (lastRepairedToolId != null) {
                val toolId = lastRepairedToolId!!
                if (repairToolResult) {
                    _state.value = _state.value.copy(
                        toolsMatrix = _state.value.toolsMatrix.map {
                            if (it.id == toolId) it.copy(status = StepStatus.SUCCESS, path = "~/LtiRomTools/bin/$toolId") else it
                        },
                    )
                    return SetupOutcome.Succeeded()
                } else {
                    return SetupOutcome.Failed(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                        reason = "Repair failed",
                    )
                }
            }
            return SetupOutcome.Failed(stage = null, reason = "fake service does not execute plans")
        }
        override fun observe(): Flow<SetupOutcome?> = flowOf(null)
        override fun observeActivity(): Flow<SetupLogEvent> = emptyFlow()
        override suspend fun recover(attemptId: String?): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has no journal")
        override suspend fun cancel(): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service has nothing to cancel")
        override suspend fun resume(planId: String): SetupOutcome =
            SetupOutcome.Failed(stage = null, reason = "fake service does not resume plans")

        var testToolOutcome: SetupOutcome = SetupOutcome.Succeeded()
        var testToolException: Throwable? = null

        override suspend fun testTool(toolId: String): SetupOutcome {
            testToolCallCount++
            lastTestedToolId = toolId
            testToolException?.let { throw it }
            if (testToolOutcome is SetupOutcome.Succeeded) {
                _state.value = _state.value.copy(
                    toolsMatrix = _state.value.toolsMatrix.map {
                        if (it.id == toolId) it.copy(status = StepStatus.SUCCESS, lastTested = "Just now") else it
                    },
                )
            } else {
                _state.value = _state.value.copy(
                    toolsMatrix = _state.value.toolsMatrix.map {
                        if (it.id == toolId) {
                            it.copy(status = StepStatus.FAILED, lastTested = "Failed with exit code 1")
                        } else {
                            it
                        }
                    },
                )
            }
            return testToolOutcome
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
        browserState.selectCategory(ToolCategory.DYNAMIC_PARTITIONS)
        val dynamicTools = browserState.filteredTools.value
        assertTrue(dynamicTools.isNotEmpty())
        assertTrue(dynamicTools.all { it.tool.category == ToolCategory.DYNAMIC_PARTITIONS })

        // Search within category
        browserState.onSearchQueryChange("lpunpack")
        val filtered = browserState.filteredTools.value
        assertEquals(1, filtered.size)
        assertEquals("lpunpack", filtered.first().tool.id)

        // Reset category to "All" and search by capability
        browserState.selectCategory(null)
        browserState.onSearchQueryChange("lz4_compression")
        val lz4Tools = browserState.filteredTools.value
        assertTrue(lz4Tools.any { it.tool.id == "mkfs.erofs" })

        // Clear search
        browserState.onSearchQueryChange("")
        assertEquals(initialCount, browserState.filteredTools.value.size)
    }

    @Test
    fun testRepairRequiresSupportedRecipeOtherwiseUnavailableWithReason() = runToolBrowserTest { scope ->
        // Every catalog tool has a recipe; a tool the user added by hand does not.
        val customTool = ToolComponentItem(
            id = "custom-vendor-tool",
            name = "Custom Vendor Tool",
            binaryName = "custom-vendor-tool",
            category = ToolCategory.PACKAGING_AND_TOOLS,
            isCore = false,
            status = StepStatus.PENDING,
        )
        val service = SpyToolchainService(
            initialState = ToolchainSetupState(toolsMatrix = ToolchainSetupState.defaultToolsMatrix() + customTool),
        )
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        val supportedToolPresentation = browserState.filteredTools.value.first { it.tool.id == "lpmake" }
        assertTrue(supportedToolPresentation.isRepairSupported, "lpmake has a supported rebuild recipe")
        assertNull(supportedToolPresentation.repairUnavailableReason, "Supported tool must not have an unavailable reason")

        val unsupportedToolPresentation =
            browserState.filteredTools.value.first { it.tool.id == "custom-vendor-tool" }
        assertFalse(unsupportedToolPresentation.isRepairSupported, "custom-vendor-tool has no recipe")
        assertEquals(
            "Not built by setup, so it can't be rebuilt here. Install or update it yourself; Test still works.",
            unsupportedToolPresentation.repairUnavailableReason,
            "Enforces exact specification phrase for unavailable repair",
        )

        // Invoking repair on unsupported tool must not execute service repair
        val repairAttempt = browserState.repairTool("custom-vendor-tool")
        assertTrue(repairAttempt is SetupOutcome.Failed, "Repair on unsupported recipe must be rejected")
        assertEquals(0, service.repairToolCallCount, "Service repair must not be called for unsupported tool")

        // Invoking repair on supported tool invokes service repair
        val supportedRepairAttempt = browserState.repairTool("lpmake")
        assertTrue(supportedRepairAttempt is SetupOutcome.Succeeded, "Repair on supported recipe must succeed")
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
            initialState = ToolchainSetupState(isRunning = true),
        )
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        // When environment is already busy with a mutation, tool repair must be blocked
        val result = browserState.repairTool("lpmake")
        assertTrue(result is SetupOutcome.Busy, "Repair should be rejected when environment mutation is active")
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
        browserState.selectCategory(ToolCategory.FILESYSTEM_AND_IMAGES)
        browserState.selectTool("mkfs.erofs")
        browserState.setListPosition(3, 120)

        // Session state must retain all filter and navigation values
        assertEquals("erofs", browserState.searchQuery.value)
        assertEquals(ToolCategory.FILESYSTEM_AND_IMAGES, browserState.toolSelection.value.category)
        assertEquals("mkfs.erofs", browserState.selectedToolId.value)
        assertEquals(Pair(3, 120), browserState.getListPosition())
    }

    @Test
    fun testProbeFailureAndExceptionsYieldTypedFailedNeverJustTested() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        service.testToolOutcome = SetupOutcome.Failed(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION.name,
            reason = "Tool mke2fs verification probe failed with exit code 1",
        )
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        val failureOutcome = browserState.testTool("mke2fs")
        assertTrue(failureOutcome is SetupOutcome.Failed, "Probe failure must map to typed Failed (FR-006)")
        assertEquals("Tool mke2fs verification probe failed with exit code 1", failureOutcome.reason)

        val failedTool = browserState.filteredTools.value.first { it.tool.id == "mke2fs" }
        assertEquals(StepStatus.FAILED, failedTool.tool.status)
        assertFalse(
            failedTool.tool.lastTested == "Just now",
            "Failed verification must never be labeled 'Just now' or 'Just tested' (FR-010)",
        )

        // Probe exception must also yield typed Failed
        service.testToolException = IllegalStateException("Transport connection dropped")
        val exceptionOutcome = browserState.testTool("mke2fs")
        assertTrue(exceptionOutcome is SetupOutcome.Failed, "Probe exception must map to typed Failed")
    }

    @Test
    fun testPresentationSeparatesInstalledTestedAndPublishedFacts() = runToolBrowserTest { scope ->
        fun tool(id: String, status: StepStatus, path: String?, lastTested: String?) = ToolComponentItem(
            id = id,
            name = id,
            category = ToolCategory.FILESYSTEM_AND_IMAGES,
            binaryName = id,
            status = status,
            path = path,
            lastTested = lastTested,
        )
        val service = SpyToolchainService(
            initialState = ToolchainSetupState(
                toolsMatrix = listOf(
                    // exists on disk, never probed, not published
                    tool("mke2fs", StepStatus.SUCCESS, "/home/lti/LtiRomTools/bin/mke2fs", null),
                    // exists, probe passed, published by the daemon
                    tool("mkfs.erofs", StepStatus.SUCCESS, "/home/lti/LtiRomTools/bin/mkfs.erofs", "Just now"),
                    // exists but its execution probe failed
                    tool("lpmake", StepStatus.FAILED, "/home/lti/LtiRomTools/bin/lpmake", null),
                    // not installed at all
                    tool("simg2img", StepStatus.PENDING, null, null),
                ),
                publishedToolIds = setOf("mkfs.erofs"),
            ),
        )
        val browserState = ToolBrowserState(scope = scope, toolchainService = service)
        val byId = browserState.filteredTools.value.associateBy { it.tool.id }

        assertEquals(Triple(true, false, false), byId.getValue("mke2fs").facts(), "installed but untested")
        assertEquals(Triple(true, true, true), byId.getValue("mkfs.erofs").facts(), "installed, tested, published")
        assertEquals(Triple(true, false, false), byId.getValue("lpmake").facts(), "failed probe: installed, not tested")
        assertEquals(Triple(false, false, false), byId.getValue("simg2img").facts(), "absent binary")
    }

    private fun ToolPresentation.facts() = Triple(isInstalled, isTested, isPublished)

    @Test
    fun testSharedBusyFromCoordinatorSurfacesInToolPresentation() = runToolBrowserTest { scope ->
        val service = SpyToolchainService(
            initialState = ToolchainSetupState(isRunning = true),
        )
        val browserState = ToolBrowserState(
            scope = scope,
            toolchainService = service,
        )

        // Shared busy state surfaces on every tool presentation
        val tools = browserState.filteredTools.value
        assertTrue(tools.isNotEmpty())
        assertTrue(
            tools.all { it.isBusy },
            "Shared busy from coordinator must surface on all tool presentations (FR-005)",
        )
    }

    @Test
    fun testOperationSurvivesNavigatingAwayFromToolsDestination() = runToolBrowserTest { scope ->
        val gate = CompletableDeferred<Unit>()
        val service = object : ToolchainProvisioningService by SpyToolchainService() {
            var confirmCalls = 0
            override suspend fun confirm(planId: String, revisionHash: String): SetupOutcome {
                confirmCalls++
                gate.await() // the repair is in flight until the test releases it
                return SetupOutcome.Succeeded()
            }
        }
        val browserState = ToolBrowserState(scope = scope, toolchainService = service)
        val destinationScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
        val appJob = requireNotNull(scope.coroutineContext[Job])
        val before = appJob.children.toSet() // state-flow collectors already owned by the app scope

        browserState.launchRepairTool("lpmake")

        val repairJob = (appJob.children.toSet() - before).single()
        assertTrue(repairJob.isActive, "Repair must be a child of the application scope, not the destination")
        assertEquals(1, service.confirmCalls)

        // The user leaves the Tools destination: only the destination-owned scope dies.
        destinationScope.cancel()
        assertTrue(repairJob.isActive, "Leaving the destination must not cancel the repair")

        gate.complete(Unit)
        testScheduler.runCurrent()
        assertTrue(repairJob.isCompleted && !repairJob.isCancelled, "The repair completes normally")
    }

    @Test
    fun testFileRefreshPreservesFailedExecutionVerification() = runToolBrowserTest { scope ->
        val service = SpyToolchainService(
            initialState = ToolchainSetupState(
                toolsMatrix = listOf(
                    ToolComponentItem(
                        id = "mke2fs",
                        name = "Make EXT4",
                        category = ToolCategory.FILESYSTEM_AND_IMAGES,
                        binaryName = "mke2fs",
                        status = StepStatus.FAILED,
                    ),
                ),
            ),
        )
        val browserState = ToolBrowserState(scope = scope, toolchainService = service)
        val before = browserState.filteredTools.value.first { it.tool.id == "mke2fs" }
        assertFalse(before.isInstalled)
        assertFalse(before.isTested)

        // A discovery scan learns the path; it carries no execution evidence.
        service._state.value = service._state.value.copy(
            toolsMatrix = service._state.value.toolsMatrix.map {
                it.copy(path = "/home/lti/LtiRomTools/bin/mke2fs")
            },
        )

        val after = browserState.filteredTools.value.first { it.tool.id == "mke2fs" }
        assertTrue(after.isInstalled, "Discovery updates the installed fact")
        assertEquals(StepStatus.FAILED, after.tool.status, "Failed execution verification is preserved (FR-010)")
        assertFalse(after.isTested, "A path alone never counts as tested")
        assertNull(after.tool.lastTested, "Discovery must never label a failed probe as tested")
    }

    @Test
    fun testEmptySearchResultsReturnEmptyList() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(scope = scope, toolchainService = service)

        browserState.onSearchQueryChange("nonexistent_tool_xyz_123")
        assertTrue(browserState.filteredTools.value.isEmpty(), "Empty search query match must return empty list")
    }

    @Test
    fun testAllCategoriesFilterCorrectly() = runToolBrowserTest { scope ->
        val service = SpyToolchainService()
        val browserState = ToolBrowserState(scope = scope, toolchainService = service)

        ToolCategory.entries.forEach { category ->
            browserState.selectCategory(category)
            val filtered = browserState.filteredTools.value
            assertTrue(
                filtered.all { it.tool.category == category },
                "Filtering by category $category must only return tools in that category",
            )
        }
    }

    @Test
    fun testToolSelectionChipRowVisibility() {
        val allSelection = ToolSelection(navPackage = ToolNavPackage.ALL)
        assertTrue(allSelection.shouldShowCategoryChips)

        val firmwareSelection = ToolSelection(navPackage = ToolNavPackage.FIRMWARE)
        assertTrue(firmwareSelection.shouldShowCategoryChips)

        val filesystemsSelection = ToolSelection(navPackage = ToolNavPackage.FILESYSTEMS)
        assertFalse(filesystemsSelection.shouldShowCategoryChips)

        val signingSelection = ToolSelection(navPackage = ToolNavPackage.SIGNING)
        assertFalse(signingSelection.shouldShowCategoryChips)
    }

    @Test
    fun testSidebarChipCombinationsAcrossAllSixCategories() {
        val tools = ToolCategory.entries.map { cat ->
            ToolComponentItem(
                id = cat.name.lowercase(),
                name = cat.displayName,
                binaryName = cat.name.lowercase(),
                category = cat,
                status = StepStatus.SUCCESS,
            )
        }

        // 1. ALL package: exposes all 6 categories, chip row visible
        val allSelection = ToolSelection(navPackage = ToolNavPackage.ALL)
        assertEquals(ToolCategory.entries, allSelection.availableCategories)
        assertTrue(allSelection.shouldShowCategoryChips)
        assertTrue(tools.all { allSelection.matches(it) }, "ALL without category must match all 6 categories")

        ToolCategory.entries.forEach { cat ->
            val withCategory = allSelection.selectCategory(cat)
            assertEquals(ToolNavPackage.ALL, withCategory.navPackage)
            assertEquals(cat, withCategory.category)
            assertTrue(tools.single { withCategory.matches(it) }.category == cat)
        }

        // 2. FIRMWARE package: exposes Boot & Kernel and Dynamic Partitions
        val firmwareSelection = ToolSelection(navPackage = ToolNavPackage.FIRMWARE)
        assertEquals(
            listOf(ToolCategory.BOOT_AND_KERNEL, ToolCategory.DYNAMIC_PARTITIONS),
            firmwareSelection.availableCategories,
        )
        assertTrue(firmwareSelection.shouldShowCategoryChips)
        assertEquals(
            setOf(ToolCategory.BOOT_AND_KERNEL, ToolCategory.DYNAMIC_PARTITIONS),
            tools.filter { firmwareSelection.matches(it) }.map { it.category }.toSet(),
        )

        // Selecting category within FIRMWARE keeps FIRMWARE
        val fwBoot = firmwareSelection.selectCategory(ToolCategory.BOOT_AND_KERNEL)
        assertEquals(ToolNavPackage.FIRMWARE, fwBoot.navPackage)
        assertEquals(ToolCategory.BOOT_AND_KERNEL, fwBoot.category)

        val fwDyn = firmwareSelection.selectCategory(ToolCategory.DYNAMIC_PARTITIONS)
        assertEquals(ToolNavPackage.FIRMWARE, fwDyn.navPackage)
        assertEquals(ToolCategory.DYNAMIC_PARTITIONS, fwDyn.category)

        // Selecting category outside FIRMWARE switches to matching package or ALL
        val fwToFs = firmwareSelection.selectCategory(ToolCategory.FILESYSTEM_AND_IMAGES)
        assertEquals(ToolNavPackage.FILESYSTEMS, fwToFs.navPackage)
        assertEquals(ToolCategory.FILESYSTEM_AND_IMAGES, fwToFs.category)

        val fwToSign = firmwareSelection.selectCategory(ToolCategory.SIGNING_AND_SECURITY)
        assertEquals(ToolNavPackage.SIGNING, fwToSign.navPackage)
        assertEquals(ToolCategory.SIGNING_AND_SECURITY, fwToSign.category)

        val fwToBridge = firmwareSelection.selectCategory(ToolCategory.BRIDGE_AND_FLASHING)
        assertEquals(ToolNavPackage.ALL, fwToBridge.navPackage)
        assertEquals(ToolCategory.BRIDGE_AND_FLASHING, fwToBridge.category)

        val fwToPkg = firmwareSelection.selectCategory(ToolCategory.PACKAGING_AND_TOOLS)
        assertEquals(ToolNavPackage.ALL, fwToPkg.navPackage)
        assertEquals(ToolCategory.PACKAGING_AND_TOOLS, fwToPkg.category)

        // 3. Single-category packages hide redundant chip row
        val fsSelection = ToolSelection(navPackage = ToolNavPackage.FILESYSTEMS)
        assertFalse(fsSelection.shouldShowCategoryChips)
        assertEquals(listOf(ToolCategory.FILESYSTEM_AND_IMAGES), fsSelection.availableCategories)

        val signingSelection = ToolSelection(navPackage = ToolNavPackage.SIGNING)
        assertFalse(signingSelection.shouldShowCategoryChips)
        assertEquals(listOf(ToolCategory.SIGNING_AND_SECURITY), signingSelection.availableCategories)
    }
}
