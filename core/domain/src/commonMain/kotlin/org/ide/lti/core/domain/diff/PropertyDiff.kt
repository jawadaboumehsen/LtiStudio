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

enum class DiffKind {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED,
}

enum class PropertyRiskLevel {
    SAFE,
    INFO,
    WARNING,
    CRITICAL,
}

data class PropertyDiffEntry(
    val key: String,
    val oldValue: String?,
    val newValue: String?,
    val kind: DiffKind,
    val riskLevel: PropertyRiskLevel = PropertyRiskLevel.SAFE,
    val riskExplanation: String? = null,
)

data class ConfigSnapshot(
    val id: String,
    val name: String,
    val timestampMillis: Long,
    val rawContent: String,
    val properties: Map<String, String>,
)

data class ConfigDiffResult(
    val entries: List<PropertyDiffEntry>,
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val hasCriticalRisks: Boolean,
)
