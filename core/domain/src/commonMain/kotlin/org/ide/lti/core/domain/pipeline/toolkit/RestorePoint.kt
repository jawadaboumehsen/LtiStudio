/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * State of a restore point item.
 */
@Serializable
enum class RestorePointState {
    Available,
    Restored,
    BackupMissing,
}

/**
 * Backup of an item before a destructive tool modified or removed it (FR-038).
 */
@Serializable
data class RestorePoint(
    val id: String,
    val tool: String,
    val itemPath: String,
    val createdAt: String,
    val backupPath: String,
    val state: RestorePointState = RestorePointState.Available,
    /** The item did not exist before the tool wrote it: restoring removes it ([backupPath] is never written). */
    val added: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "RestorePoint id must not be blank" }
        require(tool.isNotBlank()) { "RestorePoint tool must not be blank" }
        require(itemPath.isNotBlank()) { "RestorePoint itemPath must not be blank" }
        require(createdAt.isNotBlank()) { "RestorePoint createdAt must not be blank" }
        require(backupPath.isNotBlank()) { "RestorePoint backupPath must not be blank" }
    }
}

/**
 * Index of restore points stored under `restore/index.json` in the workspace.
 */
@Serializable
data class RestoreIndex(val items: List<RestorePoint> = emptyList()) {
    fun findById(id: String): RestorePoint? = items.firstOrNull { it.id == id }

    fun withState(id: String, newState: RestorePointState): RestoreIndex {
        val updated = items.map {
            if (it.id == id) it.copy(state = newState) else it
        }
        return copy(items = updated)
    }

    fun add(point: RestorePoint): RestoreIndex = copy(items = items + point)

    /** This index with [tool]'s points replaced by [points]; other tools' points are kept. */
    fun withTool(tool: String, points: List<RestorePoint>): RestoreIndex =
        copy(items = items.filterNot { it.tool == tool } + points)

    fun toJson(): String = jsonFormat.encodeToString(serializer(), this)

    companion object {
        val Empty = RestoreIndex()

        /** The workspace's one restore index (FR-038). */
        const val FILE: String = "restore/index.json"

        /** [jsonString] parsed; blank text (a new index) is [Empty]. */
        fun parseOrEmpty(jsonString: String): RestoreIndex = if (jsonString.isBlank()) Empty else fromJson(jsonString)

        private val jsonFormat = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun fromJson(jsonString: String): RestoreIndex = jsonFormat.decodeFromString(serializer(), jsonString)
    }
}
