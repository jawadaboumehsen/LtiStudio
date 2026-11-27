/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import org.ide.lti.core.domain.pipeline.ContentManifest
import org.ide.lti.core.domain.pipeline.StageCheckpoint
import org.ide.lti.core.domain.pipeline.VerificationReceipt
import org.ide.lti.core.model.workspace.Workspace

data class StagingHandle(val id: String, val checkpoint: StageCheckpoint, val stagingRelPath: String)

sealed interface StagingResult {
    data class Staged(val handle: StagingHandle) : StagingResult
    data class InsufficientDisk(val requiredBytes: Long, val availableBytes: Long) : StagingResult
    data class SourceMissing(val relPath: String) : StagingResult
}

interface CheckpointPort {
    /** Budget check BEFORE any copy. */
    suspend fun stage(ws: Workspace, checkpoint: StageCheckpoint, requiredBytes: Long): StagingResult

    /** Actual bytes vs manifest, not just the descriptor. */
    suspend fun verify(ws: Workspace, checkpoint: StageCheckpoint): VerificationReceipt

    /** Atomic; returns the new immutable checkpoint. */
    suspend fun promote(ws: Workspace, handle: StagingHandle, actual: ContentManifest): StageCheckpoint

    suspend fun discard(ws: Workspace, handle: StagingHandle)
}
