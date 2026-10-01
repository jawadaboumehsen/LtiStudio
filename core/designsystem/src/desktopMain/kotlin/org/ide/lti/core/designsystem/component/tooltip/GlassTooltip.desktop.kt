/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.tooltip

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@OptIn(ExperimentalFoundationApi::class)
@Composable
actual fun GlassTooltipArea(
    tooltipText: String,
    modifier: Modifier,
    delayMillis: Int,
    tooltipPlacement: GlassTooltipPlacement,
    content: @Composable () -> Unit,
) {
    if (tooltipText.isBlank()) {
        Box(modifier = modifier) { content() }
        return
    }

    val placement = resolveTooltipPlacement(tooltipPlacement)

    TooltipArea(
        tooltip = {
            GlassTooltipPopupContent(tooltipText = tooltipText)
        },
        modifier = modifier,
        delayMillis = delayMillis,
        tooltipPlacement = placement,
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun resolveTooltipPlacement(placement: GlassTooltipPlacement): TooltipPlacement = when (placement) {
    GlassTooltipPlacement.End -> TooltipPlacement.ComponentRect(
        anchor = Alignment.CenterEnd,
        alignment = Alignment.CenterEnd,
        offset = DpOffset(Spacing.Small, Spacing.None),
    )
    GlassTooltipPlacement.Start -> TooltipPlacement.ComponentRect(
        anchor = Alignment.CenterStart,
        alignment = Alignment.CenterStart,
        offset = DpOffset(-Spacing.Small, Spacing.None),
    )
    GlassTooltipPlacement.Above -> TooltipPlacement.ComponentRect(
        anchor = Alignment.TopCenter,
        alignment = Alignment.BottomCenter,
        offset = DpOffset(Spacing.None, -Spacing.ExtraSmall),
    )
    GlassTooltipPlacement.Below -> TooltipPlacement.ComponentRect(
        anchor = Alignment.BottomCenter,
        alignment = Alignment.TopCenter,
        offset = DpOffset(Spacing.None, Spacing.ExtraSmall),
    )
}

@Composable
internal fun GlassTooltipPopupContent(tooltipText: String, modifier: Modifier = Modifier) {
    val backgroundColor = if (GlassTheme.effectsEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Elevated)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val borderColor = if (GlassTheme.effectsEnabled) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Border)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Text(
        text = tooltipText,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = FontSize.BodySmall,
        fontFamily = GlassFontFamily.ide(),
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = GlassShapes.ShellPill,
            )
            .clip(GlassShapes.ShellPill)
            .glassOutlineBorder(
                color = borderColor,
                width = StrokeWidth.Hairline,
                shape = GlassShapes.ShellPill,
            )
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
    )
}
