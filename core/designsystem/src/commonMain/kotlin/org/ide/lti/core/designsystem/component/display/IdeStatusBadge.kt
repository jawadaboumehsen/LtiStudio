/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing

enum class IdeStatusSeverity {
    Ready,
    Warning,
    Blocking,
    Neutral,
    Running,
    Failed,
}

/**
 * Status badge communicating severity via text, iconography, and color.
 */
@Composable
fun IdeStatusBadge(
    label: String,
    severity: IdeStatusSeverity,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    useDot: Boolean = false,
) {
    val style = when (severity) {
        IdeStatusSeverity.Ready -> StatusBadgeStyle(
            bgColor = colors.accentGreen.copy(alpha = AlphaTokens.Glow),
            fgColor = colors.accentGreen,
            borderColor = colors.accentGreen.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.CheckCircle,
        )
        IdeStatusSeverity.Warning -> StatusBadgeStyle(
            bgColor = colors.warningAmberBg,
            fgColor = colors.warningAmber,
            borderColor = colors.warningAmber.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Warning,
        )
        IdeStatusSeverity.Blocking, IdeStatusSeverity.Failed -> StatusBadgeStyle(
            bgColor = colors.errorRedBg,
            fgColor = colors.errorRed,
            borderColor = colors.errorRed.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Error,
        )
        IdeStatusSeverity.Running -> StatusBadgeStyle(
            bgColor = colors.accentBlue.copy(alpha = AlphaTokens.Glow),
            fgColor = colors.accentBlue,
            borderColor = colors.accentBlue.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Refresh,
        )
        IdeStatusSeverity.Neutral -> StatusBadgeStyle(
            bgColor = colors.borderMuted.copy(alpha = AlphaTokens.Hover),
            fgColor = colors.textMuted,
            borderColor = colors.borderMuted,
            icon = Icons.Default.Info,
        )
    }

    Row(
        modifier = modifier
            .background(style.bgColor, GlassShapes.ShellBadge)
            .border(GlassDimens.HairlineBorder, style.borderColor, GlassShapes.ShellBadge)
            .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (useDot) {
            Box(
                modifier = Modifier
                    .size(IconSize.Indicator)
                    .background(style.fgColor, GlassShapes.Circle),
            )
        } else {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.fgColor,
                modifier = Modifier.size(GlassDimens.StatusBadgeIconSize),
            )
        }
        Text(
            text = if (useDot) label else label.uppercase(),
            color = style.fgColor,
            fontSize = FontSize.Micro,
            fontWeight = if (useDot) FontWeight.Medium else FontWeight.SemiBold,
            fontFamily = if (useDot) GlassFontFamily.ide() else GlassFontFamily.code(),
            letterSpacing = if (useDot) LetterSpacing.Normal else LetterSpacing.Wide,
        )
    }
}

private data class StatusBadgeStyle(
    val bgColor: Color,
    val fgColor: Color,
    val borderColor: Color,
    val icon: ImageVector,
)
