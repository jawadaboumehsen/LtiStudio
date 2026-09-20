package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolResult
import io.ltirom.tooling.core.ToolRisk
import io.ltirom.tooling.core.events.DefaultToolEventBus
import io.ltirom.tooling.core.events.ToolDomainEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventPublishingInterceptorTest {

    @Test
    fun `EventPublishingInterceptor emits start and completed on success`() = runTest {
        val bus = DefaultToolEventBus()
        val interceptor = EventPublishingInterceptor(bus)
        val context = ExecutionContext()

        val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
            override val toolId = ToolId.MKE2FS
            override val risk: ToolRisk = ToolRisk.READ_ONLY
            override fun getArguments() = listOf("-t", "ext4", "img.ext4")
            override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = "ok"
        }

        val chain = object : ExecutionChain<String> {
            override val command = mockCommand
            override val context = context
            override suspend fun proceed(
                command: io.ltirom.tooling.core.ToolCommand<String>,
                context: ExecutionContext
            ): ToolResult<String> {
                return ToolResult.Success("ok", 25L)
            }
        }

        val collected = mutableListOf<ToolDomainEvent>()
        val job = launch {
            bus.events.take(2).toList(collected)
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is ToolResult.Success<*>)

        job.join()
        assertEquals(2, collected.size)
        assertTrue(collected[0] is ToolDomainEvent.ExecutionStarted)
        assertTrue(collected[1] is ToolDomainEvent.ExecutionCompleted)
        assertTrue((collected[1] as ToolDomainEvent.ExecutionCompleted).isSuccess)
    }

    @Test
    fun `EventPublishingInterceptor emits failed event on failure`() = runTest {
        val bus = DefaultToolEventBus()
        val interceptor = EventPublishingInterceptor(bus)
        val context = ExecutionContext()

        val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
            override val toolId = ToolId.MKE2FS
            override val risk: ToolRisk = ToolRisk.READ_ONLY
            override fun getArguments() = listOf("-f", "corrupt.img")
            override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = ""
        }

        val chain = object : ExecutionChain<String> {
            override val command = mockCommand
            override val context = context
            override suspend fun proceed(
                command: io.ltirom.tooling.core.ToolCommand<String>,
                context: ExecutionContext
            ): ToolResult<String> {
                return ToolResult.Failure.NonZeroExit(
                    toolId = "e2fsck",
                    exitCode = 8,
                    stdout = "",
                    stderr = "Corrupted filesystem",
                    message = "Check failed"
                )
            }
        }

        val collected = mutableListOf<ToolDomainEvent>()
        val job = launch {
            bus.events.take(2).toList(collected)
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is ToolResult.Failure)

        job.join()
        assertEquals(2, collected.size)
        assertTrue(collected[0] is ToolDomainEvent.ExecutionStarted)
        assertTrue(collected[1] is ToolDomainEvent.ExecutionFailed)
        val failed = collected[1] as ToolDomainEvent.ExecutionFailed
        assertEquals("Check failed", failed.errorMessage)
    }
}
