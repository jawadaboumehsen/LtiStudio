/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme

/**
 * CompositionLocal indicating whether a composable is hosted inside an [IdeAppFrame].
 * When true, child chrome components (top app bar, navigator panel, pipeline rail,
 * status bar, etc.) render their content transparently into the frame's single,
 * continuous glass surface rather than creating disjoint, redundant glass slices.
 */
val LocalInIdeAppFrame = compositionLocalOf { false }

/**
 * Stateless root layout frame for the professional LtiRom IDE shell.
 *
 * Implements the normative geometry:
 * - 44 dp persistent full-width top app bar
 * - 150 dp optional pipeline-stage rail (screens with a stage/pipeline concept, e.g. Setup, Studio)
 * - 215 dp context-sensitive navigator panel - the screen's own tab navigation when there is no
 *   separate pipeline rail (or collapsible drawer below compact breakpoint)
 * - 44 dp context-sensitive right panel (inspector/properties)
 * - 32 dp breadcrumb bar
 * - Central content region owning its bounded vertical scroll inside an inset glass panel
 * - 24 dp persistent full-width status bar
 *
 * The top bar, pipeline rail, navigator, right panel, and status bar all sample the same
 * shared [GlassTheme.hazeState] with the identical [GlassShapes.HazeFlat] shape and
 * `surfaceContainer` tint (see [IdeTopAppBar]/[IdePipelineRail]/[IdeNavigatorPanel]/
 * [IdeStatusBar]), so flush edges render as one continuous glass frame around the inset
 * content panel rather than as separate disjoint pieces.
 */
@Composable
fun IdeAppFrame(
    topBar: @Composable () -> Unit,
    pipelineRail: (@Composable () -> Unit)? = null,
    navigator: (@Composable () -> Unit)? = null,
    rightPanel: (@Composable () -> Unit)? = null,
    breadcrumb: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    statusBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isDrawerOpen: Boolean = false,
    onDismissDrawer: () -> Unit = {},
    drawerContent: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    var isCompact by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false)
            .onSizeChanged { size ->
                val widthDp = with(density) { size.width.toDp() }
                isCompact = widthDp < GlassDimens.CompactBreakpoint
            },
    ) {
        CompositionLocalProvider(LocalInIdeAppFrame provides true) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Full-width Top App Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GlassDimens.TopBarHeight),
                ) {
                    topBar()
                }

                // Middle Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // Persistent Pipeline Rail (optional - only screens with a stage/pipeline
                    // concept pass one; others rely on navigator alone as the left panel)
                    if (pipelineRail != null) {
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight(),
                        ) {
                            pipelineRail()
                        }
                    }

                    // Workspace Navigator (Inline if not compact and present)
                    if (navigator != null && !isCompact) {
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight(),
                        ) {
                            navigator()
                        }
                    }

                    val centralColumnBackground = if (GlassTheme.effectsEnabled) {
                        MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.Half)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    // Inset Central Content Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(GlassDimens.ContentInset)
                            .clip(GlassShapes.ShellPanel)
                            .background(centralColumnBackground)
                            .glassOutlineBorder(
                                width = GlassDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.outline,
                                shape = GlassShapes.ShellPanel,
                            ),
                    ) {
                        // Breadcrumb Bar
                        if (breadcrumb != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(GlassDimens.BreadcrumbHeight),
                            ) {
                                breadcrumb()
                            }
                            HorizontalDivider(
                                thickness = GlassDimens.HairlineBorder,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }

                        // Bounded Content Region
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            content()
                        }
                    }

                    // Context-Sensitive Right Panel (Inspector/Properties)
                    if (rightPanel != null && !isCompact) {
                        Box(
                            modifier = Modifier
                                .wrapContentWidth()
                                .fillMaxHeight(),
                        ) {
                            rightPanel()
                        }
                    }
                }

                // Bottom Status Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GlassDimens.StatusBarHeight),
                ) {
                    statusBar()
                }
            }
        }

        // Overlay Drawer for compact viewports
        if (isCompact && (navigator != null || drawerContent != null)) {
            IdeCompactDrawer(
                isOpen = isDrawerOpen,
                onDismiss = onDismissDrawer,
                content = { drawerContent?.invoke() ?: navigator?.invoke() },
            )
        }
    }
}

@Composable
private fun IdeCompactDrawer(isOpen: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = AlphaTokens.Prominent))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(GlassDimens.WorkspaceNavigatorWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                content()
            }
        }
    }
}
