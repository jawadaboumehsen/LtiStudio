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

/**
 * Thread-safe registry providing string ID resolution for [AppTheme] instances.
 */
object ThemeRegistry {
    private val themes = mutableMapOf<String, AppTheme>()

    init {
        register(AppTheme.Blue)
        register(AppTheme.Dark)
        register(AppTheme.Light)
    }

    fun register(theme: AppTheme) {
        themes[theme.id.lowercase()] = theme
    }

    fun fromId(id: String): AppTheme =
        themes[id.lowercase()] ?: AppTheme.Blue

    fun registeredThemes(): List<AppTheme> = themes.values.toList()
}
