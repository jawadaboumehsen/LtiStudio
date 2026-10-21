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

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppearanceTest {
    @Test
    fun anAccentBecomesThePrimaryColorWithReadableTextOnIt() {
        val scheme = colorSchemeFor(AppTheme.Blue)
        assertSame(scheme, scheme.withAccent(AccentColor.Default))
        val amber = scheme.withAccent(AccentColor.Amber)
        assertEquals(AccentColor.Amber.color, amber.primary)
        assertEquals(Color.Black, amber.onPrimary)
        assertEquals(Color.White, scheme.withAccent(AccentColor.Cobalt).onPrimary)
        assertEquals(AccentColor.Default, AccentColor.fromId("unknown"))
    }
}
