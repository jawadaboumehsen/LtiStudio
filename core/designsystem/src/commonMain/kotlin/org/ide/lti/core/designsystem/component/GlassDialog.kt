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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Frosted (heavy) glass effect stack for modal surfaces, per the kmp-liquid-glass skill's
 * "Frosted dialog" recipe: brighten/saturate, heavy blur, deep lens refraction with depth.
 * Branches by theme — light mode needs more brightness lift and a heavier blur to read as a
 * bright frosted panel over content; dark mode needs neither.
 */
fun GlassDialogEffects(isLightTheme: Boolean): BackdropEffectScope.() -> Unit = {
    colorControls(
        brightness = if (isLightTheme) 0.2f else 0f,
        saturation = 1.5f
    )
    blur(if (isLightTheme) 16.dp.toPx() else 8.dp.toPx())
    lens(24.dp.toPx(), 48.dp.toPx(), depthEffect = true)
}

/**
 * Glass dialog component with scrim and glass surface.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Dialog),
    effects: BackdropEffectScope.() -> Unit = GlassDialogEffects(!isSystemInDarkTheme()),
    backdrop: Backdrop? = null,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
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
            highlight = { Highlight.Plain },
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
) {
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
                    shape = ContinuousRoundedRectangle(CornerRadius.ExtraLarge),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.DialogPadding),
                    effects = GlassDialogEffects(!isSystemInDarkTheme()),
                    highlight = { Highlight.Plain },
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
                            modifier = Modifier.padding(bottom = Spacing.Medium)
                        )
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}