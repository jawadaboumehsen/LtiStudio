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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.Workspace

/**
 * ROM Setup Studio Overview screen.
 *
 * Architectural Boundary:
 * Overview is a center-pane presentation MODE, NEVER a ninth stage.
 * Opening it preserves the last editor context, and returning to the editor restores it.
 *
 * Features:
 * - Summary of ROM build pipeline configuration status and readiness across all 8 stages.
 * - Missing settings list with actionable remediation steps.
 * - Highlighted "Next Action" pointing directly to the next stage/object requiring attention.
 * - Workspace chooser when no workspace is selected or active.
 */
@Composable
public fun StudioOverview(
    workspaceId: String,
    validationReport: ValidationReport,
    availableWorkspaces: List<Workspace>,
    onSelectStage: (StageId) -> Unit,
    onSelectObject: (StageId, String) -> Unit,
    onSelectWorkspace: (String) -> Unit,
    onReturnToEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // 1. Header Banner & Return to Editor
            item(key = "overview-header") {
                OverviewHeaderRow(workspaceId = workspaceId, onReturnToEditor = onReturnToEditor)
            }

            // 2. Workspace Chooser (when no workspace is selected)
            if (workspaceId.isBlank()) {
                item(key = "workspace-chooser-card") {
                    WorkspaceChooserCard(
                        availableWorkspaces = availableWorkspaces,
                        onSelectWorkspace = onSelectWorkspace,
                    )
                }
            }

            // 3. Next Action Card
            val blockingErrors = validationReport.errors.filter { it.severity == Severity.ERROR }
            val firstIssue = blockingErrors.firstOrNull() ?: validationReport.errors.firstOrNull()
            item(key = "next-action-card") {
                NextActionCard(firstIssue = firstIssue, onSelectObject = onSelectObject)
            }

            // 4. The overview deliberately compresses readiness into one scannable surface.
            item(key = "build-readiness") {
                BuildReadinessCard(
                    validationReport = validationReport,
                    onSelectStage = onSelectStage,
                )
            }
        }
    }
}

@Composable
private fun OverviewHeaderRow(workspaceId: String, onReturnToEditor: () -> Unit) {
    val glassColors = GlassTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Your ROM workspace",
                style = MaterialTheme.typography.headlineSmall,
                color = glassColors.onGlass,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
            Text(
                text = if (workspaceId.isNotBlank()) {
                    "Workspace: $workspaceId • Pipeline Configuration & Readiness"
                } else {
                    "No Workspace Active • Select or create a workspace to proceed"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.onGlassSecondary,
            )
        }

        GlassButton(
            onClick = onReturnToEditor,
            variant = GlassButtonVariant.Primary,
            minHeight = ComponentSize.ButtonMinHeight,
            shape = GlassShapes.Compact,
            modifier = Modifier.semantics {
                contentDescription = "Return to stage editor"
                role = Role.Button
            },
        ) {
            Text(
                text = "Configure firmware",
                style = MaterialTheme.typography.labelMedium,
                color = glassColors.onGlass,
            )
        }
    }
}

@Composable
private fun WorkspaceChooserCard(availableWorkspaces: List<Workspace>, onSelectWorkspace: (String) -> Unit) {
    val glassColors = GlassTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.InfoPainterResource(),
                    contentDescription = "Warning",
                    modifier = Modifier.size(IconSize.Large),
                    tint = glassColors.diagnostics.warning,
                )
                Text(
                    text = "Workspace Selection Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = glassColors.onGlass,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "ROM Setup Studio requires an active workspace to configure and " +
                    "assemble targets.",
                style = MaterialTheme.typography.bodySmall,
                color = glassColors.onGlassSecondary,
            )

            if (availableWorkspaces.isNotEmpty()) {
                Text(
                    text = "Available Workspaces:",
                    style = MaterialTheme.typography.labelMedium,
                    color = glassColors.onGlass,
                    modifier = Modifier.padding(top = Spacing.Small),
                )
                availableWorkspaces.forEach { ws ->
                    WorkspaceChooserRow(workspace = ws, onSelectWorkspace = onSelectWorkspace)
                }
            } else {
                Text(
                    text = "No existing workspaces found. Scaffold a new workspace from Setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassMuted,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceChooserRow(workspace: Workspace, onSelectWorkspace: (String) -> Unit) {
    val glassColors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Compact)
            .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.Hover))
            .clickable { onSelectWorkspace(workspace.id) }
            .padding(Spacing.SmallMedium),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.onGlass,
            )
            Text(
                text = workspace.path,
                style = MaterialTheme.typography.bodySmall,
                color = glassColors.onGlassMuted,
            )
        }
        GlassButton(
            onClick = { onSelectWorkspace(workspace.id) },
            variant = GlassButtonVariant.Standard,
            shape = GlassShapes.Compact,
            minWidth = Spacing.None,
            minHeight = ComponentSize.TopBarButtonSize,
        ) {
            Text("Select", color = glassColors.onGlass)
        }
    }
}

