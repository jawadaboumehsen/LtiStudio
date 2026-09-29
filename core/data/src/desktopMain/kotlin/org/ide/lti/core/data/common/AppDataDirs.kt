/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.common

import java.io.File
import java.nio.file.Path

/**
 * Resolves the platform-specific default base directory for LtiRomGui app data.
 */
internal fun resolveAppDataDir(subdir: String): Path {
    val localAppData = System.getenv("LOCALAPPDATA")
    val baseFile = if (!localAppData.isNullOrBlank()) {
        File(localAppData, "LtiRomGui/$subdir")
    } else {
        val userHome = System.getProperty("user.home") ?: "."
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        when {
            osName.contains("mac") -> File(userHome, "Library/Application Support/LtiRomGui/$subdir")
            else -> File(userHome, ".local/share/LtiRomGui/$subdir")
        }
    }
    return baseFile.toPath()
}
