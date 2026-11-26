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
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassContainer
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.component.progress.GlassSpinner
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
import org.ide.lti.feature.setup.ToolchainLayoutRecoveryPresentation
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
    onInstallUbuntu: (String) -> Unit = {},
    onShowDiagnostics: () -> Unit = {},
    onCancel: () -> Unit = {},
    daemonPingMs: Long? = null,
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelTargetTool by remember { mutableStateOf<String?>(null) }

    val stepMap = steps.associateBy { it.stage }
    val orderedStages =
        listOf(
            SetupStepStage.WSL_DETECTION,
            SetupStepStage.SERVER_CONNECTIVITY,
            SetupStepStage.SYSTEM_PACKAGES,
            SetupStepStage.TOOLCHAIN_COMPILATION,
        )

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
                modifier = Modifier.fillMaxWidth().glassContainer(),
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
                        activeDistro = activeDistro,
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
                                onSelectDistro = onSelectDistro,
                                onInstallUbuntu = onInstallUbuntu,
                                activeDistro = activeDistro,
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
            text =
            "The current build of $toolText stops; tools already built are kept. " +
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
    onSelectDistro: (String) -> Unit,
    onInstallUbuntu: (String) -> Unit,
    activeDistro: String?,
    onShowDiagnostics: () -> Unit,
    onShowCancelDialog: (String?) -> Unit,
) {
    val actionDistro = distro ?: if (stage == SetupStepStage.SERVER_CONNECTIVITY) activeDistro else null
    when (action) {
        SetupRowAction.RETRY, SetupRowAction.CHECK_AGAIN ->
            if (stage == SetupStepStage.WSL_DETECTION || stage == SetupStepStage.SYSTEM_PACKAGES) {
                onVerify()
            } else {
                onRetryStage(stage)
            }

        SetupRowAction.INSTALL_PACKAGES -> onInstallPackages()

        SetupRowAction.CONTINUE_SETUP -> onShowHandoffSheet()

        SetupRowAction.OPEN_DISTRO -> actionDistro?.let(onOpenDistro)

        SetupRowAction.SHOW_DIAGNOSTICS, SetupRowAction.SHOW_LOG -> onShowDiagnostics()

        SetupRowAction.CANCEL -> onShowCancelDialog(extractToolName(stepDescription))

        SetupRowAction.INSTALL_UBUNTU -> onInstallUbuntu(actionDistro ?: "Ubuntu-24.04")

        SetupRowAction.USE_DISTRO -> actionDistro?.let(onSelectDistro) ?: onVerify()
    }
}

private fun extractToolName(description: String): String? {
    val regex = Regex("""(?:Building|Syncing)\s+([^\s(]+)""")
    val match = regex.find(description)
    return match?.groupValues?.get(1)
}

private data class StageRowDisplayState(
    val isRunning: Boolean,
    val isSuccess: Boolean,
    val isRestored: Boolean,
    val isActionRequired: Boolean,
    val isFailed: Boolean,
    val displayDescription: String,
    val primaryAction: SetupRowAction?,
    val primaryLabel: String,
    val secondaryAction: SetupRowAction?,
    val secondaryActionLabel: String?,
    val layoutRecovery: ToolchainLayoutRecoveryPresentation?,
    val errorText: String?,
)

