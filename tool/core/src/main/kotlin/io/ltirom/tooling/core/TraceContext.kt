package io.ltirom.tooling.core

import java.util.UUID

/**
 * Distributed trace/span identifiers propagated across the daemon HTTP bridge.
 */
public data class TraceContext(
    val traceId: String,
    val spanId: String,
) {
    public companion object {
        public const val HEADER_TRACE_ID: String = "X-LtiRom-Trace-Id"
        public const val HEADER_SPAN_ID: String = "X-LtiRom-Span-Id"

        public fun create(): TraceContext = TraceContext(
            traceId = UUID.randomUUID().toString(),
            spanId = UUID.randomUUID().toString().substring(0, 8),
        )
    }
}
