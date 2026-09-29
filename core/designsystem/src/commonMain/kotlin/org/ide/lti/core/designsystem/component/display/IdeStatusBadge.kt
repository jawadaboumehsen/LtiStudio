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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
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
    useDot: Boolean = false,
) {
    val style = when (severity) {
        IdeStatusSeverity.Ready -> StatusBadgeStyle(
            bgColor = GlassTheme.diagnosticColors.success.copy(alpha = AlphaTokens.Glow),
            fgColor = GlassTheme.diagnosticColors.success,
            borderColor = GlassTheme.diagnosticColors.success.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.CheckCircle,
        )
        IdeStatusSeverity.Warning -> StatusBadgeStyle(
            bgColor = GlassTheme.diagnosticColors.warning.copy(alpha = AlphaTokens.Subtle),
            fgColor = GlassTheme.diagnosticColors.warning,
            borderColor = GlassTheme.diagnosticColors.warning.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Warning,
        )
        IdeStatusSeverity.Blocking, IdeStatusSeverity.Failed -> StatusBadgeStyle(
            bgColor = GlassTheme.diagnosticColors.error.copy(alpha = AlphaTokens.Subtle),
            fgColor = GlassTheme.diagnosticColors.error,
            borderColor = GlassTheme.diagnosticColors.error.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Error,
        )
        IdeStatusSeverity.Running -> StatusBadgeStyle(
            bgColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Glow),
            fgColor = MaterialTheme.colorScheme.primary,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Muted),
            icon = Icons.Default.Refresh,
        )
        IdeStatusSeverity.Neutral -> StatusBadgeStyle(
            bgColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Hover),
            fgColor = MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            icon = Icons.Default.Info,
        )
    }

    Row(
        modifier = modifier
            .background(style.bgColor, GlassShapes.ShellBadge)
            .glassOutlineBorder(
                color = style.borderColor,
                width = GlassDimens.HairlineBorder,
                shape = GlassShapes.ShellBadge,
            )
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
