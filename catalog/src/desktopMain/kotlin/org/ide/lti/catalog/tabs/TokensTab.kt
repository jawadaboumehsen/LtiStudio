/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ColorContrast
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.DarkGlassColorScheme
import org.ide.lti.core.designsystem.theme.Elevation
import org.ide.lti.core.designsystem.theme.GlassColorScheme
import org.ide.lti.core.designsystem.theme.GlassLayoutColors
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.LightGlassColorScheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.MotionEasing
import org.ide.lti.core.designsystem.theme.MotionSpring
import org.ide.lti.core.designsystem.theme.MotionThreshold
import org.ide.lti.core.designsystem.theme.PasswordStrengthColors
import org.ide.lti.core.designsystem.theme.PerformanceMonitorColors
import org.ide.lti.core.designsystem.theme.PreviewSampleColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.ToggleColors
import org.ide.lti.core.designsystem.theme.WindowControlColors

@Composable
fun TokensTab() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Active Theme GlassColorScheme Swatches
        SectionHeader("1. Active Glass Color Scheme & WCAG Contrast Matrix")
        ColorSchemeGrid(GlassTheme.colors)

        // Section 2: Dark vs Light Color Scheme Matrix
        SectionHeader("2. Dark vs Light Color Scheme Comparison")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                    .background(DarkGlassColorScheme.glassSurface)
                    .padding(Spacing.Medium)
            ) {
                Text(
                    "DarkGlassColorScheme",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGlassColorScheme.onGlass
                )
                Spacer(Modifier.height(Spacing.Small))
                ColorSchemeGrid(DarkGlassColorScheme)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                    .background(LightGlassColorScheme.glassSurface)
                    .padding(Spacing.Medium)
            ) {
                Text(
                    "LightGlassColorScheme",
                    style = MaterialTheme.typography.titleMedium,
                    color = LightGlassColorScheme.onGlass
                )
                Spacer(Modifier.height(Spacing.Small))
                ColorSchemeGrid(LightGlassColorScheme)
            }
        }

        // Section 3: Specialized Color Tokens
        SectionHeader("3. Specialized Token Objects")
        SpecializedColorsGrid()

        // Section 4: Alpha & Opacity Scale
        SectionHeader("4. Centralized Alpha & Opacity Tokens (AlphaTokens)")
        AlphaTokensGrid()

        // Section 5: Dimensions & Metrics
        SectionHeader("5. Centralized Dimension Metrics (Dimens.kt)")
        DimensionsGrid()

        // Section 6: Motion & Animation Tokens
        SectionHeader("6. Centralized Motion & Animation Tokens (Motion.kt)")
        MotionTokensGrid()

        // Section 7: Typography Scale
        SectionHeader("7. Centralized Typography Scale (Type.kt)")
        TypographyScaleGrid()
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = GlassTheme.colors.onGlass,
        modifier = Modifier.padding(bottom = Spacing.Small)
    )
}

