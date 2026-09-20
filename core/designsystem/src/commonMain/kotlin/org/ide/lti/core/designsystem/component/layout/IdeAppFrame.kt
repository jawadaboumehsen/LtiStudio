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
import androidx.compose.foundation.border
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.LocalGlassBackdrop
import org.ide.lti.core.designsystem.theme.config.AppTheme

/**
 * Stateless root layout frame for the professional LtiRom IDE shell.
 *
 * Implements the normative geometry:
 * - 56 dp persistent full-width top app bar
 * - 150 dp persistent pipeline-stage rail
 * - 215 dp context-sensitive navigator panel (or collapsible drawer below compact breakpoint)
 * - 32 dp breadcrumb bar
 * - Central content region owning its bounded vertical scroll inside an inset glass panel
 * - 32 dp persistent full-width status bar
 */
@Composable
fun IdeAppFrame(
    topBar: @Composable () -> Unit,
    pipelineRail: @Composable () -> Unit,
    navigator: (@Composable () -> Unit)? = null,
    breadcrumb: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    statusBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    isDrawerOpen: Boolean = false,
    onDismissDrawer: () -> Unit = {},
    drawerContent: (@Composable () -> Unit)? = null,
) {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val ambientBackdrop = (GlassTheme.backdrop as? LayerBackdrop)
    val resolvedBackdrop = ambientBackdrop ?: rememberLayerBackdrop()

    val density = LocalDensity.current
    var isCompact by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalGlassBackdrop provides resolvedBackdrop) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    val widthDp = with(density) { size.width.toDp() }
                    isCompact = widthDp < IdeShellDimens.CompactBreakpoint
                }
                .background(colors.appBackground),
        ) {
            if (ambientBackdrop == null) {
                if (isBlueGlass) {
                    StudioGlassBackdrop(backdrop = resolvedBackdrop)
                } else {
                    GlassBackdrop(backdrop = resolvedBackdrop)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Full-width Top App Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IdeShellDimens.TopBarHeight),
                ) {
                    topBar()
                }
                HorizontalDivider(
                    thickness = IdeShellDimens.HairlineBorder,
                    color = colors.border,
                )

                // Middle Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // Persistent Pipeline Rail
                    Box(
                        modifier = Modifier
                            .width(IdeShellDimens.PipelineRailWidth)
                            .fillMaxHeight(),
                    ) {
                        pipelineRail()
                    }
                    VerticalDivider(
                        thickness = IdeShellDimens.HairlineBorder,
                        color = colors.border,
                    )

                    // Workspace Navigator (Inline if not compact and present)
                    if (navigator != null && !isCompact) {
                        Box(
                            modifier = Modifier
                                .width(IdeShellDimens.WorkspaceNavigatorWidth)
                                .fillMaxHeight(),
                        ) {
                            navigator()
                        }
                        VerticalDivider(
                            thickness = IdeShellDimens.HairlineBorder,
                            color = colors.border,
                        )
                    }

                    // Inset Central Content Column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(IdeShellDimens.ContentInset)
                            .clip(IdeShellShapes.Panel)
                            .background(colors.contentPanel)
                            .border(
                                width = IdeShellDimens.HairlineBorder,
                                color = colors.contentPanelBorder,
                                shape = IdeShellShapes.Panel,
                            ),
                    ) {
                        // Breadcrumb Bar
                        if (breadcrumb != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IdeShellDimens.BreadcrumbHeight),
                            ) {
                                breadcrumb()
                            }
                            HorizontalDivider(
                                thickness = IdeShellDimens.HairlineBorder,
                                color = colors.borderMuted,
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
                }

                // Bottom Status Bar
                HorizontalDivider(
                    thickness = IdeShellDimens.HairlineBorder,
                    color = colors.border,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IdeShellDimens.StatusBarHeight),
                ) {
                    statusBar()
                }
            }

            // Overlay Drawer for compact viewports
            if (isCompact && (navigator != null || drawerContent != null)) {
                IdeCompactDrawer(
                    isOpen = isDrawerOpen,
                    onDismiss = onDismissDrawer,
                    colors = colors,
                    content = { drawerContent?.invoke() ?: navigator?.invoke() },
                )
            }
        }
    }
}

@Composable
private fun IdeCompactDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    colors: IdeShellColorScheme,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = isOpen,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground.copy(alpha = AlphaTokens.Prominent))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(IdeShellDimens.WorkspaceNavigatorWidth)
                    .fillMaxHeight()
                    .background(colors.navigatorBackground)
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
