package io.ltirom.tooling.core

interface ToolRepository {
    suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): R
}
