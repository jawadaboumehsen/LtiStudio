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
 * Record of an explicit user acknowledgement for a warning or risky operation (FR-035).
 * Stored per-workspace so acknowledgements persist across restarts.
 */
@Serializable
data class Acknowledgement(val featureId: String, val acceptedAt: String) {
    init {
        require(featureId.isNotBlank()) { "featureId must not be blank" }
        require(acceptedAt.isNotBlank()) { "acceptedAt must not be blank" }
    }
}

/**
 * Collection of acknowledgements with idempotent acceptance logic.
 */
@Serializable
data class Acknowledgements(val items: List<Acknowledgement> = emptyList()) {
    fun isAcknowledged(featureId: String): Boolean = items.any { it.featureId == featureId }

    /**
     * Idempotently accept a feature warning. If [featureId] is already acknowledged,
     * the existing record is kept without modification.
     */
    fun accept(featureId: String, acceptedAt: String): Acknowledgements {
        require(featureId.isNotBlank()) { "featureId must not be blank" }
        require(acceptedAt.isNotBlank()) { "acceptedAt must not be blank" }
        if (isAcknowledged(featureId)) return this
        return copy(items = items + Acknowledgement(featureId, acceptedAt))
    }

    fun toJson(): String = jsonFormat.encodeToString(serializer(), this)

    companion object {
        val Empty = Acknowledgements()

        private val jsonFormat = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        fun fromJson(jsonString: String): Acknowledgements = jsonFormat.decodeFromString(serializer(), jsonString)
    }
}
