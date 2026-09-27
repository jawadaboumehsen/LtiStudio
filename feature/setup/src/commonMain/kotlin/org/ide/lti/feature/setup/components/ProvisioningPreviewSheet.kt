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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.SetupTokens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.feature.setup.PlannedSetupChanges

/**
 * Modal preview sheet presenting planned environment provisioning changes before mutation execution (FR-005, FR-014).
 * Discloses planned system packages, git submodules, toolchains to compile, and elevation requirements.
 * Dismisses on Escape key or outside scrim click; traps Tab/Shift-Tab and confirmation until explicitly approved.
 * Bounded and virtualized via LazyColumn; supports reduced motion and reduced transparency (SC-005).
 */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProvisioningPreviewSheet(
    plannedChanges: PlannedSetupChanges,
    isAutoDoctorEnabled: Boolean,
    onSetAutoDoctorEnabled: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    plan: SetupPlan? = null,
    contextualError: String? = null,
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = AlphaTokens.Scrim))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else if (event.key == Key.Tab) {
                        val direction = if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next
                        focusManager.moveFocus(direction)
                        true
                    } else {
                        false
                    }
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
                .then(
                    if (SetupTokens.IsReducedTransparency) {
                        Modifier.background(SetupTokens.OpaqueSurfaceColor, GlassShapes.Card)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Intercept click so dialog doesn't close on inner click */ },
                ),
            surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = previewSubtitle(plan),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                GlassHorizontalDivider()

                // Bounded and Virtualized Action Lists
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ComponentSize.MaxPanelHeight),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    if (contextualError != null) {
                        item(key = "contextual_error") {
                            GlassBanner(
                                severity = GlassBannerSeverity.ERROR,
                                title = "Operation Warning / Error",
                                description = contextualError,
                            )
                        }
                    }

                    // Target Environment Badge
                    item(key = "target_subsystem") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        ) {
                            Text(
                                text = "Target Subsystem:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(GlassShapes.Small)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(
                                        width = StrokeWidth.Standard,
                                        color = MaterialTheme.colorScheme.outline,
                                        shape = GlassShapes.Small,
                                    )
                                    .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                            ) {
                                Text(
                                    text = plannedChanges.targetDistro,
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = codeFontFamily()),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (plan != null) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "Plan: ${plan.planId.take(8)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = codeFontFamily()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Elevation Banner if required
                    if (plannedChanges.requiresElevation) {
                        item(key = "elevation_banner") {
                            val reason = plannedChanges.elevationReason
                                ?: "sudo apt-get install / system configuration"
                            GlassBanner(
                                severity = GlassBannerSeverity.WARNING,
                                title = "Root / Sudo Elevation Required",
                                description =
                                "System packages or toolchain binaries require administrative permissions: " +
                                    reason,
                            )
                        }
                    }

                    // System Packages to install
                    if (plannedChanges.packagesToInstall.isNotEmpty()) {
                        item(key = "packages_section") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                Text(
                                    text = "System Packages (${plannedChanges.packagesToInstall.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .border(
                                                    width = StrokeWidth.Hairline,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = GlassShapes.Small,
                                                )
                                                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                                        ) {
                                            Text(
                                                text = pkg,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Submodules to sync
                    if (plannedChanges.submodulesToSync.isNotEmpty()) {
                        item(key = "submodules_section") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                Text(
                                    text = "Git Submodules (${plannedChanges.submodulesToSync.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(GlassShapes.Small)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .border(
                                            width = StrokeWidth.Hairline,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = GlassShapes.Small,
                                        )
                                        .padding(Spacing.Small),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    for (sub in plannedChanges.submodulesToSync) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tools to compile
                    if (plannedChanges.toolsToCompile.isNotEmpty()) {
                        item(key = "tools_to_compile_section") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                Text(
                                    text = "Tools to Compile (${plannedChanges.toolsToCompile.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                                .border(
                                                    width = StrokeWidth.Hairline,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = GlassShapes.Small,
                                                )
                                                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
                                        ) {
                                            Text(
                                                text = tool,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Tools to publish to the daemon registry
                    if (plannedChanges.toolsToPublish.isNotEmpty()) {
                        item(key = "tools_to_publish_section") {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                            ) {
                                Text(
                                    text = "Tools to Publish (${plannedChanges.toolsToPublish.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = plannedChanges.toolsToPublish.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (plannedChanges.isEmpty) {
                        item(key = "empty_changes_notice") {
                            Text(
                                text = if (plan?.kind == SetupPlanKind.CACHE_RESET) {
                                    "Persisted setup evidence for this environment will be cleared and the " +
                                        "environment re-checked. No packages, sources or binaries change."
                                } else {
                                    "No system packages or toolchain compilations required for this operation."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Auto Doctor Remediation Option
                    item(key = "auto_doctor_toggle") {
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Automatically fix non-interactive package prerequisites during setup",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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

/** One-line description of what confirming [plan] does; the plan kind is the only source. */
private fun previewSubtitle(plan: SetupPlan?): String = when (plan?.kind) {
    null, SetupPlanKind.FULL_SETUP -> "Review required mutations before running environment setup"
    SetupPlanKind.STAGE_RETRY ->
        "Review the mutations for retrying ${plan.targetStageOrToolId ?: "the selected stage"}"
    SetupPlanKind.REPAIR_TOOL -> "Review the rebuild for ${plan.targetStageOrToolId ?: "the selected tool"}"
    SetupPlanKind.CACHE_RESET -> "Review the cache reset before clearing persisted setup evidence"
    SetupPlanKind.BOOTSTRAP_PACKAGES -> "Review required bootstrap packages before terminal handoff"
}
