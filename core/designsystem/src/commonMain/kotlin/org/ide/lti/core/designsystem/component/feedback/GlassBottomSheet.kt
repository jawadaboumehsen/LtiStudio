/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.feedback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * State for managing the bottom sheet visibility and animations.
 *
 * @param initialValue The initial state of the bottom sheet
 */
@Stable
class GlassBottomSheetState(initialValue: GlassBottomSheetValue = GlassBottomSheetValue.Collapsed) {
    /**
     * The current value of the bottom sheet.
     */
    var currentValue by mutableStateOf(initialValue)
        private set

    /**
     * The current offset of the bottom sheet in pixels (0 = fully expanded).
     */
    var offsetY by mutableFloatStateOf(0f)
        internal set

    /**
     * Whether the bottom sheet is currently visible (expanded or expanding).
     */
    val isVisible: Boolean
        get() = currentValue != GlassBottomSheetValue.Collapsed

    /**
     * Expand the bottom sheet with animation.
     */
    suspend fun expand() {
        currentValue = GlassBottomSheetValue.Expanded
    }

    /**
     * Collapse the bottom sheet with animation.
     */
    suspend fun collapse() {
        currentValue = GlassBottomSheetValue.Collapsed
    }

    /**
     * Toggle the bottom sheet state.
     */
    suspend fun toggle() {
        if (currentValue == GlassBottomSheetValue.Expanded) {
            collapse()
        } else {
            expand()
        }
    }
}

/**
 * Possible values for the bottom sheet state.
 */
sealed interface GlassBottomSheetValue {
    /**
     * The bottom sheet is collapsed (hidden).
     */
    data object Collapsed : GlassBottomSheetValue

    /**
     * The bottom sheet is partially expanded.
     */
    data object HalfExpanded : GlassBottomSheetValue

    /**
     * The bottom sheet is fully expanded (visible).
     */
    data object Expanded : GlassBottomSheetValue
}

/**
 * Handle style rendered at the top of [GlassBottomSheet].
 */
sealed interface SheetDragHandle {
    data object Default : SheetDragHandle
    data object None : SheetDragHandle
    data class Custom(val content: @Composable () -> Unit) : SheetDragHandle
}

/**
 * Remembers a [GlassBottomSheetState] across recompositions.
 */
@Composable
fun rememberGlassBottomSheetState(
    initialValue: GlassBottomSheetValue = GlassBottomSheetValue.Collapsed,
): GlassBottomSheetState = remember { GlassBottomSheetState(initialValue) }

/**
 * A bottom sheet with glass morphism effects and drag-to-dismiss functionality.
 *
 * The bottom sheet slides up from the bottom of the screen with smooth animations
 * and can be dismissed by dragging down. It features a glass background with
 * configurable blur and transparency effects.
 *
 * @param state The state object controlling the bottom sheet visibility
 * @param onDismissRequest Callback invoked when the user dismisses the sheet
 * @param modifier Modifier for the bottom sheet container
 * @param backdrop Optional shared backdrop for glass effects
 * @param preset Effect preset to use (defaults to GlassMorphism.Standard)
 * @param shape Shape of the bottom sheet
 * @param scrimColor Color of the scrim overlay when sheet is open
 * @param dismissThreshold Fraction of sheet height to trigger dismiss (0.0 - 1.0)
 * @param content The content to display in the bottom sheet
 */
