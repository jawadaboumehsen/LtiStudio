/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/**
 * Everything a stage may read while planning. [runtimeValues] grows as steps with a `resultKey`
 * complete; a plan must be a pure function of this context.
 */
public data class StageContext(
    val workspace: Workspace,
    val snapshot: ConfigurationSnapshot,
    val target: TargetDevice,
    val runtimeValues: Map<String, String> = emptyMap(),
) {
    val wsPath: String get() = requireNotNull(workspace.linuxPath) { "Workspace ${workspace.id} has no Linux path" }

    /** Absolute path of a workspace-relative file. */
    public fun abs(relPath: String): String = "$wsPath/$relPath"

    public fun value(key: String): String? = runtimeValues[key]
    public fun has(key: String): Boolean = runtimeValues.containsKey(key)
    public fun exitOf(key: String): Int? = runtimeValues[RuntimeKeys.exitOf(key)]?.toIntOrNull()
    public fun longValue(key: String): Long? = runtimeValues[key]?.trim()?.toLongOrNull()
}

/**
 * One of the six product-owned pipeline stages (contracts/pipeline-stages.md rev. 3).
 *
 * Invariant: re-planning with more runtime values must keep every already-executed step
 * (same label, same position) — plans may only extend or change the tail.
 */
public interface StageDefinition {
    public val id: StageId

    /** Tool ids this stage invokes; the union across stages must equal the readiness set. */
    public val requiredToolIds: Set<String>

    /** Digest of every declared input; the stage is skipped when input and output digests match. */
    public fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String = ""): String

    /** Workspace-relative files that must exist (and are digested) once the stage succeeded. */
    public fun outputs(ctx: StageContext): List<String>

    public fun plan(ctx: StageContext): List<PipelineStep>
}
