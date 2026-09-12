package io.ltirom.tooling.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ToolResultTest {

    @Test
    fun `map transforms successful value`() {
        val success: ToolResult<Int> = ToolResult.Success(42, 10L)
        val mapped = success.map { it * 2 }

        assertTrue(mapped.isSuccess)
        assertEquals(84, (mapped as ToolResult.Success).value)
        assertEquals(10L, mapped.durationMs)
    }

    @Test
    fun `map preserves failure`() {
        val failure: ToolResult<Int> = ToolResult.Failure.ToolNotFound("aapt2", "Tool not found")
        val mapped = failure.map { it * 2 }

        assertTrue(mapped.isFailure)
        assertTrue(mapped is ToolResult.Failure.ToolNotFound)
        assertEquals("aapt2", (mapped as ToolResult.Failure.ToolNotFound).toolId)
    }

    @Test
    fun `flatMap chains operations`() {
        val initial: ToolResult<String> = ToolResult.Success("100", 5L)
        val chained = initial.flatMap { str ->
            ToolResult.Success(str.toInt(), 15L)
        }

        assertTrue(chained.isSuccess)
        assertEquals(100, chained.getOrThrow())
    }

    @Test
    fun `getOrElse returns fallback on failure`() {
        val failure: ToolResult<String> = ToolResult.Failure.ConnectionTimeout(5000L, "Timed out")
        val value = failure.getOrElse { "fallback" }

        assertEquals("fallback", value)
    }

    @Test
    fun `onSuccess and onFailure callbacks work correctly`() {
        var successCalled = false
        var failureCalled = false

        ToolResult.Success("ok", 1L).onSuccess { successCalled = true }
        ToolResult.Failure.SecurityViolation("blocked").onFailure { failureCalled = true }

        assertTrue(successCalled)
        assertTrue(failureCalled)
    }
}