@Composable
private fun ColorSchemeGrid(scheme: GlassColorScheme) {
    val tokens = listOf(
        "glassSurface" to scheme.glassSurface,
        "glassSurfaceElevated" to scheme.glassSurfaceElevated,
        "glassTint" to scheme.glassTint,
        "glassTintSecondary" to scheme.glassTintSecondary,
        "glassBorder" to scheme.glassBorder,
        "glassBorderHighlight" to scheme.glassBorderHighlight,
        "onGlass" to scheme.onGlass,
        "onGlassSecondary" to scheme.onGlassSecondary,
        "onGlassMuted" to scheme.onGlassMuted,
        "glassHighlight" to scheme.glassHighlight,
        "glassShadow" to scheme.glassShadow,
        "glassAccent" to scheme.glassAccent,
        "focusRing" to scheme.focusRing,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        tokens.chunked(4).forEach { rowTokens ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                rowTokens.forEach { (name, color) ->
                    ColorSwatchCard(
                        name = name,
                        color = color,
                        surfaceColor = scheme.glassSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space in incomplete rows
                repeat(4 - rowTokens.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchCard(
    name: String,
    color: Color,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val hex = ColorContrast.toHexString(color)
    val contrast = ColorContrast.contrastRatio(color, surfaceColor)
    val contrastLabel = "${"%.2f".format(contrast)}:1"

    val wcagBadge = when {
        contrast >= 7.0 -> "AAA"
        contrast >= 4.5 -> "AA"
        contrast >= 3.0 -> "UI/AA Large"
        else -> "Sub-AA"
    }

    Column(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(CornerRadius.Small))
            .background(GlassTheme.colors.glassSurfaceElevated)
            .border(
                width = StrokeWidth.Hairline,
                color = GlassTheme.colors.glassBorder,
                shape = ContinuousRoundedRectangle(CornerRadius.Small)
            )
            .padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ComponentSize.ButtonMinHeight)
                .clip(ContinuousRoundedRectangle(CornerRadius.ExtraSmall))
                .background(color)
                .border(
                    width = StrokeWidth.Hairline,
                    color = GlassTheme.colors.glassBorder,
                    shape = ContinuousRoundedRectangle(CornerRadius.ExtraSmall)
                )
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTheme.colors.onGlass
        )
        Text(
            text = hex,
            style = MaterialTheme.typography.labelSmall,
            color = GlassTheme.colors.onGlassSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = contrastLabel,
                style = MaterialTheme.typography.labelSmall,
                color = GlassTheme.colors.onGlassMuted
            )
            Box(
                modifier = Modifier
                    .clip(ContinuousCapsule)
                    .background(
                        if (contrast >= 3.0) GlassTheme.colors.glassAccent.copy(alpha = AlphaTokens.Hover)
                        else GlassTheme.colors.glassShadow.copy(alpha = AlphaTokens.Faint)
                    )
                    .padding(horizontal = Spacing.Five, vertical = Spacing.Hairline)
            ) {
                Text(
                    text = wcagBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (contrast >= 3.0) GlassTheme.colors.glassAccent else GlassTheme.colors.onGlassMuted
                )
            }
        }
    }
}

@Composable
private fun SpecializedColorsGrid() {
    val items = listOf(
        "WindowControl.CloseHover" to WindowControlColors.CloseHover,
        "Brand.LogoGradientEnd" to BrandColors.LogoGradientEnd,
        "Perf.Good" to PerformanceMonitorColors.Good,
        "Perf.Warning" to PerformanceMonitorColors.Warning,
        "Perf.Degraded" to PerformanceMonitorColors.Degraded,
        "Perf.Bad" to PerformanceMonitorColors.Bad,
        "Toggle.ActiveLight" to ToggleColors.ActiveLight,
        "Toggle.ActiveDark" to ToggleColors.ActiveDark,
        "Preview.Blue" to PreviewSampleColors.Blue,
        "Preview.Purple" to PreviewSampleColors.Purple,
        "Preview.Orange" to PreviewSampleColors.Orange,
        "Preview.Green" to PreviewSampleColors.Green,
        "GlassLayout.PanelShadow" to GlassLayoutColors.PanelShadow,
        "Password.Weak" to PasswordStrengthColors.Weak,
        "Password.Strong" to PasswordStrengthColors.Strong,
        "Password.VeryStrong" to PasswordStrengthColors.VeryStrong,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        items.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                rowItems.forEach { (name, color) ->
                    SpecializedColorCard(name, color, Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpecializedColorCard(name: String, color: Color, modifier: Modifier = Modifier) {
    val hex = ColorContrast.toHexString(color)
    Row(
        modifier = modifier
            .clip(ContinuousRoundedRectangle(CornerRadius.Small))
            .background(GlassTheme.colors.glassSurfaceElevated)
            .border(
                width = StrokeWidth.Hairline,
                color = GlassTheme.colors.glassBorder,
                shape = ContinuousRoundedRectangle(CornerRadius.Small)
            )
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.SegmentedButtonSize)
                .clip(ContinuousCapsule)
                .background(color)
        )
        Column {
            Text(name, style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlass)
            Text(hex, style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlassMuted)
        }
    }
}

@Composable
private fun AlphaTokensGrid() {
    val alphas = listOf(
        "UltraFaint" to AlphaTokens.UltraFaint,
        "Faint" to AlphaTokens.Faint,
        "Subtle" to AlphaTokens.Subtle,
        "Glow" to AlphaTokens.Glow,
        "Hover" to AlphaTokens.Hover,
        "Faded" to AlphaTokens.Faded,
        "Muted / Scrim" to AlphaTokens.Muted,
        "Ambient" to AlphaTokens.Ambient,
        "Medium / Disabled" to AlphaTokens.Medium,
        "Soft" to AlphaTokens.Soft,
        "Half / Border" to AlphaTokens.Half,
        "Strong" to AlphaTokens.Strong,
        "Prominent" to AlphaTokens.Prominent,
        "IconTint" to AlphaTokens.IconTint,
    )

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        alphas.chunked(2).forEach { rowAlphas ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                rowAlphas.forEach { (name, alphaValue) ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ContinuousRoundedRectangle(CornerRadius.Small))
                            .background(GlassTheme.colors.glassSurfaceElevated)
                            .padding(Spacing.SmallMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = GlassTheme.colors.onGlass)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(ComponentSize.ButtonMinWidth)
                                    .height(ComponentSize.ProgressBarHeight * 3)
                                    .clip(ContinuousCapsule)
                                    .background(GlassTheme.colors.glassAccent.copy(alpha = alphaValue))
                            )
                            Text(
                                text = "${(alphaValue * 100).toInt()}% ($alphaValue)",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassTheme.colors.onGlassSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionsGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(CornerRadius.Card))
            .background(GlassTheme.colors.glassSurfaceElevated)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)
    ) {
        DimensionCategoryRow(
            category = "Spacing",
            tokens = listOf(
                "Hairline (${Spacing.Hairline})",
                "ExtraSmall (${Spacing.ExtraSmall})",
                "Small (${Spacing.Small})",
                "MediumSmall (${Spacing.MediumSmall})",
                "SmallMedium (${Spacing.SmallMedium})",
                "Medium (${Spacing.Medium})",
                "Large (${Spacing.Large})",
                "ExtraLarge (${Spacing.ExtraLarge})",
                "DialogPadding (${Spacing.DialogPadding})",
                "PanelPadding (${Spacing.PanelPadding})"
            )
        )

        DimensionCategoryRow(
            category = "CornerRadius",
            tokens = listOf(
                "None (${CornerRadius.None})",
                "ExtraSmall (${CornerRadius.ExtraSmall})",
                "Compact (${CornerRadius.Compact})",
                "MediumSmall (${CornerRadius.MediumSmall})",
                "Medium (${CornerRadius.Medium})",
                "Card (${CornerRadius.Card})",
                "Dialog (${CornerRadius.Dialog})",
                "Panel (${CornerRadius.Panel})",
                "ExtraLarge (${CornerRadius.ExtraLarge})",
                "Pill (${CornerRadius.Pill})"
            )
        )

        DimensionCategoryRow(
            category = "IconSize",
            tokens = listOf(
                "Indicator (${IconSize.Indicator})",
                "Small (${IconSize.Small})",
                "Search (${IconSize.Search})",
                "Medium (${IconSize.Medium})",
                "Large (${IconSize.Large})",
                "ExtraLarge (${IconSize.ExtraLarge})"
            )
        )

        DimensionCategoryRow(
            category = "StrokeWidth",
            tokens = listOf(
                "Hairline (${StrokeWidth.Hairline})",
                "Standard (${StrokeWidth.Standard})",
                "Focused (${StrokeWidth.Focused})",
                "Active (${StrokeWidth.Active})"
            )
        )

        DimensionCategoryRow(
            category = "Elevation",
            tokens = listOf(
                "None (${Elevation.None})",
                "Raised (${Elevation.Raised})",
                "Panel (${Elevation.Panel})"
            )
        )
    }
}

@Composable
private fun DimensionCategoryRow(category: String, tokens: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(category, style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.glassAccent)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)
        ) {
            tokens.forEach { token ->
                Box(
                    modifier = Modifier
                        .clip(ContinuousCapsule)
                        .background(GlassTheme.colors.glassSurface)
                        .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall)
                ) {
                    Text(token, style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlassSecondary)
                }
            }
        }
    }
}

