package org.ide.lti.core.domain.ports

import org.ide.lti.core.domain.pipeline.ContentManifest
import org.ide.lti.core.domain.pipeline.StageCheckpoint
import org.ide.lti.core.domain.pipeline.VerificationReceipt
import org.ide.lti.core.model.workspace.Workspace

public data class StagingHandle(val id: String, val checkpoint: StageCheckpoint, val stagingRelPath: String)

public sealed interface StagingResult {
    public data class Staged(val handle: StagingHandle) : StagingResult
    public data class InsufficientDisk(val requiredBytes: Long, val availableBytes: Long) : StagingResult
    public data class SourceMissing(val relPath: String) : StagingResult
}

public interface CheckpointPort {
    /** Budget check BEFORE any copy. */
    public suspend fun stage(ws: Workspace, checkpoint: StageCheckpoint, requiredBytes: Long): StagingResult

    /** Actual bytes vs manifest, not just the descriptor. */
    public suspend fun verify(ws: Workspace, checkpoint: StageCheckpoint): VerificationReceipt

    /** Atomic; returns the new immutable checkpoint. */
    public suspend fun promote(ws: Workspace, handle: StagingHandle, actual: ContentManifest): StageCheckpoint

    public suspend fun discard(ws: Workspace, handle: StagingHandle)
}
