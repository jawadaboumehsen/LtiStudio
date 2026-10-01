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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Terminal
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
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.component.layout.IdeRightRail
import org.ide.lti.core.designsystem.component.layout.IdeRightRailItem
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuItem
import org.ide.lti.core.designsystem.component.layout.IdeSubMenuTabBar
import org.ide.lti.core.designsystem.component.layout.resolveNavIcon
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageDescriptor
import org.ide.lti.feature.rom.studio.api.StageEditorContext
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.api.StudioIconKey
import org.ide.lti.feature.rom.studio.api.StudioSubobjectId
import org.ide.lti.feature.rom.studio.api.descriptorFor
import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import org.ide.lti.feature.workspace.panels.run.RunPanel
import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationPanel
import org.ide.lti.feature.workspace.studio.components.StudioStatusBar
import org.ide.lti.feature.workspace.studio.components.StudioTopAppBar
import org.ide.lti.feature.workspace.studio.components.TopBarActionButton
import org.ide.lti.feature.workspace.studio.components.TopBarTargetSpecifier
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
public fun StudioIconKey.painter(): Painter = when (this) {
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
public fun StudioScreen(
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
) {
    val uiState by viewModel.uiState.collectAsState()
    val shellState by viewModel.shellState.collectAsState()
    val stageEditorRegistry = viewModel.stageEditorRegistry
    val currentWorkspace by workspaceManager.currentWorkspace.collectAsState()
    val boundProfileId = currentWorkspace?.targetBinding?.profileId

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
            if (section == WorkspaceSection.TargetProfile && boundProfileId != null) {
                onOpenEditConfiguration(boundProfileId)
            } else {
                viewModel.selectWorkspaceSection(section)
            }
        },
        onSelectStage = { stageId ->
            viewModel.selectStage(stageId)
        },
        onValidate = { viewModel.validateWorkspace() },
        onSave = { viewModel.saveWorkspace() },
        modifier = modifier,
        iconsOnly = true,
        onOpenProblems = { viewModel.selectWorkspaceSection(WorkspaceSection.Overview) },
        onOpenActivity = { viewModel.selectWorkspaceSection(WorkspaceSection.RunHistory) },
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
                boundProfileId = boundProfileId,
                onOpenEditConfiguration = onOpenEditConfiguration,
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
    boundProfileId: String?,
    onOpenEditConfiguration: (targetId: String) -> Unit,
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
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.RomConfig) {
        WorkspaceConfigurationPanel(modifier = Modifier.fillMaxSize())
    } else if (shellState.selectedWorkspaceSection == WorkspaceSection.RunHistory) {
        RunPanel(
            viewModel = koinViewModel<BuildRunViewModel>(),
            onNavigateToSetup = onNavigateToSetup,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // Overview, Artifacts, and TargetProfile have no dedicated panel yet - the overview
        // stays the fallback for them, matching prior behavior.
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
            onEditProfile = {
                if (boundProfileId != null) {
                    onOpenEditConfiguration(boundProfileId)
                } else {
                    viewModel.selectWorkspaceSection(WorkspaceSection.TargetProfile)
                }
            },
            onOpenHistory = { viewModel.selectWorkspaceSection(WorkspaceSection.RunHistory) },
        )
    }
}

private fun dispatchOverviewAction(
    action: WorkspaceActionUi,
    viewModel: StudioViewModel,
    workspaceId: String,
    onNavigateToSetup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (action) {
        is WorkspaceActionUi.OpenStage -> viewModel.selectStage(action.stageId)
        is WorkspaceActionUi.OpenWorkspaceSection -> viewModel.selectWorkspaceSection(action.section)
        WorkspaceActionUi.Reload -> viewModel.onEntry(workspaceId)
        WorkspaceActionUi.Validate -> viewModel.validateWorkspace()
        WorkspaceActionUi.Save -> viewModel.saveWorkspace()
        WorkspaceActionUi.OpenSetup -> onNavigateToSetup()
        WorkspaceActionUi.OpenSettings -> onOpenSettings()
    }
}

