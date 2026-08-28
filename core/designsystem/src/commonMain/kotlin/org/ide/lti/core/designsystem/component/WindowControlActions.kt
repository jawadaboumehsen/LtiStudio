/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.WindowControlColors

/**
 * Window control actions interface for desktop platform integration.
 */
data class WindowControlActions(
    val onMinimize: () -> Unit = {},
    val onMaximize: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onDragWindow: () -> Unit = {},
    val isMaximized: () -> Boolean = { false }
)

val LocalWindowControlActions = compositionLocalOf { WindowControlActions() }

/**
 * Desktop Window control buttons (minimize, maximize, close).
 */
@Composable
fun DesktopWindowControls(
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowControlButton(
            icon = Icons.Default.Remove,
            contentDescription = "Minimize",
            onClick = onMinimize,
        )

        WindowControlButton(
            icon = Icons.Default.CropSquare,
            contentDescription = "Maximize",
            onClick = onMaximize,
        )

        WindowControlButton(
            icon = Icons.Default.Close,
            contentDescription = "Close",
            onClick = onClose,
            isCloseButton = true,
        )
    }
}

/**
 * Individual Window Control Button with OS-style close hover.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCloseButton: Boolean = false,
) {
    var isHovered by remember { mutableStateOf(false) }
    val glassColors = GlassTheme.colors

    val backgroundColor = when {
        isCloseButton && isHovered -> WindowControlColors.CloseHover
        isHovered -> glassColors.glassSurfaceElevated
        else -> Color.Transparent
    }

    val iconTint = when {
        isCloseButton && isHovered -> glassColors.onGlass
        isHovered -> glassColors.onGlass
        else -> glassColors.onGlassMuted
    }

    Box(
        modifier = modifier
            .size(width = ComponentSize.WindowControlWidth, height = ComponentSize.WindowControlHeight)
            .clip(RoundedCornerShape(CornerRadius.ExtraSmall))
            .background(backgroundColor)
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.WindowControl),
            tint = iconTint,
        )
    }
}

/**
 * Window drag gesture detector modifier that coordinates with OS window dragging.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.windowDragGesture(onDrag: () -> Unit): Modifier =
    pointerInput(onDrag) {
        awaitPointerEventScope {
            var pendingDrag = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { change ->
                    if (!change.isConsumed) {
                        when (event.type) {
                            PointerEventType.Press -> {
                                pendingDrag = event.button == PointerButton.Primary
                            }
                            PointerEventType.Move -> {
                                if (pendingDrag) {
                                    onDrag()
                                    pendingDrag = false
                                }
                            }
                            PointerEventType.Release -> {
                                pendingDrag = false
                            }
                        }
                    }
                }
            }
        }
    }

