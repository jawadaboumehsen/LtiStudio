package io.ltirom.tooling.client.resilience

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Minimal circuit breaker guarding calls to the remote WSL daemon bridge.
 *
 * Reconstruction note: no surviving call site pinned the failure threshold or reset
 * timeout, only the `execute { ... }` call shape. These defaults (5 consecutive
 * failures trips the breaker, 30s cooldown before a half-open retry) are a judgment
 * call, not a recovered value — revisit if the daemon bridge shows different real
 * tuning needs.
 */
public class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 30_000L,
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private var state = State.CLOSED
    private var consecutiveFailures = 0
    private var openedAtMs = 0L

    public suspend fun <T> execute(block: suspend () -> T): T {
        mutex.withLock {
            if (state == State.OPEN) {
                val elapsed = System.currentTimeMillis() - openedAtMs
                if (elapsed >= resetTimeoutMs) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException(
                        "Circuit breaker is open; retry in ${resetTimeoutMs - elapsed}ms"
                    )
                }
            }
        }

        return try {
            val result = block()
            mutex.withLock {
                consecutiveFailures = 0
                state = State.CLOSED
            }
            result
        } catch (e: Exception) {
            mutex.withLock {
                consecutiveFailures++
                if (consecutiveFailures >= failureThreshold || state == State.HALF_OPEN) {
                    state = State.OPEN
                    openedAtMs = System.currentTimeMillis()
                }
            }
            throw e
        }
    }
}

public class CircuitBreakerOpenException(message: String) : Exception(message)