@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
@Composable
public fun StudioShellContent(
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
    content: @Composable () -> Unit,
) {
    val breadcrumbItems: List<String> = remember(
        uiState.globalDestination,
        uiState.selectedWorkspaceSection,
        uiState.pipelineItems,
        selectedObjectId,
        stageEditorRegistry,
    ) {
        val selectedStage = uiState.pipelineItems.firstOrNull { it.isSelected }
        if (selectedStage != null && uiState.selectedWorkspaceSection == null) {
            val currentStage = stageEditorRegistry?.descriptorFor(selectedStage.stageId)
                ?: CanonicalStageDescriptors.descriptorFor(selectedStage.stageId)
            val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
            val currentSubobject = currentStage?.subobjects?.firstOrNull { it.id.value == activeObjectId }
            if (currentSubobject != null && currentStage != null) {
                listOf("ROM Setup", currentStage.title, currentSubobject.title)
            } else {
                listOf("ROM Setup", selectedStage.label)
            }
        } else {
            val sectionName = when (uiState.selectedWorkspaceSection) {
                WorkspaceSection.Overview -> "Overview"
                WorkspaceSection.RomConfig -> "ROM Configuration"
                WorkspaceSection.RunHistory -> "Run History"
                WorkspaceSection.Artifacts -> "Artifacts"
                WorkspaceSection.TargetProfile -> "Target Profile"
                null -> "Overview"
            }
            listOf("Workspace", sectionName)
        }
    }

    val stages = remember(uiState.pipelineItems) {
        uiState.pipelineItems.map { item ->
            IdePipelineStageItem(
                id = item.stageId.name,
                label = item.label,
                icon = item.iconKey,
                enabled = item.isEnabled,
            )
        }
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
                selectedStageId = selectedStageId,
                onSelectStage = { stageName ->
                    val stageId = StageId.entries.firstOrNull { it.name == stageName }
                    if (stageId != null) {
                        onSelectStage(stageId)
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
            Column(modifier = Modifier.fillMaxSize()) {
                if (isStageEditor) {
                    ContentHeader(
                        title = headerTitle,
                        breadcrumbs = breadcrumbItems,
                        subtitle = headerSubtitle,
                        actions = {
                            val targetContext = uiState.targetContext
                            if (targetContext != null) {
                                TopBarTargetSpecifier(
                                    targetContext = targetContext,
                                )
                            }

                            val isValidating = uiState.validateOperation is OperationUiState.Running
                            TopBarActionButton(
                                label = "Validate",
                                isLoading = isValidating,
                                isEnabled = !isValidating,
                                onClick = onValidate,
                                testTag = "TopBar_Validate",
                            )

                            val isSaving = uiState.saveOperation is OperationUiState.Running
                            TopBarActionButton(
                                label = "Save",
                                isLoading = isSaving,
                                isEnabled = !isSaving,
                                isPrimary = true,
                                onClick = onSave,
                                testTag = "TopBar_Save",
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

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    content()
                }
            }
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
private fun WorkspaceRightRail(modifier: Modifier = Modifier) {
    val items = remember {
        listOf(
            IdeRightRailItem(
                id = "ai",
                label = "AI Assistant (Demo)",
                icon = Icons.Default.AutoAwesome,
            ),
            IdeRightRailItem(
                id = "terminal",
                label = "Terminal (Demo)",
                icon = Icons.Default.Terminal,
            ),
        )
    }
    IdeRightRail(
        items = items,
        modifier = modifier.testTag("WorkspaceRightRail"),
    )
}

@Composable
private fun StageSubMenuTabBar(
    stage: StageDescriptor,
    selectedObjectId: String?,
    onSelectStageObject: ((StageId, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val items = remember(stage) {
        stage.subobjects.map { sub ->
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
                id = WorkspaceSection.TargetProfile.name,
                label = "Target profile",
                icon = resolveNavIcon("phone"),
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

private fun resolveHeaderTitle(
    selectedSection: WorkspaceSection?,
    stageItem: PipelineRailItemUi?,
    selectedObjectId: String?,
    registry: StageEditorRegistry?,
): String {
    if (stageItem != null && selectedSection == null) {
        val currentStage = registry?.descriptorFor(stageItem.stageId)
            ?: CanonicalStageDescriptors.descriptorFor(stageItem.stageId)
        val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
        val currentSubobject = currentStage?.subobjects?.firstOrNull { it.id.value == activeObjectId }
        return currentSubobject?.title ?: currentStage?.title ?: stageItem.label
    }
    return when (selectedSection) {
        WorkspaceSection.Overview -> "Workspace Overview"
        WorkspaceSection.RomConfig -> "ROM Configuration"
        WorkspaceSection.RunHistory -> "Run History"
        WorkspaceSection.Artifacts -> "Generated Artifacts"
        WorkspaceSection.TargetProfile -> "Target Device Profile"
        null -> "Workspace Overview"
    }
}

private fun resolveHeaderSubtitle(
    selectedSection: WorkspaceSection?,
    stageItem: PipelineRailItemUi?,
    selectedObjectId: String?,
    registry: StageEditorRegistry?,
): String? {
    if (stageItem != null && selectedSection == null) {
        val currentStage = registry?.descriptorFor(stageItem.stageId)
            ?: CanonicalStageDescriptors.descriptorFor(stageItem.stageId)
        val activeObjectId = selectedObjectId ?: currentStage?.subobjects?.firstOrNull()?.id?.value
        return resolveStageSubtitle(stageItem.stageId, activeObjectId)
    }
    return null
}

private fun resolveStageSubtitle(stageId: StageId, subobjectId: String?): String = when (stageId) {
    StageId.FIRMWARE_ACQUISITION -> resolveAcquireSubtitle(subobjectId)
    StageId.FIRMWARE_EXTRACTION -> resolveExtractSubtitle(subobjectId)
    StageId.WORK_TREE_ASSEMBLY -> resolveAssembleSubtitle(subobjectId)
    StageId.DEBLOAT -> "Configure apps and components to remove."
    StageId.MODULE_APPLICATION -> "Enable and configure patch modules."
    StageId.BUILD_FLASHABLE_ZIP -> "Configure output images and package signing."
    StageId.GENERATE_OTA_MANIFEST -> "Edit release information and review derived details."
    StageId.PUBLISH_RELEASE -> "Configure release export, split archives, and distribution."
}

private fun resolveAcquireSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "source" -> "Configure acquisition mode and target firmware source."
    "firmware-baseline", "firmware_baseline", "baseline" ->
        "Select the official firmware build to use as the foundation for this workspace build."
    "existing-archives", "existing_archives", "archives" ->
        "Manage and verify previously downloaded or cached firmware archives."
    "download-policy", "download_policy", "download" ->
        "Configure how source files are downloaded, with retry and resume behavior."
    "integrity" -> "Verify downloaded source against expected hash."
    else -> "Acquire and verify official source firmware for this target."
}

private fun resolveExtractSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "archive-layout", "archive_layout" ->
        "Inspect the archive layout and choose target-supported extraction inputs."
    "dynamic-partitions", "dynamic_partitions" ->
        "Choose what to extract from the verified source."
    "boot-partitions", "boot_partitions" ->
        "Configure boot, recovery, and vendor_boot extraction parameters."
    "slot" -> "Select active A/B slot and payload partition mapping."
    "filesystem-handling", "filesystem_handling" ->
        "Configure filesystem extraction adapters and sparsing options."
    else -> "Extract and unpack partition images from verified firmware."
}

private fun resolveAssembleSubtitle(subobjectId: String?): String = when (subobjectId?.lowercase()) {
    "work-trees", "work_trees" ->
        "Configure source work trees and assembly options."
    "system-ext-handling", "system_ext_handling" ->
        "Configure how system_ext is handled during assembly."
    "boot-preparation", "boot_preparation" ->
        "Configure boot image handling for the assembled build."
    "aot-cleanup", "aot_cleanup" ->
        "Configure ahead-of-time compilation cleanup and odex stripping."
    "build-properties", "build_properties" ->
        "Configure build properties and fingerprint overrides."
    else -> "Assemble work trees and staging structures for customization."
}
