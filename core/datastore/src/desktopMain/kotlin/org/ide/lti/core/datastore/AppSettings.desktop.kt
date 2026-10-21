/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * A properties file in the app data folder. Not Java Preferences (the Windows registry): that store refuses any
 * value over 8 KB, and the workspace, snapshot and setup lists grow past that.
 */
public actual fun appSettings(): Settings =
    fileSettings(File(DesktopRunFileStorage.resolveDefaultBaseDir().parentFile, "settings.properties"), Settings())

/** Settings kept in [file]; every change rewrites it through a temporary file, so it is never half-written. */
public fun fileSettings(file: File, legacy: Settings? = null): Settings {
    val properties = Properties()
    if (file.isFile) {
        file.inputStream().use { properties.load(it) }
    }
    if (legacy != null && properties.getProperty(MIGRATED) != "true") {
        // Preferences stores every type as text. Keep the original store intact until and after migration.
        // Merge missing keys for installs that already opened the file backend, preserving newer values.
        legacy.keys.filterNot { properties.containsKey(it) }.forEach { key ->
            properties.setProperty(key, legacy.getString(key, ""))
        }
        properties.setProperty(MIGRATED, "true")
        persist(file, properties)
    }
    return PropertiesSettings(properties) { changed ->
        persist(file, changed)
    }
}

private const val MIGRATED = "lti_settings_legacy_migrated_v1"

private fun persist(file: File, properties: Properties) {
    synchronized(properties) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { properties.store(it, null) }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
