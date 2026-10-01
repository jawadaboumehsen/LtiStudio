package io.ltirom.tooling.client.resilience

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CircuitBreakerTest {

    @Test
    fun `circuit breaker remains CLOSED on successful calls`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 2, resetTimeoutMs = 1000)
        val result = breaker.execute { "success" }

        assertEquals("success", result)
        assertEquals(CircuitState.CLOSED, breaker.getState())
    }

    @Test
    fun `circuit breaker trips to OPEN after threshold failures`() = runBlocking {
        val breaker = CircuitBreaker(failureThreshold = 2, resetTimeoutMs = 1000)

        assertFailsWith<IllegalStateException> {
            breaker.execute { throw IllegalStateException("fail 1") }
        }
        assertEquals(CircuitState.CLOSED, breaker.getState())

        assertFailsWith<IllegalStateException> {
            breaker.execute { throw IllegalStateException("fail 2") }
        }
        assertEquals(CircuitState.OPEN, breaker.getState())

        // Next call fails fast with CircuitBreakerOpenException without invoking block
        var blockInvoked = false
        assertFailsWith<CircuitBreakerOpenException> {
            breaker.execute {
                blockInvoked = true
                "should not run"
            }
        }
        assertEquals(false, blockInvoked)
    }

    @Test
    fun `circuit breaker transitions to HALF_OPEN after timeout and closes on success`() = runBlocking {
        var currentTime = 1000L
        val breaker = CircuitBreaker(
            failureThreshold = 1,
            resetTimeoutMs = 500L,
            clock = { currentTime }
        )

        assertFailsWith<RuntimeException> {
            breaker.execute { throw RuntimeException("fail") }
        }
        assertEquals(CircuitState.OPEN, breaker.getState())

        // Advance time beyond reset timeout
        currentTime += 600L
        assertEquals(CircuitState.HALF_OPEN, breaker.getState())

        // Successful probe closes circuit
        val result = breaker.execute { "recovered" }
        assertEquals("recovered", result)
        assertEquals(CircuitState.CLOSED, breaker.getState())
    }
}
