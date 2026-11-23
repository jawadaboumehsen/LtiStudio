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
 * Controls the quality and appearance of chromatic aberration (color dispersion)
 * at the refraction edges.
 *
 * Future minor releases may add entries. Keep an `else` branch when you match exhaustively with `when`.
 */
public enum class ChromaticAberrationMode {
    /**
     * Three-sample split: red shifted forward, green centered, blue shifted backward.
     * Fast and suitable for most use cases.
     */
    Simple,

    /**
     * Seven-sample spectral decomposition with position-dependent intensity.
     * More physically realistic but significantly more expensive.
     */
    Full,
}