private fun buildStageRowDisplayState(
    step: SetupStepDetail,
    presRunning: Boolean = false,
    presReady: Boolean = false,
    severity: IdeStatusSeverity? = null,
    displayDescription: String = step.description,
    primaryAction: SetupRowAction? = SetupRowAction.RETRY,
    actionCountsAsRequired: Boolean = primaryAction != null,
    primaryActionLabel: String? = null,
    secondaryAction: SetupRowAction? = null,
    secondaryActionLabel: String? = null,
    layoutRecovery: ToolchainLayoutRecoveryPresentation? = null,
    errorText: String? = null,
): StageRowDisplayState {
    val isRunning = step.status == StepStatus.RUNNING || presRunning
    val isSuccess = (step.status == StepStatus.SUCCESS || presReady) && !isRunning
    val isActionRequired = !isSuccess &&
        !isRunning &&
        (step.status == StepStatus.FAILED || step.status == StepStatus.WARNING || actionCountsAsRequired)
    return StageRowDisplayState(
        isRunning = isRunning,
        isSuccess = isSuccess,
        isRestored = step.provenance == StepProvenance.RESTORED,
        isActionRequired = isActionRequired,
        isFailed = step.status == StepStatus.FAILED || severity == IdeStatusSeverity.Failed,
        displayDescription = displayDescription,
        primaryAction = primaryAction,
        primaryLabel = primaryActionLabel ?: "Retry Stage",
        secondaryAction = secondaryAction,
        secondaryActionLabel = secondaryActionLabel,
        layoutRecovery = layoutRecovery,
        errorText = errorText,
    )
}

private fun resolveBuildServiceDisplayState(
    step: SetupStepDetail,
    isBusy: Boolean,
    daemonPingMs: Long?,
    systemPackagesStep: SetupStepDetail?,
): StageRowDisplayState {
    val pres = MachineRowMapper.mapBuildService(
        step = step,
        daemonPingMs = daemonPingMs,
        isChecking = isBusy || step.status == StepStatus.RUNNING,
        systemPackagesStep = systemPackagesStep,
    )
    return buildStageRowDisplayState(
        step = step,
        presRunning = pres.isRunning,
        presReady = pres.isReady,
        severity = pres.severity,
        displayDescription = pres.detailText.ifBlank { step.description },
        primaryAction = pres.primaryAction,
        primaryActionLabel = pres.primaryActionLabel,
        secondaryAction = pres.secondaryAction,
        secondaryActionLabel = pres.secondaryActionLabel,
        layoutRecovery = pres.layoutRecovery,
    )
}

private fun resolveSystemPackagesDisplayState(
    step: SetupStepDetail,
    isBusy: Boolean,
    activeOperation: SetupOperation?,
    handoffSheetState: TerminalHandoffState,
): StageRowDisplayState {
    val pres = MachineRowMapper.mapSystemPackages(
        step = step,
        isChecking = isBusy || step.status == StepStatus.RUNNING,
        activeOperation = activeOperation,
        handoffSheetState = handoffSheetState,
    )
    return buildStageRowDisplayState(
        step = step,
        presRunning = pres.isChecking,
        presReady = pres.isReady,
        severity = pres.severity,
        displayDescription = pres.detailText.ifBlank { step.description },
        primaryAction = pres.primaryAction,
        primaryActionLabel = pres.primaryActionLabel,
    )
}

private fun resolveToolchainDisplayState(
    step: SetupStepDetail,
    activeOperation: SetupOperation?,
    currentEpochMs: Long?,
): StageRowDisplayState {
    val pres = MachineRowMapper.mapToolchainRow(
        step = step,
        nowEpochMs = currentEpochMs,
        canCancel = activeOperation?.executionState == SetupOperationState.RUNNING,
    )
    return buildStageRowDisplayState(
        step = step,
        presRunning = pres?.isRunning == true,
        presReady = pres?.isReady == true,
        severity = pres?.severity,
        displayDescription = pres?.detailText?.ifBlank { step.description } ?: step.description,
        primaryAction = pres?.primaryAction,
        actionCountsAsRequired = pres?.primaryAction != null && pres.primaryAction != SetupRowAction.CANCEL,
        primaryActionLabel = pres?.primaryActionLabel,
        secondaryAction = pres?.secondaryAction,
        secondaryActionLabel = pres?.secondaryActionLabel,
        errorText = pres?.errorText,
    )
}

