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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.glass.GlassSceneState
import org.ide.lti.core.designsystem.theme.glass.GlassStyles

/**
 * Type-safe action buttons configuration for [GlassDialog].
 */
sealed interface DialogActions {
    data class Single(
        val confirmText: String,
        val onConfirm: () -> Unit,
    ) : DialogActions

    data class ConfirmDismiss(
        val confirmText: String,
        val onConfirm: () -> Unit,
        val dismissText: String,
        val onDismiss: () -> Unit,
    ) : DialogActions

    data class Custom(
        val content: @Composable RowScope.() -> Unit,
    ) : DialogActions
}

/**
 * Glass dialog component with scrim and glass surface.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = GlassShapes.Dialog,
    backdrop: GlassSceneState? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val glassColors = GlassTheme.colors
    val style = GlassStyles.dialog.then { copy(shape = shape) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .glass(style, backdrop = backdrop),
        ) {
            CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
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
                            color = GlassTheme.colors.onGlass,
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
@Composable
fun GlassLoadingDialog(
    visibilityState: LoadingDialogState,
    modifier: Modifier = Modifier,
    backdrop: GlassSceneState? = null,
) {
    val style = GlassStyles.dialog.then { copy(shape = GlassShapes.ExtraLarge) }

    when (visibilityState) {
        is LoadingDialogState.Hidden -> Unit
        is LoadingDialogState.Shown -> {
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
                        .glass(style, backdrop = backdrop),
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
                            color = GlassTheme.colors.onGlass,
                            modifier = Modifier.padding(bottom = Spacing.Medium),
                        )
                        CircularProgressIndicator(
                            color = GlassTheme.colors.glassAccent,
                        )
                    }
                }
            }
        }
    }
}
