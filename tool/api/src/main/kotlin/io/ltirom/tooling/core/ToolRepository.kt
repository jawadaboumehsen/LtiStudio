package io.ltirom.tooling.core

public interface ToolRepository {
    public suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): R
}
