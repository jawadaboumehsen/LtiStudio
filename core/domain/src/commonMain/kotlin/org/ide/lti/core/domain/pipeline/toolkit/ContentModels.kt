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
enum class DeodexStatus {
    Done,
    AlreadyDone,
    NoCompanion,
    Failed,
}

/** Which packages the deodexer looks at. */
@Serializable
enum class DeodexScope(val words: String) {
    SERVICES("services.jar only"),
    FRAMEWORK("every jar and apk in framework/"),
    APPS("every app"),
    FILE("one file"),
}

/**
 * Result of de-odex operation per application.
 *
 * Key: `content.deodex.results`
 */
@Serializable
data class DeodexAppResult(val app: String, val status: DeodexStatus, val reason: String? = null)

/**
 * Result of modifying code/resources in an APK or JAR container (FR-029).
 *
 * Key: `content.apk.results`
 */
@Serializable
data class ApkPatchResult(
    val file: String,
    val status: String,
    val reason: String? = null,
    val restorePointId: String? = null,
)

/**
 * Result of modifying a system library inside a container/APEX module (FR-030).
 */
@Serializable
data class ApexPatchResult(
    val module: String,
    val alreadyChanged: Boolean,
    val success: Boolean,
    val message: String,
    val restorePointId: String? = null,
)

/**
 * Kind of configuration file being edited (FR-031).
 */
@Serializable
enum class ConfigKind {
    FSTAB,
    BUILD_PROP,
    FS_CONFIG,
    FILE_CONTEXTS,
}

/**
 * Result of validated configuration edit with before/after comparison (FR-031).
 */
@Serializable
data class ConfigEditDiff(
    val oldText: String,
    val newText: String,
    val differences: List<String>,
    val restorePointId: String? = null,
) {
    val hasChanges: Boolean get() = differences.isNotEmpty()
}

/**
 * Difference between two text files (FR-033).
 */
@Serializable
data class FileDiff(
    val pathA: String,
    val pathB: String,
    val identical: Boolean,
    val lineDifferences: List<String> = emptyList(),
)

/**
 * Difference between two archives (FR-033).
 */
@Serializable
data class ArchiveDiff(
    val missingInB: List<String> = emptyList(),
    val extraInB: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
) {
    val identical: Boolean get() = missingInB.isEmpty() && extraInB.isEmpty() && modified.isEmpty()
}

/**
 * Difference between two workspace directory trees (FR-033).
 */
@Serializable
data class WorkspaceDiff(
    val missingInB: List<String> = emptyList(),
    val extraInB: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
) {
    val identical: Boolean get() = missingInB.isEmpty() && extraInB.isEmpty() && modified.isEmpty()
}

@Serializable
enum class MethodChangeKind {
    Added,
    Removed,
    Modified,
}

@Serializable
data class MethodDifference(
    val className: String,
    val methodName: String,
    val changeKind: MethodChangeKind,
    /** For a modified method, its changed instructions (`- old`, `+ new`); empty when too large to diff. */
    val lines: List<String> = emptyList(),
    /** Instructions on each side; null where the method is absent (and for the class-level entry). */
    val instructionsA: Int? = null,
    val instructionsB: Int? = null,
    /** Modified only in the compiler's numbering (labels, registers, synthetic class indices). */
    val renumberedOnly: Boolean = false,
)

/**
 * Difference between two code packages down to methods (FR-033).
 */
@Serializable
data class CodePackageDiff(
    val classDifferences: List<String> = emptyList(),
    val methodDifferences: List<MethodDifference> = emptyList(),
    /** The dex files of the two packages that differ: `changed classes2.dex`, `added classes4.dex`. */
    val dexDifferences: List<String> = emptyList(),
) {
    val identical: Boolean get() = classDifferences.isEmpty() && methodDifferences.isEmpty()

    /** The methods that really changed, without those [MethodDifference.renumberedOnly]. */
    val realChanges: List<MethodDifference> get() = methodDifferences.filterNot { it.renumberedOnly }
}
