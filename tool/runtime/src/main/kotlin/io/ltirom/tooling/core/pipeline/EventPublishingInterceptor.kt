package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ToolResult
import io.ltirom.tooling.core.events.ToolDomainEvent
import io.ltirom.tooling.core.events.ToolEventBus

/**
 * Single Responsibility: Automatically publishes domain events on the [ToolEventBus]
 * for command execution lifecycle transitions (started, completed, failed).
 */
public class EventPublishingInterceptor(
    private val eventBus: ToolEventBus
) : ToolExecutionInterceptor {

    override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
        val startMs = System.currentTimeMillis()
        eventBus.publish(
            ToolDomainEvent.ExecutionStarted(
                toolId = chain.command.toolId,
                context = chain.context,
                commandName = chain.command.toolId.logicalName,
                arguments = chain.command.getArguments(),
                timestampEpochMs = startMs
            )
        )

        val result = chain.proceed()
        val durationMs = System.currentTimeMillis() - startMs

        when (result) {
            is ToolResult.Success -> {
                eventBus.publish(
                    ToolDomainEvent.ExecutionCompleted(
                        toolId = chain.command.toolId,
                        context = chain.context,
                        durationMs = durationMs,
                        isSuccess = true
                    )
                )
            }
            is ToolResult.Failure -> {
                val cause = when (result) {
                    is ToolResult.Failure.ExecutionError -> result.cause
                    is ToolResult.Failure.ValidationFailed -> result.cause
                    else -> null
                }
                eventBus.publish(
                    ToolDomainEvent.ExecutionFailed(
                        toolId = chain.command.toolId,
                        context = chain.context,
                        durationMs = durationMs,
                        errorMessage = result.message,
                        throwable = cause
                    )
                )
            }
        }

        return result
    }
}