@Composable
private fun BuildReadinessCard(validationReport: ValidationReport, onSelectStage: (StageId) -> Unit) {
    val glassColors = GlassTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.Medium,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Build readiness",
                        style = MaterialTheme.typography.titleMedium,
                        color = glassColors.onGlass,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Review the pipeline before starting a ROM build.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.onGlassSecondary,
                    )
                }
                Text(
                    text = "${StudioDestinationCatalog.stages.size} stages",
                    style = MaterialTheme.typography.labelMedium,
                    color = glassColors.onGlassMuted,
                )
            }
            Text(
                text = "STAGE                                      STATUS                 ACTION",
                style = MaterialTheme.typography.labelSmall,
                color = glassColors.onGlassMuted,
                modifier = Modifier.padding(top = Spacing.Small),
            )
            StudioDestinationCatalog.stages.forEach { stage ->
                val stageErrors = validationReport.errors.filter { it.stageId == stage.stageId }
                BuildReadinessRow(
                    stage = stage,
                    errorCount = stageErrors.count { it.severity == Severity.ERROR },
                    warningCount = stageErrors.count { it.severity == Severity.WARNING },
                    onClick = { onSelectStage(stage.stageId) },
                )
            }
        }
    }
}

@Composable
private fun BuildReadinessRow(stage: StudioStageDestination, errorCount: Int, warningCount: Int, onClick: () -> Unit) {
    val glassColors = GlassTheme.colors
    val status = stageBadgeStatus(errorCount = errorCount, warningCount = warningCount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Compact)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${stage.title}: ${status.label}. Open stage."
                role = Role.Button
            }
            .padding(horizontal = Spacing.Small, vertical = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = stage.icon(),
            contentDescription = null,
            modifier = Modifier.size(IconSize.Medium),
            tint = glassColors.glassAccent,
        )
        Text(
            text = stage.title,
            style = MaterialTheme.typography.bodyMedium,
            color = glassColors.onGlass,
            modifier = Modifier
                .padding(start = Spacing.Small)
                .weight(1f),
        )
        StageStatusBadge(errorCount = errorCount, warningCount = warningCount)
        Text(
            text = "Configure",
            style = MaterialTheme.typography.labelMedium,
            color = glassColors.glassAccent,
            modifier = Modifier.padding(start = Spacing.Medium),
        )
    }
}

@Composable
private fun NextActionCard(firstIssue: ValidationError?, onSelectObject: (StageId, String) -> Unit) {
    val glassColors = GlassTheme.colors
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = Spacing.Medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        painter = if (firstIssue != null) {
                            AppIcons.InfoPainterResource()
                        } else {
                            AppIcons.CheckPainterResource()
                        },
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Large),
                        tint = if (firstIssue != null) {
                            glassColors.diagnostics.warning
                        } else {
                            glassColors.diagnostics.success
                        },
                    )
                    Text(
                        text = "Next Recommended Action",
                        style = MaterialTheme.typography.titleMedium,
                        color = glassColors.onGlass,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                if (firstIssue != null) {
                    Text(
                        text = firstIssue.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.onGlass,
                    )
                    val rem = firstIssue.remediation
                    if (rem != null) {
                        Text(
                            text = rem,
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.onGlassSecondary,
                        )
                    }
                } else {
                    Text(
                        text = "All pipeline configuration stages are valid and ready for build execution.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.onGlassSecondary,
                    )
                }
            }

            val targetStageId = firstIssue?.stageId
            val targetObjectId = firstIssue?.objectId
            if (targetStageId != null && targetObjectId != null) {
                Spacer(modifier = Modifier.width(Spacing.Medium))
                GlassButton(
                    onClick = { onSelectObject(targetStageId, targetObjectId) },
                    variant = GlassButtonVariant.Primary,
                    shape = GlassShapes.Compact,
                ) {
                    Text("Configure", color = glassColors.onGlass)
                }
            }
        }
    }
}

