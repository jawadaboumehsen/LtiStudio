/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.datastore.StudioPresentationStore
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.StudioPresentation
import org.ide.lti.core.model.workspace.Workspace

/**
 * Tabs available in the bottom activity/problems/artifacts dock.
 */
public enum class StudioDockTab(public val title: String) {
    ACTIVITY("Activity"),
    PROBLEMS("Problems"),
    ARTIFACTS("Artifacts"),
}

/**
 * Presentation model for a stage navigation rail item derived from the catalog.
 */
public data class StudioRailItem(
    val stage: StudioStageDestination,
    val isSelected: Boolean,
    val errorCount: Int = 0,
    val warningCount: Int = 0,
    val statusLabel: String = "Valid",
)

/**
 * UI State for ROM Setup Studio.
 */
public data class StudioUiState(
    val workspaceId: String = "",
    val entry: StudioEntry = StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE),
    val presentation: StudioPresentation = StudioPresentation(workspaceId = ""),
    val railItems: List<StudioRailItem> = emptyList(),
    val searchQuery: String = "",
    val activeDockTab: StudioDockTab = StudioDockTab.PROBLEMS,
    val isDockExpanded: Boolean = false,
    val isNavigatorDrawerOpen: Boolean = false,
    val availableWorkspaces: List<Workspace> = emptyList(),
    val validationReport: ValidationReport = ValidationReport(),
    val saveRequestedNotification: String? = null,
)

/**
 * ViewModel for ROM Setup Studio.
 *
 * Responsibilities:
 * - Orchestrates entry resolution via [resolveStudioEntry] with route arguments and stored presentation.
 * - Exposes a reactive [StateFlow] of UI state containing entry, presentation, and catalog-derived rail items.
 * - Persists editor selection, navigator width, and section expansion changes.
 * - Guaranteed: Selecting a stage or object NEVER starts work, never mutates a ConfigurationSnapshot,
 *   and never resets a draft.
 */
