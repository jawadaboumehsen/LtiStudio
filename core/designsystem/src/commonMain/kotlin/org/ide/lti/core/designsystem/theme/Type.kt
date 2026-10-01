/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.inter_bold
import org.ide.lti.core.designsystem.generated.resources.inter_medium
import org.ide.lti.core.designsystem.generated.resources.inter_regular
import org.ide.lti.core.designsystem.generated.resources.inter_semi_bold
import org.ide.lti.core.designsystem.generated.resources.jetbrains_mono_bold
import org.ide.lti.core.designsystem.generated.resources.jetbrains_mono_medium
import org.ide.lti.core.designsystem.generated.resources.jetbrains_mono_regular
import org.ide.lti.core.designsystem.generated.resources.outfit_black
import org.ide.lti.core.designsystem.generated.resources.outfit_bold
import org.ide.lti.core.designsystem.generated.resources.outfit_extra_bold
import org.ide.lti.core.designsystem.generated.resources.outfit_extra_light
import org.ide.lti.core.designsystem.generated.resources.outfit_light
import org.ide.lti.core.designsystem.generated.resources.outfit_medium
import org.ide.lti.core.designsystem.generated.resources.outfit_regular
import org.ide.lti.core.designsystem.generated.resources.outfit_semi_bold
import org.ide.lti.core.designsystem.generated.resources.outfit_thin
import org.jetbrains.compose.resources.Font

@Composable
fun ideFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semi_bold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

@Composable
private fun fontFamily(): FontFamily = FontFamily(
    Font(Res.font.outfit_black, FontWeight.Black),
    Font(Res.font.outfit_bold, FontWeight.Bold),
    Font(Res.font.outfit_semi_bold, FontWeight.SemiBold),
    Font(Res.font.outfit_medium, FontWeight.Medium),
    Font(Res.font.outfit_regular, FontWeight.Normal),
    Font(Res.font.outfit_light, FontWeight.Light),
    Font(Res.font.outfit_thin, FontWeight.Thin),
    Font(Res.font.outfit_extra_light, FontWeight.ExtraLight),
    Font(Res.font.outfit_extra_bold, FontWeight.ExtraBold),
)

/**
 * Returns the bundled JetBrains Mono [FontFamily] configured with Regular, Medium, and Bold weights.
 */
@Composable
fun codeFontFamily(): FontFamily = FontFamily(
    Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(Res.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
)

// Set of Material typography styles to start with
@Composable
internal fun appTypography(): Typography {
    val defaultFamily = fontFamily()
    return Typography(
        displayLarge = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            letterSpacing = (-0.25).sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Bottom,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        titleLarge = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.1.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        // Default text style
        bodyLarge = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.None,
            ),
        ),
        bodyMedium = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        // Used for Button
        labelLarge = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        // Used for Navigation items
        labelMedium = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.LastLineBottom,
            ),
        ),
        // Used for Tag
        labelSmall = TextStyle(
            fontFamily = defaultFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.LastLineBottom,
            ),
        ),
    )
}

/**
 * Canonical font size tokens for the design system.
 */
object FontSize {
    val DisplayLarge = 57.sp
    val DisplayMedium = 45.sp
    val DisplaySmall = 36.sp
    val HeadlineLarge = 34.sp
    val HeadlineMedium = 32.sp
    val HeadlineSmall = 26.sp
    val TitleLarge = 24.sp
    val TitleMedium = 20.sp
    val TitleSmall = 14.sp
    val BodyLarge = 16.sp
    val BodyMedium = 14.sp
    val BodySmall = 12.sp
    val LabelLarge = 16.sp
    val LabelMedium = 12.sp
    val LabelSmall = 10.sp

    // Sub-scale & precision micro typography for IDE status bars & chips
    val Micro = 10.sp
    val MicroAlt = 10.5.sp
    val CodeCompact = 11.sp
    val CodeMedium = 13.sp
    val Chip = 11.5.sp
    val Code = 14.sp
}

/**
 * Canonical line height tokens for the design system.
 */
object LineHeight {
    val DisplayLarge = 64.sp
    val DisplayMedium = 52.sp
    val DisplaySmall = 44.sp
    val HeadlineLarge = 42.sp
    val HeadlineMedium = 40.sp
    val HeadlineSmall = 34.sp
    val TitleLarge = 32.sp
    val TitleMedium = 28.sp
    val TitleSmall = 20.sp
    val BodyLarge = 24.sp
    val BodyMedium = 20.sp
    val BodySmall = 16.sp
    val LabelLarge = 20.sp
    val LabelMedium = 16.sp
    val LabelSmall = 14.sp
    val Code = 20.sp
}

/**
 * Canonical letter spacing tokens for the design system.
 */
object LetterSpacing {
    val Tight = (-0.25).sp
    val None = 0.sp
    val Snug = 0.1.sp
    val Normal = 0.25.sp
    val Loose = 0.4.sp
    val Wide = 0.5.sp
    val TrackingHeader = 1.sp
    val CodeWide = 1.2.sp
    val Widest = 1.2.sp
}

/**
 * Accessors for design system font families.
 */
object GlassFontFamily {
    /** Brand display & UI font family (Outfit). */
    @Composable
    fun default(): FontFamily = fontFamily()

    /** High-legibility monospaced font family for code, terminals, and hex (JetBrains Mono). */
    @Composable
    fun code(): FontFamily = codeFontFamily()

    /** Professional matte IDE desktop UI font family (Inter). */
    @Composable
    fun ide(): FontFamily = ideFontFamily()
}
