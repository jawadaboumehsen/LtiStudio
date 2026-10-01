/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.actions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideWindowsCloseSmall
import org.ide.lti.core.designsystem.icon.ideWindowsMaximizeSmall
import org.ide.lti.core.designsystem.icon.ideWindowsMinimizeSmall
import org.ide.lti.core.designsystem.icon.ideWindowsRestoreSmall
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.WindowControlColors

/**
 * Window control actions interface for desktop platform integration.
 */
data class WindowControlActions(
    val onMinimize: () -> Unit = {},
    val onMaximize: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onDragWindow: () -> Unit = {},
    val onDragDelta: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    val isMaximized: () -> Boolean = { false },
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
    isMaximized: Boolean = LocalWindowControlActions.current.isMaximized(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowControlButton(
            icon = AppIcon.Painted(AppIcons.ideWindowsMinimizeSmall),
            contentDescription = "Minimize",
            onClick = onMinimize,
        )

        WindowControlButton(
            icon = AppIcon.Painted(
                if (isMaximized) AppIcons.ideWindowsRestoreSmall else AppIcons.ideWindowsMaximizeSmall,
            ),
            contentDescription = if (isMaximized) "Restore" else "Maximize",
            onClick = onMaximize,
        )

        WindowControlButton(
            icon = AppIcon.Painted(AppIcons.ideWindowsCloseSmall),
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
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCloseButton: Boolean = false,
) {
    var isHovered by remember { mutableStateOf(false) }

    val targetBackgroundColor = when {
        isCloseButton && isHovered -> WindowControlColors.CloseHover
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
        else -> Color.Transparent
    }

    val targetIconTint = when {
        isCloseButton && isHovered -> MaterialTheme.colorScheme.onSurface
        isHovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val backgroundColor by animateColorAsState(targetBackgroundColor, label = "WindowControlButton_Background")
    val iconTint by animateColorAsState(targetIconTint, label = "WindowControlButton_IconTint")

    val borderModifier = if (isHovered && !isCloseButton) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Hover),
            shape = GlassShapes.ExtraSmall,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(width = ComponentSize.WindowControlWidth, height = ComponentSize.WindowControlHeight)
            .clip(GlassShapes.ExtraSmall)
            .background(backgroundColor)
            .then(borderModifier)
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
        when (icon) {
            is AppIcon.Vector -> Icon(
                imageVector = icon.imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize.WindowControl),
                tint = iconTint,
            )
            is AppIcon.Painted -> Icon(
                painter = icon.painter(),
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize.WindowControl),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun WindowControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCloseButton: Boolean = false,
) = WindowControlButton(
    icon = AppIcon.Vector(icon),
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
    isCloseButton = isCloseButton,
)

/**
 * Window drag gesture detector modifier that coordinates with OS window dragging.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.windowDragGesture(
    onDrag: () -> Unit,
    onDragDelta: (dx: Float, dy: Float) -> Unit = { _, _ -> },
): Modifier = pointerInput(onDrag, onDragDelta) {
    awaitPointerEventScope {
        var pendingDrag = false
        var isDragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            event.changes.forEach { change ->
                if (!change.isConsumed) {
                    when (event.type) {
                        PointerEventType.Press -> {
                            if (event.button == PointerButton.Primary) {
                                pendingDrag = true
                                isDragging = false
                            }
                        }
                        PointerEventType.Move -> {
                            if (pendingDrag) {
                                onDrag()
                                pendingDrag = false
                                isDragging = true
                            }
                            if (isDragging) {
                                val delta = change.position - change.previousPosition
                                if (delta.x != 0f || delta.y != 0f) {
                                    onDragDelta(delta.x, delta.y)
                                }
                            }
                        }
                        PointerEventType.Release -> {
                            pendingDrag = false
                            isDragging = false
                        }
                    }
                }
            }
        }
    }
}