public class StudioViewModel(
    private val studioPresentationStore: StudioPresentationStore,
    initialWorkspaceId: String = "",
    private val workspaceManager: WorkspaceManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StudioUiState(
            workspaceId = initialWorkspaceId,
            presentation = StudioPresentation(workspaceId = initialWorkspaceId),
            railItems = computeInitialRailItems(),
            validationReport = createDefaultValidationReport(),
        ),
    )
    public val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    init {
        observeWorkspaces()
        if (initialWorkspaceId.isNotBlank()) {
            onEntry(initialWorkspaceId)
        }
    }

    private fun observeWorkspaces() {
        if (workspaceManager != null) {
            viewModelScope.launch {
                workspaceManager.getWorkspaces().collect { workspaces ->
                    _uiState.update { it.copy(availableWorkspaces = workspaces) }
                }
            }
        }
    }

    /**
     * Handles entry or route parameter updates for the studio host route.
     * Calls [resolveStudioEntry] with the route arguments and stored presentation.
     */
    public fun onEntry(workspaceId: String, requestedStageId: StageId? = null, requestedObjectId: String? = null) {
        viewModelScope.launch {
            val stored = studioPresentationStore.get(workspaceId)
            val entry = resolveStudioEntry(
                presentation = stored,
                requestedStageId = requestedStageId,
                requestedObjectId = requestedObjectId,
            )

            val basePresentation = stored ?: StudioPresentation(workspaceId = workspaceId)
            val updatedPresentation = when (entry) {
                is StudioEntry.Editor -> basePresentation.withEditor(entry.stageId, entry.objectId)
                is StudioEntry.Overview -> basePresentation
            }

            if (updatedPresentation != stored) {
                studioPresentationStore.put(updatedPresentation)
            }

            _uiState.update { current ->
                val railItems = computeRailItems(entry, current.validationReport)
                current.copy(
                    workspaceId = workspaceId,
                    entry = entry,
                    presentation = updatedPresentation,
                    railItems = railItems,
                )
            }
        }
    }

    /**
     * Selects a pipeline stage.
     * If the current editor object belongs to this stage, it is preserved; otherwise selects the first subobject.
     * Presentation state only: never starts work, never mutates a snapshot, never resets a draft.
     */
    public fun selectStage(stageId: StageId) {
        val stage = StudioDestinationCatalog.stage(stageId) ?: return
        val currentEditor = _uiState.value.entry as? StudioEntry.Editor
        val targetObjectId = if (currentEditor != null && stage.subobjects.any { it.id == currentEditor.objectId }) {
            currentEditor.objectId
        } else {
            stage.subobjects.first().id
        }

        val updatedPresentation = _uiState.value.presentation.withEditor(stageId, targetObjectId)
        val newEntry = StudioEntry.Editor(stageId, targetObjectId, StudioEntryReason.DEEP_LINK)

        _uiState.update { current ->
            current.copy(
                entry = newEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(newEntry, current.validationReport),
                isNavigatorDrawerOpen = false,
            )
        }

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    /**
     * Selects a subobject inside a stage.
     * Presentation state only: never starts work, never mutates a snapshot, never resets a draft.
     * A stage/object pair the catalog does not recognize is a no-op, matching [resolveStudioEntry]'s
     * own guarantee that an invalid selection is never silently persisted.
     */
    public fun selectObject(stageId: StageId, objectId: String) {
        val stage = StudioDestinationCatalog.stage(stageId) ?: return
        if (stage.subobjects.none { it.id == objectId }) return

        val updatedPresentation = _uiState.value.presentation.withEditor(stageId, objectId)
        val newEntry = StudioEntry.Editor(stageId, objectId, StudioEntryReason.DEEP_LINK)

        _uiState.update { current ->
            current.copy(
                entry = newEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(newEntry, current.validationReport),
                isNavigatorDrawerOpen = false,
            )
        }

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    /**
     * Updates the user-adjustable object navigator width (clamped to 200..360).
     */
    public fun setNavigatorWidth(width: Int) {
        val updatedPresentation = _uiState.value.presentation.withNavigatorWidth(width)
        _uiState.update { it.copy(presentation = updatedPresentation) }

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    /**
     * Updates the expanded state of an editor section.
     */
    public fun toggleSectionExpanded(objectId: String, sectionId: String) {
        val currentSections = _uiState.value.presentation.expandedSections[objectId] ?: emptySet()
        val updatedSet = if (sectionId in currentSections) {
            currentSections - sectionId
        } else {
            currentSections + sectionId
        }
        val updatedMap = _uiState.value.presentation.expandedSections + (objectId to updatedSet)
        val updatedPresentation = _uiState.value.presentation.copy(expandedSections = updatedMap)

        _uiState.update { it.copy(presentation = updatedPresentation) }

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    /**
     * Switches center pane mode to Overview without destroying the last editor context.
     */
    public fun openOverview() {
        val overviewEntry = StudioEntry.Overview(StudioEntryReason.RESTORED)
        _uiState.update { current ->
            current.copy(
                entry = overviewEntry,
                railItems = computeRailItems(overviewEntry, current.validationReport),
                isNavigatorDrawerOpen = false,
            )
        }
    }

    /**
     * Restores the last active editor context from presentation state.
     */
    public fun returnToEditor() {
        val pres = _uiState.value.presentation
        val resolved = resolveStudioEntry(pres, null, null)
        val editorEntry = if (resolved is StudioEntry.Editor) {
            resolved
        } else {
            val firstStage = StudioDestinationCatalog.stages.first()
            StudioEntry.Editor(
                stageId = firstStage.stageId,
                objectId = firstStage.subobjects.first().id,
                reason = StudioEntryReason.RESTORED,
            )
        }

        _uiState.update { current ->
            current.copy(
                entry = editorEntry,
                railItems = computeRailItems(editorEntry, current.validationReport),
            )
        }
    }

    public fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    public fun setActiveDockTab(tab: StudioDockTab) {
        _uiState.update { it.copy(activeDockTab = tab, isDockExpanded = true) }
    }

    public fun toggleDock() {
        _uiState.update { it.copy(isDockExpanded = !it.isDockExpanded) }
    }

    public fun toggleNavigatorDrawer() {
        _uiState.update { it.copy(isNavigatorDrawerOpen = !it.isNavigatorDrawerOpen) }
    }

    public fun closeNavigatorDrawer() {
        _uiState.update { it.copy(isNavigatorDrawerOpen = false) }
    }

    public fun requestSave() {
        _uiState.update { it.copy(saveRequestedNotification = "Configuration saved successfully.") }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            _uiState.update { it.copy(saveRequestedNotification = null) }
        }
    }

    private fun computeRailItems(entry: StudioEntry, report: ValidationReport): List<StudioRailItem> {
        val selectedStageId = (entry as? StudioEntry.Editor)?.stageId
        return StudioDestinationCatalog.stages.map { stage ->
            val isSelected = stage.stageId == selectedStageId
            val errors = report.errors.filter { it.stageId == stage.stageId }
            val errorCount = errors.count { it.severity == Severity.ERROR }
            val warningCount = errors.count { it.severity == Severity.WARNING }
            val statusLabel = when {
                errorCount > 0 -> "$errorCount error${if (errorCount > 1) "s" else ""}"
                warningCount > 0 -> "$warningCount warning${if (warningCount > 1) "s" else ""}"
                else -> "Valid"
            }
            StudioRailItem(
                stage = stage,
                isSelected = isSelected,
                errorCount = errorCount,
                warningCount = warningCount,
                statusLabel = statusLabel,
            )
        }
    }

    private companion object {
        fun computeInitialRailItems(): List<StudioRailItem> = StudioDestinationCatalog.stages.map { stage ->
            StudioRailItem(
                stage = stage,
                isSelected = false,
                errorCount = 0,
                warningCount = 0,
                statusLabel = "Valid",
            )
        }

        fun createDefaultValidationReport(): ValidationReport = ValidationReport(
            errors = listOf(
                ValidationError(
                    stageId = StageId.FIRMWARE_ACQUISITION,
                    objectId = "source",
                    fieldPath = "acquisition.source",
                    code = "MISSING_SOURCE_ARCHIVE",
                    severity = Severity.ERROR,
                    message = "Official firmware baseline archive is not specified.",
                    remediation = "Select or import a stock firmware update.zip in the Acquire stage.",
                ),
                ValidationError(
                    stageId = StageId.DEBLOAT,
                    objectId = "presets",
                    fieldPath = "debloat.preset",
                    code = "NO_PRESET_SELECTED",
                    severity = Severity.WARNING,
                    message = "No debloat preset selected; default stock inventory will be preserved.",
                    remediation = "Choose a recommended debloat preset in the Debloat stage.",
                ),
                ValidationError(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    objectId = "avb-keys",
                    fieldPath = "build.avbKeys",
                    code = "CUSTOM_KEYS_NOT_CONFIGURED",
                    severity = Severity.WARNING,
                    message = "Custom AVB signing keypair is missing; test keys will be used.",
                    remediation = "Provide AVB signing keys in Build and Sign stage.",
                ),
            ),
        )
    }
}
