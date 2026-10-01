/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.TrustCheck
import org.ide.lti.feature.plugins.components.PluginDiagnosticsTable
import org.ide.lti.feature.plugins.components.PluginFooterBar
import org.ide.lti.feature.plugins.components.PluginPipelineFlow
import org.jetbrains.compose.resources.painterResource

private data class SdkAuthorTask(val id: String, val title: String, val icon: @Composable () -> Painter)

@Composable
internal fun PluginAuthorToolsTab(
    projectPath: String,
    trustCheck: TrustCheck?,
    selectedTask: String,
    taskResult: AuthorTaskResult?,
    isRunningTask: Boolean,
    onProjectPathChange: (String) -> Unit,
    onCheckProject: (String) -> Unit,
    onApproveProject: () -> Unit,
    onRevokeProject: () -> Unit,
    onSelectTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Kotlin author tools",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Build and test LtiRom modules with a simplified workflow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AuthorProjectHeaderCard(
                projectPath = projectPath,
                trustCheck = trustCheck,
                onProjectPathChange = onProjectPathChange,
                onCheckProject = onCheckProject,
                onApproveProject = onApproveProject,
                onRevokeProject = onRevokeProject,
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                AuthorTasksSidebar(
                    selectedTask = selectedTask,
                    taskResult = taskResult,
                    isRunningTask = isRunningTask,
                    onSelectTask = onSelectTask,
                    modifier = Modifier.width(ComponentSize.PluginAuthorTasksWidth),
                )

                AuthorExecutionPanel(
                    projectPath = projectPath,
                    selectedTask = selectedTask,
                    taskResult = taskResult,
                    isRunningTask = isRunningTask,
                    onRunTask = onRunTask,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val footerMessage = if (projectPath.isNotBlank()) {
            "Authoring workspace: $projectPath"
        } else {
            "Authoring tools ready · No project path configured"
        }
        PluginFooterBar(message = footerMessage)
    }
}

@Composable
private fun AuthorProjectHeaderCard(
    projectPath: String,
    trustCheck: TrustCheck?,
    onProjectPathChange: (String) -> Unit,
    onCheckProject: (String) -> Unit,
    onApproveProject: () -> Unit,
    onRevokeProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPath = projectPath.isNotBlank()
    val displayTitle = if (hasPath) {
        formatPluginDisplayName(projectPath.substringAfterLast('/').substringAfterLast('\\').ifBlank { projectPath })
    } else {
        "No project selected"
    }
    val locationText = if (hasPath) projectPath else "No author project path configured"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Project",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
                    Row(
                        modifier = Modifier
                            .clip(GlassShapes.MediumSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .glassOutlineBorder(
                                width = StrokeWidth.Hairline,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                                shape = GlassShapes.HazeMediumSmall,
                            )
                            .clickable { onProjectPathChange(projectPath) }
                            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "$displayTitle ▾",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.Medium)) {
                    Text(
                        text = "Project location",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraExtraSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                    ) {
                        Icon(
                            painter = AppIcons.FolderPainterResource(),
                            contentDescription = "Project Location",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Column {
                            Text(
                                text = "Local author project",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = locationText,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = codeFontFamily(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Hairline),
                ) {
                    GlassButton(
                        onClick = { onCheckProject(projectPath) },
                        variant = GlassButtonVariant.Primary,
                        shape = GlassShapes.HazeMediumSmall,
                        minHeight = ComponentSize.PluginActionBtnCompactHeight,
                        contentPadding = PaddingValues(
                            horizontal = Spacing.Medium,
                            vertical = Spacing.ExtraSmall,
                        ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ideNodesPlugin),
                                contentDescription = "Open in IntelliJ IDEA",
                                tint = BrandColors.OnLogoBadge,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text("Open in IntelliJ IDEA")
                        }
                    }
                    GlassButton(
                        onClick = { onCheckProject(projectPath) },
                        variant = GlassButtonVariant.Primary,
                        shape = GlassShapes.HazeMediumSmall,
                        minHeight = ComponentSize.PluginActionBtnCompactHeight,
                        contentPadding = PaddingValues(
                            horizontal = Spacing.Small,
                            vertical = Spacing.ExtraSmall,
                        ),
                    ) {
                        Text("▾")
                    }
                }
            }

            GlassHorizontalDivider(specular = true)

            AuthorTrustStatusRow(
                trustCheck = trustCheck,
                onApproveProject = onApproveProject,
                onRevokeProject = onRevokeProject,
            )
        }
    }
}