@Composable
private fun MotionTokensGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                .background(GlassTheme.colors.glassSurfaceElevated)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text("MotionDuration", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.glassAccent)
            Text("Fast: ${MotionDuration.Fast}ms", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("Moderate: ${MotionDuration.Moderate}ms", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("Standard: ${MotionDuration.Standard}ms", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                .background(GlassTheme.colors.glassSurfaceElevated)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text("MotionSpring", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.glassAccent)
            Text("StiffnessInteractive: ${MotionSpring.StiffnessInteractive}f", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("DampingRatioMediumBouncy: ${MotionSpring.DampingRatioMediumBouncy}f", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("DampingRatioNoBouncy: ${MotionSpring.DampingRatioNoBouncy}f", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(ContinuousRoundedRectangle(CornerRadius.Card))
                .background(GlassTheme.colors.glassSurfaceElevated)
                .padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            Text("MotionEasing & Threshold", style = MaterialTheme.typography.titleSmall, color = GlassTheme.colors.glassAccent)
            Text("MotionEasing.Standard", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("MotionEasing.EmphasizedDecelerate", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("Threshold.Fine: ${MotionThreshold.Fine}", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
            Text("Threshold.Offset: ${MotionThreshold.Offset}", style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlass)
        }
    }
}

@Composable
private fun TypographyScaleGrid() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(CornerRadius.Card))
            .background(GlassTheme.colors.glassSurfaceElevated)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        Text("Headline Large — Inter UI Font", style = MaterialTheme.typography.headlineLarge, color = GlassTheme.colors.onGlass)
        Text("Title Large — Screen Headers & Modal Titles", style = MaterialTheme.typography.titleLarge, color = GlassTheme.colors.onGlass)
        Text("Title Medium — Section Headers & Card Headers", style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.onGlass)
        Text("Body Large — Primary paragraph reading text", style = MaterialTheme.typography.bodyLarge, color = GlassTheme.colors.onGlass)
        Text("Body Medium — Secondary information and content", style = MaterialTheme.typography.bodyMedium, color = GlassTheme.colors.onGlassSecondary)
        Text("Label Medium — Button labels and tab indicators", style = MaterialTheme.typography.labelMedium, color = GlassTheme.colors.onGlass)
        Text("Label Small — Status tags and caption metadata", style = MaterialTheme.typography.labelSmall, color = GlassTheme.colors.onGlassMuted)
    }
}
