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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.layout.IdeAppFrame
import org.ide.lti.core.designsystem.component.layout.IdeBreadcrumbBar
import org.ide.lti.core.designsystem.component.layout.IdePipelineRail
import org.ide.lti.core.designsystem.component.layout.IdePipelineStageItem
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.workspace.studio.components.StudioStatusBar
import org.ide.lti.feature.workspace.studio.components.StudioTopAppBar
import org.ide.lti.feature.workspace.studio.components.WorkspaceNavigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
    viewModel: StudioViewModel = koinViewModel { parametersOf(workspaceId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    val shellState by viewModel.shellState.collectAsState()

    LaunchedEffect(workspaceId, initialStageId, initialObjectId) {
        viewModel.onEntry(
            workspaceId = workspaceId,
            requestedStageId = initialStageId,
            requestedObjectId = initialObjectId,
        )
    }

    StudioShellContent(
        uiState = shellState,
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
            viewModel.selectWorkspaceSection(section)
        },
        onSelectStage = { stageId ->
            viewModel.selectStage(stageId)
        },
        onValidate = { viewModel.validateWorkspace() },
        onSave = { viewModel.saveWorkspace() },
        modifier = modifier,
        onOpenProblems = { viewModel.selectWorkspaceSection(WorkspaceSection.Overview) },
        onOpenActivity = { viewModel.selectWorkspaceSection(WorkspaceSection.RunHistory) },
        content = {
            val selectedStageId = (uiState.entry as? StudioEntry.Editor)?.stageId
                ?: uiState.presentation.editorStageId

            if (selectedStageId != null && uiState.entry is StudioEntry.Editor) {
                val currentStage = StudioDestinationCatalog.stage(selectedStageId)
                    ?: StudioDestinationCatalog.stages.first()
                val selectedObjectId = (uiState.entry as? StudioEntry.Editor)?.objectId
                    ?: uiState.presentation.editorObjectId
                    ?: currentStage.subobjects.firstOrNull()?.id

                StageObjectEditor(
                    stage = currentStage,
                    objectId = selectedObjectId,
                    validationErrors = uiState.validationReport.errors.filter {
                        it.stageId == currentStage.stageId && it.objectId == selectedObjectId
                    },
                    onSave = { viewModel.saveWorkspace() },
                )
            } else {
                WorkspaceOverview(
                    state = uiState.overviewState,
                )
            }
        },
    )
}

@Suppress("LongParameterList")
@Composable
public fun StudioShellContent(
    uiState: WorkspaceShellUiState,
    onSelectGlobalDestination: (GlobalDestination) -> Unit,
    onSelectWorkspaceSection: (WorkspaceSection) -> Unit,
    onSelectStage: (StageId) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    onOpenProblems: (() -> Unit)? = null,
    onOpenActivity: (() -> Unit)? = null,
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val breadcrumbItems: List<String> = remember(
        uiState.globalDestination,
        uiState.selectedWorkspaceSection,
        uiState.pipelineItems,
    ) {
        val selectedStage = uiState.pipelineItems.firstOrNull { it.isSelected }
        if (selectedStage != null) {
            listOf("Workspace", selectedStage.label)
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

    val selectedStageId = uiState.pipelineItems.firstOrNull { it.isSelected }?.stageId?.name

    IdeAppFrame(
        modifier = modifier.testTag("StudioShellLayout"),
        colors = colors,
        topBar = {
            StudioTopAppBar(
                uiState = uiState,
                onSelectGlobalDestination = onSelectGlobalDestination,
                onValidate = onValidate,
                onSave = onSave,
                colors = colors,
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
                colors = colors,
            )
        },
        navigator = {
            WorkspaceNavigator(
                uiState = uiState,
                onSelectSection = onSelectWorkspaceSection,
                colors = colors,
            )
        },
        breadcrumb = {
            IdeBreadcrumbBar(
                segments = breadcrumbItems,
                modifier = Modifier.testTag("IdeBreadcrumbBar"),
                colors = colors,
            )
        },
        content = content,
        statusBar = {
            StudioStatusBar(
                uiState = uiState,
                colors = colors,
                onOpenProblems = onOpenProblems,
                onOpenActivity = onOpenActivity,
            )
        },
    )
}

/**
 * Stage Object Editor component for stages 1..8.
 */
@Composable
private fun StageObjectEditor(
    stage: StudioStageDestination,
    objectId: String?,
    validationErrors: List<ValidationError>,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

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
                            painter = stage.icon(),
                            contentDescription = stage.accessibleLabel,
                            modifier = Modifier.size(IconSize.ActionCard),
                            tint = glassColors.glassAccent,
                        )
                        Column {
                            Text(
                                text = "${stage.title} Editor: ${objectId ?: "Settings"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = glassColors.onGlass,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Pipeline Stage ${stage.position} • Dedicated configuration editor placeholder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassColors.onGlassSecondary,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(StrokeWidth.Hairline)
                            .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
                    )

                    Text(
                        text = "The dedicated editor components for stage ${stage.title} " +
                            "(subobject: ${objectId ?: "default"}) are under active development. " +
                            "Configuration parameters defined in this stage can be reviewed below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.onGlass,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassButton(
                            onClick = {},
                            enabled = false,
                            variant = GlassButtonVariant.Standard,
                            shape = GlassShapes.Compact,
                        ) {
                            Text("Execute Stage (Unavailable in Studio)", color = glassColors.onGlassMuted)
                        }

                        GlassButton(
                            onClick = onSave,
                            variant = GlassButtonVariant.Primary,
                            shape = GlassShapes.Compact,
                        ) {
                            Text("Save Draft", color = glassColors.onGlass)
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
                            color = glassColors.diagnostics.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        validationErrors.forEach { err ->
                            Text(
                                text = "• [${err.fieldPath}] ${err.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.onGlass,
                            )
                            if (err.remediation != null) {
                                Text(
                                    text = "  Remediation: ${err.remediation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glassColors.onGlassSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
