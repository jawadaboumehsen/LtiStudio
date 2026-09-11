package org.ide.lti.core.model.run

import kotlinx.serialization.Serializable

/**
 * Execution state of a product-owned ROM pipeline build run.
 *
 * State transitions:
 * QUEUED -> RUNNING -> (PAUSED_WAITING_FOR_APP <-> RUNNING) -> (CANCELLING ->) CANCELLED | SUCCEEDED | FAILED | INTERRUPTED
 */
@Serializable
public enum class RunState {
    QUEUED,
    RUNNING,
    PAUSED_WAITING_FOR_APP,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED,
    INTERRUPTED;

    public val isTerminal: Boolean
        get() = this == CANCELLED || this == SUCCEEDED || this == FAILED || this == INTERRUPTED

    public val isActive: Boolean
        get() = !isTerminal
}
