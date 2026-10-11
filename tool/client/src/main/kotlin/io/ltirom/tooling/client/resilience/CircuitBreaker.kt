package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ToolingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class CircuitBreakerOpenException(
    message: String = "Circuit breaker is OPEN. Fast-failing calls until cooldown expires.",
) : ToolingException(message)

public class CommandAdmissionException(message: String = "Command admission is closed; client heartbeat required") :
    ToolingException(message)

public class ToolingClientException(message: String) : ToolingException(message)

public enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

/**
 * Circuit Breaker pattern to protect against cascading remote connection failures.
 * If consecutive failures exceed [failureThreshold], trips to [CircuitState.OPEN] to
 * fail-fast immediately without waiting for long network timeouts.
 */
public class CircuitBreaker(
    public val failureThreshold: Int = 3,
    public val resetTimeoutMs: Long = 10_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var failureCount = 0
    private var lastFailureEpochMs = 0L
    private var state = CircuitState.CLOSED

    /** The connection the counters belong to; another connection starts from a clean, closed breaker. */
    private var owner: Any? = null

    public suspend fun getState(): CircuitState = mutex.withLock { checkState() }

    private fun checkState(): CircuitState {
        val now = clock()
        if (state == CircuitState.OPEN && now - lastFailureEpochMs > resetTimeoutMs) {
            state = CircuitState.HALF_OPEN
        }
        return state
    }

    public suspend fun <T> execute(block: suspend () -> T): T = execute(Unit, block)

    /**
     * Runs [block] under the breaker of [scope] (the connection it talks to). Failures of a dead server must not
     * hold back its replacement, and a late result of the old connection must not change the new one's state.
     */
    public suspend fun <T> execute(scope: Any, block: suspend () -> T): T {
        mutex.withLock {
            if (owner != scope) {
                owner = scope
                failureCount = 0
                lastFailureEpochMs = 0L
                state = CircuitState.CLOSED
            }
            when (checkState()) {
                CircuitState.OPEN -> {
                    throw CircuitBreakerOpenException(
                        "Circuit breaker is OPEN ($failureCount consecutive failures). Cooldown active for ${resetTimeoutMs - (clock() - lastFailureEpochMs)} ms",
                    )
                }
                CircuitState.CLOSED, CircuitState.HALF_OPEN -> { /* Allow execution */ }
            }
        }

        return try {
            val result = block()
            onSuccess(scope)
            result
        } catch (ce: CancellationException) {
            throw ce
        } catch (unconfirmed: io.ltirom.tooling.core.RemoteCancellationUnconfirmedException) {
            throw unconfirmed
        } catch (cae: CommandAdmissionException) {
            throw cae
        } catch (tce: ToolingClientException) {
            throw tce
        } catch (e: Throwable) {
            onFailure(scope)
            throw e
        }
    }

    private suspend fun onSuccess(scope: Any) = mutex.withLock {
        if (owner != scope) return@withLock // a connection that has since been replaced
        failureCount = 0
        state = CircuitState.CLOSED
    }

    private suspend fun onFailure(scope: Any) = mutex.withLock {
        if (owner != scope) return@withLock
        failureCount++
        lastFailureEpochMs = clock()
        if (failureCount >= failureThreshold || state == CircuitState.HALF_OPEN) {
            state = CircuitState.OPEN
        }
    }

    public suspend fun reset() = mutex.withLock {
        owner = null
        failureCount = 0
        lastFailureEpochMs = 0L
        state = CircuitState.CLOSED
    }
}
