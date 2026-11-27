/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package dev.chrisbanes.haze.glass

/**
 * Describes the cross-sectional surface shape of the Glass bezel.
 *
 * The profile controls how light bends at the transition from the edge to the flat interior,
 * producing different refraction characteristics with [RefractionProfile.Surface]. With
 * [RefractionProfile.Edge], this profile controls lighting only.
 *
 * Future minor releases may add entries. Keep an `else` branch when you match exhaustively with `when`.
 */
enum class SurfaceProfile {
    /**
     * Spherical dome profile. Light rays bend sharply near the edge, creating a pronounced
     * refraction rim that softens toward the flat interior.
     */
    Circle,

    /**
     * Squircle profile with a gentler flat-to-curve transition. Produces smoother refraction
     * gradients even when the shape is stretched into non-square rectangles.
     */
    Squircle,

    /**
     * Inverted dome that causes light rays to diverge outward, displacing them beyond the
     * glass boundaries for a concave lens look.
     */
    Concave,

    /**
     * Blends a raised convex rim with a shallow concave center dip. Useful for creating
     * compound lens effects such as magnifying or recessed glass.
     */
    Lip,
}