private fun resolveWslDisplayState(step: SetupStepDetail, wslPres: WslRowPresentation?): StageRowDisplayState =
    buildStageRowDisplayState(
        step = step,
        severity = wslPres?.severity,
        displayDescription = wslPres?.detailText ?: step.description,
        primaryAction = wslPres?.primaryAction ?: SetupRowAction.RETRY,
        actionCountsAsRequired = wslPres?.primaryAction != null,
        primaryActionLabel = wslPres?.primaryActionLabel,
        secondaryAction = wslPres?.secondaryAction,
        secondaryActionLabel = wslPres?.secondaryActionLabel,
        errorText = step.error,
    )

private fun resolveStageRowDisplayState(
    step: SetupStepDetail,
    stage: SetupStepStage,
    isBusy: Boolean,
    activeOperation: SetupOperation?,
    handoffSheetState: TerminalHandoffState,
    wslPres: WslRowPresentation?,
    daemonPingMs: Long?,
    systemPackagesStep: SetupStepDetail?,
    currentEpochMs: Long?,
): StageRowDisplayState = when (stage) {
    SetupStepStage.SERVER_CONNECTIVITY -> resolveBuildServiceDisplayState(
        step = step,
        isBusy = isBusy,
        daemonPingMs = daemonPingMs,
        systemPackagesStep = systemPackagesStep,
    )
    SetupStepStage.SYSTEM_PACKAGES -> resolveSystemPackagesDisplayState(
        step = step,
        isBusy = isBusy,
        activeOperation = activeOperation,
        handoffSheetState = handoffSheetState,
    )
    SetupStepStage.TOOLCHAIN_COMPILATION -> resolveToolchainDisplayState(
        step = step,
        activeOperation = activeOperation,
        currentEpochMs = currentEpochMs,
    )
    SetupStepStage.WSL_DETECTION -> resolveWslDisplayState(step = step, wslPres = wslPres)
    else -> buildStageRowDisplayState(
        step = step,
        actionCountsAsRequired = false,
        errorText = step.error,
    )
}

@Suppress("DEPRECATION")
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
    activeDistro: String? = null,
    onSelectDistro: (String) -> Unit = {},
) {
    val stageDisplayName = step.title.ifBlank { stage.displayName }
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

    val state = resolveStageRowDisplayState(
        step = step,
        stage = stage,
        isBusy = isBusy,
        activeOperation = activeOperation,
        handoffSheetState = handoffSheetState,
        wslPres = wslPres,
        daemonPingMs = daemonPingMs,
        systemPackagesStep = systemPackagesStep,
        currentEpochMs = currentEpochMs,
    )

    if (state.isActionRequired) {
        ExpandedActionRequiredStageRow(
            stageDisplayName = stageDisplayName,
            displayDescription = state.displayDescription,
            isFailed = state.isFailed,
            primaryAction = state.primaryAction,
            primaryLabel = state.primaryLabel,
            secondaryAction = state.secondaryAction,
            secondaryActionLabel = state.secondaryActionLabel,
            actionTarget = wslPres?.distro ?: activeDistro,
            isBusy = isBusy,
            onAction = onAction,
            layoutRecovery = state.layoutRecovery,
            errorText = state.errorText,
            wslPres = wslPres,
            onSelectDistro = onSelectDistro,
        )
    } else {
        StandardStageRowView(
            stageDisplayName = stageDisplayName,
            displayDescription = state.displayDescription,
            isRunning = state.isRunning,
            isSuccess = state.isSuccess,
            isRestored = state.isRestored,
            primaryAction = state.primaryAction,
            onAction = onAction,
        )
    }
}

@Composable
private fun StageRowLayoutRecoveryBox(recovery: ToolchainLayoutRecoveryPresentation) {
    val clipboardManager = LocalClipboardManager.current
    var recoveryCommandCopied by remember(recovery.command) { mutableStateOf(false) }
    LaunchedEffect(recovery.command, recoveryCommandCopied) {
        if (recoveryCommandCopied) {
            delay(COPIED_FEEDBACK_MS)
            recoveryCommandCopied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ExtraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outline,
                shape = GlassShapes.ExtraSmall,
            )
            .padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = recovery.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        SelectionContainer {
            Text(
                text = recovery.command,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassSecondaryButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(recovery.command))
                recoveryCommandCopied = true
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = if (recoveryCommandCopied) {
                        AppIcons.ideStatusSuccess()
                    } else {
                        AppIcons.CopyPainterResource()
                    },
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(if (recoveryCommandCopied) "Copied" else "Copy recovery command")
            }
        }
    }
}

