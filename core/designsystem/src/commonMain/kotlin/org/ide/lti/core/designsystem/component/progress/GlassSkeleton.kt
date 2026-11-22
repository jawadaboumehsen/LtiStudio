/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.progress

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.MotionDuration
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * macOS-style subtle liquid glass shimmer modifier.
 *
 * Sweeps a delicate highlight band across the element. When [GlassTheme.reducedMotionPolicy] is
 * set to [GlassReducedMotionPolicy.Reduced], the shimmer stays stationary with a subtle translucent tint.
 * Reads animation progress exclusively inside the draw phase via [drawWithCache] to prevent recompositions.
 */
@Composable
fun Modifier.glassShimmer(
    shape: Shape = GlassShapes.HazeCapsule,
    baseColor: Color? = null,
    highlightColor: Color? = null,
    durationMillis: Int = MotionDuration.ShimmerSweep,
): Modifier {
    val reduced = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val base = baseColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.Faint)
    val highlight = highlightColor ?: MaterialTheme.colorScheme.onSurface.copy(alpha = AlphaTokens.Glow)

    if (reduced) {
        return this.clip(shape).background(base)
    }

    val transition = rememberInfiniteTransition(label = "glassShimmerTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glassShimmerProgress",
    )

    return this
        .clip(shape)
        .drawWithCache {
            onDrawBehind {
                val w = size.width
                val h = size.height
                val bandWidth = (w * 0.75f).coerceAtLeast(100f)
                val totalTravel = w + bandWidth * 2
                val startX = -bandWidth + totalTravel * progress
                val brush = Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(startX, 0f),
                    end = Offset(startX + bandWidth, h),
                )
                drawRect(brush = brush)
            }
        }
}

/**
 * A rounded translucent pill skeleton element.
 */
@Composable
fun GlassSkeletonPill(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = Spacing.SmallMedium,
    shape: Shape = GlassShapes.HazeCapsule,
    baseColor: Color? = null,
    highlightColor: Color? = null,
) {
    val sizeMod = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = sizeMod
            .height(height)
            .glassShimmer(shape = shape, baseColor = baseColor, highlightColor = highlightColor),
    )
}

/**
 * macOS-style list/table row placeholder with icon, title, subtitle, and trailing badge.
 */
@Composable
fun GlassSkeletonRow(
    modifier: Modifier = Modifier,
    hasIcon: Boolean = true,
    iconSize: Dp = ComponentSize.SkeletonRowIconSize,
    iconShape: Shape = GlassShapes.HazeSmall,
    titleFraction: Float = 0.45f,
    subtitleFraction: Float = 0.70f,
    hasSubtitle: Boolean = true,
    hasTrailingBadge: Boolean = true,
    trailingWidth: Dp = ComponentSize.SkeletonTrailingBadgeWidth,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (hasIcon) {
            Box(
                modifier = Modifier
                    .size(iconSize)
                    .glassShimmer(shape = iconShape),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.Compact),
        ) {
            GlassSkeletonPill(
                modifier = Modifier.fillMaxWidth(titleFraction),
                height = Spacing.Fourteen,
            )
            if (hasSubtitle) {
                GlassSkeletonPill(
                    modifier = Modifier.fillMaxWidth(subtitleFraction),
                    height = Spacing.MediumSmall,
                )
            }
        }
        if (hasTrailingBadge) {
            Box(
                modifier = Modifier
                    .width(trailingWidth)
                    .height(Spacing.Large)
                    .glassShimmer(shape = GlassShapes.HazeCapsule),
            )
        }
    }
}

/**
 * Multiple organic macOS-style skeleton rows with staggered title/subtitle widths to look dynamic.
 */
@Composable
fun GlassSkeletonList(
    count: Int = 4,
    modifier: Modifier = Modifier,
    hasIcon: Boolean = true,
    hasSubtitle: Boolean = true,
    hasTrailingBadge: Boolean = true,
    titleFractions: List<Float> = listOf(0.42f, 0.58f, 0.35f, 0.65f),
    subtitleFractions: List<Float> = listOf(0.72f, 0.50f, 0.80f, 0.62f),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        repeat(count) { index ->
            GlassSkeletonRow(
                hasIcon = hasIcon,
                titleFraction = titleFractions[index % titleFractions.size],
                subtitleFraction = subtitleFractions[index % subtitleFractions.size],
                hasSubtitle = hasSubtitle,
                hasTrailingBadge = hasTrailingBadge,
            )
        }
    }
}

/**
 * macOS-style frosted glass card placeholder with header, content lines, and action button.
 */
@Composable
fun GlassSkeletonCard(
    modifier: Modifier = Modifier,
    hasHeaderIcon: Boolean = true,
    lineCount: Int = 3,
    hasAction: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.HazeCard)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Half))
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.Hover),
                shape = GlassShapes.HazeCard,
            )
            .padding(Spacing.CardPadding),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
            ) {
                if (hasHeaderIcon) {
                    Box(
                        modifier = Modifier
                            .size(ComponentSize.SkeletonCardHeaderIconSize)
                            .glassShimmer(shape = GlassShapes.HazeSmall),
                    )
                }
                GlassSkeletonPill(
                    modifier = Modifier.fillMaxWidth(0.38f),
                    height = Spacing.Medium,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                val fractions = listOf(0.92f, 0.78f, 0.60f, 0.85f)
                repeat(lineCount.coerceAtLeast(1)) { i ->
                    GlassSkeletonPill(
                        modifier = Modifier.fillMaxWidth(fractions[i % fractions.size]),
                        height = Spacing.MediumSmall,
                    )
                }
            }

            if (hasAction) {
                Box(
                    modifier = Modifier
                        .width(ComponentSize.SkeletonCardActionWidth)
                        .height(ComponentSize.SkeletonCardActionHeight)
                        .glassShimmer(shape = GlassShapes.HazeMediumSmall),
                )
            }
        }
    }
}

/**
 * Container providing a seamless transition between loading skeleton and real content.
 */
@Composable
fun GlassLoadingContainer(
    loading: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Crossfade(
        targetState = loading,
        animationSpec = tween(durationMillis = MotionDuration.Moderate),
        modifier = modifier,
        label = "glassLoadingTransition",
    ) { isLoading ->
        if (isLoading) {
            skeleton()
        } else {
            content()
        }
    }
}
