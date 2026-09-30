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

/** Layout and motion tokens specific to the first-run setup wizard's wide, document-style screens. */
@Suppress("PropertyName") // Setup layout tokens use PascalCase per CLAUDE.md token catalog
object SetupTokens {
    val SidebarWidthWide = 240.dp
    val SidebarWidthCompact = 192.dp
    val ContentMaxWidth = 1120.dp
    val CompactBreakpoint = 1100.dp
    val MinWindowWidth = 900.dp

    val TextTitle = 28.sp
    val TextSection = 18.sp
    val TextBody = 14.sp
    val TextMetadata = 12.sp

    const val DurationFeedbackMs: Int = 120
    const val DurationFadeMs: Int = 180

    /** iOS critically-damped spring ratio for fluid navigation transitions (0.85f). */
    const val MotionDampingRatio: Float = MotionSpring.DampingRatioNavigation

    /** Smooth iOS navigation transition stiffness (380f). */
    const val MotionStiffness: Float = MotionSpring.StiffnessNavigation

    /** Subtle scale factor applied to exiting/entering tabs for iOS depth parallax. */
    const val MotionScaleInitial: Float = 0.98f

    /** Fractional horizontal offset (15%) for fluid tab slide transitions. */
    const val MotionSlideFraction: Float = 0.15f

    /** Opaque wizard background per theme, since the wizard renders outside any glass surface. */
    private fun opaqueSurfaceColor(theme: AppTheme): Color = when (theme) {
        AppTheme.Light -> Color(0xFFFFFFFF)
        AppTheme.Dark -> Color(0xFF1E1E1E)
        AppTheme.Blue -> Color(0xFF0F1E4A)
    }

    val OpaqueSurfaceColor: Color
        @Composable
        @ReadOnlyComposable
        get() = opaqueSurfaceColor(GlassTheme.appTheme)

    /** Always false - the effects-disabled/reduced-transparency toggle was removed with the old backdrop engine. */
    const val IsReducedTransparency: Boolean = false
}
