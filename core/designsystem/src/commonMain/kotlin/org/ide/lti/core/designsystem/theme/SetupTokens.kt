package org.ide.lti.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.theme.config.AppTheme

object SetupTokens {
    val SidebarWidthWide = 224.dp
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

    val ContentSurfaceColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (GlassTheme.appTheme) {
            AppTheme.Light -> Color(0xFFF0F0F0) // Porcelain
            AppTheme.Dark -> Color(0xFF2C2C2C)  // Graphite
            AppTheme.Blue -> Color(0xFF1E3A8A)  // Deep Blue
            is AppTheme.Custom -> Color(0xFF2C2C2C)
        }

    val TextNeutralColor: Color
        @Composable
        @ReadOnlyComposable
        get() = when (GlassTheme.appTheme) {
            AppTheme.Light -> Color(0xFF424242) // Neutral text
            else -> Color(0xFFF5F5F5)
        }
        
    val DiagnosticSuccess = Color(0xFF4CAF50)
    val DiagnosticWarning = Color(0xFFFF9800)
    val DiagnosticError = Color(0xFFF44336)
}
