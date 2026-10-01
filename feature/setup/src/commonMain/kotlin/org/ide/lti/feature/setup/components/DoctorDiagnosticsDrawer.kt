/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticItem
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticList
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipItem
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipRow
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.EnvironmentReadiness

/**
 * Categorized Pre-Flight Diagnostics Inspector Drawer implementing Progressive Disclosure.
 *
 * Replaces unformatted 13-item dumps with clean category tabs:
 * - All (13)
 * - Compilers (2)
 * - Runtimes (3)
 * - Kernel & Mount (2)
 * - Tools & Storage (6)
 *
 * Adheres strictly to:
 * - Single Responsibility: Diagnostic checks visualization, filtering, and copyable commands.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorDiagnosticsDrawer(
    diagnosticsList: List<DiagnosticCheckItem>,
    modifier: Modifier = Modifier,
    readiness: EnvironmentReadiness? = null,
    onAutoRemediateDoctor: (() -> Unit)? = null,
) {
    val diagnostics = GlassTheme.diagnosticColors

    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    val hasIssues = diagnosticsList.any { it.status == StepStatus.FAILED || it.status == StepStatus.WARNING }
    var isExpanded by remember(hasIssues) { mutableStateOf(hasIssues) }
    var selectedCategory by remember { mutableStateOf("All") }

    val categoryChipItems = remember(diagnosticsList) {
        listOf(GlassFilterChipItem(label = "All", count = diagnosticsList.size, value = "All")) +
            DiagnosticCategory.entries.map { cat ->
                GlassFilterChipItem(
                    label = cat.displayName,
                    count = diagnosticsList.count { cat == it.category },
                    value = cat.displayName,
                )
            }
    }

    val filteredList = remember(diagnosticsList, selectedCategory) {
        val base = if (selectedCategory == "All") {
            diagnosticsList
        } else {
            diagnosticsList.filter { it.category.displayName == selectedCategory }
        }
        base.sortedWith(
            compareBy(
                {
                    when (it.status) {
                        StepStatus.FAILED -> 0
                        StepStatus.WARNING -> 1
                        StepStatus.RUNNING -> 2
                        StepStatus.PENDING -> 3
                        StepStatus.SUCCESS -> 4
                    }
                },
                { it.title },
            ),
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row (Clickable Accordion - FrameLayout / Box Anchored Layout)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Small)
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = Spacing.ExtraSmall),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(end = ComponentSize.ButtonMinWidth * 2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    Icon(
                        painter = if (isExpanded) {
                            AppIcons.ChevronDownPainterResource()
                        } else {
                            AppIcons.ChevronRightPainterResource()
                        },
                        contentDescription = "Toggle Diagnostics Drawer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Small),
                    )

                    Text(
                        text = "Pre-Flight System Diagnostics (${diagnosticsList.size} Checks)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    val passedCount = diagnosticsList.count { it.status == StepStatus.SUCCESS }
                    val issueCount = diagnosticsList.count {
                        it.status == StepStatus.FAILED || it.status == StepStatus.WARNING
                    }
                    GlassChip(
                        label = when {
                            hasIssues -> if (issueCount == 1) "1 issue" else "$issueCount issues"
                            diagnosticsList.isEmpty() -> "Not run yet"
                            passedCount == diagnosticsList.size -> "All $passedCount verified"
                            else -> "$passedCount of ${diagnosticsList.size} verified"
                        },
                        selected = !hasIssues,
                    )
                }

                Text(
                    text = if (isExpanded) "Hide Details" else "Inspect Checks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterEnd),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val totalChecks = diagnosticsList.size
            val passedChecksCount = diagnosticsList.count { it.status == StepStatus.SUCCESS }
            val warningChecksCount = diagnosticsList.count { it.status == StepStatus.WARNING }
            val failedChecksCount = diagnosticsList.count { it.status == StepStatus.FAILED }

            // Concise Measured Diagnostic Status Banner (replaces fake health percentage radar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GlassShapes.Medium)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        width = StrokeWidth.Standard,
                        color = when {
                            failedChecksCount > 0 -> diagnostics.error.copy(alpha = AlphaTokens.Border)
                            warningChecksCount > 0 -> diagnostics.warning.copy(alpha = AlphaTokens.Border)
                            else -> diagnostics.success.copy(alpha = AlphaTokens.Border)
                        },
                        shape = GlassShapes.Medium,
                    )
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(GlassShapes.Small)
                                .background(
                                    when {
                                        failedChecksCount > 0 -> diagnostics.error.copy(alpha = AlphaTokens.Hover)
                                        warningChecksCount > 0 -> diagnostics.warning.copy(alpha = AlphaTokens.Hover)
                                        else -> diagnostics.success.copy(alpha = AlphaTokens.Hover)
                                    },
                                )
                                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
                        ) {
                            Text(
                                text = when {
                                    failedChecksCount > 0 -> "$failedChecksCount Failed"
                                    warningChecksCount > 0 -> "$warningChecksCount Warnings"
                                    else -> "All Verified"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    failedChecksCount > 0 -> diagnostics.error
                                    warningChecksCount > 0 -> diagnostics.warning
                                    else -> diagnostics.success
                                },
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                        ) {
                            Text(
                                text = when {
                                    failedChecksCount > 0 || warningChecksCount > 0 -> "Pre-Flight Diagnostics Issues Detected"
                                    else -> "Host Environment Checks Verified"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "$passedChecksCount Passed · $warningChecksCount Warnings · $failedChecksCount Failures",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassSecondaryButton(
                            onClick = {
                                val report = buildString {
                                    appendLine("=== LtiRom Host Environment Diagnostics ===")
                                    if (readiness != null) {
                                        appendLine("Readiness State: ${readiness.state}")
                                        appendLine("Observed At: ${readiness.observedAt}")
                                        readiness.failingCheck?.let { appendLine("Failing Check: $it") }
                                        readiness.remediation?.let { appendLine("Remediation: $it") }
                                        if (readiness.missingToolIds.isNotEmpty()) {
                                            appendLine("Missing Tools: ${readiness.missingToolIds.joinToString(", ")}")
                                        }
                                    }
                                    appendLine(
                                        "Checks Summary: $passedChecksCount passed, " +
                                            "$warningChecksCount warnings, $failedChecksCount failed",
                                    )
                                    appendLine("--- Detailed Checks ---")
                                    diagnosticsList.forEach { check ->
                                        appendLine("[${check.status}] ${check.title} (${check.category.displayName})")
                                        if (check.detail.isNotBlank()) {
                                            appendLine("  Detail: ${check.detail}")
                                        }
                                        check.remediation?.let { rem ->
                                            appendLine("  Remediation: $rem")
                                        }
                                        check.copyableCommand?.let { cmd ->
                                            appendLine("  Remediation Command: $cmd")
                                        }
                                    }
                                }
                                clipboardManager.setText(AnnotatedString(report))
                                isCopied = true
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                Icon(
                                    painter = AppIcons.CopyPainterResource(),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                                Text(
                                    text = if (isCopied) "Copied" else "Copy Diagnostics",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (onAutoRemediateDoctor != null && (failedChecksCount > 0 || warningChecksCount > 0)) {
                            GlassPrimaryButton(
                                onClick = onAutoRemediateDoctor,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    Icon(
                                        painter = AppIcons.SparklesPainterResource(),
                                        contentDescription = null,
                                        modifier = Modifier.size(IconSize.Small),
                                    )
                                    Text(
                                        "Auto-Fix All Issues",
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    // Doctor Automated Self-Healing Informational Banner (without redundant button)
                    if (hasIssues && onAutoRemediateDoctor != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GlassShapes.Small)
                                .background(diagnostics.warning.copy(alpha = AlphaTokens.UltraFaint))
                                .border(
                                    width = StrokeWidth.Hairline,
                                    color = diagnostics.warning.copy(alpha = AlphaTokens.Border),
                                    shape = GlassShapes.Small,
                                )
                                .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    painter = AppIcons.SparklesPainterResource(),
                                    contentDescription = null,
                                    tint = diagnostics.warning,
                                    modifier = Modifier.size(IconSize.Medium),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                                ) {
                                    Text(
                                        text = "Doctor Automated Self-Healing",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "The doctor will automatically provision missing compilers, packages, and kernel configurations via root elevation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Category Filter Tabs
                    GlassFilterChipRow(
                        items = categoryChipItems,
                        selectedValue = selectedCategory,
                        onSelect = { selectedCategory = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Checks List
                    GlassDiagnosticList(
                        items = filteredList.map { item ->
                            GlassDiagnosticItem(
                                id = item.id,
                                title = item.title,
                                detail = item.detail,
                                status = item.status.toGlassStepStatus(),
                                category = item.category.displayName,
                                copyableCommand = item.copyableCommand,
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun StepStatus.toGlassStepStatus(): GlassStepStatus = when (this) {
    StepStatus.PENDING -> GlassStepStatus.PENDING
    StepStatus.RUNNING -> GlassStepStatus.RUNNING
    StepStatus.SUCCESS -> GlassStepStatus.SUCCESS
    StepStatus.WARNING -> GlassStepStatus.WARNING
    StepStatus.FAILED -> GlassStepStatus.FAILED
}
