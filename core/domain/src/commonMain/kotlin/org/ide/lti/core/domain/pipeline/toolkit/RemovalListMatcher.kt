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
 * Scanned removable application or component in the ROM (FR-027).
 */
@Serializable
public data class RemovalItem(
    public val id: String,
    public val name: String,
    public val path: String,
    public val packageId: String? = null,
    public val sizeBytes: Long = 0L,
    public val isProtected: Boolean = false,
)

/**
 * Saved or preset list of removal entries (package IDs or relative paths).
 */
@Serializable
public data class RemovalList(public val name: String, public val entries: List<String> = emptyList()) {
    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        public fun fromJson(text: String): RemovalList = json.decodeFromString(serializer(), text)
        public fun toJson(list: RemovalList): String = json.encodeToString(serializer(), list)
    }
}

/**
 * Outcome of matching a removal list against available workspace items (FR-027).
 */
@Serializable
public data class RemovalMatchResult(
    public val matched: List<RemovalItem> = emptyList(),
    public val unknown: List<String> = emptyList(),
    public val blockedProtected: List<RemovalItem> = emptyList(),
    public val effectiveRemovals: List<RemovalItem> = emptyList(),
)

/**
 * Pure matcher for removal lists: matches entries, blocks protected components,
 * and identifies unknown entries to skip rather than silently dropping (FR-027).
 */
public object RemovalListMatcher {

    public fun match(
        list: RemovalList,
        availableItems: List<RemovalItem>,
        protectedIds: Set<String> = emptySet(),
    ): RemovalMatchResult {
        val byId = availableItems.associateBy { it.id }
        val byPath = availableItems.associateBy { normalize(it.path) }
        val byPackage = availableItems.filter { !it.packageId.isNullOrBlank() }.associateBy { it.packageId!! }

        val matchedItems = mutableListOf<RemovalItem>()
        val unknownEntries = mutableListOf<String>()

        for (entry in list.entries) {
            val normalized = normalize(entry)
            val item = byId[entry] ?: byPath[normalized] ?: byPackage[entry]
            if (item != null) {
                if (!matchedItems.contains(item)) {
                    matchedItems.add(item)
                }
            } else {
                unknownEntries.add(entry)
            }
        }

        val blocked = mutableListOf<RemovalItem>()
        val effective = mutableListOf<RemovalItem>()

        for (item in matchedItems) {
            val isBlocked = item.isProtected ||
                protectedIds.contains(item.id) ||
                protectedIds.contains(item.path) ||
                (item.packageId != null && protectedIds.contains(item.packageId))

            if (isBlocked) {
                blocked.add(item)
            } else {
                effective.add(item)
            }
        }

        return RemovalMatchResult(
            matched = matchedItems,
            unknown = unknownEntries,
            blockedProtected = blocked,
            effectiveRemovals = effective,
        )
    }

    private fun normalize(path: String): String = path.replace('\\', '/').trimStart('/').removePrefix("./").trimEnd('/')
}
