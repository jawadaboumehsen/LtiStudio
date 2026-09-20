/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.theme.config.AppTheme

object SetupTokens {
    val SidebarWidthWide = 240.dp
    val SidebarWidthCompact = 192.dp
    val ContentMaxWidth = 1120.dp
    val GutterWide = 32.dp
    val GutterCompact = 20.dp
    val CompactBreakpoint = 1100.dp
    val MinWindowWidth = 900.dp
    val MinWindowHeight = 600.dp

    val SpaceSmall = 4.dp
    val SpaceMedium = 8.dp

    val TextTitle = 28.sp
    val TextSection = 18.sp
    val TextBody = 14.sp
    val TextMetadata = 12.sp

    val DurationFeedbackMs: Int
        @Composable
        @ReadOnlyComposable
        get() = if (GlassTheme.reducedMotion) 0 else 120

    val DurationFadeMs: Int
        @Composable
        @ReadOnlyComposable
        get() = if (GlassTheme.reducedMotion) 0 else 180

    val DurationSheetMs: Int
        @Composable
        @ReadOnlyComposable
        get() = if (GlassTheme.reducedMotion) 0 else 220

    fun contentSurfaceColor(theme: AppTheme): Color = when (theme) {
        AppTheme.Light -> Color(0xFFF0F0F0) // Porcelain
        AppTheme.Dark -> Color(0xFF2C2C2C) // Graphite
        AppTheme.Blue -> Color(0xFF1E3A8A) // Deep Blue
        is AppTheme.Custom -> Color(0xFF2C2C2C)
    }

    fun textNeutralColor(theme: AppTheme): Color = when (theme) {
        AppTheme.Light -> Color(0xFF424242) // Neutral text
        else -> Color(0xFFF5F5F5)
    }

    fun opaqueSurfaceColor(theme: AppTheme): Color = when (theme) {
        AppTheme.Light -> Color(0xFFFFFFFF)
        AppTheme.Dark -> Color(0xFF1E1E1E)
        AppTheme.Blue -> Color(0xFF0F1E4A)
        is AppTheme.Custom -> Color(0xFF1E1E1E)
    }

    val ContentSurfaceColor: Color
        @Composable
        @ReadOnlyComposable
        get() = contentSurfaceColor(GlassTheme.appTheme)

    val TextNeutralColor: Color
        @Composable
        @ReadOnlyComposable
        get() = textNeutralColor(GlassTheme.appTheme)

    val OpaqueSurfaceColor: Color
        @Composable
        @ReadOnlyComposable
        get() = opaqueSurfaceColor(GlassTheme.appTheme)

    val IsReducedTransparency: Boolean
        @Composable
        @ReadOnlyComposable
        get() = !GlassTheme.effectsEnabled

    val DiagnosticSuccess = Color(0xFF4CAF50)
    val DiagnosticWarning = Color(0xFFFF9800)
    val DiagnosticError = Color(0xFFF44336)
}
