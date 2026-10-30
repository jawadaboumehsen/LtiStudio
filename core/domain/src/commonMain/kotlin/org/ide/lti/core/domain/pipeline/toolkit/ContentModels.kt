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

/**
 * Status of de-odexing a single application (FR-028).
 */
@Serializable
public enum class DeodexStatus {
    Done,
    AlreadyDone,
    NoCompanion,
    Failed,
}

/**
 * Result of de-odex operation per application.
 *
 * Key: `content.deodex.results`
 */
@Serializable
public data class DeodexAppResult(
    public val app: String,
    public val status: DeodexStatus,
    public val reason: String? = null,
)

/**
 * Result of modifying code/resources in an APK or JAR container (FR-029).
 *
 * Key: `content.apk.results`
 */
@Serializable
public data class ApkPatchResult(
    public val file: String,
    public val status: String,
    public val reason: String? = null,
    public val restorePointId: String? = null,
)

/**
 * Result of modifying a system library inside a container/APEX module (FR-030).
 */
@Serializable
public data class ApexPatchResult(
    public val module: String,
    public val alreadyChanged: Boolean,
    public val success: Boolean,
    public val message: String,
    public val restorePointId: String? = null,
)

/**
 * Kind of configuration file being edited (FR-031).
 */
@Serializable
public enum class ConfigKind {
    FSTAB,
    BUILD_PROP,
    FS_CONFIG,
    FILE_CONTEXTS,
}

/**
 * Result of validated configuration edit with before/after comparison (FR-031).
 */
@Serializable
public data class ConfigEditDiff(
    public val oldText: String,
    public val newText: String,
    public val differences: List<String>,
    public val restorePointId: String? = null,
) {
    public val hasChanges: Boolean get() = differences.isNotEmpty()
}

/**
 * Difference between two text files (FR-033).
 */
@Serializable
public data class FileDiff(
    public val pathA: String,
    public val pathB: String,
    public val identical: Boolean,
    public val lineDifferences: List<String> = emptyList(),
)

/**
 * Difference between two archives (FR-033).
 */
@Serializable
public data class ArchiveDiff(
    public val missingInB: List<String> = emptyList(),
    public val extraInB: List<String> = emptyList(),
    public val modified: List<String> = emptyList(),
) {
    public val identical: Boolean get() = missingInB.isEmpty() && extraInB.isEmpty() && modified.isEmpty()
}

/**
 * Difference between two workspace directory trees (FR-033).
 */
@Serializable
public data class WorkspaceDiff(
    public val missingInB: List<String> = emptyList(),
    public val extraInB: List<String> = emptyList(),
    public val modified: List<String> = emptyList(),
) {
    public val identical: Boolean get() = missingInB.isEmpty() && extraInB.isEmpty() && modified.isEmpty()
}

@Serializable
public enum class MethodChangeKind {
    Added,
    Removed,
    Modified,
}

@Serializable
public data class MethodDifference(
    public val className: String,
    public val methodName: String,
    public val changeKind: MethodChangeKind,
    /** For a modified method, its changed instructions (`- old`, `+ new`); empty when too large to diff. */
    public val lines: List<String> = emptyList(),
)

/**
 * Difference between two code packages down to methods (FR-033).
 */
@Serializable
public data class CodePackageDiff(
    public val classDifferences: List<String> = emptyList(),
    public val methodDifferences: List<MethodDifference> = emptyList(),
) {
    public val identical: Boolean get() = classDifferences.isEmpty() && methodDifferences.isEmpty()
}
