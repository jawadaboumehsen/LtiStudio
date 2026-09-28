/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.hazeGlass
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.sceneHazeInput

/**
 * Type-safe action buttons configuration for [GlassDialog].
 */
sealed interface DialogActions {
    data class Single(val confirmText: String, val onConfirm: () -> Unit) : DialogActions

    data class ConfirmDismiss(
        val confirmText: String,
        val onConfirm: () -> Unit,
        val dismissText: String,
        val onDismiss: () -> Unit,
    ) : DialogActions

    data class Custom(val content: @Composable RowScope.() -> Unit) : DialogActions
}

/**
 * Glass dialog component with scrim and glass surface - built on Haze's real
 * `Modifier.hazeGlass`, overlay tier ([MaterialTheme.colorScheme.surfaceContainerHighest]).
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: HazeShape = GlassShapes.HazePanel,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val theme = GlassTheme.appTheme

    val dialogSurfaceModifier = if (GlassTheme.effectsEnabled && PlatformDialogPolicy.supportsCrossWindowBlur) {
        Modifier.hazeGlass(
            input = sceneHazeInput(GlassTheme.hazeState),
            style = remember(theme, shape) {
                org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                    theme = theme,
                ).then {
                    this.shape(shape)
                }
            },
        )
    } else {
        Modifier.background(colors.surfaceContainerHighest, shape = shape)
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .then(dialogSurfaceModifier)
                .clip(shape),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.DialogPadding),
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(Spacing.Medium))
                    }

                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        content()
                    }

                    Spacer(modifier = Modifier.height(Spacing.DialogPadding))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(modifier = Modifier.width(Spacing.Small))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * State for loading dialog.
 */
sealed class LoadingDialogState {
    data object Hidden : LoadingDialogState()
    data object Shown : LoadingDialogState()
}

/**
 * Glass loading dialog with circular progress indicator.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassLoadingDialog(visibilityState: LoadingDialogState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme

    when (visibilityState) {
        is LoadingDialogState.Hidden -> Unit
        is LoadingDialogState.Shown -> {
            val theme = GlassTheme.appTheme
            val loadingSurfaceModifier = if (GlassTheme.effectsEnabled &&
                PlatformDialogPolicy.supportsCrossWindowBlur
            ) {
                Modifier.hazeGlass(
                    input = sceneHazeInput(GlassTheme.hazeState),
                    style = remember(theme) {
                        org.ide.lti.core.designsystem.theme.GlassMaterialStyles.baseStyle(
                            theme = theme,
                        ).then {
                            shape(GlassShapes.HazePanel)
                        }
                    },
                )
            } else {
                Modifier.background(colors.surfaceContainerHighest, shape = GlassShapes.HazePanel)
            }

            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(Spacing.DialogPadding)
                        .then(loadingSurfaceModifier)
                        .clip(GlassShapes.HazePanel),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.DialogPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Loading..",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                            modifier = Modifier.padding(bottom = Spacing.Medium),
                        )
                        CircularProgressIndicator(
                            color = colors.primary,
                        )
                    }
                }
            }
        }
    }
}
