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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.feature.setup.PlannedSetupChanges

/**
 * Modal preview sheet presenting planned environment provisioning changes before mutation execution (FR-005).
 * Discloses planned system packages, git submodules, toolchains to compile, and elevation requirements.
 * Dismisses on Escape key or outside scrim click; traps confirmation until explicitly approved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvisioningPreviewSheet(
    plannedChanges: PlannedSetupChanges,
    isAutoDoctorEnabled: Boolean,
    onSetAutoDoctorEnabled: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(glassColors.glassSurface.copy(alpha = AlphaTokens.Scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            modifier = Modifier
                .widthIn(max = ComponentSize.MaxPanelWidth)
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Intercept click so dialog doesn't close on inner click */ },
                ),
            backdrop = backdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                    ) {
                        Text(
                            text = "Provisioning Preview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = glassColors.onGlass,
                        )
                        Text(
                            text = "Review required mutations before running environment setup",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.onGlassSecondary,
                        )
                    }

                    GlassIconButton(
                        onClick = onDismiss,
                        size = ComponentSize.PanelHeaderActionTouchTarget,
                    ) {
                        Icon(
                            painter = AppIcons.ClosePainterResource(),
                            contentDescription = "Dismiss preview",
                            modifier = Modifier.size(IconSize.Medium),
                            tint = glassColors.onGlassSecondary,
                        )
                    }
                }

                GlassHorizontalDivider()

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    // Target Environment Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "Target Subsystem:",
                            style = MaterialTheme.typography.labelMedium,
                            color = glassColors.onGlassMuted,
                        )
                        Box(
                            modifier = Modifier
                                .clip(GlassShapes.Small)
                                .background(glassColors.glassSurface)
                                .border(
                                    width = StrokeWidth.Standard,
                                    color = glassColors.glassBorder,
                                    shape = GlassShapes.Small,
                                )
                                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                        ) {
                            Text(
                                text = plannedChanges.targetDistro,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = codeFontFamily()),
                                color = glassColors.onGlass,
                            )
                        }
                    }

                    // Elevation Banner if required
                    if (plannedChanges.requiresElevation) {
                        GlassBanner(
                            severity = GlassBannerSeverity.WARNING,
                            title = "Root / Sudo Elevation Required",
                            description = "System packages or toolchain binaries require administrative permissions: " +
                                (plannedChanges.elevationReason ?: "sudo apt-get install / system configuration"),
                            backdrop = backdrop,
                        )
                    }

                    // System Packages to install
                    if (plannedChanges.packagesToInstall.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "System Packages (${plannedChanges.packagesToInstall.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = glassColors.onGlass,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                for (pkg in plannedChanges.packagesToInstall) {
                                    Box(
                                        modifier = Modifier
                                            .clip(GlassShapes.Small)
                                            .background(glassColors.glassSurface)
                                            .border(
                                                width = StrokeWidth.Hairline,
                                                color = glassColors.glassBorder,
                                                shape = GlassShapes.Small,
                                            )
                                            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                                    ) {
                                        Text(
                                            text = pkg,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                            color = glassColors.onGlassSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Submodules to sync
                    if (plannedChanges.submodulesToSync.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "Git Submodules (${plannedChanges.submodulesToSync.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = glassColors.onGlass,
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(GlassShapes.Small)
                                    .background(glassColors.glassSurface)
                                    .border(
                                        width = StrokeWidth.Hairline,
                                        color = glassColors.glassBorder,
                                        shape = GlassShapes.Small,
                                    )
                                    .padding(Spacing.Small),
                                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                for (sub in plannedChanges.submodulesToSync) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                        color = glassColors.onGlassSecondary,
                                    )
                                }
                            }
                        }
                    }

                    // Tools to compile
                    if (plannedChanges.toolsToCompile.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "Tools to Compile (${plannedChanges.toolsToCompile.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = glassColors.onGlass,
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                for (tool in plannedChanges.toolsToCompile) {
                                    Box(
                                        modifier = Modifier
                                            .clip(GlassShapes.Small)
                                            .background(glassColors.glassSurface)
                                            .border(
                                                width = StrokeWidth.Hairline,
                                                color = glassColors.glassBorder,
                                                shape = GlassShapes.Small,
                                            )
                                            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                                    ) {
                                        Text(
                                            text = tool,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                            color = glassColors.onGlassSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (plannedChanges.packagesToInstall.isEmpty() &&
                        plannedChanges.submodulesToSync.isEmpty() &&
                        plannedChanges.toolsToCompile.isEmpty()
                    ) {
                        Text(
                            text = "No system packages or toolchain compilations required for this operation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glassColors.onGlassMuted,
                        )
                    }

                    // Auto Doctor Remediation Option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        GlassToggle(
                            checked = isAutoDoctorEnabled,
                            onCheckedChange = onSetAutoDoctorEnabled,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Text(
                                text = "Automated Doctor Remediation",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = glassColors.onGlass,
                            )
                            Text(
                                text = "Automatically fix non-interactive package prerequisites during setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.onGlassSecondary,
                            )
                        }
                    }
                }

                GlassHorizontalDivider()

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
                ) {
                    GlassSecondaryButton(
                        onClick = onDismiss,
                    ) {
                        Text("Cancel")
                    }

                    GlassPrimaryButton(
                        onClick = onConfirm,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Icon(
                                painter = AppIcons.SparklesPainterResource(),
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text("Confirm & Set Up")
                        }
                    }
                }
            }
        }
    }
}
