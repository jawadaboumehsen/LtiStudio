/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

/**
 * Semantic role of a glass surface determining source input, optical material styling,
 * and solid effects-off fallback.
 */
enum class GlassRole {
    /** Shell chrome: TopBar, Navigator, PipelineRail, Breadcrumb, StatusBar. Samples wallpaper source. */
    Shell,

    /**
     * Text-bearing cards and panels: GlassCard, Setup cards, Settings cards. Uses Haze Regular with subtle tint.
     */
    TextCard,

    /**
     * Small floating controls: floating action buttons, sliders, segmented toggles, pill tabs, chips.
     * Uses Clear optics.
     */
    FloatingControl,
}
