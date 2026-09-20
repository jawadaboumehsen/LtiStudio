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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.Shadow
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Frosted glass effect stack for modal surfaces.
 * Delegated to canonical [GlassEffects.dialog].
 */
fun glassDialogEffects(isLightTheme: Boolean): BackdropEffectScope.() -> Unit = GlassEffects.dialog(isLightTheme)

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
    effects: BackdropEffectScope.() -> Unit = GlassEffects.dialog(!GlassTheme.isDark),
    backdrop: Backdrop? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    val shadowColor = GlassTheme.colors.glassShadow

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        GlassSurface(
            modifier = modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            shape = shape,
            effects = effects,
            backdrop = backdrop,
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = Elevation.Modal, color = shadowColor) },
        ) {
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
    backdrop: Backdrop? = null,
) {
    val shadowColor = GlassTheme.colors.glassShadow

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
                GlassSurface(
                    shape = GlassShapes.ExtraLarge,
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(Spacing.DialogPadding),
                    effects = glassDialogEffects(MaterialTheme.colorScheme.surface.luminance() > 0.5f),
                    backdrop = backdrop,
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = Elevation.Modal, color = shadowColor) },
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
