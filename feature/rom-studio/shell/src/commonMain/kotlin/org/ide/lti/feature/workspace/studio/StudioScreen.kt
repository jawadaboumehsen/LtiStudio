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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.layout.ContentHeader
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdeRightRail
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuItem
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuTabBar
import org.ide.lti.core.designsystem.component.layout.resolveNavIcon
import org.ide.lti.core.designsystem.component.progress.GlassLoadingStatus
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.workbench.WorkbenchLog
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageDescriptor
import org.ide.lti.feature.rom.studio.api.StageEditorContext
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.api.StudioIconKey
import org.ide.lti.feature.rom.studio.api.StudioSubobjectId
import org.ide.lti.feature.rom.studio.api.descriptorFor
import org.ide.lti.feature.workspace.panels.run.ArtifactsPanel
import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import org.ide.lti.feature.workspace.panels.run.RunPanel
import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationPanel
import org.ide.lti.feature.workspace.studio.components.OutputPanel
import org.ide.lti.feature.workspace.studio.components.PreflightCard
import org.ide.lti.feature.workspace.studio.components.StudioStatusBar
import org.ide.lti.feature.workspace.studio.components.StudioTopAppBar
import org.ide.lti.feature.workspace.studio.components.TopBarActionButton
import org.ide.lti.feature.workspace.studio.components.TopBarTargetSpecifier
import org.ide.lti.feature.workspace.studio.components.WorkspaceRestorePointsPanel
import org.ide.lti.feature.workspace.studio.components.WorkspaceStoragePanel
import org.ide.lti.feature.workspace.studio.components.WorkspaceStorageViewModel
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StudioIconKey.painter(): Painter = when (this) {
    StudioIconKey.ACQUIRE -> AppIconsRomStages.acquire()
    StudioIconKey.EXTRACT -> AppIconsRomStages.extract()
    StudioIconKey.ASSEMBLE -> AppIconsRomStages.assemble()
    StudioIconKey.DEBLOAT -> AppIconsRomStages.debloat()
    StudioIconKey.PATCH -> AppIconsRomStages.patches()
    StudioIconKey.BUILD -> AppIconsRomStages.build()
    StudioIconKey.METADATA -> AppIconsRomStages.releaseMetadata()
    StudioIconKey.PUBLISH -> AppIconsRomStages.publish()
}

