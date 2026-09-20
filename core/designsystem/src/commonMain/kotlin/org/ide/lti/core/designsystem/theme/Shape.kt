/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import com.kyant.capsule.Continuity
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.concentricInset
import com.kyant.capsule.concentricOutset
import com.kyant.capsule.continuities.G1Continuity
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.lerp as capsuleLerp

/**
 * Standard cached squircle shapes for glass UI components.
 * Powered by Capsule 1.1.1's G2 curvature continuity, ensuring mathematically seamless
 * transitions from straight edges to corner arcs without inflection artifacts.
 *
 * Reusing static singleton instances avoids per-recomposition allocations
 * and ensures fast referential equality checks in ModifierNodeElement diffing.
 */
@Immutable
object GlassShapes {
    // --- Continuities ---
    /** High-fidelity Apple-style G2 curvature continuity (default for resting surfaces). */
    val ContinuityG2: Continuity = G2Continuity()

    /** High-performance G1 tangential continuity (optimized for rapid animations). */
    val ContinuityG1: Continuity = G1Continuity

    // --- Core Shape Presets ---
    val None = RectangleShape
    val ExtraSmall = ContinuousRoundedRectangle(CornerRadius.ExtraSmall, continuity = ContinuityG2)
    val Small = ContinuousRoundedRectangle(CornerRadius.Small, continuity = ContinuityG2)
    val Compact = ContinuousRoundedRectangle(CornerRadius.Compact, continuity = ContinuityG2)
    val MediumSmall = ContinuousRoundedRectangle(CornerRadius.MediumSmall, continuity = ContinuityG2)
    val Medium = ContinuousRoundedRectangle(CornerRadius.Medium, continuity = ContinuityG2)
    val Large = ContinuousRoundedRectangle(CornerRadius.Large, continuity = ContinuityG2)
    val ExtraLarge = ContinuousRoundedRectangle(CornerRadius.ExtraLarge, continuity = ContinuityG2)
    val Full = CircleShape
    val Circle = CircleShape
    val Capsule = ContinuousCapsule(continuity = ContinuityG2)
    val Card = ContinuousRoundedRectangle(CornerRadius.Card, continuity = ContinuityG2)
    val Dialog = ContinuousRoundedRectangle(CornerRadius.Dialog, continuity = ContinuityG2)
    val Panel = ContinuousRoundedRectangle(CornerRadius.Panel, continuity = ContinuityG2)
    val TextField = ContinuousRoundedRectangle(CornerRadius.Medium, continuity = ContinuityG2)
    val Tab = ContinuousRoundedRectangle(CornerRadius.Small, continuity = ContinuityG2)
    val Chip = ContinuousCapsule(continuity = ContinuityG2)
    val Pill = ContinuousCapsule(continuity = ContinuityG2)
    val BottomSheet = ContinuousRoundedRectangle(
        topStart = CornerRadius.Medium,
        topEnd = CornerRadius.Medium,
        bottomEnd = CornerRadius.None,
        bottomStart = CornerRadius.None,
        continuity = ContinuityG2,
    )

    // --- Concentric Shape Utilities ---
    /**
     * Creates a concentric inner squircle shape offset inward by [padding].
     * Guarantees R_inner = max(0, R_outer - padding) for concentric nested glass cards and panels.
     */
    @Stable
    fun concentric(shape: ContinuousRoundedRectangle, padding: Dp): ContinuousRoundedRectangle =
        shape.concentricInset(padding)

    /**
     * Creates a concentric outer squircle shape offset outward by [padding].
     * Useful for focus rings, borders, and ambient halos.
     */
    @Stable
    fun concentricOutset(shape: ContinuousRoundedRectangle, padding: Dp): ContinuousRoundedRectangle =
        shape.concentricOutset(padding)

    // --- Shape Morphing & Interpolation ---
    /**
     * Linearly interpolates between two continuous rounded rectangle squircles.
     * Enables smooth shape-morphing animations between component states.
     */
    @Stable
    fun lerp(
        start: ContinuousRoundedRectangle,
        stop: ContinuousRoundedRectangle,
        fraction: Float,
    ): ContinuousRoundedRectangle = capsuleLerp(start, stop, fraction)

    /**
     * Returns a copy of [shape] with [ContinuityG1], recommended during rapid layout transitions or
     * drag gestures where maximum framerate is paramount.
     */
    @Stable
    fun asAnimated(shape: ContinuousRoundedRectangle): ContinuousRoundedRectangle =
        shape.copy(continuity = ContinuityG1)
}

/**
 * IDE shell surface and control shapes defined by Feature 005.
 */
@Immutable
object IdeShellShapes {
    val Frame = RectangleShape
    val Panel = RectangleShape
    val Card = androidx.compose.foundation.shape.RoundedCornerShape(IdeShellDimens.CardCornerRadius)
    val Control = androidx.compose.foundation.shape.RoundedCornerShape(IdeShellDimens.ControlCornerRadius)
    val Badge = androidx.compose.foundation.shape.RoundedCornerShape(IdeShellDimens.BadgeCornerRadius)
}
