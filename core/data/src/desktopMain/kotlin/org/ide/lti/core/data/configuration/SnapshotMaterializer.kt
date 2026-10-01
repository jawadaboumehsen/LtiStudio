/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.admission.atomicWrite
import org.ide.lti.core.data.admission.readIfPresent
import org.ide.lti.core.domain.ports.SnapshotMaterializationIntent
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.toList

@Serializable
private data class IntentData(val workspaceId: String, val snapshotId: String, val status: String)

public class SnapshotMaterializer(private val storageDir: Path) : SnapshotMaterializerPort {

    init {
        Files.createDirectories(storageDir)
    }

    private fun getFile(workspaceId: String, snapshotId: String): Path =
        storageDir.resolve("intent_${workspaceId}_$snapshotId.json")

    override suspend fun markMaterializing(workspaceId: String, snapshotId: String) {
        val file = getFile(workspaceId, snapshotId)
        val data = IntentData(workspaceId, snapshotId, "materializing")
        atomicWrite(file, Json.encodeToString(data))
    }

    override suspend fun clearIntent(workspaceId: String, snapshotId: String) {
        val file = getFile(workspaceId, snapshotId)
        Files.deleteIfExists(file)
    }

    override suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent> {
        if (!Files.exists(storageDir)) return emptyList()
        return Files.list(storageDir).use { stream ->
            stream.toList().filter { it.name.endsWith(".json") }.mapNotNull { file ->
                val content = readIfPresent(file) ?: return@mapNotNull null
                try {
                    val data = Json.decodeFromString<IntentData>(content)
                    SnapshotMaterializationIntent(data.workspaceId, data.snapshotId, data.status)
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
