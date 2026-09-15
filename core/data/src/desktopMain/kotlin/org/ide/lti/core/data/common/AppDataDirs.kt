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
