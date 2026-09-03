/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.theme.config

import androidx.compose.ui.graphics.Color
import org.ide.lti.core.designsystem.theme.ThemeColors

/**
 * Polymorphic application theme contract adhering to Clean Architecture & Open/Closed Principle.
 *
 * Each theme owns its self-contained [ThemeConfiguration] without external hardcoded switches.
 */
sealed interface AppTheme {
    val id: String
    val displayName: String
    val indicatorColor: Color
    val isDark: Boolean
    val configuration: ThemeConfiguration

    /**
     * Signature AMOLED Dark Theme with light sky-blue accents and cosmic aurora refraction.
     */
    data object Blue : AppTheme {
        override val id: String = "blue"
        override val displayName: String = "Blue"
        override val indicatorColor: Color = ThemeColors.Blue
        override val isDark: Boolean = true
        override val configuration: ThemeConfiguration get() = BlueThemeConfig
    }

    /**
     * Neutral Fleet Dark Theme with pure black base, layered black-gray surfaces, and slate dividers.
     */
    data object Dark : AppTheme {
        override val id: String = "dark"
        override val displayName: String = "Dark"
        override val indicatorColor: Color = ThemeColors.Dark
        override val isDark: Boolean = true
        override val configuration: ThemeConfiguration get() = DarkThemeConfig
    }

    /**
     * Crisp Frosted Glass Light Theme with luminous ambient backdrop and dark typography.
     */
    data object Light : AppTheme {
        override val id: String = "light"
        override val displayName: String = "Light"
        override val indicatorColor: Color = ThemeColors.Light
        override val isDark: Boolean = false
        override val configuration: ThemeConfiguration get() = LightThemeConfig
    }

    /**
     * User-defined custom theme allowing dynamic extensions without modifying core code.
     */
    data class Custom(
        override val configuration: ThemeConfiguration,
    ) : AppTheme {
        override val id: String get() = configuration.id
        override val displayName: String get() = configuration.displayName
        override val indicatorColor: Color get() = configuration.indicatorColor
        override val isDark: Boolean get() = configuration.isDark
    }

    companion object {
        val allThemes: List<AppTheme> = listOf(Blue, Dark, Light)

        fun fromId(id: String): AppTheme = ThemeRegistry.fromId(id)
    }
}
