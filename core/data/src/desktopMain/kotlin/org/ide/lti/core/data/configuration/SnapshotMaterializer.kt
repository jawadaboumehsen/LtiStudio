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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.common.utils.atomicWrite
import org.ide.lti.core.common.utils.readIfPresent
import org.ide.lti.core.domain.ports.SnapshotMaterializationIntent
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.streams.toList

@Serializable
private data class IntentData(val workspaceId: String, val snapshotId: String, val status: String)

public class SnapshotMaterializer(
    private val storageDir: Path,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SnapshotMaterializerPort {

    init {
        Files.createDirectories(storageDir)
    }

    private fun getFile(workspaceId: String, snapshotId: String): Path =
        storageDir.resolve("intent_${workspaceId}_$snapshotId.json")

    override suspend fun markMaterializing(workspaceId: String, snapshotId: String): Unit = withContext(ioDispatcher) {
        val file = getFile(workspaceId, snapshotId)
        val data = IntentData(workspaceId, snapshotId, "materializing")
        atomicWrite(file, Json.encodeToString(data))
    }

    override suspend fun clearIntent(workspaceId: String, snapshotId: String): Unit = withContext(ioDispatcher) {
        val file = getFile(workspaceId, snapshotId)
        Files.deleteIfExists(file)
    }

    override suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent> {
        return withContext(ioDispatcher) {
            if (!Files.exists(storageDir)) return@withContext emptyList()
            Files.list(storageDir).use { stream ->
                stream.toList().filter { it.name.endsWith(".json") }.mapNotNull { file ->
                    val content = readIfPresent(file) ?: return@mapNotNull null
                    try {
                        val data = Json.decodeFromString<IntentData>(content)
                        SnapshotMaterializationIntent(data.workspaceId, data.snapshotId, data.status)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }
    }
}
