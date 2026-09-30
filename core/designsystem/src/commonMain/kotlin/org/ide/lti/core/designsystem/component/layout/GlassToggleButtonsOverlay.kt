/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralChevronUp
import org.ide.lti.core.designsystem.theme.IconSize

/**
 * Floating panel toggle buttons overlay for [GlassLayout].
 */
@Composable
internal fun GlassToggleButtonsOverlay(state: LtiLayoutState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Row(
            modifier = Modifier.padding(
                top = LtiLayoutDefaults.ToggleButtonTopPadding,
                end = LtiLayoutDefaults.ToggleButtonEndPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(LtiLayoutDefaults.ToggleButtonSpacing),
        ) {
            AnimatedToggleButton(
                onClick = { state.toggleLeft() },
                selected = state.showLeft,
            ) {
                Icon(
                    painter = if (state.showLeft) {
                        AppIcons.ClosePainterResource()
                    } else {
                        AppIcons.MenuPainterResource()
                    },
                    contentDescription = if (state.showLeft) {
                        "Hide left panel"
                    } else {
                        "Show left panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            AnimatedToggleButton(
                onClick = { state.toggleBottom() },
                selected = state.showBottom,
            ) {
                Icon(
                    painter = if (state.showBottom) {
                        AppIcons.ChevronDownPainterResource()
                    } else {
                        AppIcons.ideGeneralChevronUp()
                    },
                    contentDescription = if (state.showBottom) {
                        "Hide bottom panel"
                    } else {
                        "Show bottom panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }

            AnimatedToggleButton(
                onClick = { state.toggleRight() },
                selected = state.showRight,
            ) {
                Icon(
                    painter = if (state.showRight) {
                        AppIcons.ClosePainterResource()
                    } else {
                        AppIcons.SettingsPainterResource()
                    },
                    contentDescription = if (state.showRight) {
                        "Hide right panel"
                    } else {
                        "Show right panel"
                    },
                    modifier = Modifier.size(IconSize.Medium),
                )
            }
        }
    }
}

/**
 * Animated toggle button with smooth hover scale micro-interaction.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AnimatedToggleButton(onClick: () -> Unit, selected: Boolean, content: @Composable () -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        animationSpec = LtiLayoutAnimations.ButtonHoverScale,
        label = "AnimatedToggleButton_Scale",
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
    ) {
        GlassIconButton(
            onClick = onClick,
            selected = selected,
            content = content,
        )
    }
}
