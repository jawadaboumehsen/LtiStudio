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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.AuthorDiagnostic
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.TrustCheck

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
    val fixedSdkTasks = listOf(
        "generateModPlan",
        "compileModPayload",
        "validateMod",
        "testMod",
        "packageMod",
        "inspectMod",
        "signMod",
    )

    CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
        Column(
            modifier = modifier.fillMaxSize().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Text(
                        text = "Author Tools & SDK Runner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTheme.colors.onGlass,
                    )
                    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
                    Text(
                        text = "Authors edit and debug Kotlin in IntelliJ IDEA. This panel selects a " +
                            "trusted local project, runs fixed SDK lifecycle tasks, and visualizes structured " +
                            "diagnostics. No Kotlin editing occurs in this app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassTextField(
                            value = projectPath,
                            onValueChange = onProjectPathChange,
                            placeholder = "C:/projects/my-plugin or /home/user/my-plugin",
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(
                            onClick = { onCheckProject(projectPath) },
                            enabled = projectPath.isNotBlank(),
                            variant = GlassButtonVariant.Primary,
                        ) {
                            Text("Check Trust")
                        }
                    }

                    if (trustCheck != null) {
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        AuthorTrustStateRow(
                            trustCheck = trustCheck,
                            onApprove = onApproveProject,
                            onRevoke = onRevokeProject,
                        )
                    }
                }
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Text(
                        text = "Fixed SDK Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassTheme.colors.glassAccent,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        fixedSdkTasks.forEach { task ->
                            val isSelected = task == selectedTask
                            GlassButton(
                                onClick = { onSelectTask(task) },
                                variant = if (isSelected) GlassButtonVariant.Primary else GlassButtonVariant.Standard,
                            ) {
                                Text(task)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        val isApproved = trustCheck is TrustCheck.Approved
                        GlassButton(
                            onClick = { onRunTask(selectedTask) },
                            enabled = isApproved && !isRunningTask,
                            variant = GlassButtonVariant.Primary,
                        ) {
                            Text(if (isRunningTask) "Executing $selectedTask..." else "Run $selectedTask")
                        }
                    }
                }
            }

            taskResult?.let { result ->
                AuthorTaskResultCard(result = result)
            }
        }
    }
}

@Composable
private fun AuthorTrustStateRow(trustCheck: TrustCheck, onApprove: () -> Unit, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (trustCheck) {
                is TrustCheck.Approved -> {
                    GlassChip(label = "Approved Project")
                    Text(
                        text = "Fingerprint: ${trustCheck.approval.fingerprint.take(12)}...",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = codeFontFamily(),
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                }
                is TrustCheck.FingerprintChanged -> {
                    GlassChip(label = "Fingerprint Changed")
                    Text(
                        text = "Build scripts or sources modified. Re-approval required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.glassHighlight,
                    )
                }
                TrustCheck.NotApproved -> {
                    GlassChip(label = "Not Approved")
                    Text(
                        text = "Project not yet approved for SDK task execution.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassMuted,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            if (trustCheck !is TrustCheck.Approved) {
                GlassButton(onClick = onApprove, variant = GlassButtonVariant.Primary) {
                    Text("Approve Project")
                }
            } else {
                GlassButton(onClick = onRevoke, variant = GlassButtonVariant.Standard) {
                    Text("Revoke Trust")
                }
            }
        }
    }
}

@Composable
private fun AuthorTaskResultCard(result: AuthorTaskResult) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            when (result) {
                is AuthorTaskResult.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Task Succeeded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GlassTheme.colors.glassAccent,
                        )
                        GlassChip(label = "Exit Code 0")
                    }
                    if (result.diagnostics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        DiagnosticsList(result.diagnostics)
                    }
                }
                is AuthorTaskResult.Failed -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Task Failed (Exit Code ${result.exitCode})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GlassTheme.colors.glassHighlight,
                        )
                        GlassChip(label = "Failure")
                    }
                    if (result.diagnostics.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        DiagnosticsList(result.diagnostics)
                    }
                }
                is AuthorTaskResult.Refused -> {
                    Text(
                        text = "Task Execution Refused: ${result.reason}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.glassHighlight,
                    )
                }
                is AuthorTaskResult.TimedOut -> {
                    Text(
                        text = "Task Timed Out (${result.afterMs}ms). Process was killed forcibly.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.glassHighlight,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsList(diagnostics: List<AuthorDiagnostic>) {
    Text(
        text = "Structured Diagnostics (${diagnostics.size}):",
        style = MaterialTheme.typography.labelMedium,
        color = GlassTheme.colors.onGlass,
    )
    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(ComponentSize.ActionCardHeight * 2),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        items(diagnostics) { diag ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassChip(label = diag.severity)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${diag.code} • ${diag.file ?: "global"}${diag.line?.let { ":$it" } ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = codeFontFamily(),
                            color = GlassTheme.colors.onGlassMuted,
                        )
                        Text(
                            text = diag.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.onGlass,
                        )
                    }
                }
            }
        }
    }
}
