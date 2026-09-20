package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolExecutionPipelineTest {

    private class TestCommand(
        override val toolId: ToolId = ToolId.ADB,
        override val risk: ToolRisk = ToolRisk.READ_ONLY
    ) : ToolCommand<String> {
        override fun getArguments(): List<String> = listOf("devices")
        override fun parseResult(result: CommandExecutionResult): String = result.stdout
    }

    @Test
    fun `pipeline executes interceptors in declared order`() = runBlocking {
        val executionOrder = mutableListOf<String>()

        val interceptorA = object : ToolExecutionInterceptor {
            override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
                executionOrder.add("A_before")
                val res = chain.proceed()
                executionOrder.add("A_after")
                return res
            }
        }

        val interceptorB = object : ToolExecutionInterceptor {
            override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
                executionOrder.add("B_before")
                val res = chain.proceed()
                executionOrder.add("B_after")
                return res
            }
        }

        val pipeline = ToolExecutionPipeline(listOf(interceptorA, interceptorB))
        val result = pipeline.execute(TestCommand(), ExecutionContext()) { _, _ ->
            executionOrder.add("terminal")
            ToolResult.Success("ok", 10L)
        }

        assertTrue(result is ToolResult.Success)
        assertEquals(listOf("A_before", "B_before", "terminal", "B_after", "A_after"), executionOrder)
    }

    @Test
    fun `interceptor can short-circuit pipeline without executing terminal`() = runBlocking {
        var terminalCalled = false

        val shortCircuitInterceptor = object : ToolExecutionInterceptor {
            override suspend fun <R> intercept(chain: ExecutionChain<R>): ToolResult<R> {
                return ToolResult.Failure.SecurityViolation("Blocked by policy")
            }
        }

        val pipeline = ToolExecutionPipeline(listOf(shortCircuitInterceptor))
        val result = pipeline.execute(TestCommand(), ExecutionContext()) { _, _ ->
            terminalCalled = true
            ToolResult.Success("ok", 5L)
        }

        assertTrue(result is ToolResult.Failure.SecurityViolation)
        assertEquals(false, terminalCalled)
    }
}
