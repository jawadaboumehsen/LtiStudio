/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.devtools.*

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.utils.InteractiveHighlight
import org.ide.lti.core.designsystem.utils.applyInteractiveHighlightTransform
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GlassIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    icon: @Composable () -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val finalContentColor = if (contentColor.isSpecified) contentColor else glassColors.onGlass
    var isHovered by remember { mutableStateOf(false) }

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope,
        )
    }

    Box(
        modifier = modifier
            .then(
                if (effectsEnabled && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { GlassShapes.Capsule },
                        effects = GlassEffects.Button,
                        highlight = { if (isHovered) Highlight.Ambient else Highlight.Plain },
                        layerBlock = if (isInteractive) {
                            {
                                applyInteractiveHighlightTransform(interactiveHighlight)
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            drawIconSurface(
                                surfaceColor = surfaceColor,
                                glassSurfaceColor = glassColors.glassSurface,
                                tint = tint,
                            )
                        },
                    )
                } else {
                    Modifier
                        .clip(GlassShapes.Capsule)
                        .drawBehind {
                            drawIconSurface(
                                surfaceColor = surfaceColor,
                                glassSurfaceColor = glassColors.glassSurface,
                                tint = tint,
                            )
                        }
                },
            )
            .glassOutlineBorder(
                color = glassColors.glassBorder,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.Capsule,
            )
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides finalContentColor) {
            icon()
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIconSurface(
    surfaceColor: Color,
    glassSurfaceColor: Color,
    tint: Color,
) {
    drawRect(if (surfaceColor.isSpecified) surfaceColor else glassSurfaceColor)
    if (tint.isSpecified && tint != Color.Transparent) {
        drawRect(tint, blendMode = BlendMode.Hue)
        drawRect(tint.copy(alpha = tint.alpha * AlphaTokens.Half))
    }
}

@Preview
@Composable
fun GlassIconPreview() {
    val backdrop = rememberLayerBackdrop()
    GlassIcon(
        onClick = {},
        backdrop = backdrop,
        icon = {
            Icon(
                painter = AppIcons.FolderPainterResource(),
                contentDescription = null,
            )
        },
    )
}
