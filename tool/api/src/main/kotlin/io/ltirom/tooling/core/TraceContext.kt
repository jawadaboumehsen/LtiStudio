package io.ltirom.tooling.core

import java.util.UUID

/**
 * Distributed trace/span identifiers propagated across the daemon HTTP bridge.
 */
data class TraceContext(
    val traceId: String,
    val spanId: String,
) {
    companion object {
        const val HEADER_TRACE_ID: String = "X-LtiRom-Trace-Id"
        const val HEADER_SPAN_ID: String = "X-LtiRom-Span-Id"

        fun create(): TraceContext = TraceContext(
            traceId = UUID.randomUUID().toString(),
            spanId = UUID.randomUUID().toString().substring(0, 8),
        )
    }
}