@Composable
private fun StageRowActionButtons(
    primaryAction: SetupRowAction?,
    primaryLabel: String,
    secondaryAction: SetupRowAction?,
    secondaryActionLabel: String?,
    actionTarget: String?,
    isBusy: Boolean,
    onAction: (SetupRowAction, String?) -> Unit,
) {
    if (primaryAction == null && secondaryAction == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
    ) {
        if (secondaryAction != null) {
            GlassSecondaryButton(
                onClick = { onAction(secondaryAction, actionTarget) },
                enabled = !isBusy,
            ) {
                Text(
                    text = secondaryActionLabel.orEmpty(),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (primaryAction != null) {
            GlassPrimaryButton(
                onClick = { onAction(primaryAction, actionTarget) },
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
                    Text(text = primaryLabel, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StageRowDistroChoices(choices: List<String>, isBusy: Boolean, onSelectDistro: (String) -> Unit) {
    if (choices.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        choices.forEach { distro ->
            GlassChip(
                label = distro,
                selected = false,
                onClick = { if (!isBusy) onSelectDistro(distro) },
            )
        }
    }
}

@Composable
private fun ExpandedActionRequiredStageRow(
    stageDisplayName: String,
    displayDescription: String,
    isFailed: Boolean,
    primaryAction: SetupRowAction?,
    primaryLabel: String,
    secondaryAction: SetupRowAction?,
    secondaryActionLabel: String?,
    actionTarget: String?,
    isBusy: Boolean,
    onAction: (SetupRowAction, String?) -> Unit,
    layoutRecovery: ToolchainLayoutRecoveryPresentation?,
    errorText: String?,
    wslPres: WslRowPresentation?,
    onSelectDistro: (String) -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors
    val severityColor = if (isFailed) diagnostics.error else diagnostics.warning

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(severityColor.copy(alpha = AlphaTokens.UltraFaint))
            .border(
                width = StrokeWidth.Hairline,
                color = severityColor.copy(alpha = AlphaTokens.Border),
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
                        tint = severityColor,
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
                color = severityColor,
            )

            layoutRecovery?.let { recovery ->
                StageRowLayoutRecoveryBox(recovery = recovery)
            }

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

            wslPres?.choices?.let { choices ->
                StageRowDistroChoices(choices = choices, isBusy = isBusy, onSelectDistro = onSelectDistro)
            }

            StageRowActionButtons(
                primaryAction = primaryAction,
                primaryLabel = primaryLabel,
                secondaryAction = secondaryAction,
                secondaryActionLabel = secondaryActionLabel,
                actionTarget = actionTarget,
                isBusy = isBusy,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun StandardStageRowView(
    stageDisplayName: String,
    displayDescription: String,
    isRunning: Boolean,
    isSuccess: Boolean,
    isRestored: Boolean,
    primaryAction: SetupRowAction?,
    onAction: (SetupRowAction, String?) -> Unit,
) {
    val diagnostics = GlassTheme.diagnosticColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                role = GlassRole.TextCard,
                shape = GlassShapes.HazeSmall,
                surfaceColor = MaterialTheme.colorScheme.surfaceContainer,
                drawBorder = true,
                borderColor = MaterialTheme.colorScheme.outline,
                borderWidth = StrokeWidth.Hairline,
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
                            GlassSpinner(
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
                        Box(
                            modifier = Modifier.size(IconSize.Small),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
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
                    label = when {
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

/** A step's technical error: selectable, with a button that copies the whole text for a bug report. */
@Suppress("DEPRECATION")
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
        modifier =
        modifier
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
