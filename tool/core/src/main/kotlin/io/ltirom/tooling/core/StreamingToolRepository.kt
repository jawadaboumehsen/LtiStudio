package io.ltirom.tooling.core

import io.ltirom.tooling.core.remote.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * An extension of [ToolRepository] that provides real-time reactive streaming of process output.
 * Follows the Interface Segregation Principle (ISP) so that basic consumers only depend on
 * [ToolRepository] without needing to know about reactive flows.
 */
public interface StreamingToolRepository : ToolRepository {
    /**
     * Executes a tool command and returns a reactive [Flow] of [StreamEvent]s (stdout, stderr, progress, and finish).
     */
    public fun <R> stream(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): Flow<StreamEvent>
}
