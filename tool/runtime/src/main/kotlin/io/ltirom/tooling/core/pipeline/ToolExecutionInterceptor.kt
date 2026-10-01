package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolCommand
import io.ltirom.tooling.core.ToolResult

/**
 * Single Responsibility: Intercepts tool command execution to enforce cross-cutting concerns
 * (safety validation, path translation, tracing, auditing, caching, metrics).
 *
 * Adheres to Open/Closed Principle: new execution behaviors can be added as interceptors
 * without modifying any repository or command implementations.
 */
public interface ToolExecutionInterceptor {
    public suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R>
}

/**
 * Encapsulates the execution context and proceeding mechanism in the interceptor chain.
 */
public interface ExecutionChain<R> {
    public val command: ToolCommand<R>
    public val context: ExecutionContext

    /**
     * Proceeds to the next interceptor in the chain, optionally with an updated command or context.
     */
    public suspend fun proceed(
        command: ToolCommand<R> = this.command,
        context: ExecutionContext = this.context
    ): ToolResult<R>
}

/**
 * Composable execution pipeline organizing an ordered chain of [ToolExecutionInterceptor]s
 * ending in a terminal execution block.
 */
public class ToolExecutionPipeline(
    public val interceptors: List<ToolExecutionInterceptor> = emptyList()
) {
    public suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext,
        terminal: suspend (ToolCommand<R>, ExecutionContext) -> ToolResult<R>
    ): ToolResult<R> {
        val chain = RealExecutionChain(
            interceptors = interceptors,
            index = 0,
            command = command,
            context = context,
            terminal = terminal
        )
        return chain.proceed(command, context)
    }

    private class RealExecutionChain<R>(
        private val interceptors: List<ToolExecutionInterceptor>,
        private val index: Int,
        override val command: ToolCommand<R>,
        override val context: ExecutionContext,
        private val terminal: suspend (ToolCommand<R>, ExecutionContext) -> ToolResult<R>
    ) : ExecutionChain<R> {

        override suspend fun proceed(
            command: ToolCommand<R>,
            context: ExecutionContext
        ): ToolResult<R> {
            if (index >= interceptors.size) {
                return terminal(command, context)
            }
            val nextChain = RealExecutionChain(
                interceptors = interceptors,
                index = index + 1,
                command = command,
                context = context,
                terminal = terminal
            )
            val interceptor = interceptors[index]
            return interceptor.intercept(nextChain)
        }
    }
}
