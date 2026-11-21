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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
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
    val onDragEnd: () -> Unit = {},
    val isMaximized: () -> Boolean = { false },
)

val LocalWindowControlActions = compositionLocalOf { WindowControlActions() }

/** Window actions for the Studio toolbar. Each dot has a full-size pointer and keyboard target. */
@Composable
internal fun StudioWindowControls(actions: WindowControlActions) {
    val isMaximized = actions.isMaximized()
    Row(verticalAlignment = Alignment.CenterVertically) {
        StudioWindowButton(
            AppIcons.ideWindowsCloseSmall(),
            "Close",
            WindowControlColors.StudioClose,
            actions.onClose,
        )
        StudioWindowButton(
            AppIcons.ideWindowsMinimizeSmall(),
            "Minimize",
            WindowControlColors.StudioMinimize,
            actions.onMinimize,
        )
        StudioWindowButton(
            if (isMaximized) AppIcons.ideWindowsRestoreSmall() else AppIcons.ideWindowsMaximizeSmall(),
            if (isMaximized) "Restore" else "Maximize",
            WindowControlColors.StudioZoom,
            actions.onMaximize,
        )
    }
}

@Composable
private fun StudioWindowButton(icon: Painter, label: String, color: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focusRing = if (focused) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Focused,
            color = MaterialTheme.colorScheme.primary,
            shape = GlassShapes.Capsule,
        )
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(ComponentSize.StudioWindowButtonSize)
            .then(focusRing)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(IconSize.WindowControl)
                .background(if (pressed) color.copy(alpha = AlphaTokens.Prominent) else color, GlassShapes.Capsule),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = WindowControlColors.StudioSymbol,
                modifier = Modifier.size(IconSize.ExtraSmall).alpha(if (hovered || focused || pressed) 1f else 0f),
            )
        }
    }
}

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
 *
 * Supports immediate mouse press, long press / long click before dragging, drag deltas,
 * and optional double-click to toggle maximize/restore.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
@Composable
fun Modifier.windowDragGesture(
    onDrag: () -> Unit,
    onDragDelta: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
): Modifier {
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    return pointerInput(Unit) {
        var lastClickTime = 0L
        awaitPointerEventScope {
            while (true) {
                val downEvent = awaitPointerEvent(PointerEventPass.Main)
                if (!isPrimaryPress(downEvent)) continue

                val downChange = downEvent.changes.firstOrNull { !it.isConsumed } ?: continue
                val now = if (downChange.uptimeMillis > 0L) downChange.uptimeMillis else 0L

                // Handle double-click to maximize/restore
                if (currentOnDoubleClick != null && lastClickTime > 0L && (now - lastClickTime) in 0L..500L) {
                    currentOnDoubleClick?.invoke()
                    lastClickTime = 0L
                    downChange.consume()
                    continue
                }
                lastClickTime = if (now > 0L) now else 1L

                // Start window drag immediately on press so long-click coordinates are accurately captured
                currentOnDrag()

                try {
                    trackDrag(downChange.id, currentOnDragDelta)
                } finally {
                    currentOnDragEnd()
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun isPrimaryPress(event: PointerEvent): Boolean = event.type == PointerEventType.Press &&
    (event.button == null || event.button == PointerButton.Primary)

private suspend fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDragDelta: (dx: Float, dy: Float) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        val change = event.changes.firstOrNull { it.id == pointerId }
        if (change == null || event.type == PointerEventType.Release) {
            break
        }
        if (event.type == PointerEventType.Move) {
            val delta = change.position - change.previousPosition
            if (delta.x != 0f || delta.y != 0f) {
                onDragDelta(delta.x, delta.y)
            }
        }
    }
}
