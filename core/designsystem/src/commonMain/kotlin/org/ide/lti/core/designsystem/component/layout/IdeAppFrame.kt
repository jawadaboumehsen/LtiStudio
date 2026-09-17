package org.ide.lti.core.designsystem.component.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens

/**
 * Stateless root layout frame for the professional LtiRom IDE shell.
 *
 * Implements the normative geometry:
 * - 40 dp persistent full-width top app bar
 * - 56 dp persistent pipeline-stage rail
 * - 230 dp context-sensitive navigator panel (or collapsible drawer below compact breakpoint)
 * - 32 dp breadcrumb bar
 * - Central content region owning its bounded vertical scroll
 * - 24 dp persistent full-width status bar
 */
@Composable
fun IdeAppFrame(
    topBar: @Composable () -> Unit,
    pipelineRail: @Composable () -> Unit,
    navigator: @Composable () -> Unit,
    breadcrumb: @Composable () -> Unit,
    content: @Composable () -> Unit,
    statusBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    isDrawerOpen: Boolean = false,
    onDismissDrawer: () -> Unit = {},
    drawerContent: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground),
    ) {
        val isCompact = maxWidth < IdeShellDimens.CompactBreakpoint

        Column(modifier = Modifier.fillMaxSize()) {
            // Full-width Top App Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IdeShellDimens.TopBarHeight)
                    .background(colors.topBarBackground),
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
                        .fillMaxHeight()
                        .background(colors.railBackground),
                ) {
                    pipelineRail()
                }
                VerticalDivider(
                    thickness = IdeShellDimens.HairlineBorder,
                    color = colors.border,
                )

                // Workspace Navigator (Inline if not compact)
                if (!isCompact) {
                    Box(
                        modifier = Modifier
                            .width(IdeShellDimens.WorkspaceNavigatorWidth)
                            .fillMaxHeight()
                            .background(colors.navigatorBackground),
                    ) {
                        navigator()
                    }
                    VerticalDivider(
                        thickness = IdeShellDimens.HairlineBorder,
                        color = colors.border,
                    )
                }

                // Central Content Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(colors.contentBackground),
                ) {
                    // Breadcrumb Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IdeShellDimens.BreadcrumbHeight)
                            .background(colors.contentBackground),
                    ) {
                        breadcrumb()
                    }
                    HorizontalDivider(
                        thickness = IdeShellDimens.HairlineBorder,
                        color = colors.border,
                    )

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
                    .height(IdeShellDimens.StatusBarHeight)
                    .background(colors.statusBarBackground),
            ) {
                statusBar()
            }
        }

        // Overlay Drawer for compact viewports
        if (isCompact) {
            val interactionSource = remember { MutableInteractionSource() }
            AnimatedVisibility(
                visible = isDrawerOpen,
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
                            onClick = onDismissDrawer,
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
                        drawerContent?.invoke() ?: navigator()
                    }
                }
            }
        }
    }
}
