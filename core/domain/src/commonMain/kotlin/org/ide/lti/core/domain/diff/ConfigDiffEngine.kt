/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.diff

/**
 * High-performance domain diff engine for Android build.prop, system.prop,
 * default.prop, and configuration property matrices.
 */
object ConfigDiffEngine {
    private val CRITICAL_SECURITY_KEYS = setOf(
        "ro.secure",
        "ro.debuggable",
        "ro.adb.secure",
        "ro.crypto.state",
    )

    private val BUILD_TYPE_KEYS = setOf(
        "ro.build.type",
        "ro.build.tags",
        "ro.product.build.type",
    )

    fun parseProperties(rawText: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        rawText.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isNotEmpty() && !line.startsWith("#") && !line.startsWith("//") && line.contains("=")) {
                val index = line.indexOf('=')
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    fun computeDiff(original: String, modified: String): ConfigDiffResult {
        val oldProps = parseProperties(original)
        val newProps = parseProperties(modified)

        val allKeys = (oldProps.keys + newProps.keys).sorted()
        val entries = mutableListOf<PropertyDiffEntry>()

        for (key in allKeys) {
            val oldVal = oldProps[key]
            val newVal = newProps[key]

            val (kind, riskLevel, explanation) = when {
                oldVal == null && newVal != null -> {
                    val (risk, expl) = assessRisk(key, null, newVal)
                    Triple(DiffKind.ADDED, risk, expl)
                }
                oldVal != null && newVal == null -> {
                    val (risk, expl) = assessRisk(key, oldVal, null)
                    Triple(DiffKind.REMOVED, risk, expl)
                }
                oldVal != newVal -> {
                    val (risk, expl) = assessRisk(key, oldVal, newVal)
                    Triple(DiffKind.MODIFIED, risk, expl)
                }
                else -> {
                    Triple(DiffKind.UNCHANGED, PropertyRiskLevel.SAFE, null)
                }
            }

            if (kind != DiffKind.UNCHANGED) {
                entries.add(
                    PropertyDiffEntry(
                        key = key,
                        oldValue = oldVal,
                        newValue = newVal,
                        kind = kind,
                        riskLevel = riskLevel,
                        riskExplanation = explanation,
                    ),
                )
            }
        }

        val added = entries.count { it.kind == DiffKind.ADDED }
        val removed = entries.count { it.kind == DiffKind.REMOVED }
        val modifiedCount = entries.count { it.kind == DiffKind.MODIFIED }
        val hasCritical = entries.any { it.riskLevel == PropertyRiskLevel.CRITICAL }

        return ConfigDiffResult(
            entries = entries,
            addedCount = added,
            removedCount = removed,
            modifiedCount = modifiedCount,
            hasCriticalRisks = hasCritical,
        )
    }

    private fun assessRisk(key: String, oldVal: String?, newVal: String?): Pair<PropertyRiskLevel, String?> {
        val lowerKey = key.lowercase()
        if (CRITICAL_SECURITY_KEYS.contains(lowerKey)) {
            if (lowerKey == "ro.crypto.state" && newVal != "encrypted") {
                return PropertyRiskLevel.CRITICAL to "Disabling force-encryption may cause /data mount failure or bootloop on stock recovery"
            }
            if (lowerKey == "ro.secure" && newVal == "0") {
                return PropertyRiskLevel.WARNING to "Setting ro.secure=0 compromises root protection and fails SafetyNet/Play Integrity"
            }
            if (lowerKey == "ro.debuggable" && newVal == "1") {
                return PropertyRiskLevel.INFO to "ro.debuggable=1 enables ADB root access and ART debug features"
            }
            return PropertyRiskLevel.WARNING to "Security configuration property modified"
        }
        if (BUILD_TYPE_KEYS.contains(lowerKey)) {
            return PropertyRiskLevel.INFO to "Build variant modified: $oldVal -> $newVal"
        }
        return PropertyRiskLevel.SAFE to null
    }

    fun createSnapshot(id: String, name: String, content: String, timestamp: Long = 0L): ConfigSnapshot {
        return ConfigSnapshot(
            id = id,
            name = name,
            timestampMillis = timestamp,
            rawContent = content,
            properties = parseProperties(content),
        )
    }

    fun rollbackTo(snapshot: ConfigSnapshot): String = snapshot.rawContent
}
