package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.AuditRecord
import io.ltirom.tooling.core.Redactor
import io.ltirom.tooling.core.ToolResult
import io.ltirom.tooling.core.TraceContext

/**
 * Single Responsibility: Observability and security audit trail recording.
 */
public class AuditLoggingInterceptor(
    private val redactor: Redactor = Redactor(),
    private val auditLogger: (AuditRecord) -> Unit = {}
) : ToolExecutionInterceptor {

    override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
        val startTime = System.currentTimeMillis()
        val result = chain.proceed(chain.command, chain.context)
        val durationMs = System.currentTimeMillis() - startTime

        val exitCode = when (result) {
            is ToolResult.Success -> 0
            is ToolResult.Failure.NonZeroExit -> result.exitCode
            is ToolResult.Failure -> -1
        }

        val record = AuditRecord(
            timestamp = startTime,
            toolId = chain.command.toolId.logicalName,
            risk = chain.command.risk.name,
            arguments = chain.command.getArguments().map { redactor.redact(it) },
            durationMs = durationMs,
            exitCode = exitCode,
            stdoutHash = "",
            stderrHash = "",
            binarySha256 = "bin-${chain.command.toolId.logicalName}",
            traceId = TraceContext.create().traceId
        )
        auditLogger(record)

        return result
    }
}