@Suppress("LongParameterList", "UnusedParameter")
@Composable
fun StudioScreen(
    workspaceId: String,
    modifier: Modifier = Modifier,
    initialStageId: StageId? = null,
    initialObjectId: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToRun: (workspaceId: String, runId: String) -> Unit = { _, _ -> },
    onNavigateToPlugins: (workspaceId: String?, packageId: String?, destination: String) -> Unit = { _, _, _ -> },
    onNavigateToSetup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenEditConfiguration: (targetId: String) -> Unit = {},
    viewModel: StudioViewModel = koinViewModel { parametersOf(workspaceId) },
    workspaceManager: WorkspaceManager = koinInject(),
    workbenchLog: WorkbenchLog? = getKoin().getOrNull(),
    appSettings: AppSettingsState? = getKoin().getOrNull(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val shellState by viewModel.shellState.collectAsState()
    val stageEditorRegistry = viewModel.stageEditorRegistry

    LaunchedEffect(workspaceId, initialStageId, initialObjectId) {
        viewModel.onEntry(
            workspaceId = workspaceId,
            requestedStageId = initialStageId,
            requestedObjectId = initialObjectId,
        )
    }

    val selectedStageId = (uiState.entry as? StudioEntry.Editor)?.stageId
        ?: uiState.presentation.editorStageId

    val selectedObjectId = (uiState.entry as? StudioEntry.Editor)?.objectId
        ?: uiState.presentation.editorObjectId

    StudioShellContent(
        uiState = shellState,
        stageEditorRegistry = stageEditorRegistry,
        selectedObjectId = selectedObjectId,
        simpleView = appSettings?.appearance?.simpleStudio == true,
        onSelectStageObject = { stageId, objectId ->
            viewModel.selectObject(stageId, objectId)
        },
        onSelectGlobalDestination = { destination ->
            viewModel.selectGlobalDestination(destination)
            when (destination) {
                GlobalDestination.Setup -> onNavigateToSetup()
                GlobalDestination.Settings -> onOpenSettings()
                GlobalDestination.PluginManager -> onNavigateToPlugins(workspaceId, null, "marketplace")
                GlobalDestination.Workspace -> {}
            }
        },
        onSelectWorkspaceSection = { section ->
            // The device belongs to the workspace and is edited in its own configuration panel.
            val target = if (section == WorkspaceSection.TargetProfile) WorkspaceSection.RomConfig else section
            viewModel.selectWorkspaceSection(target)
        },
        onSelectStage = { stageId ->
            viewModel.selectStage(stageId)
        },
        onValidate = { viewModel.validateWorkspace() },
        onSave = { viewModel.saveWorkspace() },
        modifier = modifier,
        onOpenProblems = { viewModel.selectWorkspaceSection(WorkspaceSection.Overview) },
        onOpenActivity = { viewModel.selectWorkspaceSection(WorkspaceSection.RunHistory) },
        output = workbenchLog?.let { log -> { OutputPanel(log) } },
        content = {
            StudioContent(
                uiState = uiState,
                shellState = shellState,
                viewModel = viewModel,
                stageEditorRegistry = stageEditorRegistry,
                workspaceId = workspaceId,
                selectedStageId = selectedStageId,
                selectedObjectId = selectedObjectId,
                onNavigateToSetup = onNavigateToSetup,
                onOpenSettings = onOpenSettings,
                onOpenPlugins = { onNavigateToPlugins(workspaceId, null, "import") },
            )
        },
    )
}

@Suppress("LongParameterList")
@Composable
private fun StudioContent(
    uiState: StudioUiState,
    shellState: WorkspaceShellUiState,
    viewModel: StudioViewModel,
    stageEditorRegistry: StageEditorRegistry,
    workspaceId: String,
    selectedStageId: StageId?,
    selectedObjectId: String?,
    onNavigateToSetup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlugins: () -> Unit,
) {
    if (selectedStageId != null && uiState.entry is StudioEntry.Editor) {
        val editor = stageEditorRegistry.editorFor(selectedStageId)
        val descriptor = stageEditorRegistry.descriptorFor(selectedStageId)
            ?: CanonicalStageDescriptors.descriptorFor(selectedStageId)
            ?: CanonicalStageDescriptors.ALL.first()
        val activeObjectId = selectedObjectId ?: descriptor.subobjects.firstOrNull()?.id?.value

        if (editor != null) {
            val context = StageEditorContext(
                workspaceId = workspaceId,
                selectedObjectId = activeObjectId?.let { StudioSubobjectId(it) },
                onSelectedObjectChange = { objId -> viewModel.selectObject(selectedStageId, objId.value) },
                onSaveRequested = { viewModel.saveWorkspace() },
                onValidationRequested = { viewModel.validateWorkspace() },
                onOpenPlugins = onOpenPlugins,
            )
            editor.Content(context = context, modifier = Modifier.fillMaxSize())
        } else {
            StageObjectEditor(
                stage = descriptor,
                objectId = activeObjectId,
                validationErrors = uiState.validationReport.errors.filter {
                    it.stageId == descriptor.stageId && it.objectId == activeObjectId
                },
                onSave = { viewModel.saveWorkspace() },
            )
        }
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.Artifacts) {
        ArtifactsPanel(runs = uiState.runs, modifier = Modifier.fillMaxSize())
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.RomConfig) {
        WorkspaceConfigurationPanel(modifier = Modifier.fillMaxSize())
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.RunHistory) {
        RunPanel(
            viewModel = koinViewModel<BuildRunViewModel>(),
            onNavigateToSetup = onNavigateToSetup,
            onOpen = { stage, objectId -> openAt(viewModel, stage, objectId) },
            modifier = Modifier.fillMaxSize(),
        )
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.Storage) {
        WorkspaceStoragePanel(
            viewModel = koinViewModel<WorkspaceStorageViewModel> { parametersOf(workspaceId) },
            modifier = Modifier.fillMaxSize(),
        )
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.RestorePoints) {
        WorkspaceRestorePointsPanel(
            restoreIndex = uiState.restoreIndex,
            restoreMessage = uiState.restoreMessage,
            restoreOperation = uiState.restoreOperation,
            onRestoreOne = { viewModel.restorePoint(it) },
            onRefresh = { viewModel.refreshRestorePoints() },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        WorkspaceOverview(
            state = uiState.overviewState,
            onAction = { action ->
                dispatchOverviewAction(
                    action = action,
                    viewModel = viewModel,
                    workspaceId = workspaceId,
                    onNavigateToSetup = onNavigateToSetup,
                    onOpenSettings = onOpenSettings,
                )
            },
            onReload = { viewModel.onEntry(workspaceId) },
            onEditProfile = { viewModel.selectWorkspaceSection(WorkspaceSection.RomConfig) },
            onOpenHistory = { viewModel.selectWorkspaceSection(WorkspaceSection.RunHistory) },
            preflight = {
                PreflightCard(
                    vm = koinViewModel<PreflightViewModel>(key = workspaceId) { parametersOf(workspaceId) },
                    onOpen = { stage, objectId -> openAt(viewModel, stage, objectId) },
                    onOpenSetup = onNavigateToSetup,
                    resolveLink = { fix ->
                        readinessStageLink(fix, uiState.railItems.map { it.stage.stageId }, uiState.validationReport)
                    },
                )
            },
        )
    }
}

/** Opens [stage]'s editor, at [objectId] when one is named: where a finding is fixed. */
private fun openAt(viewModel: StudioViewModel, stage: StageId, objectId: String) {
    viewModel.selectStage(stage)
    if (objectId.isNotEmpty()) viewModel.selectObject(stage, objectId)
}

private fun dispatchOverviewAction(
    action: WorkspaceActionUi,
    viewModel: StudioViewModel,
    workspaceId: String,
    onNavigateToSetup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (action) {
        is WorkspaceActionUi.OpenStage -> action.objectId?.let { viewModel.selectObject(action.stageId, it) }
            ?: viewModel.selectStage(action.stageId)

        is WorkspaceActionUi.OpenWorkspaceSection -> viewModel.selectWorkspaceSection(action.section)

        WorkspaceActionUi.Reload -> viewModel.onEntry(workspaceId)

        WorkspaceActionUi.Validate -> viewModel.validateWorkspace()

        WorkspaceActionUi.Save -> viewModel.saveWorkspace()

        WorkspaceActionUi.OpenSetup -> onNavigateToSetup()

        WorkspaceActionUi.OpenSettings -> onOpenSettings()
    }
}

@Suppress("LongParameterList")
@Composable
fun StudioShellContent(
    uiState: WorkspaceShellUiState,
    onSelectGlobalDestination: (GlobalDestination) -> Unit,
    onSelectWorkspaceSection: (WorkspaceSection) -> Unit,
    onSelectStage: (StageId) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    stageEditorRegistry: StageEditorRegistry? = null,
    selectedObjectId: String? = null,
    onSelectStageObject: ((StageId, String) -> Unit)? = null,
    onOpenProblems: (() -> Unit)? = null,
    onOpenActivity: (() -> Unit)? = null,
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
    iconsOnly: Boolean = false,
    /** Simple Studio view: each stage's main panes only ([CanonicalStageDescriptors.SIMPLE]). */
    simpleView: Boolean = false,
    /** The workbench jobs' Output panel, under the content. */
    output: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val breadcrumbItems: List<String> = remember(
        uiState.globalDestination,
        uiState.selectedWorkspaceSection,
        uiState.pipelineItems,
        selectedObjectId,
        stageEditorRegistry,
    ) {
        resolveStudioBreadcrumbs(uiState, selectedObjectId, stageEditorRegistry)
    }

    val stages = remember(uiState.pipelineItems, uiState.deviceHasError) {
        resolveStudioPipelineStages(uiState)
    }

    val selectedStageItem = remember(uiState.pipelineItems) {
        uiState.pipelineItems.firstOrNull { it.isSelected }
    }
    val selectedStageId = selectedStageItem?.stageId?.name
    val isStageEditor = selectedStageId != null && uiState.selectedWorkspaceSection == null

    val headerTitle = remember(
        uiState.selectedWorkspaceSection,
        selectedStageItem,
        selectedObjectId,
        stageEditorRegistry,
    ) {
        resolveHeaderTitle(
            selectedSection = uiState.selectedWorkspaceSection,
            stageItem = selectedStageItem,
            selectedObjectId = selectedObjectId,
            registry = stageEditorRegistry,
        )
    }

    val headerSubtitle = remember(
        uiState.selectedWorkspaceSection,
        selectedStageItem,
        selectedObjectId,
        stageEditorRegistry,
    ) {
        resolveHeaderSubtitle(
            selectedSection = uiState.selectedWorkspaceSection,
            stageItem = selectedStageItem,
            selectedObjectId = selectedObjectId,
            registry = stageEditorRegistry,
        )
    }

    IdeAppFrame(
        modifier = modifier.testTag("StudioShellLayout"),
        topBar = {
            StudioTopAppBar(
                uiState = uiState,
                onSelectGlobalDestination = onSelectGlobalDestination,
                onValidate = onValidate,
                onSave = onSave,
                windowControls = windowControls,
                onDragWindow = onDragWindow,
            )
        },
        pipelineRail = {
            IdePipelineRail(
                stages = stages,
                selectedStageId = selectedStageId ?: DEVICE_RAIL_ITEM.id.takeIf {
                    uiState.selectedWorkspaceSection == WorkspaceSection.RomConfig
                },
                onSelectStage = { stageName ->
                    val stageId = StageId.entries.firstOrNull { it.name == stageName }
                    when {
                        stageName == DEVICE_RAIL_ITEM.id -> onSelectWorkspaceSection(WorkspaceSection.RomConfig)
                        stageId != null -> onSelectStage(stageId)
                    }
                },
                modifier = Modifier.testTag("IdePipelineRail"),
                iconsOnly = iconsOnly,
            )
        },
        navigator = null,
        rightPanel = {
            WorkspaceRightRail()
        },
        content = {
            StudioMainContent(
                uiState = uiState,
                isStageEditor = isStageEditor,
                headerTitle = headerTitle,
                headerSubtitle = headerSubtitle,
                breadcrumbItems = breadcrumbItems,
                stageEditorRegistry = stageEditorRegistry,
                selectedObjectId = selectedObjectId,
                onSelectStageObject = onSelectStageObject,
                onSelectWorkspaceSection = onSelectWorkspaceSection,
                simpleView = simpleView,
                onValidate = onValidate,
                onSelectStage = onSelectStage,
                output = output,
                content = content,
            )
        },
        statusBar = {
            StudioStatusBar(
                uiState = uiState,
                onOpenProblems = onOpenProblems,
                onOpenActivity = onOpenActivity,
            )
        },
    )
}

@Composable
private fun StudioMainContent(
    uiState: WorkspaceShellUiState,
    isStageEditor: Boolean,
    headerTitle: String,
    headerSubtitle: String?,
    breadcrumbItems: List<String>,
    stageEditorRegistry: StageEditorRegistry?,
    selectedObjectId: String?,
    onSelectStageObject: ((StageId, String) -> Unit)?,
    onSelectWorkspaceSection: (WorkspaceSection) -> Unit,
    simpleView: Boolean,
    onValidate: () -> Unit,
    onSelectStage: (StageId) -> Unit,
    output: @Composable (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isStageEditor) {
            ContentHeader(
                title = headerTitle,
                breadcrumbs = breadcrumbItems,
                subtitle = headerSubtitle,
                context = uiState.targetContext?.let { target -> { TopBarTargetSpecifier(target) } },
                actions = {
                    val isValidating = uiState.validateOperation is OperationUiState.Running
                    TopBarActionButton(
                        label = "Validate",
                        isLoading = isValidating,
                        isEnabled = !isValidating,
                        onClick = onValidate,
                        testTag = "TopBar_Validate",
                    )
                },
            )

            val selectedStage = uiState.pipelineItems.firstOrNull { it.isSelected }
            val descriptor = selectedStage?.let { stage ->
                stageEditorRegistry?.descriptorFor(stage.stageId)
                    ?: CanonicalStageDescriptors.descriptorFor(stage.stageId)
            }
            if (descriptor != null && descriptor.subobjects.isNotEmpty()) {
                StageSubMenuTabBar(
                    stage = descriptor,
                    selectedObjectId = selectedObjectId,
                    onSelectStageObject = onSelectStageObject,
                    simple = simpleView,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                )
            }
        } else {
            WorkspaceSubMenuTabBar(
                selectedSection = uiState.selectedWorkspaceSection,
                onSelectSection = onSelectWorkspaceSection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            )
        }
        if (uiState.saveOperation is OperationUiState.Running) {
            GlassLoadingStatus("Saving workspace…", Modifier.padding(horizontal = Spacing.ExtraLarge))
        }
        // The result of Validate / Check readiness, until the next one.
        (uiState.validateOperation as? OperationUiState.Succeeded)?.message?.let { message ->
            Text(
                text = message,
                color = if (message.startsWith(READY)) {
                    GlassTheme.diagnosticColors.success
                } else {
                    GlassTheme.diagnosticColors.warning
                },
                fontSize = FontSize.BodySmall,
                modifier = Modifier
                    .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.ExtraSmall)
                    .testTag("ValidationResult"),
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
        if (isStageEditor) {
            val items = uiState.pipelineItems
            items.getOrNull(items.indexOfFirst { it.isSelected } + 1)?.let { next ->
                StageContinueBar(next = next, onContinue = { onSelectStage(next.stageId) })
            }
        }
        output?.invoke()
    }
}

/** The way forward through the pipeline: the next stage, or what to finish before it opens. */
@Composable
private fun StageContinueBar(next: PipelineRailItemUi, onContinue: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Small)
            .testTag("StageContinueBar"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        next.disabledReason?.takeIf { !next.isEnabled }?.let {
            Text(text = it, color = GlassTheme.diagnosticColors.warning, fontSize = FontSize.BodySmall)
        }
        GlassButton(onClick = onContinue, enabled = next.isEnabled, variant = GlassButtonVariant.Primary) {
            Text("Continue to ${next.label} →")
        }
    }
}

@Composable
private fun WorkspaceRightRail(modifier: Modifier = Modifier) {
    // No tool windows yet: the rail keeps the frame's shape without offering actions that do nothing.
    IdeRightRail(items = emptyList(), modifier = modifier.testTag("WorkspaceRightRail"))
}

@Composable
private fun StageSubMenuTabBar(
    stage: StageDescriptor,
    selectedObjectId: String?,
    onSelectStageObject: ((StageId, String) -> Unit)?,
    modifier: Modifier = Modifier,
    simple: Boolean = false,
) {
    // Simple view: the main panes, plus the one open now (a fix's link may open another).
    val items = remember(stage, simple, selectedObjectId) {
        CanonicalStageDescriptors.visible(stage, simple, selectedObjectId).map { sub ->
            IdeSubMenuItem(
                id = sub.id.value,
                label = sub.title,
                icon = resolveNavIcon(sub.id.value),
            )
        }
    }
    val activeObjectId = selectedObjectId ?: stage.subobjects.firstOrNull()?.id?.value

    IdeSubMenuTabBar(
        items = items,
        selectedItemId = activeObjectId,
        onSelectItem = { objectId ->
            onSelectStageObject?.invoke(stage.stageId, objectId)
        },
        modifier = modifier.testTag("StageNavigator"),
        wrap = true,
    )
}

@Composable
private fun WorkspaceSubMenuTabBar(
    selectedSection: WorkspaceSection?,
    onSelectSection: (WorkspaceSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember {
        listOf(
            IdeSubMenuItem(
                id = WorkspaceSection.Overview.name,
                label = "Overview",
                icon = resolveNavIcon("dashboard"),
            ),
            IdeSubMenuItem(
                id = WorkspaceSection.RomConfig.name,
                label = "ROM configuration",
                icon = resolveNavIcon("settings"),
            ),
            IdeSubMenuItem(
                id = WorkspaceSection.RunHistory.name,
                label = "Run history",
                icon = resolveNavIcon("history"),
            ),
            IdeSubMenuItem(
                id = WorkspaceSection.Artifacts.name,
                label = "Artifacts",
                icon = resolveNavIcon("archive"),
            ),
            IdeSubMenuItem(
                id = WorkspaceSection.RestorePoints.name,
                label = "Restore points",
                icon = resolveNavIcon("history"),
            ),
            IdeSubMenuItem(
                id = WorkspaceSection.Storage.name,
                label = "Disk usage",
                icon = resolveNavIcon("archive"),
            ),
        )
    }

    IdeSubMenuTabBar(
        items = items,
        selectedItemId = selectedSection?.name,
        onSelectItem = { itemId ->
            val section = WorkspaceSection.entries.firstOrNull { it.name == itemId }
            if (section != null) {
                onSelectSection(section)
            }
        },
        modifier = modifier.testTag("WorkspaceNavigator"),
        wrap = true,
    )
}

/**
 * Stage Object Editor component for stages 1..8.
 */
@Composable
private fun StageObjectEditor(
    stage: StageDescriptor,
    objectId: String?,
    validationErrors: List<ValidationError>,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        item(key = "editor-placeholder-card") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = Spacing.Large,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        Icon(
                            painter = stage.iconKey.painter(),
                            contentDescription = stage.accessibleLabel,
                            modifier = Modifier.size(IconSize.ActionCard),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(
                                text = "${stage.title} Editor: ${objectId ?: "Settings"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            val position = PipelineStageSequence.ORDER.indexOf(stage.stageId) + 1
                            Text(
                                text = "Pipeline Stage $position • Dedicated configuration editor placeholder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(StrokeWidth.Hairline)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)),
                    )

                    Text(
                        text = "The dedicated editor components for stage ${stage.title} " +
                            "(subobject: ${objectId ?: "default"}) are under active development. " +
                            "Configuration parameters defined in this stage can be reviewed below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassButton(
                            onClick = {},
                            enabled = false,
                            variant = GlassButtonVariant.Standard,
                            shape = GlassShapes.HazeCompact,
                        ) {
                            Text(
                                "Execute Stage (Unavailable in Studio)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        GlassButton(
                            onClick = onSave,
                            variant = GlassButtonVariant.Primary,
                            shape = GlassShapes.HazeCompact,
                        ) {
                            Text("Save Draft", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        if (validationErrors.isNotEmpty()) {
            item(key = "editor-validation-issues") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.Medium,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Validation Issues for ${objectId ?: "this object"}:",
                            style = MaterialTheme.typography.titleSmall,
                            color = GlassTheme.diagnosticColors.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        validationErrors.forEach { err ->
                            Text(
                                text = "• [${err.fieldPath}] ${err.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (err.remediation != null) {
                                Text(
                                    text = "  Remediation: ${err.remediation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
