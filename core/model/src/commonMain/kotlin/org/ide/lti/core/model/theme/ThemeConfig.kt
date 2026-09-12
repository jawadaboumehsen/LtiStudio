/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.theme

/**
 * Top-level application themes:
 * - [Blue]: Signature dark theme with vibrant light-blue (#38BDF8) accents, blue glass tints, and cosmic aurora.
 * - [Dark]: Neutral dark theme with black, dark gray, and gray tones.
 * - [Light]: Crisp frosted glass light theme.
 */
enum class AppTheme(val id: String, val displayName: String) {
    Blue("blue", "Blue"),
    Dark("dark", "Dark"),
    Light("light", "Light"),
}

/**
 * Surface tone variants for the neutral [AppTheme.Dark] theme:
 * - [Black]: Pure pitch-black AMOLED surfaces (#000000).
 * - [DarkGray]: Deep charcoal graphite surfaces (#14161A).
 * - [Gray]: Neutral slate gray surfaces (#21252B).
 */
enum class DarkTone(val id: String, val displayName: String, val hexCode: String) {
    Black("black", "Black", "#000000"),
    DarkGray("dark_gray", "Dark Gray", "#14161A"),
    Gray("gray", "Gray", "#21252B"),
}
