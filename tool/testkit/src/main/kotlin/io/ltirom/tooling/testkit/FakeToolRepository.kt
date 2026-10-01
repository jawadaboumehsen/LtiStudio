package io.ltirom.tooling.testkit

import io.ltirom.tooling.core.CommandExecutionResult
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.StreamingToolRepository
import io.ltirom.tooling.core.ToolCommand
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.remote.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public data class RecordedInvocation(
    val command: ToolCommand<*>,
    val context: ExecutionContext,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

public class FakeToolRepository(
    private val defaultHandler: suspend (ToolCommand<*>, ExecutionContext) -> Any? = { cmd, _ ->
        CommandExecutionResult(
            exitCode = 0,
            stdout = "fake stdout for ${cmd.toolId.logicalName}",
            stderr = "",
            durationMs = 1L
        )
    }
) : StreamingToolRepository {

    private val _invocations = mutableListOf<RecordedInvocation>()
    public val invocations: List<RecordedInvocation> get() = _invocations.toList()

    private val customHandlers = mutableMapOf<ToolId, suspend (ToolCommand<*>, ExecutionContext) -> Any?>()
    private val customStreamHandlers = mutableMapOf<ToolId, (ToolCommand<*>, ExecutionContext) -> Flow<StreamEvent>>()

    public fun onTool(toolId: ToolId, handler: suspend (ToolCommand<*>, ExecutionContext) -> Any?) {
        customHandlers[toolId] = handler
    }

    public fun onStreamTool(toolId: ToolId, streamProvider: (ToolCommand<*>, ExecutionContext) -> Flow<StreamEvent>) {
        customStreamHandlers[toolId] = streamProvider
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <R> execute(command: ToolCommand<R>, context: ExecutionContext): R {
        _invocations.add(RecordedInvocation(command, context))
        val handler = customHandlers[command.toolId] ?: defaultHandler
        val raw = handler(command, context)
        if (raw is CommandExecutionResult) {
            return command.parseResult(raw)
        }
        return raw as R
    }

    override fun <R> stream(command: ToolCommand<R>, context: ExecutionContext): Flow<StreamEvent> {
        _invocations.add(RecordedInvocation(command, context))
        val customStream = customStreamHandlers[command.toolId]
        if (customStream != null) {
            return customStream(command, context)
        }
        return flow {
            emit(StreamEvent.OutputChunk("fake streaming stdout for ${command.toolId.logicalName}\n"))
            emit(StreamEvent.ExecutionFinished(exitCode = 0, durationMs = 1L))
        }
    }

    public fun reset() {
        _invocations.clear()
        customHandlers.clear()
        customStreamHandlers.clear()
    }

    public fun assertInvoked(toolId: ToolId, times: Int? = null) {
        val matches = _invocations.filter { it.command.toolId == toolId }
        if (times != null) {
            assertEquals(times, matches.size, "Expected $times executions of tool $toolId but found ${matches.size}")
        } else {
            assertTrue(matches.isNotEmpty(), "Expected at least one execution of tool $toolId but none occurred")
        }
    }

    public fun assertNeverInvoked(toolId: ToolId) {
        val matches = _invocations.filter { it.command.toolId == toolId }
        assertTrue(matches.isEmpty(), "Expected tool $toolId to never be executed, but found ${matches.size} calls")
    }

    public fun assertArgumentPresent(toolId: ToolId, expectedArg: String) {
        val matchingInvocation = _invocations.firstOrNull { it.command.toolId == toolId }
        assertTrue(matchingInvocation != null, "No execution of tool $toolId was recorded")
        val args = matchingInvocation.command.getArguments()
        assertTrue(
            args.contains(expectedArg),
            "Expected argument '$expectedArg' not found in arguments: $args"
        )
    }
}