/** Status derived once from error/warning counts, instead of a `when` repeated at every use site. */
private enum class StageBadgeStatus(val label: String) {
    ERROR("errors"),
    WARNING("warnings"),
    VALID("Valid"),
}

private fun stageBadgeStatus(errorCount: Int, warningCount: Int): StageBadgeStatus = when {
    errorCount > 0 -> StageBadgeStatus.ERROR
    warningCount > 0 -> StageBadgeStatus.WARNING
    else -> StageBadgeStatus.VALID
}

@Composable
private fun StageOverviewCard(
    stage: StudioStageDestination,
    errors: List<ValidationError>,
    errorCount: Int,
    warningCount: Int,
    onSelectStage: () -> Unit,
    onSelectObject: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val shape = GlassShapes.Panel

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.UltraFaint))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder.copy(alpha = AlphaTokens.Faint),
                shape = shape,
            )
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        StageTitleRow(
            stage = stage,
            errorCount = errorCount,
            warningCount = warningCount,
            onSelectStage = onSelectStage,
        )
        if (errors.isNotEmpty()) {
            StageIssuesList(errors = errors, onSelectStage = onSelectStage, onSelectObject = onSelectObject)
        }
    }
}

@Composable
private fun StageTitleRow(
    stage: StudioStageDestination,
    errorCount: Int,
    warningCount: Int,
    onSelectStage: () -> Unit,
) {
    val glassColors = GlassTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSelectStage),
        ) {
            Icon(
                painter = stage.icon(),
                contentDescription = stage.accessibleLabel,
                modifier = Modifier.size(IconSize.Large),
                tint = glassColors.glassAccent,
            )
            Column {
                Text(
                    text = "Stage ${stage.position}: ${stage.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = glassColors.onGlass,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${stage.subobjects.size} configuration objects",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.onGlassMuted,
                )
            }
        }
        StageStatusBadge(errorCount = errorCount, warningCount = warningCount)
    }
}

// Distinguishable stage badge (WITHOUT colour alone - icon and text).
@Composable
private fun StageStatusBadge(errorCount: Int, warningCount: Int) {
    val glassColors = GlassTheme.colors
    val status = stageBadgeStatus(errorCount, warningCount)
    val (color, painter, label) = when (status) {
        StageBadgeStatus.ERROR ->
            Triple(glassColors.diagnostics.error, AppIcons.ClearPainterResource(), "$errorCount ${status.label}")
        StageBadgeStatus.WARNING ->
            Triple(glassColors.diagnostics.warning, AppIcons.InfoPainterResource(), "$warningCount ${status.label}")
        StageBadgeStatus.VALID ->
            Triple(glassColors.diagnostics.success, AppIcons.CheckPainterResource(), status.label)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        modifier = Modifier
            .clip(GlassShapes.Pill)
            .background(color.copy(alpha = AlphaTokens.Hover))
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
    ) {
        Icon(painter = painter, contentDescription = null, modifier = Modifier.size(IconSize.Small), tint = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

// Missing settings / validation issues for this stage.
@Composable
private fun StageIssuesList(
    errors: List<ValidationError>,
    onSelectStage: () -> Unit,
    onSelectObject: (String) -> Unit,
) {
    val glassColors = GlassTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.ExtraLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = "Issues & Missing Settings:",
            style = MaterialTheme.typography.labelSmall,
            color = glassColors.onGlassSecondary,
            fontWeight = FontWeight.Medium,
        )
        errors.forEach { err ->
            StageIssueRow(error = err, onSelectStage = onSelectStage, onSelectObject = onSelectObject)
        }
    }
}

@Composable
private fun StageIssueRow(error: ValidationError, onSelectStage: () -> Unit, onSelectObject: (String) -> Unit) {
    val glassColors = GlassTheme.colors
    val objId = error.objectId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Compact)
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Faint))
            .clickable { if (objId != null) onSelectObject(objId) else onSelectStage() }
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "• ${error.message}",
            style = MaterialTheme.typography.bodySmall,
            color = if (error.severity == Severity.ERROR) {
                glassColors.diagnostics.error
            } else {
                glassColors.diagnostics.warning
            },
            modifier = Modifier.weight(1f),
        )
        if (objId != null) {
            Text(text = objId, style = MaterialTheme.typography.labelSmall, color = glassColors.onGlassMuted)
        }
    }
}
