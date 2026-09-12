/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState

/**
 * Structured overload for [LaunchpadStepContent] utilizing [SetupUiState] and [SetupUiActions]
 * to prevent parameter sprawl and ensure single-responsibility composition.
 */
@Composable
fun LaunchpadStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    onNavigateToEnvironment: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    LaunchpadStepContent(
        toolchainSetupState = state.toolchainSetupState,
        selectedTarget = state.selectedTarget,
        onCreateWorkspace = actions.onCreateWorkspace,
        onNavigateToEnvironment = onNavigateToEnvironment,
        onProvisionAvbKey = actions.onProvisionAvbKey,
        resolvedBackdrop = resolvedBackdrop,
        modifier = modifier,
    )
}

/**
 * Phase 3: Workspace Launchpad Cockpit.
 *
 * Implements Time-to-First-Value:
 * - Summarizes configured workspace directory and UNC paths
 * - Verifies machine-local AVB 2.0 RSA-4096 signing key
 * - Provides high-visibility "Create Studio Workspace" hero call to action
 *
 * Adheres strictly to:
 * - Single Responsibility: Final workspace verification and IDE launch transition.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 */
@Composable
fun LaunchpadStepContent(
    toolchainSetupState: ToolchainSetupState,
    selectedTarget: TargetDevice? = null,
    onCreateWorkspace: () -> Unit,
    onNavigateToEnvironment: () -> Unit,
    onProvisionAvbKey: () -> Unit,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics

    val isAvbKeyReady = toolchainSetupState.isAvbKeyProvisioned
    val canLaunchWorkspace = toolchainSetupState.canLaunchWorkspace


    Column(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupCockpitMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(
                start = Spacing.ScreenHorizontal,
                end = Spacing.ScreenHorizontal,
                top = Spacing.Medium,
                bottom = Spacing.ScreenVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        // Header Row
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Workspace Launchpad",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = glassColors.onGlass,
            )
            Text(
                text = "Your engineering environment is fully configured. Review workspace parameters and launch LtiRom Studio.",
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.onGlassSecondary,
            )
        }

        // Workspace Summary Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                // FrameLayout / Box Anchored Layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ComponentSize.SidePanelRailButtonSize)
                                .clip(GlassShapes.Medium)
                                .background(diagnostics.success.copy(alpha = AlphaTokens.PrimaryButtonTint))
                                .border(width = StrokeWidth.Standard, color = diagnostics.success.copy(alpha = AlphaTokens.Border), shape = GlassShapes.Medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = AppIcons.CheckPainterResource(),
                                contentDescription = null,
                                tint = diagnostics.success,
                                modifier = Modifier.size(IconSize.Medium),
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                        ) {
                            Text(
                                text = "Active Staging Workspace",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = glassColors.onGlass,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "WSL 2 ext4 native volume with Windows UNC bridge",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.onGlassSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    GlassChip(
                        label = if (canLaunchWorkspace) "Ready to Build" else "Verification Required",
                        selected = canLaunchWorkspace,
                    )
                }

                GlassHorizontalDivider()

                // Path Details
                val linuxPath = "~/LtiRomWorkDir"
                val distro = toolchainSetupState.activeDistro ?: "Ubuntu"
                val uncPath = "\\\\wsl.localhost\\$distro\\home\\lti\\LtiRomWorkDir"
                val readyToolsCount = toolchainSetupState.toolsMatrix.count { it.status == StepStatus.SUCCESS }
                val totalToolsCount = toolchainSetupState.toolsMatrix.size
                val toolchainSummary = if (readyToolsCount > 0) {
                    "$readyToolsCount of $totalToolsCount native tools compiled in ~/LtiRomTools/bin (adb, fastboot, avbtool, erofsfuse, etc.)"
                } else {
                    "$totalToolsCount native tools compiled in ~/LtiRomTools/bin (adb, fastboot, avbtool, erofsfuse, etc.)"
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    WorkspacePathRow(
                        label = "Linux ext4 Path",
                        path = linuxPath,
                        isCopyable = true,
                    )
                    WorkspacePathRow(
                        label = "Windows UNC Path",
                        path = uncPath,
                        isCopyable = true,
                    )
                    WorkspacePathRow(
                        label = "Toolchain Status",
                        path = toolchainSummary,
                        isCopyable = false,
                    )
                }
            }
        }

        // AVB 2.0 Security Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            // FrameLayout / Box Anchored Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            .size(IconSize.Large)
                            .clip(GlassShapes.Capsule)
                            .background(
                                if (isAvbKeyReady) diagnostics.success.copy(alpha = AlphaTokens.GlassSecondaryTint)
                                else diagnostics.warning.copy(alpha = AlphaTokens.GlassSecondaryTint)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = if (isAvbKeyReady) AppIcons.LockedPainterResource() else AppIcons.InfoPainterResource(),
                            contentDescription = null,
                            tint = if (isAvbKeyReady) diagnostics.success else diagnostics.warning,
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "AVB 2.0 RSA-4096 Signing Key",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = glassColors.onGlass,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            GlassChip(
                                label = if (isAvbKeyReady) "Provisioned" else "Pending",
                                selected = isAvbKeyReady,
                            )
                        }
                        Text(
                            text = if (isAvbKeyReady) {
                                "Machine-local RSA-4096 key active in workspace security vault for boot/vbmeta image signing."
                            } else {
                                "Key not generated yet. Generate machine-local RSA-4096 key before rebuilding boot images."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.onGlassSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!isAvbKeyReady) {
                    GlassSecondaryButton(
                        onClick = onProvisionAvbKey,
                        enabled = !toolchainSetupState.isBusy,
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
                            Text("Generate Key", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        // Toolchain Verification Warning if Prerequisites Unmet
        if (!canLaunchWorkspace) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backdrop = resolvedBackdrop,
                contentPadding = Spacing.CardPadding,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    Box(
                        modifier = Modifier
                            .size(IconSize.Large)
                            .clip(GlassShapes.Capsule)
                            .background(diagnostics.warning.copy(alpha = AlphaTokens.GlassSecondaryTint)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = AppIcons.ideStatusWarning(),
                            contentDescription = null,
                            tint = diagnostics.warning,
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "Toolchain & Server Verification Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = glassColors.onGlass,
                        )
                        Text(
                            text = "LtiRomServer daemon bridge and native toolchain binaries must be verified before entering the workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.onGlassSecondary,
                        )
                    }
                }
            }
        }

        // Hero Launch Action Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            // Responsive Action Bar: SpaceBetween anchoring with auto-wrap to prevent resize overlap
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                GlassSecondaryButton(
                    onClick = onNavigateToEnvironment,
                    enabled = !toolchainSetupState.isBusy,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Icon(
                            painter = AppIcons.ChevronLeftPainterResource(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Small),
                        )
                        Text("Back to Environment", maxLines = 1, softWrap = false)
                    }
                }

                if (canLaunchWorkspace) {
                    GlassPrimaryButton(
                        onClick = onCreateWorkspace,
                        enabled = !toolchainSetupState.isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            modifier = Modifier.padding(horizontal = Spacing.Small),
                        ) {
                            Text(
                                text = "Create Studio Workspace",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Icon(
                                painter = AppIcons.SendPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                        }
                    }
                } else {
                    GlassPrimaryButton(
                        onClick = onNavigateToEnvironment,
                        enabled = !toolchainSetupState.isBusy,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                            modifier = Modifier.padding(horizontal = Spacing.Small),
                        ) {
                            Icon(
                                painter = AppIcons.SparklesPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = "Connect & Verify Toolchain to Launch",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Monospaced workspace path row with copy-to-clipboard action.
 */
@Composable
private fun WorkspacePathRow(
    label: String,
    path: String,
    modifier: Modifier = Modifier,
    isCopyable: Boolean = false,
) {
    val glassColors = GlassTheme.colors
    val diagnostics = glassColors.diagnostics
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember(path) { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000)
            isCopied = false
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = glassColors.onGlassMuted,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(modifier = Modifier.width(Spacing.Small))
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
                color = glassColors.onGlass,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCopyable) {
                GlassTooltipArea(
                    tooltipText = if (isCopied) "Copied to clipboard!" else "Copy path",
                ) {
                    GlassIconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(path))
                            isCopied = true
                        },
                        size = ComponentSize.PanelHeaderAction,
                    ) {
                        Icon(
                            painter = if (isCopied) AppIcons.CheckPainterResource() else AppIcons.CopyPainterResource(),
                            contentDescription = "Copy path",
                            tint = if (isCopied) diagnostics.success else glassColors.onGlassSecondary,
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                }
            }
        }
    }
}
