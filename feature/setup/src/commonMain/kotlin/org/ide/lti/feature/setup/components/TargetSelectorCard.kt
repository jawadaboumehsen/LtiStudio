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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenu
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuHeader
import org.ide.lti.core.designsystem.component.navigation.GlassDropdownMenuItem
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion

/**
 * Compact target hardware selector card for the Setup dashboard tab.
 *
 * Displays the currently selected hardware target profile (name, codename, region,
 * and baseline firmware) and allows switching between available targets via a dropdown menu.
 *
 * Adheres strictly to:
 * - Single Responsibility: Displays active target hardware and dispatches target selection events.
 * - Design System Guardrails: 100% token usage (zero raw dp or alpha float literals).
 * - Independent Architecture: Completely decoupled from `feature/workspace`.
 */
@Composable
fun TargetSelectorCard(
    selectedTarget: TargetDevice?,
    availableTargets: List<TargetDevice>,
    onSelectTargetAndLaunch: (TargetDevice) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    if (selectedTarget == null) return

    val glassColors = GlassTheme.colors
    val density = LocalDensity.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0) }
    val canSwitch = availableTargets.size > 1

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        backdrop = backdrop,
        contentPadding = Spacing.CardPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Target Metadata Summary (Badge + Info)
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Target Hardware Icon Badge
                Box(
                    modifier = Modifier
                        .size(ComponentSize.SidePanelRailButtonSize)
                        .clip(GlassShapes.Small)
                        .background(glassColors.glassAccent.copy(alpha = AlphaTokens.Hover))
                        .border(
                            width = StrokeWidth.Hairline,
                            color = glassColors.glassAccent.copy(alpha = AlphaTokens.Border),
                            shape = GlassShapes.Small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "📱",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        Text(
                            text = "Active Target",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = codeFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = glassColors.glassAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        GlassChip(
                            label = selectedTarget.selectedRegion.displayName,
                            selected = false,
                        )
                    }

                    Text(
                        text = "${selectedTarget.name} (${selectedTarget.codename})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = glassColors.onGlass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = "Firmware: ${selectedTarget.selectedFirmware.version}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = codeFontFamily(),
                        ),
                        color = glassColors.onGlassSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Trailing Switch Target Action & Dropdown
            Box(
                modifier = Modifier.onSizeChanged { anchorWidth = it.width },
            ) {
                GlassTooltipArea(
                    tooltipText = "Only one target device configured. Add another device from an open workspace to switch.",
                    delayMillis = if (canSwitch) Int.MAX_VALUE else MotionDuration.TooltipDelay,
                ) {
                    GlassSecondaryButton(
                        onClick = {
                            if (canSwitch) {
                                isMenuOpen = !isMenuOpen
                            }
                        },
                        enabled = canSwitch,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Text(
                                text = "Switch Target",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (canSwitch) glassColors.onGlass else glassColors.onGlassMuted,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (canSwitch) {
                                Icon(
                                    painter = if (isMenuOpen) {
                                        AppIcons.ChevronUpPainterResource()
                                    } else {
                                        AppIcons.ChevronDownPainterResource()
                                    },
                                    contentDescription = "Toggle Target Selection Menu",
                                    tint = glassColors.onGlassSecondary,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                            }
                        }
                    }
                }

                if (canSwitch) {
                    val dropdownMinWidthPx = with(density) { ComponentSize.DropdownMenuMinWidth.roundToPx() }
                    val offsetX = (anchorWidth - dropdownMinWidthPx).coerceAtMost(0)

                    GlassDropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        offset = IntOffset(
                            x = offsetX,
                            y = with(density) { ComponentSize.ButtonMinHeight.roundToPx() },
                        ),
                    ) {
                        GlassDropdownMenuHeader(title = "Hardware Targets")

                        availableTargets.forEach { target ->
                            val isSelected = target.id == selectedTarget.id
                            val regionEmoji = if (target.selectedRegion == TargetRegion.GLOBAL) "🌐" else "🇨🇳"

                            GlassDropdownMenuItem(
                                text = "${target.name} (${target.codename})",
                                subtitle = "${target.selectedRegion.displayName} • ${target.selectedFirmware.version}",
                                leadingIcon = {
                                    Text(
                                        text = regionEmoji,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                isSelected = isSelected,
                                onClick = {
                                    isMenuOpen = false
                                    onSelectTargetAndLaunch(target)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
