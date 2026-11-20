/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.workspace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import org.ide.lti.core.data.common.resolveAppDataDir
import org.ide.lti.core.domain.workspace.BuildProfile
import org.ide.lti.core.domain.workspace.BuildProfileStore
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Each workspace's profiles as one JSON file in the app's data folder (`profiles/<workspace id>.json`). */
internal class FileBuildProfileStore(private val folder: File = resolveAppDataDir("profiles").toFile()) :
    BuildProfileStore {
    private val writes = Mutex()

    override suspend fun load(workspaceId: String): List<BuildProfile> = withContext(Dispatchers.IO) {
        val file = fileOf(workspaceId)
        if (!file.isFile) return@withContext emptyList()
        ConfigurationSnapshot.canonicalJson.decodeFromString(serializer, file.readText())
    }

    override suspend fun update(
        workspaceId: String,
        change: (List<BuildProfile>) -> List<BuildProfile>,
    ): List<BuildProfile> = writes.withLock { change(load(workspaceId)).also { write(workspaceId, it) } }

    /** Written to a temporary file of its own and renamed, so a crash never leaves half a file. */
    private suspend fun write(workspaceId: String, profiles: List<BuildProfile>): Unit = withContext(Dispatchers.IO) {
        val file = fileOf(workspaceId)
        file.parentFile.mkdirs()
        val partial = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.partial")
        partial.writeText(ConfigurationSnapshot.canonicalJson.encodeToString(serializer, profiles))
        Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** A workspace id is a file name here: anything but letters, digits, `-` and `_` is refused. */
    private fun fileOf(workspaceId: String): File {
        require(SAFE.matches(workspaceId)) { "Not a workspace id: $workspaceId" }
        return File(folder, "$workspaceId.json")
    }

    private companion object {
        val serializer = ListSerializer(BuildProfile.serializer())
        val SAFE = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
