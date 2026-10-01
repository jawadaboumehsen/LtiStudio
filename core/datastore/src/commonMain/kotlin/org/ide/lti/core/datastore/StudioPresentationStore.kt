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

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.studio.StudioPresentation

@Serializable
public data class StudioPresentationMap(
    val schemaVersion: Int = 1,
    val presentations: Map<String, StudioPresentation> = emptyMap(),
)

/**
 * Multiplatform persistent preferences data source for ROM Setup Studio workspace presentation states.
 *
 * Presentation state is strictly isolated per workspace. Corrupted or undecodable data is safely discarded
 * rather than thrown.
 */
public class StudioPresentationStore(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : org.ide.lti.core.domain.ports.StudioPresentationStorePort {
    private val mutex = Mutex()

    private val _presentations = MutableStateFlow(readPresentationsSafe())
    override val presentations: Flow<Map<String, StudioPresentation>> = _presentations.asStateFlow()
    override val currentPresentations: Map<String, StudioPresentation> get() = _presentations.value

    override fun presentation(workspaceId: String): Flow<StudioPresentation?> =
        _presentations.map { map -> map[workspaceId] }

    override suspend fun get(workspaceId: String): StudioPresentation? = withContext(ioDispatcher) {
        mutex.withLock {
            _presentations.value[workspaceId]
        }
    }

    override suspend fun put(presentation: StudioPresentation): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val sanitized = sanitize(presentation)
            val current = _presentations.value
            val updated = current + (sanitized.workspaceId to sanitized)
            persistPresentationsInternal(updated)
            _presentations.value = updated
        }
    }

    override suspend fun clear(workspaceId: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _presentations.value
            if (workspaceId in current) {
                val updated = current - workspaceId
                persistPresentationsInternal(updated)
                _presentations.value = updated
            }
        }
    }

    private fun sanitize(presentation: StudioPresentation): StudioPresentation = presentation.copy(
        navigatorWidth = presentation.navigatorWidth.coerceIn(
            StudioPresentation.MIN_NAVIGATOR_WIDTH,
            StudioPresentation.MAX_NAVIGATOR_WIDTH,
        ),
    )

    private fun readPresentationsSafe(): Map<String, StudioPresentation> {
        val raw = settings.getStringOrNull(KEY_PRESENTATIONS)
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyMap()

        return try {
            json.decodeFromString<StudioPresentationMap>(trimmed)
                .presentations
                .mapValues { (_, pres) -> sanitize(pres) }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.w(e) { "Failed to decode persisted StudioPresentation store. Discarding." }
            settings.remove(KEY_PRESENTATIONS)
            emptyMap()
        }
    }

    private fun persistPresentationsInternal(presentations: Map<String, StudioPresentation>) {
        try {
            val sanitized = presentations.mapValues { (_, pres) -> sanitize(pres) }
            val wrapper = StudioPresentationMap(schemaVersion = 1, presentations = sanitized)
            val encoded = json.encodeToString(wrapper)
            settings[KEY_PRESENTATIONS] = encoded
        } catch (e: Exception) {
            // Presentation state is disposable by design, so a write failure must not break the session -
            // but it is still worth a log line rather than silence.
            co.touchlab.kermit.Logger.w(e) { "Failed to persist StudioPresentation store." }
        }
    }

    public companion object {
        public const val KEY_PRESENTATIONS: String = "lti_studio_presentations_v1"

        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