@Composable
private fun AuthorTrustStatusRow(
    trustCheck: TrustCheck?,
    onApproveProject: () -> Unit,
    onRevokeProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isApproved = trustCheck is TrustCheck.Approved || trustCheck == null
    val trustTint = if (isApproved) {
        GlassTheme.diagnosticColors.success
    } else {
        GlassTheme.diagnosticColors.warning
    }
    val trustPainter = if (isApproved) {
        AppIcons.CheckPainterResource()
    } else {
        AppIcons.InfoPainterResource()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = trustPainter,
                contentDescription = "Trust Status",
                tint = trustTint,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = if (isApproved) {
                    "Trusted project · Wrapper and build scripts unchanged."
                } else {
                    "Build scripts modified · Trust review required."
                },
                style = MaterialTheme.typography.bodySmall,
                color = trustTint,
            )
        }

        GlassButton(
            onClick = if (trustCheck is TrustCheck.Approved) onRevokeProject else onApproveProject,
            variant = GlassButtonVariant.Standard,
            shape = GlassShapes.HazeMediumSmall,
            minHeight = ComponentSize.PluginActionBtnCompactHeight,
            contentPadding = PaddingValues(
                horizontal = Spacing.Medium,
                vertical = Spacing.ExtraSmall,
            ),
        ) {
            Text("Review trust →")
        }
    }
}

@Composable
private fun AuthorTasksSidebar(
    selectedTask: String,
    taskResult: AuthorTaskResult?,
    isRunningTask: Boolean,
    onSelectTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fixedTasks = listOf(
        SdkAuthorTask("compileModPayload", "Compile payload") { AppIcons.SettingsPainterResource() },
        SdkAuthorTask("generateModPlan", "Generate plan") { AppIconsRomStages.releaseMetadata() },
        SdkAuthorTask("validateMod", "Validate") { AppIcons.CheckPainterResource() },
        SdkAuthorTask("testMod", "Test") { AppIcons.SparklesPainterResource() },
        SdkAuthorTask("packageMod", "Package") { AppIconsRomStages.assemble() },
        SdkAuthorTask("inspectMod", "Inspect") { AppIcons.SearchPainterResource() },
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Author tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Run tasks to build, test and package your module.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GlassHorizontalDivider(specular = true)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                items(fixedTasks, key = { it.id }) { task ->
                    val isSelected = task.id == selectedTask
                    AuthorTaskRowItem(
                        task = task,
                        isSelected = isSelected,
                        isRunning = isRunningTask && isSelected,
                        taskResult = if (isSelected) taskResult else null,
                        onClick = { onSelectTask(task.id) },
                    )
                }
            }
        }
    }
}

private fun getAuthorTaskStatusLabel(
    isRunning: Boolean,
    isTaskSuccessful: Boolean,
    taskResult: AuthorTaskResult?,
): String = when {
    isRunning -> "Running..."
    isTaskSuccessful -> "Succeeded"
    taskResult is AuthorTaskResult.Failed -> "Failed (${taskResult.exitCode})"
    taskResult is AuthorTaskResult.Refused -> "Refused"
    taskResult is AuthorTaskResult.TimedOut -> "Timed out"
    else -> "Not run"
}

private fun getAuthorTaskStatusColor(
    isRunning: Boolean,
    isSelected: Boolean,
    isTaskSuccessful: Boolean,
    taskResult: AuthorTaskResult?,
    onGlassAccent: Color,
    onGlassSecondary: Color,
    errorColor: Color,
    mutedColor: Color,
): Color = when {
    isRunning -> onGlassAccent
    isSelected -> BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Hover)
    isTaskSuccessful -> onGlassSecondary
    taskResult is AuthorTaskResult.Failed -> errorColor
    else -> mutedColor
}

