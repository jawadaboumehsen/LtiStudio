/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.domain.pipeline.configuration.ValidationReport

/**
 * Context for resolving partition paths to filesystem locations within a staged ROM work tree.
 *
 * @property workTreeRelPath Relative or absolute path to the work tree root.
 * @property partitionRoots Mapping from partition name (e.g. "system", "product") to
 * relative directory under work tree.
 */
data class MutationContext(val workTreeRelPath: String, val partitionRoots: Map<String, String>)

/**
 * Describes the observed effect of a concrete mutation operation.
 *
 * @property operationId Identifier of the operation that caused this effect.
 * @property partition Partition name where the mutation occurred.
 * @property relativePath Relative path within the partition.
 * @property kind Type of modification applied or previewed.
 * @property sidecarUpdates Paths to work tree sidecar files (e.g. fs_config, file_context) that require
 * synchronized updates by downstream debloat or assembly stages. Sidecar files are not modified directly
 * by the file mutation adapter.
 */
data class MutationEffect(
    val operationId: String,
    val partition: String,
    val relativePath: String,
    val kind: EffectKind,
    val sidecarUpdates: List<String>,
)

enum class EffectKind {
    CREATED,
    REPLACED,
    DELETED,
    PROPERTY_SET,
    TEXT_PATCHED,
}

sealed interface MutationOutcome {
    data class Applied(val effects: List<MutationEffect>) : MutationOutcome
    data class Refused(val report: ValidationReport) : MutationOutcome
}

interface FileMutationPort {
    suspend fun preview(plan: ConcreteMutationPlan, ctx: MutationContext, assetRoot: String): MutationOutcome
    suspend fun apply(plan: ConcreteMutationPlan, ctx: MutationContext, assetRoot: String): MutationOutcome
}

object FileMutationCodes {
    const val PARTITION_NOT_IN_TREE: String = "PARTITION_NOT_IN_TREE"
    const val PATH_ESCAPES_PARTITION: String = "PATH_ESCAPES_PARTITION"
    const val SOURCE_ASSET_MISSING: String = "SOURCE_ASSET_MISSING"
    const val TARGET_MISSING: String = "TARGET_MISSING"
    const val PREIMAGE_MISMATCH: String = "PREIMAGE_MISMATCH"
    const val PROPERTY_KEY_INVALID: String = "PROPERTY_KEY_INVALID"
    const val TEXT_CONTEXT_NOT_FOUND: String = "TEXT_CONTEXT_NOT_FOUND"
    const val TEXT_CONTEXT_AMBIGUOUS: String = "TEXT_CONTEXT_AMBIGUOUS"
    const val OVERLAPPING_MUTATION: String = "OVERLAPPING_MUTATION"
    const val UNSUPPORTED_OPERATION: String = "UNSUPPORTED_OPERATION"
}
