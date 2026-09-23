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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.Continuity
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.capsule.concentricInset
import com.kyant.capsule.concentricOutset
import com.kyant.capsule.continuities.G1Continuity
import com.kyant.capsule.continuities.G2Continuity
import com.kyant.capsule.lerp as capsuleLerp

/**
 * The shape type Haze's `dev.chrisbanes.haze.glass.GlassStyleScope.shape()` requires - Haze's
 * whole mask/shader pipeline extracts 4 simple per-corner pixel radii from this exact type
 * (`CornerRadii.kt`'s `RoundedCornerShape.toCornerRadiiPx()`), so it cannot accept the squircle
 * [ContinuousCapsule]/[ContinuousRoundedRectangle] shapes below - those use G1/G2 Bezier curvature
 * blending, not circular-arc corners, and don't reduce to that model. Every `Haze*` preset in
 * [GlassShapes] is this type; components handing a shape to `hazeGlass` declare their `shape`
 * parameter as this type so they never import or name `RoundedCornerShape` themselves.
 */
typealias HazeShape = RoundedCornerShape

/**
 * Standard cached squircle shapes for glass UI components, plus their [HazeShape]-typed
 * equivalents for the few places that hand a shape to Haze's real `Modifier.hazeGlass` (each
 * `Haze*` preset below is dp-matched 1:1 to the squircle preset with the same base name).
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

    // --- Core squircle shape presets (own rendering: .clip()/.background()/glassOutlineBorder) ---
    val None = RectangleShape
    val ExtraSmall = ContinuousRoundedRectangle(CornerRadius.ExtraSmall, continuity = ContinuityG2)
    val Small = ContinuousRoundedRectangle(CornerRadius.Small, continuity = ContinuityG2)
    val Compact = ContinuousRoundedRectangle(CornerRadius.Compact, continuity = ContinuityG2)
    val MediumSmall = ContinuousRoundedRectangle(CornerRadius.MediumSmall, continuity = ContinuityG2)
    val Medium = ContinuousRoundedRectangle(CornerRadius.Medium, continuity = ContinuityG2)
    val Large = ContinuousRoundedRectangle(CornerRadius.Large, continuity = ContinuityG2)
    val ExtraLarge = ContinuousRoundedRectangle(CornerRadius.ExtraLarge, continuity = ContinuityG2)
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

    // --- Haze-typed equivalents (Modifier.hazeGlass / GlassStyleScope.shape()) ---
    // Each preset here is the HazeShape counterpart of the squircle preset with the same base
    // name above - same dp values, real RoundedCornerShape geometry (self-clamping, so a "full
    // capsule" is just percent = 50, not a separate curve model). Referenced directly by
    // components; never constructed ad-hoc at a call site.
    val HazeExtraSmall: HazeShape = RoundedCornerShape(CornerRadius.ExtraSmall)
    val HazeSmall: HazeShape = RoundedCornerShape(CornerRadius.Small)
    val HazeCompact: HazeShape = RoundedCornerShape(CornerRadius.Compact)
    val HazeMediumSmall: HazeShape = RoundedCornerShape(CornerRadius.MediumSmall)
    val HazeMedium: HazeShape = RoundedCornerShape(CornerRadius.Medium)
    val HazeLarge: HazeShape = RoundedCornerShape(CornerRadius.Large)
    val HazeExtraLarge: HazeShape = RoundedCornerShape(CornerRadius.ExtraLarge)
    val HazeCapsule: HazeShape = RoundedCornerShape(percent = 50)
    val HazeCard: HazeShape = RoundedCornerShape(CornerRadius.Card)
    val HazeDialog: HazeShape = RoundedCornerShape(CornerRadius.Dialog)
    val HazePanel: HazeShape = RoundedCornerShape(CornerRadius.Panel)

    /** [HazeShape] equivalent of [None] - square-cornered, for full-width bars (top/status bar chrome). */
    val HazeFlat: HazeShape = RoundedCornerShape(0.dp)

    /** [HazeShape] equivalent of [BottomSheet] - top-rounded-only, for modal bottom sheets. */
    val HazeBottomSheet: HazeShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    // --- IDE Shell Shapes (folded in from the former IdeShellShapes, Feature 005) ---
    // Kept as plain RoundedCornerShape (not ContinuousRoundedRectangle) and under `Shell`-prefixed
    // names - these are real, deliberately different (smaller, sharper) corner values from the
    // glass-tier Card/Panel/Pill above (6dp/8dp/10dp vs 16dp/12dp/full-capsule); merging the
    // *names* without also merging the *values* would silently change either the shell's precise
    // IDE-native look or the glass tier's soft one. Zero visual change from this consolidation -
    // same shape type, same values, one shape registry instead of two.
    val ShellFrame = RectangleShape
    val ShellPanel = RoundedCornerShape(GlassDimens.ContentRadius)
    val ShellPill = RoundedCornerShape(10.dp)
    val ShellCard = RoundedCornerShape(GlassDimens.CardCornerRadius)
    val ShellControl = RoundedCornerShape(GlassDimens.ControlCornerRadius)
    val ShellBadge = RoundedCornerShape(GlassDimens.BadgeCornerRadius)

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
