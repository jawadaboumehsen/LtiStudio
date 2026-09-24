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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GlassThemeTest {

    @Test
    fun appThemeFromIdResolvesCorrectly() {
        assertEquals(AppTheme.Blue, AppTheme.fromId("blue"))
        assertEquals(AppTheme.Dark, AppTheme.fromId("dark"))
        assertEquals(AppTheme.Light, AppTheme.fromId("light"))
        assertEquals(AppTheme.Blue, AppTheme.fromId("unknown"))
        assertEquals(AppTheme.Blue, AppTheme.fromId(null))
    }

    @Test
    fun colorSchemesExistForAllThemes() {
        assertNotNull(colorSchemeFor(AppTheme.Blue))
        assertNotNull(colorSchemeFor(AppTheme.Dark))
        assertNotNull(colorSchemeFor(AppTheme.Light))
    }
}
