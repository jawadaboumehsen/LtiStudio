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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.StepStatus
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
        val SUPPORTED_RECIPE_TOOL_IDS: Set<String> = setOf(
            "adb", "fastboot", "mke2fs", "avbtool", "mkbootimg", "lpmake", "lpunpack", "lpdump",
            "mkfs.erofs", "dump.erofs", "fsck.erofs", "erofsfuse", "img2sdat",
            "apktool", "signapk", "payload-dumper-go"
        )

        const val REPAIR_UNAVAILABLE_REASON =
            "Repair requires a supported recipe; otherwise the action is unavailable with a reason."

        fun isRecipeSupported(toolId: String): Boolean = toolId in SUPPORTED_RECIPE_TOOL_IDS

        fun unavailableReasonFor(toolId: String): String? =
            if (isRecipeSupported(toolId)) null else REPAIR_UNAVAILABLE_REASON
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

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
        selectedCategory,
    ) { state, query, category ->
        var list = state.toolsMatrix.ifEmpty { ToolchainSetupState.defaultToolsMatrix() }

        if (category != "All") {
            list = list.filter { it.category.displayName.equals(category, ignoreCase = true) }
        }

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
            val supported = isRecipeSupported(tool.id)
            ToolPresentation(
                tool = tool,
                isRepairSupported = supported,
                repairUnavailableReason = if (supported) null else REPAIR_UNAVAILABLE_REASON,
                isBusy = tool.status == StepStatus.RUNNING || state.isRunning,
            )
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = ToolchainSetupState.defaultToolsMatrix().map { tool ->
            val supported = isRecipeSupported(tool.id)
            ToolPresentation(
                tool = tool,
                isRepairSupported = supported,
                repairUnavailableReason = if (supported) null else REPAIR_UNAVAILABLE_REASON,
            )
        },
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
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
        return toolchainService.testTool(toolId)
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
    suspend fun repairTool(toolId: String): Boolean {
        if (!isRecipeSupported(toolId)) {
            return false
        }
        val isServiceBusy = toolchainStateFlow.value.isBusy
        if (isServiceBusy) {
            return false
        }
        if (toolchainService == null) {
            return false
        }
        return toolchainService.repairTool(toolId)
    }

    fun launchRepairTool(toolId: String) {
        scope.launch {
            repairTool(toolId)
        }
    }
}
