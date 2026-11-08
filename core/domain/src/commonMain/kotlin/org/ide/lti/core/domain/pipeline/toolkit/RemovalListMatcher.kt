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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/** A community list's opinion of one package (Universal Android Debloater: `Recommended`, `Advanced`, …). */
@Serializable
public data class CommunityOpinion(public val rating: String, public val note: String? = null)

/**
 * Saved or preset list of removal entries (package IDs or relative paths). A community list also keeps its
 * [opinions] per package, shown beside each app's own verdict; [device] marks the picks kept automatically for one
 * phone (its codename), offered to the next workspace of that phone.
 */
@Serializable
public data class RemovalList(
    public val name: String,
    public val entries: List<String> = emptyList(),
    public val opinions: Map<String, CommunityOpinion> = emptyMap(),
    public val device: String? = null,
) {
    public companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        public fun fromJson(text: String): RemovalList = json.decodeFromString(serializer(), text)

        /**
         * A list file, named [name] unless it names itself:
         * - this app's JSON (`{"name":…,"entries":[…]}`);
         * - a package map, as Universal Android Debloater's `uad_lists.json` (`{"<package>": {"removal": …}}`, or an
         *   array of `{"id": …, "removal": …}`): only the packages it rates `Recommended` (all, when it rates none);
         * - plain text, one package name or `partition:path` per line (blank lines and `#` comments skipped).
         */
        public fun parse(name: String, text: String): RemovalList {
            val trimmed = text.trimStart()
            val isJson = trimmed.startsWith("{") || trimmed.startsWith("[")
            val root = if (isJson) json.parseToJsonElement(text) else null
            return when {
                root == null -> RemovalList(
                    name,
                    text.lines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") },
                )

                root is JsonObject && "entries" in root -> fromJson(text)

                else -> communityList(name, root)
            }
        }

        /**
         * A package map as a community list: every package's rating and description as its opinion, and as entries
         * (what applying it picks) the packages it rates `Recommended`, or all of them when it rates none.
         */
        private fun communityList(name: String, root: JsonElement): RemovalList {
            fun field(o: JsonElement?, key: String) =
                (o as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.content }
            val rated = when (root) {
                is JsonObject -> root.map { (id, v) -> Triple(id, field(v, "removal"), field(v, "description")) }

                is JsonArray -> root.mapNotNull { e ->
                    field(e, "id")?.let { Triple(it, field(e, "removal"), field(e, "description")) }
                }

                else -> emptyList()
            }
            require(rated.isNotEmpty()) { "This file is neither a removal list nor a list of packages." }
            val opinions = rated.mapNotNull { (id, rating, note) ->
                rating?.let { id to CommunityOpinion(it, note?.trim()?.ifEmpty { null }) }
            }.toMap()
            val entries = rated.filter { it.second == RECOMMENDED }.ifEmpty { rated }.map { it.first }
            return RemovalList(name, entries, opinions)
        }

        private const val RECOMMENDED = "Recommended"

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
        val byPackage = availableItems.filter { !it.packageId.isNullOrBlank() }.groupBy { it.packageId!! }

        val matchedItems = mutableListOf<RemovalItem>()
        val unknownEntries = mutableListOf<String>()

        for (entry in list.entries) {
            val normalized = normalize(entry)
            // A package name means every installed copy of it, in whichever partition.
            val items = (byId[entry] ?: byPath[normalized])?.let(::listOf) ?: byPackage[entry]
            if (items != null) {
                items.filterNot(matchedItems::contains).forEach(matchedItems::add)
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
