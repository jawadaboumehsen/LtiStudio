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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.ports.StudioPresentationStorePort
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.usecase.build.ObserveRunUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.StudioPresentation
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageDescriptor
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.api.allDescriptors
import org.ide.lti.feature.rom.studio.shell.registry.DefaultStageEditorRegistry

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
    val stage: StageDescriptor,
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
    val overviewState: WorkspaceOverviewUiState = WorkspaceOverviewMapper.map(),
    /** The workspace's saved build settings (`config.json`), as the overview reports them. */
    val snapshot: ConfigurationSnapshot? = null,
    /** The workspace's build runs, newest first. */
    val runs: List<BuildRun> = emptyList(),
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
@Suppress("TooManyFunctions")
public class StudioViewModel(
    private val studioPresentationStore: StudioPresentationStorePort,
    public val stageEditorRegistry: StageEditorRegistry =
        DefaultStageEditorRegistry(emptyList(), enforceFullPipeline = false),
    initialWorkspaceId: String = "",
    private val workspaceManager: WorkspaceManager? = null,
    private val workspaceDeviceRepository: WorkspaceDeviceRepository? = null,
    private val settingsStore: WorkspaceSettingsStore? = null,
    private val observeRuns: ObserveRunUseCase? = null,
) : ViewModel() {

    /** The workspace's own device configuration, when known; shown by the overview instead of an id. */
    private var device: TargetDevice? = null
    private var validationJob: Job? = null

    private fun activeDescriptors(): List<StageDescriptor> {
        val registeredMap = stageEditorRegistry.allDescriptors().associateBy { it.stageId }
        return CanonicalStageDescriptors.ALL.map { canonical ->
            registeredMap[canonical.stageId] ?: canonical
        }
    }

    private val _uiState = MutableStateFlow(
        StudioUiState(
            workspaceId = initialWorkspaceId,
            presentation = StudioPresentation(workspaceId = initialWorkspaceId),
            railItems = computeInitialRailItems(activeDescriptors()),
            validationReport = ValidationReport(errors = emptyList()),
        ),
    )
    public val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val _shellState = MutableStateFlow(
        buildInitialShellState(initialWorkspaceId, activeDescriptors()),
    )
    public val shellState: StateFlow<WorkspaceShellUiState> = _shellState.asStateFlow()

    init {
        observeWorkspaces()
        observeDevice()
        observeSettingsChanges()
        observeRunsOfWorkspace()
        if (initialWorkspaceId.isNotBlank()) {
            onEntry(initialWorkspaceId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDevice() {
        val repository = workspaceDeviceRepository ?: return
        viewModelScope.launch {
            _uiState
                .map { it.workspaceId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id.isBlank()) flowOf(null) else repository.observe(id).catch { emit(null) }
                }
                .collect { found ->
                    device = found
                    _uiState.update { it.copy(overviewState = recomputeOverview(it)) }
                    refreshValidation(_uiState.value.workspaceId)
                }
        }
    }

    private fun isLocked(stageId: StageId, report: ValidationReport): Boolean =
        report.blocks(null) || stageBlocking(stageId, activeDescriptors().map { it.stageId }, report) != null

    /** Every saved stage edit re-checks the settings, so the stage after it unlocks (or locks) right away. */
    private fun observeSettingsChanges() {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.changes.collect { id -> if (id == _uiState.value.workspaceId) refreshValidation(id) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRunsOfWorkspace() {
        val use = observeRuns ?: return
        viewModelScope.launch {
            _uiState
                .map { it.workspaceId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id.isBlank()) flowOf(emptyList()) else use.observeRuns(id).catch { emit(emptyList()) }
                }
                .collect { found ->
                    val runs = found.sortedByDescending { it.startedAt }
                    _uiState.update { current ->
                        val updated = current.copy(runs = runs)
                        updated.copy(overviewState = recomputeOverview(updated))
                    }
                }
        }
    }

    private fun observeWorkspaces() {
        if (workspaceManager != null) {
            viewModelScope.launch {
                workspaceManager.getWorkspaces().collect { workspaces ->
                    _uiState.update { current ->
                        val updatedState = current.copy(availableWorkspaces = workspaces)
                        updatedState.copy(overviewState = recomputeOverview(updatedState))
                    }
                    _shellState.update { buildShellState(_uiState.value) }
                }
            }
        }
    }

    /**
     * Handles entry or route parameter updates for the studio host route.
     * Calls [resolveStudioEntry] with the route arguments and stored presentation.
     */
    public fun onEntry(workspaceId: String, requestedStageId: StageId? = null, requestedObjectId: String? = null) {
        refreshValidation(workspaceId)
        viewModelScope.launch {
            val descriptors = activeDescriptors()
            val stored = studioPresentationStore.get(workspaceId)
            val entry = resolveStudioEntry(
                presentation = stored,
                requestedStageId = requestedStageId,
                requestedObjectId = requestedObjectId,
                descriptors = descriptors,
            )

            val basePresentation = stored ?: StudioPresentation(workspaceId = workspaceId)
            val updatedPresentation = when (entry) {
                is StudioEntry.Editor -> basePresentation.withEditor(entry.stageId, entry.objectId)
                is StudioEntry.Overview -> basePresentation.withOverview()
            }

            if (updatedPresentation != stored) {
                studioPresentationStore.put(updatedPresentation)
            }

            _uiState.update { current ->
                val railItems = computeRailItems(entry, current.validationReport, descriptors)
                val updated = current.copy(
                    workspaceId = workspaceId,
                    entry = entry,
                    presentation = updatedPresentation,
                    railItems = railItems,
                )
                updated.copy(overviewState = recomputeOverview(updated))
            }
            updateShellState()
        }
    }

    /**
     * Selects a pipeline stage.
     * If the current editor object belongs to this stage, it is preserved; otherwise selects the first subobject.
     * Presentation state only: never starts work, never mutates a snapshot, never resets a draft.
     */
    public fun selectStage(stageId: StageId) {
        // A stage opens only after the ones before it are configured: a locked one opens what to finish first.
        val report = _uiState.value.validationReport
        val blocker = stageBlocking(stageId, activeDescriptors().map { it.stageId }, report)
        when {
            report.blocks(null) -> selectWorkspaceSection(WorkspaceSection.RomConfig)
            blocker != null -> selectStage(blocker)
            else -> openStage(stageId)
        }
    }

    private fun openStage(stageId: StageId) {
        val descriptors = activeDescriptors()
        val stage = descriptors.firstOrNull { it.stageId == stageId } ?: return
        val currentEditor = _uiState.value.entry as? StudioEntry.Editor
        val targetObjectId = if (currentEditor != null &&
            stage.subobjects.any { it.id.value == currentEditor.objectId }
        ) {
            currentEditor.objectId
        } else {
            stage.subobjects.first().id.value
        }

        val updatedPresentation = _uiState.value.presentation.withEditor(stageId, targetObjectId)
        val newEntry = StudioEntry.Editor(stageId, targetObjectId, StudioEntryReason.DEEP_LINK)

        _uiState.update { current ->
            current.copy(
                entry = newEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(newEntry, current.validationReport, descriptors),
                isNavigatorDrawerOpen = false,
            )
        }
        updateShellState()

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
        val descriptors = activeDescriptors()
        val stage = descriptors.firstOrNull { it.stageId == stageId } ?: return
        if (stage.subobjects.none { it.id.value == objectId }) return

        val updatedPresentation = _uiState.value.presentation.withEditor(stageId, objectId)
        val newEntry = StudioEntry.Editor(stageId, objectId, StudioEntryReason.DEEP_LINK)

        _uiState.update { current ->
            current.copy(
                entry = newEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(newEntry, current.validationReport, descriptors),
                isNavigatorDrawerOpen = false,
            )
        }
        updateShellState()

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
        val descriptors = activeDescriptors()
        val overviewEntry = StudioEntry.Overview(StudioEntryReason.RESTORED)
        val updatedPresentation = _uiState.value.presentation.withOverview()
        _uiState.update { current ->
            current.copy(
                entry = overviewEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(overviewEntry, current.validationReport, descriptors),
                isNavigatorDrawerOpen = false,
            )
        }
        updateShellState()

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    /**
     * Restores the last active editor context from presentation state.
     */
    public fun returnToEditor() {
        val pres = _uiState.value.presentation
        val descriptors = activeDescriptors()
        val stageId = pres.editorStageId ?: descriptors.first().stageId
        val stage = descriptors.firstOrNull { it.stageId == stageId } ?: descriptors.first()
        val objectId = pres.editorObjectId?.takeIf { id -> stage.subobjects.any { it.id.value == id } }
            ?: stage.subobjects.first().id.value

        val editorEntry = StudioEntry.Editor(stageId, objectId, StudioEntryReason.RESTORED)
        val updatedPresentation = pres.withEditor(stageId, objectId)
        _uiState.update { current ->
            current.copy(
                entry = editorEntry,
                presentation = updatedPresentation,
                railItems = computeRailItems(editorEntry, current.validationReport, descriptors),
            )
        }
        updateShellState()

        viewModelScope.launch {
            studioPresentationStore.put(updatedPresentation)
        }
    }

    public fun selectGlobalDestination(destination: GlobalDestination) {
        if (destination == GlobalDestination.Workspace) {
            openOverview()
        }
        _shellState.update { current ->
            if (destination == GlobalDestination.Workspace) {
                current.copy(
                    globalDestination = destination,
                    selectedWorkspaceSection = WorkspaceSection.Overview,
                )
            } else {
                current.copy(
                    globalDestination = destination,
                    selectedWorkspaceSection = null,
                )
            }
        }
    }

    public fun selectWorkspaceSection(section: WorkspaceSection) {
        openOverview()
        _shellState.update { current ->
            current.copy(
                globalDestination = GlobalDestination.Workspace,
                selectedWorkspaceSection = section,
            )
        }
    }

    public fun validateWorkspace() {
        val generation = 1L
        _shellState.update { current ->
            current.copy(
                validateOperation = OperationUiState.Running(generation = generation, startedFromRevision = 1L),
            )
        }
        viewModelScope.launch {
            val workspaceId = _uiState.value.workspaceId
            val report = currentValidation(workspaceId, settingsStore?.current(workspaceId))
            _uiState.update { it.copy(validationReport = report) }
            updateShellState()
            val errorCount = report.errors.size
            _shellState.update { current ->
                current.copy(
                    validateOperation = OperationUiState.Succeeded(
                        generation = generation,
                        completedRevision = 1L,
                        message = if (errorCount == 0) {
                            "$READY: the device and every stage's settings pass."
                        } else {
                            val first = report.errors.first()
                            "$errorCount issue(s) to fix before a build. First: ${first.message}"
                        },
                    ),
                )
            }
        }
    }

    /** The real build check (the same one that gates Start): the device and every stage's saved settings. */
    private fun refreshValidation(workspaceId: String) {
        // Edits save a few times a second: one check after they settle, not one service round trip per save.
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            delay(VALIDATION_DEBOUNCE_MS)
            // A read that failed (the service busy or down) is not a missing configuration: keep what is known.
            val snapshot = runCatching { settingsStore?.current(workspaceId) }.getOrElse { return@launch }
            val report = currentValidation(workspaceId, snapshot)
            _uiState.update { current ->
                val updated = current.copy(validationReport = report, snapshot = snapshot)
                updated.copy(overviewState = recomputeOverview(updated))
            }
            updateShellState()
            // The open stage became locked (an earlier one now has errors): show what to finish first.
            (_uiState.value.entry as? StudioEntry.Editor)?.stageId?.takeIf { isLocked(it, report) }?.let(::selectStage)
        }
    }

    private suspend fun currentValidation(workspaceId: String, snapshot: ConfigurationSnapshot?): ValidationReport {
        val store = settingsStore ?: return ValidationReport(errors = emptyList())
        // The device the screen already watches covers a read that failed (the service busy for a moment).
        val device = store.device(workspaceId) ?: device
        val missing = when {
            snapshot == null -> "This workspace has no saved build settings yet."
            device == null -> "This workspace has no device configuration."
            else -> null
        }
        return if (missing != null || snapshot == null || device == null) {
            ValidationReport(
                errors = listOf(
                    ValidationError(
                        stageId = null,
                        objectId = null,
                        fieldPath = "workspace",
                        code = "WORKSPACE_INCOMPLETE",
                        severity = Severity.ERROR,
                        message = missing ?: "Incomplete workspace",
                    ),
                ),
            )
        } else {
            // Publish is not part of a build (the build check leaves it out), but its stage still shows its errors.
            val publish = StageSettingsValidator.validatePublish(snapshot.publish).errors.filter {
                it.severity == Severity.ERROR
            }
            ValidationReport(errors = DeviceConfigReadiness.check(device, snapshot) + publish)
        }
    }

    public fun saveWorkspace() {
        val generation = 1L
        _shellState.update { current ->
            current.copy(
                saveOperation = OperationUiState.Running(generation = generation, startedFromRevision = 1L),
            )
        }
        viewModelScope.launch {
            val result = settingsStore?.flush(_uiState.value.workspaceId) ?: Result.success(Unit)
            _shellState.update { current ->
                current.copy(
                    hasUnsavedChanges = result.isFailure,
                    saveOperation = result.fold(
                        onSuccess = {
                            OperationUiState.Succeeded(
                                generation = generation,
                                completedRevision = 2L,
                                message = "Workspace changes saved.",
                            )
                        },
                        onFailure = {
                            OperationUiState.Failed(
                                generation = generation,
                                startedFromRevision = 1L,
                                userMessage = it.message ?: "Could not save workspace changes",
                            )
                        },
                    ),
                )
            }
        }
    }

    public fun markDirty(isDirty: Boolean = true) {
        _shellState.update { it.copy(hasUnsavedChanges = isDirty) }
    }

    public fun setConnectionState(connectionState: ConnectionUiState) {
        _shellState.update { it.copy(connection = connectionState) }
    }

    public fun setActivityState(activityState: ActivityUiState) {
        _shellState.update { it.copy(activity = activityState) }
    }

    private fun updateShellState() {
        _shellState.update { buildShellState(_uiState.value) }
    }

    /**
     * Derives a fresh [WorkspaceOverviewUiState] from the current [StudioUiState].
     *
     * Workspace, validation report, and workspace-id are sourced from [uiState].
     */
    private fun recomputeOverview(uiState: StudioUiState): WorkspaceOverviewUiState {
        val workspace = uiState.availableWorkspaces.firstOrNull { it.id == uiState.workspaceId }
        val snapshot = uiState.snapshot?.takeIf { it.workspaceId == uiState.workspaceId }
        val acquisitionErrors = uiState.validationReport.errors.any {
            it.severity == Severity.ERROR &&
                (it.stageId == StageId.FIRMWARE_ACQUISITION || it.fieldPath.startsWith("acquisition"))
        }
        val signed = snapshot?.build?.signing?.signPackage == true
        return WorkspaceOverviewMapper.map(
            workspace = workspace,
            targetDevice = device,
            draft = snapshot?.let { WorkspacePipelineDraft.create(uiState.workspaceId, it, device) },
            validationReport = uiState.validationReport,
            runs = uiState.runs,
            // The keys are proven by a build that signed with them; the source by the saved, valid settings.
            signingVerified = signed && uiState.runs.any { it.state == RunState.SUCCEEDED },
            hasExplicitValidSource = snapshot != null && !acquisitionErrors,
        )
    }

    /** The editor header's target: this workspace, its own device, and the device file's live revision. */
    private fun targetContextOf(uiState: StudioUiState): TargetContextUi {
        if (uiState.workspaceId.isBlank()) {
            return TargetContextUi(
                targetId = DisplayValue.Unavailable("No active ROM profile selected"),
                profile = DisplayValue.Unavailable("Environment unconfigured"),
                revision = DisplayValue.Unavailable("Workspace not initialized"),
                draftBadge = null,
            )
        }
        val ws = uiState.availableWorkspaces.firstOrNull { it.id == uiState.workspaceId }
        val tb = ws?.targetBinding
        // A binding to the workspace itself is its own device: show the device, never the workspace id.
        val sharedProfile = tb?.profileId?.takeIf { it != ws.id }
        val noDevice = DisplayValue.Unavailable("No device")
        return TargetContextUi(
            targetId = DisplayValue.Available(ws?.name ?: uiState.workspaceId),
            profile = (sharedProfile ?: device?.name)?.let { DisplayValue.Available(it) } ?: noDevice,
            // The device file's own edit counter is the live revision; the binding keeps the one it was made with.
            revision = (device?.revision ?: tb?.profileRevision)?.let { DisplayValue.Available("rev $it") } ?: noDevice,
            draftBadge = null,
        )
    }

    private fun buildShellState(uiState: StudioUiState): WorkspaceShellUiState {
        val currentShell = _shellState.value
        val selectedStageId = (uiState.entry as? StudioEntry.Editor)?.stageId
        val items = createDefaultPipelineRailItems(selectedStageId, activeDescriptors()).map { item ->
            item.copy(hasError = uiState.validationReport.blocks(item.stageId))
        }
        val pipelineItems = lockStages(items, uiState.validationReport)
        val problemCount = uiState.validationReport.errors.size
        val targetContext = targetContextOf(uiState)

        val effectiveSection = if (currentShell.globalDestination == GlobalDestination.Workspace) {
            when (uiState.entry) {
                is StudioEntry.Overview -> currentShell.selectedWorkspaceSection ?: WorkspaceSection.Overview
                is StudioEntry.Editor -> null
            }
        } else {
            null
        }

        return currentShell.copy(
            targetContext = targetContext,
            problemCount = problemCount,
            pipelineItems = pipelineItems,
            deviceHasError = uiState.validationReport.blocks(null),
            selectedWorkspaceSection = effectiveSection,
        )
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

    private fun computeRailItems(
        entry: StudioEntry,
        report: ValidationReport,
        descriptors: List<StageDescriptor>,
    ): List<StudioRailItem> {
        val selectedStageId = (entry as? StudioEntry.Editor)?.stageId
        return descriptors.map { stage ->
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
        const val VALIDATION_DEBOUNCE_MS = 400L
        fun buildInitialShellState(workspaceId: String, descriptors: List<StageDescriptor>): WorkspaceShellUiState {
            val targetContext = if (workspaceId.isNotBlank()) {
                TargetContextUi(
                    targetId = DisplayValue.Available(workspaceId),
                    profile = DisplayValue.Available("Default"),
                    revision = DisplayValue.Available("rev-1"),
                    draftBadge = null,
                )
            } else {
                TargetContextUi(
                    targetId = DisplayValue.Unavailable("No active ROM profile selected"),
                    profile = DisplayValue.Unavailable("Environment unconfigured"),
                    revision = DisplayValue.Unavailable("Workspace not initialized"),
                    draftBadge = null,
                )
            }
            return WorkspaceShellUiState(
                globalDestination = GlobalDestination.Workspace,
                targetContext = targetContext,
                hasUnsavedChanges = false,
                validateOperation = OperationUiState.Idle,
                saveOperation = OperationUiState.Idle,
                connection = ConnectionUiState.Connected("Local Engine"),
                problemCount = 0,
                activity = ActivityUiState.Idle,
                pipelineItems = createDefaultPipelineRailItems(descriptors = descriptors),
                selectedWorkspaceSection = WorkspaceSection.Overview,
            )
        }

        fun computeInitialRailItems(descriptors: List<StageDescriptor>): List<StudioRailItem> =
            descriptors.map { stage ->
                StudioRailItem(
                    stage = stage,
                    isSelected = false,
                    errorCount = 0,
                    warningCount = 0,
                    statusLabel = "Valid",
                )
            }
    }
}
