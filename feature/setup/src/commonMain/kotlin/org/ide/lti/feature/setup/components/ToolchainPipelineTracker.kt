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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import kotlinx.coroutines.delay
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusError
import org.ide.lti.core.designsystem.icon.ideStatusSuccess
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.feature.setup.MachineRowMapper
import org.ide.lti.feature.setup.SetupOperation
import org.ide.lti.feature.setup.SetupOperationState
import org.ide.lti.feature.setup.SetupRowAction
import org.ide.lti.feature.setup.TerminalHandoffState
import org.ide.lti.feature.setup.WslRowPresentation

/**
 * 5-stage vertical Toolchain Provisioning Pipeline Tracker (US2).
 *
 * Implements:
 * - 5 vertical checklist stages (WSL, Bridge, Doctor, Submodules, Compilers)
 * - Compact healthy rows showing measured status
 * - Expanded failed rows displaying root-cause descriptions and targeted retry button
 * - Restored/historical indicator without claiming live readiness
 * - Single Responsibility: Visualizes stage-by-stage pipeline progression
 * - 100% design-system token usage (zero raw dp or color literals)
 */
@Composable
fun ToolchainPipelineTracker(
    steps: List<SetupStepDetail>,
    onRetryStep: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryStage: (SetupStepStage) -> Unit = { onRetryStep() },
    isBusy: Boolean = false,
    activeOperation: SetupOperation? = null,
    handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    onInstallPackages: () -> Unit = {},
    onShowHandoffSheet: () -> Unit = {},
    distroStatuses: List<DistroStatus> = emptyList(),
    activeDistro: String? = null,
    onVerify: () -> Unit = {},
    onOpenDistro: (String) -> Unit = {},
    onSelectDistro: (String) -> Unit = {},
    onShowDiagnostics: () -> Unit = {},
    onCancel: () -> Unit = {},
    daemonPingMs: Long? = null,
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelTargetTool by remember { mutableStateOf<String?>(null) }

    val stepMap = steps.associateBy { it.stage }
    val orderedStages =
        if (stepMap.containsKey(SetupStepStage.SYSTEM_PACKAGES)) {
            listOf(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SYSTEM_PACKAGES,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_DIAGNOSTICS,
                SetupStepStage.REPO_SYNCHRONIZATION,
                SetupStepStage.TOOLCHAIN_COMPILATION,
            )
        } else {
            listOf(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_DIAGNOSTICS,
                SetupStepStage.REPO_SYNCHRONIZATION,
                SetupStepStage.TOOLCHAIN_COMPILATION,
            )
        }

    val completedCount =
        orderedStages.count { stage ->
            stepMap[stage]?.status == StepStatus.SUCCESS
        }

    if (showCancelDialog) {
        SetupCancelDialog(
            toolText = cancelTargetTool ?: "the active tool",
            onConfirm = {
                showCancelDialog = false
                onCancel()
            },
            onDismiss = { showCancelDialog = false },
        )
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentPadding = Spacing.CardPadding,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = "This machine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassChip(
                    label = "$completedCount of ${orderedStages.size} verified",
                    selected = completedCount == orderedStages.size,
                )
            }

            // Vertical Stages
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                val systemPackagesStep = stepMap[SetupStepStage.SYSTEM_PACKAGES]
                orderedStages.forEach { stage ->
                    val step =
                        stepMap[stage] ?: SetupStepDetail(
                            stage = stage,
                            title = stage.displayName,
                            status = StepStatus.PENDING,
                            description = "Pending verification.",
                        )
                    VerticalStageRow(
                        step = step,
                        stage = stage,
                        isBusy = isBusy,
                        activeOperation = activeOperation,
                        handoffSheetState = handoffSheetState,
                        daemonPingMs = daemonPingMs,
                        systemPackagesStep = systemPackagesStep,
                        wslPres =
                        if (stage == SetupStepStage.WSL_DETECTION) {
                            MachineRowMapper.mapWslRow(step, distroStatuses, activeDistro)
                        } else {
                            null
                        },
                        onAction = { action, distro ->
                            handleTrackerAction(
                                action = action,
                                distro = distro,
                                stage = stage,
                                stepDescription = step.description,
                                onVerify = onVerify,
                                onRetryStage = onRetryStage,
                                onInstallPackages = onInstallPackages,
                                onShowHandoffSheet = onShowHandoffSheet,
                                onOpenDistro = onOpenDistro,
                                onShowDiagnostics = onShowDiagnostics,
                                onShowCancelDialog = { tool ->
                                    cancelTargetTool = tool
                                    showCancelDialog = true
                                },
                            )
                        },
                        onSelectDistro = onSelectDistro,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCancelDialog(toolText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    GlassDialog(
        onDismissRequest = onDismiss,
        title = "Cancel setup operation?",
        confirmButton = {
            GlassPrimaryButton(onClick = onConfirm) {
                Text("Cancel operation")
            }
        },
        dismissButton = {
            GlassSecondaryButton(onClick = onDismiss) {
                Text("Keep running")
            }
        },
    ) {
        Text(
            text = "The current build of $toolText stops; tools already built are kept. " +
                "You can resume setup at any time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun handleTrackerAction(
    action: SetupRowAction,
    distro: String?,
    stage: SetupStepStage,
    stepDescription: String,
    onVerify: () -> Unit,
    onRetryStage: (SetupStepStage) -> Unit,
    onInstallPackages: () -> Unit,
    onShowHandoffSheet: () -> Unit,
    onOpenDistro: (String) -> Unit,
    onShowDiagnostics: () -> Unit,
    onShowCancelDialog: (String?) -> Unit,
) {
    when (action) {
        SetupRowAction.RETRY ->
            if (stage == SetupStepStage.WSL_DETECTION || stage == SetupStepStage.SYSTEM_PACKAGES) {
                onVerify()
            } else {
                onRetryStage(stage)
            }
        SetupRowAction.INSTALL_PACKAGES -> onInstallPackages()
        SetupRowAction.CONTINUE_SETUP -> onShowHandoffSheet()
        SetupRowAction.OPEN_DISTRO -> distro?.let(onOpenDistro)
        SetupRowAction.SHOW_DIAGNOSTICS, SetupRowAction.SHOW_LOG -> onShowDiagnostics()
        SetupRowAction.CANCEL -> onShowCancelDialog(extractToolName(stepDescription))
    }
}

private fun extractToolName(description: String): String? {
    val regex = Regex("""(?:Building|Syncing)\s+([^\s(]+)""")
    val match = regex.find(description)
    return match?.groupValues?.get(1)
}

@Composable
private fun VerticalStageRow(
    step: SetupStepDetail,
    stage: SetupStepStage,
    isBusy: Boolean,
    onAction: (SetupRowAction, String?) -> Unit,
    activeOperation: SetupOperation? = null,
    handoffSheetState: TerminalHandoffState = TerminalHandoffState.Idle,
    wslPres: WslRowPresentation? = null,
    daemonPingMs: Long? = null,
    systemPackagesStep: SetupStepDetail? = null,
    onSelectDistro: (String) -> Unit = {},
) {
    val diagnostics = GlassTheme.diagnosticColors

    val stageDisplayName = stage.displayName

    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val currentEpochMs by produceState<Long?>(
        initialValue = if (isReducedMotion) null else System.currentTimeMillis(),
        key1 = step.status == StepStatus.RUNNING,
        key2 = isReducedMotion,
    ) {
        if (step.status != StepStatus.RUNNING || isReducedMotion) {
            value = null
            return@produceState
        }
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val buildServicePres =
        if (stage == SetupStepStage.SERVER_CONNECTIVITY) {
            MachineRowMapper.mapBuildService(
                step = step,
                daemonPingMs = daemonPingMs,
                isChecking = isBusy || step.status == StepStatus.RUNNING,
                systemPackagesStep = systemPackagesStep,
            )
        } else {
            null
        }

    val systemPackagesPres =
        if (stage == SetupStepStage.SYSTEM_PACKAGES) {
            MachineRowMapper.mapSystemPackages(
                step = step,
                isChecking = isBusy || step.status == StepStatus.RUNNING,
                activeOperation = activeOperation,
                handoffSheetState = handoffSheetState,
            )
        } else {
            null
        }

    val toolchainPres =
        if (stage == SetupStepStage.TOOLCHAIN_COMPILATION) {
            MachineRowMapper.mapToolchainRow(
                step,
                currentEpochMs,
                canCancel = activeOperation?.executionState == SetupOperationState.RUNNING,
            )
        } else {
            null
        }

    val rowSeverity =
        systemPackagesPres?.severity
            ?: toolchainPres?.severity
            ?: buildServicePres?.severity
            ?: wslPres?.severity
    val isBuildServiceRunning = buildServicePres?.isRunning == true
    val isToolchainRunning = toolchainPres?.isRunning == true
    val isPackagesChecking = systemPackagesPres?.isChecking == true
    val isRunning =
        step.status == StepStatus.RUNNING ||
            isPackagesChecking ||
            isToolchainRunning ||
            isBuildServiceRunning

    val isBuildServiceReady = buildServicePres?.isReady == true
    val isToolchainReady = toolchainPres?.isReady == true
    val isPackagesReady = systemPackagesPres?.isReady == true
    val isAnyReady = isPackagesReady || isToolchainReady || isBuildServiceReady
    val isSuccess = (step.status == StepStatus.SUCCESS || isAnyReady) && !isRunning
    val isRestored = step.provenance == StepProvenance.RESTORED
    val isActionRequired =
        (
            step.status == StepStatus.FAILED ||
                step.status == StepStatus.WARNING ||
                systemPackagesPres?.primaryAction != null ||
                (toolchainPres?.primaryAction != null && toolchainPres.primaryAction != SetupRowAction.CANCEL) ||
                buildServicePres?.primaryAction != null
            ) &&
            !isSuccess &&
            !isRunning

    val isFailed = step.status == StepStatus.FAILED || rowSeverity == IdeStatusSeverity.Failed
    val isWarning = !isFailed && (step.status == StepStatus.WARNING || rowSeverity == IdeStatusSeverity.Warning)

    val displayDescription =
        systemPackagesPres?.detailText
            ?: toolchainPres?.detailText
            ?: buildServicePres?.detailText
            ?: wslPres?.detailText
            ?: step.description
    // A plain step row always offers a stage retry; a mapped row offers exactly what its state allows.
    val hasMappedRow =
        systemPackagesPres != null ||
            toolchainPres != null ||
            buildServicePres != null ||
            wslPres != null
    val primaryAction =
        systemPackagesPres?.primaryAction
            ?: toolchainPres?.primaryAction
            ?: buildServicePres?.primaryAction
            ?: wslPres?.primaryAction
            ?: SetupRowAction.RETRY.takeIf { !hasMappedRow }
    val primaryLabel =
        systemPackagesPres?.primaryActionLabel
            ?: toolchainPres?.primaryActionLabel
            ?: buildServicePres?.primaryActionLabel
            ?: wslPres?.primaryActionLabel
            ?: "Retry Stage"
    val secondaryAction = wslPres?.secondaryAction ?: buildServicePres?.secondaryAction

    if (isActionRequired) {
        // Expanded Failed / Warning Stage Row
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(
                    if (isFailed) {
                        diagnostics.error.copy(alpha = AlphaTokens.UltraFaint)
                    } else {
                        diagnostics.warning.copy(alpha = AlphaTokens.UltraFaint)
                    },
                ).border(
                    width = StrokeWidth.Hairline,
                    color =
                    if (isFailed) {
                        diagnostics.error.copy(alpha = AlphaTokens.Border)
                    } else {
                        diagnostics.warning.copy(alpha = AlphaTokens.Border)
                    },
                    shape = GlassShapes.Small,
                ).semantics {
                    liveRegion = if (isFailed) LiveRegionMode.Assertive else LiveRegionMode.Polite
                }.padding(Spacing.MediumSmall),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            painter = if (isFailed) AppIcons.ideStatusError() else AppIcons.ideStatusWarning(),
                            contentDescription = if (isFailed) "Failed" else "Warning",
                            tint = if (isFailed) diagnostics.error else diagnostics.warning,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text(
                            text = stageDisplayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    GlassChip(
                        label = if (isFailed) "Failed" else "Notice",
                        selected = false,
                    )
                }

                Text(
                    text = displayDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFailed) diagnostics.error else diagnostics.warning,
                )

                val errorText = toolchainPres?.errorText ?: if (!hasMappedRow) step.error else null
                if (!errorText.isNullOrBlank()) {
                    CopyableErrorText(errorText)
                }

                wslPres?.instruction?.let { instruction ->
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (wslPres != null && wslPres.choices.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        wslPres.choices.forEach { distro ->
                            GlassChip(
                                label = distro,
                                selected = false,
                                onClick = { if (!isBusy) onSelectDistro(distro) },
                            )
                        }
                    }
                }

                if (primaryAction != null || secondaryAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
                    ) {
                        if (secondaryAction != null) {
                            GlassSecondaryButton(
                                onClick = { onAction(secondaryAction, wslPres?.distro) },
                                enabled = !isBusy,
                            ) {
                                Text(
                                    wslPres?.secondaryActionLabel.orEmpty(),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (primaryAction != null) {
                            GlassPrimaryButton(
                                onClick = { onAction(primaryAction, wslPres?.distro) },
                                enabled = !isBusy,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                ) {
                                    if (primaryAction == SetupRowAction.RETRY) {
                                        Icon(
                                            painter = AppIcons.RefreshPainterResource(),
                                            contentDescription = null,
                                            modifier = Modifier.size(IconSize.Small),
                                        )
                                    }
                                    Text(primaryLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Compact Healthy / Running / Pending Stage Row
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    width = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Small,
                ).semantics {
                    liveRegion = LiveRegionMode.Polite
                }.padding(horizontal = Spacing.MediumSmall, vertical = Spacing.Small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    when {
                        isRunning -> {
                            val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
                            if (isReducedMotion) {
                                Text(
                                    text = "Checking…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(IconSize.Small),
                                    strokeWidth = StrokeWidth.Focused,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        isSuccess -> {
                            Icon(
                                painter = AppIcons.ideStatusSuccess(),
                                contentDescription = "Ready",
                                tint = diagnostics.success,
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                        else -> {
                            // Pending is neutral, not a warning: a muted dot.
                            Box(
                                modifier =
                                Modifier
                                    .size(IconSize.Small),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                    Modifier
                                        .size(IconSize.Indicator)
                                        .clip(GlassShapes.Capsule)
                                        .background(MaterialTheme.colorScheme.outline),
                                )
                            }
                        }
                    }

                    Text(
                        text = stageDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.size(Spacing.ExtraSmall))

                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isRunning && primaryAction == SetupRowAction.CANCEL) {
                    GlassSecondaryButton(
                        onClick = { onAction(SetupRowAction.CANCEL, null) },
                    ) {
                        Text(
                            text = "Cancel",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    GlassChip(
                        label =
                        when {
                            isRestored -> "Historical"
                            isSuccess -> "Ready"
                            isRunning -> "Checking…"
                            else -> "Pending"
                        },
                        selected = isSuccess && !isRestored,
                    )
                }
            }
        }
    }
}

/** A step's technical error: selectable, with a button that copies the whole text for a bug report. */
@Composable
internal fun CopyableErrorText(errorText: String, modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    val diagnostics = GlassTheme.diagnosticColors
    var copied by remember(errorText) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ExtraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outline,
                shape = GlassShapes.ExtraSmall,
            ).padding(Spacing.Small),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassIconButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(errorText))
                copied = true
            },
            size = ComponentSize.PanelHeaderAction,
        ) {
            Icon(
                painter = if (copied) AppIcons.CheckPainterResource() else AppIcons.CopyPainterResource(),
                contentDescription = if (copied) "Copied" else "Copy error",
                tint = if (copied) diagnostics.success else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

private const val COPIED_FEEDBACK_MS = 2_000L