@Composable
fun GlassBottomSheet(
    state: GlassBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: HazeShape = GlassShapes.HazeBottomSheet,
    scrimColor: Color = Color.Unspecified,
    dismissThreshold: Float = 0.3f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val resolvedScrimColor = if (scrimColor.isSpecified) scrimColor else MaterialTheme.colorScheme.scrim

    // Animation for slide in/out
    val targetOffsetY = if (state.currentValue == GlassBottomSheetValue.Expanded) 0f else 1f
    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "bottomSheetOffset",
    )

    // Scrim alpha animation
    val scrimAlpha by animateFloatAsState(
        targetValue = if (state.currentValue == GlassBottomSheetValue.Expanded) 1f else 0f,
        animationSpec = tween(MotionDuration.Standard),
        label = "scrimAlpha",
    )

    // Only render when visible or animating out
    if (state.currentValue != GlassBottomSheetValue.Collapsed || scrimAlpha > 0f) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolvedScrimColor)
                    .alpha(scrimAlpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    )
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, _ ->
                            // Intercept drag gestures on scrim
                        }
                    },
            )

            // Bottom sheet content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        // Apply slide animation
                        val offset = animatedOffsetY * size.height + state.offsetY
                        translationY = offset
                    }
                    .then(modifier),
            ) {
                GlassSurface(
                    shape = shape,
                    surfaceColor = MaterialTheme.colorScheme.surfaceContainer,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle),
                    borderColor = MaterialTheme.colorScheme.outline,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                var dragStartY = 0f
                                var initialOffsetY = 0f

                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        dragStartY = offset.y
                                        initialOffsetY = state.offsetY
                                    },
                                    onDragEnd = {
                                        // Check if drag threshold exceeded
                                        val sheetHeight = size.height.toFloat()
                                        val dragDistance = state.offsetY - initialOffsetY
                                        val dragFraction = dragDistance / sheetHeight

                                        if (dragFraction > dismissThreshold) {
                                            // Dismiss the sheet
                                            scope.launch {
                                                state.collapse()
                                                onDismissRequest()
                                            }
                                        } else {
                                            // Snap back to expanded
                                            scope.launch {
                                                // Animate back to 0
                                                animate(
                                                    initialValue = state.offsetY,
                                                    targetValue = 0f,
                                                    animationSpec = spring(),
                                                ) { value, _ ->
                                                    state.offsetY = value
                                                }
                                            }
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        // Only allow dragging down (positive offset)
                                        val newOffset = state.offsetY + dragAmount
                                        state.offsetY = maxOf(0f, newOffset)
                                    },
                                )
                            },
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.SmallMedium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(ComponentSize.DragHandleWidth)
                                    .height(ComponentSize.DragHandleHeight)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        shape = GlassShapes.Capsule,
                                    ),
                            )
                        }

                        // User content
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Convenience composable for a simple glass bottom sheet with header.
 *
 * @param visible Whether the bottom sheet is visible
 * @param onDismissRequest Callback when sheet is dismissed
 * @param title Optional title text for the header
 * @param headerContent Optional custom header content
 * @param backdrop Optional shared backdrop
 * @param preset Effect preset (defaults to GlassMorphism.Standard)
 * @param content The main content of the bottom sheet
 */
@Composable
fun GlassBottomSheetScaffold(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    headerContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberGlassBottomSheetState(
        initialValue = if (visible) {
            GlassBottomSheetValue.Expanded
        } else {
            GlassBottomSheetValue.Collapsed
        },
    )

    // Sync state with visible parameter
    LaunchedEffect(visible) {
        if (visible) {
            state.expand()
        } else {
            state.collapse()
        }
    }

    GlassBottomSheet(
        state = state,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        // Header
        if (title != null || headerContent != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    androidx.compose.material3.Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                headerContent?.invoke(this)
            }
        }

        // Content
        content()
    }
}

/**
 * Example usage of GlassBottomSheet with common patterns.
 */
@Composable
fun GlassBottomSheetExample(modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(Spacing.Medium)) {
            GlassButton(onClick = { showSheet = true }) {
                androidx.compose.material3.Text("Show Bottom Sheet")
            }
        }

        GlassBottomSheetScaffold(
            visible = showSheet,
            onDismissRequest = { showSheet = false },
            title = "Glass Bottom Sheet",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            ) {
                androidx.compose.material3.Text(
                    "This is a bottom sheet with glass morphism effects.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                androidx.compose.material3.Text(
                    "Drag down to dismiss, or tap outside.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(Spacing.Small))

                GlassPrimaryButton(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Text("Close")
                }
            }
        }
    }
}
