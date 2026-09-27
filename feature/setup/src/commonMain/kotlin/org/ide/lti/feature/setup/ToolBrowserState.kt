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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Presentation model for an individual tool in the Tools destination (US3, FR-009).
 *
 * Validation rule: "Repair requires a supported recipe; otherwise the action is unavailable with a reason."
 */
data class ToolPresentation(
    val tool: ToolComponentItem,
    val isRepairSupported: Boolean,
    val repairUnavailableReason: String? = null,
    val isBusy: Boolean = false,
    /** The binary exists on disk (file discovery). */
    val isInstalled: Boolean,
    /** An execution probe with an accepted exit code succeeded (live or restored evidence). */
    val isTested: Boolean,
    /** The daemon lists the tool as published. */
    val isPublished: Boolean,
)

/**
 * Feature-local state holder for toolchain binary inspection and repair (US3).
 *
 * Responsibilities:
 * - Virtualized tool list filtering by search query and category (FR-009, SC-004).
 * - Enforces: "Repair requires a supported recipe; otherwise the action is unavailable with a reason." (FR-009).
 * - Coordinates tool verification (`testTool`) and real recipe execution (`repairTool`).
 * - Enforces single-flight operation serialization (at most one setup mutation active per environment).
 * - Preserves session search, category, selection, and scroll position across navigation (FR-004).
 */
class ToolBrowserState(
    private val scope: CoroutineScope,
    private val toolchainService: ToolchainProvisioningService? = null,
    private val environmentSetupState: EnvironmentSetupState? = null,
) {
    companion object {
        /**
         * Set of tools with real, automated build and distribution recipes in the toolchain engine.
         * Tools outside this set cannot be repaired automatically and report an unavailable reason.
         */
        val SUPPORTED_RECIPE_TOOL_IDS: Set<String>
            get() = ToolCapabilities.SUPPORTED_TOOL_IDS

        const val REPAIR_UNAVAILABLE_REASON: String = ToolCapabilities.REPAIR_UNAVAILABLE_REASON

        fun isRecipeSupported(toolId: String): Boolean = ToolCapabilities.isRecipeSupported(toolId)

        fun unavailableReasonFor(toolId: String): String? = ToolCapabilities.unavailableReasonFor(toolId)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _toolSelection = MutableStateFlow(ToolSelection())
    val toolSelection: StateFlow<ToolSelection> = _toolSelection.asStateFlow()

    private val _selectedToolId = MutableStateFlow<String?>(null)
    val selectedToolId: StateFlow<String?> = _selectedToolId.asStateFlow()

    var scrollIndex: Int = 0
        private set
    var scrollOffset: Int = 0
        private set

    private val toolchainStateFlow: StateFlow<ToolchainSetupState> =
        toolchainService?.state ?: MutableStateFlow(ToolchainSetupState()).asStateFlow()

    val filteredTools: StateFlow<List<ToolPresentation>> = combine(
        toolchainStateFlow,
        searchQuery,
        toolSelection,
    ) { state, query, selection ->
        var list = state.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }

        list = list.filter { selection.matches(it) }

        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            list = list.filter { tool ->
                tool.name.contains(trimmed, ignoreCase = true) ||
                    tool.id.contains(trimmed, ignoreCase = true) ||
                    tool.binaryName.contains(trimmed, ignoreCase = true) ||
                    tool.capabilities.any { cap -> cap.contains(trimmed, ignoreCase = true) }
            }
        }

        list.map { tool ->
            val supported = ToolCapabilities.isRecipeSupported(tool.id)
            val isInstalled = tool.path != null
            val isTested = tool.status == StepStatus.SUCCESS && tool.lastTested != null
            val isPublished = state.publishedToolIds.contains(tool.id)
            ToolPresentation(
                tool = tool,
                isRepairSupported = supported,
                repairUnavailableReason = ToolCapabilities.unavailableReasonFor(tool.id),
                isBusy = tool.status == StepStatus.RUNNING || state.isRunning,
                isInstalled = isInstalled,
                isTested = isTested,
                isPublished = isPublished,
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ToolchainSetupState.defaultToolsMatrix().map { tool ->
            val supported = ToolCapabilities.isRecipeSupported(tool.id)
            ToolPresentation(
                tool = tool,
                isRepairSupported = supported,
                repairUnavailableReason = ToolCapabilities.unavailableReasonFor(tool.id),
                isBusy = false,
                isInstalled = tool.path != null,
                isTested = tool.status == StepStatus.SUCCESS && tool.lastTested != null,
                isPublished = false,
            )
        },
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: ToolCategory?) {
        _toolSelection.value = _toolSelection.value.selectCategory(category)
    }

    fun selectPackage(pkg: ToolNavPackage) {
        _toolSelection.value = _toolSelection.value.selectPackage(pkg)
    }

    fun selectToolSelection(selection: ToolSelection) {
        _toolSelection.value = selection
    }

    fun selectTool(toolId: String?) {
        _selectedToolId.value = toolId
    }

    fun setListPosition(index: Int, offset: Int) {
        scrollIndex = index
        scrollOffset = offset
    }

    fun getListPosition(): Pair<Int, Int> = Pair(scrollIndex, scrollOffset)

    /**
     * Executes tool verification on the selected tool component.
     */
    suspend fun testTool(toolId: String): SetupOutcome {
        if (toolchainService == null) {
            return SetupOutcome.Failed(
                stage = null,
                reason = "Toolchain service unavailable",
            )
        }
        return try {
            toolchainService.testTool(toolId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A probe that throws is a failed verification, never an implicit success (FR-006).
            SetupOutcome.Failed(
                stage = null,
                reason = e.message ?: "Test failed with exception",
            )
        }
    }

    fun launchTestTool(toolId: String) {
        scope.launch {
            testTool(toolId)
        }
    }

    /**
     * Invokes the real rebuild/acquisition recipe for the selected tool (FR-009).
     * Enforces single-flight serialization and rejects unsupported recipes.
     */
    @Suppress("ReturnCount")
    suspend fun repairTool(toolId: String): SetupOutcome {
        if (!ToolCapabilities.isRecipeSupported(toolId)) {
            return SetupOutcome.Failed(
                stage = null,
                reason = ToolCapabilities.unavailableReasonFor(toolId) ?: ToolCapabilities.REPAIR_UNAVAILABLE_REASON,
            )
        }
        val isServiceBusy = toolchainStateFlow.value.isBusy
        if (isServiceBusy) {
            return SetupOutcome.Busy("Operation already in progress")
        }
        if (toolchainService == null) {
            return SetupOutcome.Failed(
                stage = null,
                reason = "Toolchain service unavailable",
            )
        }
        return try {
            val plan = toolchainService.prepare(SetupPlanKind.REPAIR_TOOL, targetId = toolId)
            toolchainService.confirm(plan.planId, plan.revisionHash)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SetupOutcome.Failed(
                stage = null,
                reason = e.message ?: "Repair failed with exception",
            )
        }
    }

    fun launchRepairTool(toolId: String) {
        scope.launch {
            repairTool(toolId)
        }
    }
}
