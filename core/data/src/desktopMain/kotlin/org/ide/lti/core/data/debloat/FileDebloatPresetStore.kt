/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.debloat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.common.resolveAppDataDir
import org.ide.lti.core.domain.debloat.DebloatPresetStore
import org.ide.lti.core.domain.pipeline.toolkit.RemovalList
import java.io.File

/** The presets as one JSON file in the app's data folder (`<LOCALAPPDATA>/LtiRomGui/debloat/presets.json`). */
internal class FileDebloatPresetStore(
    private val file: File = resolveAppDataDir("debloat").resolve("presets.json").toFile(),
) : DebloatPresetStore {
    override suspend fun load(): List<RemovalList> = withContext(Dispatchers.IO) {
        if (file.isFile) json.decodeFromString(serializer, file.readText()) else emptyList()
    }

    /** Written to a temporary file and renamed, so a crash never leaves half a file. */
    override suspend fun save(presets: List<RemovalList>): Unit = withContext(Dispatchers.IO) {
        file.parentFile.mkdirs()
        val partial = File(file.parentFile, "${file.name}.partial")
        partial.writeText(json.encodeToString(serializer, presets))
        java.nio.file.Files.move(
            partial.toPath(),
            file.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private companion object {
        val serializer = ListSerializer(RemovalList.serializer())
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }
    }
}
