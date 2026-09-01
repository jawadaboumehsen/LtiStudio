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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.icon.AppIcon
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.theme.BackdropBlur
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LensRefraction
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Secondary Window Liquid Glass Top App Bar for auxiliary windows (such as Settings).
 *
 * Features:
 * - Liquid Glass effects with vibrancy, blur, lens refraction
 * - Window dragging via pointer input
 * - Title and optional icon display
 * - Desktop window controls (minimize, maximize, close)
 */
@Composable
fun GlassSecondaryTopBar(
    title: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    onMinimize: (() -> Unit)? = null,
    onMaximize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onDrag: (() -> Unit)? = null,
    backdrop: Backdrop? = null,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled
    val localControls = LocalWindowControlActions.current

    val effectiveMinimize = onMinimize ?: localControls.onMinimize
    val effectiveMaximize = onMaximize ?: localControls.onMaximize
    val effectiveClose = onClose ?: localControls.onClose
    val effectiveDrag = onDrag ?: localControls.onDragWindow

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.TopBarHeight)
            .windowDragGesture(effectiveDrag)
            .then(
                if (effectsEnabled && actualBackdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = actualBackdrop,
                        shape = { ContinuousRoundedRectangle(CornerRadius.Panel) },
                        effects = {
                            vibrancy()
                            blur(BackdropBlur.TopBar.toPx())
                            lens(LensRefraction.TopBar.height.toPx(), LensRefraction.TopBar.amount.toPx())
                        },
                        highlight = { Highlight.Default },
                        onDrawSurface = {
                            drawRect(glassColors.glassSurface)
                        }
                    )
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                }
            )
            .border(
                width = StrokeWidth.Hairline,
                color = glassColors.glassBorder,
                shape = ContinuousRoundedRectangle(CornerRadius.Panel)
            )
            .padding(horizontal = Spacing.MediumSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                if (icon != null) {
                    when (icon) {
                        is AppIcon.Vector -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = null,
                            tint = glassColors.glassAccent,
                            modifier = Modifier.size(IconSize.Large),
                        )
                        is AppIcon.Painted -> Icon(
                            painter = icon.painter(),
                            contentDescription = null,
                            tint = glassColors.glassAccent,
                            modifier = Modifier.size(IconSize.Large),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                )
            }

            DesktopWindowControls(
                onMinimize = effectiveMinimize,
                onMaximize = effectiveMaximize,
                onClose = effectiveClose,
            )
        }
    }
}