@Composable
private fun AuthorTaskStatusBadge(isTaskSuccessful: Boolean, taskResult: AuthorTaskResult?) {
    if (isTaskSuccessful) {
        Box(
            modifier = Modifier
                .size(IconSize.Medium)
                .clip(GlassShapes.Circle)
                .background(GlassTheme.diagnosticColors.success),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = AppIcons.CheckPainterResource(),
                contentDescription = "Completed",
                tint = BrandColors.OnLogoBadge,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    } else if (taskResult is AuthorTaskResult.Failed || taskResult is AuthorTaskResult.TimedOut) {
        Box(
            modifier = Modifier
                .size(IconSize.Medium)
                .clip(GlassShapes.Circle)
                .background(GlassTheme.diagnosticColors.error),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = AppIcons.ClearPainterResource(),
                contentDescription = "Failed",
                tint = BrandColors.OnLogoBadge,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
private fun AuthorTaskRowItem(
    task: SdkAuthorTask,
    isSelected: Boolean,
    isRunning: Boolean,
    taskResult: AuthorTaskResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (isSelected) {
        PluginThemeColors.SelectedRowBackground
    } else {
        Color.Transparent
    }

    val borderModifier = if (isSelected) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = PluginThemeColors.SelectedRowBorder,
            shape = GlassShapes.Small,
        )
    } else {
        Modifier
    }

    val isTaskSuccessful = taskResult is AuthorTaskResult.Success
    val statusLabel = getAuthorTaskStatusLabel(isRunning, isTaskSuccessful, taskResult)
    val statusColor = getAuthorTaskStatusColor(
        isRunning = isRunning,
        isSelected = isSelected,
        isTaskSuccessful = isTaskSuccessful,
        taskResult = taskResult,
        onGlassAccent = MaterialTheme.colorScheme.primary,
        onGlassSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
        errorColor = GlassTheme.diagnosticColors.error,
        mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(bg)
            .then(borderModifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = task.icon(),
                contentDescription = task.title,
                tint = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Medium),
            )
            Column {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        AuthorTaskStatusBadge(
            isTaskSuccessful = isTaskSuccessful,
            taskResult = taskResult,
        )
    }
}

@Composable
private fun AuthorExecutionPanel(
    projectPath: String,
    selectedTask: String,
    taskResult: AuthorTaskResult?,
    isRunningTask: Boolean,
    onRunTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canRun = projectPath.isNotBlank() && !isRunningTask

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Medium)
                .background(PluginThemeColors.CardBackground)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = PluginThemeColors.CardBorder,
                    shape = GlassShapes.Medium,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Build package",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Compile, validate, test and package your module.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GlassButton(
                    onClick = { onRunTask(selectedTask) },
                    enabled = canRun,
                    variant = GlassButtonVariant.Primary,
                    shape = GlassShapes.HazeMediumSmall,
                    minHeight = ComponentSize.PluginActionBtnCompactHeight,
                    contentPadding = PaddingValues(
                        horizontal = Spacing.Medium,
                        vertical = Spacing.ExtraSmall,
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Icon(
                            painter = AppIcons.PlayPainterResource(),
                            contentDescription = "Run",
                            tint = BrandColors.OnLogoBadge,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(if (isRunningTask) "Executing..." else "Run package tasks")
                    }
                }
            }
        }

        PluginPipelineFlow(
            taskResult = taskResult,
            isRunningTask = isRunningTask,
            selectedTask = selectedTask,
        )

        val effectiveDiagnostics = when (taskResult) {
            is AuthorTaskResult.Success -> taskResult.diagnostics
            is AuthorTaskResult.Failed -> taskResult.diagnostics
            else -> emptyList()
        }

        Text(
            text = "Diagnostics",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        PluginDiagnosticsTable(
            diagnostics = effectiveDiagnostics,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "Output",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        AuthorPackageOutputCard(
            selectedTask = selectedTask,
            taskResult = taskResult,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.MediumSmall)
                .background(PluginThemeColors.CardBackground)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = PluginThemeColors.CardBorder,
                    shape = GlassShapes.HazeMediumSmall,
                )
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.InfoPainterResource(),
                    contentDescription = "Information",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Author tasks execute local code. Installation is a separate action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AuthorPackageOutputCard(
    selectedTask: String,
    taskResult: AuthorTaskResult?,
    modifier: Modifier = Modifier,
) {
    val packageTaskCompleted = packageTaskSucceeded(selectedTask, taskResult)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.LogoBadgeSize)
                        .clip(GlassShapes.MediumSmall)
                        .background(PluginThemeColors.SelectedRowBackground)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = PluginThemeColors.SelectedRowBorder,
                            shape = GlassShapes.HazeMediumSmall,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = AppIcons.FilePainterResource(),
                        contentDescription = "Package output",
                        tint = BrandColors.OnLogoBadge,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }

                Column {
                    Text(
                        text = if (packageTaskCompleted) {
                            "Package task completed"
                        } else {
                            "No package receipt"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (packageTaskCompleted) {
                            "The task succeeded, but returned no artifact receipt."
                        } else {
                            "No package has been produced yet. Run the package task to compile and package your module."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
