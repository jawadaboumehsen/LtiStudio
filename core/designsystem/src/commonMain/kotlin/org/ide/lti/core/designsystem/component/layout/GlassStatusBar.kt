/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.highlight.Highlight
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.display.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Modern IDE Liquid Glass Status Bar Primitive.
 *
 * Generic status bar scaffold providing standard glass chrome, border, and content slot alignment.
 * Domain-specific indicators (Git branch, Ln/Col caret metrics, encoding, file types) are projected
 * via the [leadingContent], [centerContent], and [trailingContent] slots.
 *
 * @param modifier Modifier applied to the status bar container.
 * @param leadingContent Optional content slot on the start/left edge.
 * @param centerContent Optional content slot centered in the status bar.
 * @param trailingContent Optional content slot on the end/right edge.
 * @param backdrop Shared glass backdrop for refraction effects.
 * @param drawOwnChrome Whether to render internal glass background and borders (false when embedded).
 */
@Composable
fun GlassStatusBar(
    modifier: Modifier = Modifier,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    centerContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    backdrop: Backdrop? = null,
    drawOwnChrome: Boolean = true,
) {
    val actualBackdrop = backdrop ?: GlassTheme.backdrop
    val glassColors = GlassTheme.colors
    val effectsEnabled = GlassTheme.effectsEnabled

    val backgroundModifier = if (drawOwnChrome) {
        if (effectsEnabled && actualBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = actualBackdrop,
                shape = { GlassShapes.Panel },
                effects = GlassEffects.StatusBar,
                highlight = { Highlight.Default },
                onDrawSurface = {
                    drawRect(glassColors.glassSurface)
                },
            )
        } else {
            Modifier.background(glassColors.glassSurface.copy(alpha = AlphaTokens.Medium))
        }
    } else {
        Modifier
    }

    val borderModifier = if (drawOwnChrome) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = glassColors.glassBorder,
            shape = GlassShapes.Panel,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.StatusBarHeightAlt)
            .then(backgroundModifier)
            .then(borderModifier)
            .padding(horizontal = Spacing.SmallMedium),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // LEADING SECTION
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.MediumSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingContent?.invoke(this)
                }

                // CENTER SECTION
                if (centerContent != null) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        centerContent.invoke(this)
                    }
                }

                // TRAILING SECTION
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Fourteen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trailingContent?.invoke(this)
                }
            }
        }
    }
}
