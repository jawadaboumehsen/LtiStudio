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

import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.OpticalSizeValue
import com.kashif_e.backdrop.ResolvedGlassEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GlassAccessibilityTest {

    @Test
    fun defaultSettingsLeaveTheResolvedEffectUnchanged() {
        val effect = ResolvedGlassEffect(blur = OpticalSizeValue.Fixed(40.dp))
        assertSame(effect, effect.withAccessibility(GlassAccessibilitySettings()))
    }

    @Test
    fun reduceTransparencyScalesBlurBySixtyFivePercent() {
        val effect = ResolvedGlassEffect(blur = OpticalSizeValue.Fixed(40.dp))
        val result = effect.withAccessibility(GlassAccessibilitySettings(reduceTransparency = true))
        assertEquals(26.dp, (result.blur as OpticalSizeValue.Fixed).value)
    }

    @Test
    fun increaseContrastAndShowBordersDoNotTouchTheResolvedEffect() {
        val effect = ResolvedGlassEffect(blur = OpticalSizeValue.Fixed(40.dp))
        val result = effect.withAccessibility(
            GlassAccessibilitySettings(increaseContrast = true, showBorders = true),
        )
        assertSame(effect, result)
    }
}
