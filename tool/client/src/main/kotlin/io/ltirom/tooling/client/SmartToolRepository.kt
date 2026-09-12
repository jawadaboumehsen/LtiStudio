package io.ltirom.tooling.client

import io.ltirom.tooling.core.*
import io.ltirom.tooling.core.remote.StreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * An adaptive [ToolRepository] that automatically selects local process execution
 * when running on a Linux or macOS host, and delegates to [remoteWslRepository]
 * when running on Windows (or when [forceRemote] is enabled).
 *
 * Implements [StreamingToolRepository] to provide reactive stream routing when supported.
 */
public class SmartToolRepository(
    private val localRepository: ToolRepository,
    private val remoteWslRepository: ToolRepository,
    private val forceRemote: Boolean = false
) : StreamingToolRepository {

    public val isUsingRemote: Boolean
        get() = forceRemote || (PlatformIdentity.CURRENT.os == OS.WINDOWS)

    override suspend fun <R> execute(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): R {
        val target = if (isUsingRemote) remoteWslRepository else localRepository
        return target.execute(command, context)
    }

    override fun <R> stream(
        command: ToolCommand<R>,
        context: ExecutionContext
    ): Flow<StreamEvent> {
        val target = if (isUsingRemote) remoteWslRepository else localRepository
        if (target is StreamingToolRepository) {
            return target.stream(command, context)
        }
        throw UnsupportedOperationException("Underlying repository ${target::class.simpleName} does not support StreamingToolRepository")
    }
}
