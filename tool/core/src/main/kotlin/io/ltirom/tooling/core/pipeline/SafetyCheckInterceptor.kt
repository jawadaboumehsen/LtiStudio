package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ToolSafetyPolicy
import io.ltirom.tooling.core.ToolResult

/**
 * Single Responsibility: Enforces tool safety constraints and validates input/output artifacts.
 */
public class SafetyCheckInterceptor(
    private val safetyPolicy: ToolSafetyPolicy = ToolSafetyPolicy()
) : ToolExecutionInterceptor {

    override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
        return try {
            safetyPolicy.checkSafety(chain.command, chain.context)
            chain.command.validateInputArtifacts()
            val result = chain.proceed(chain.command, chain.context)
            if (result is ToolResult.Success) {
                chain.command.validateOutputArtifacts()
            }
            result
        } catch (e: Throwable) {
            ToolResult.Failure.ValidationFailed(e.message ?: "Safety validation failed", e)
        }
    }
}
