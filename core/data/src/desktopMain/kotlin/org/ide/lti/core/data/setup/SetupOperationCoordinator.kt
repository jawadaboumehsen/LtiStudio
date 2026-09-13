package org.ide.lti.core.data.setup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.domain.setup.SetupOutcome
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-lifetime coordinator enforcing single-owner environment operations and honest typed outcomes.
 *
 * Enforces:
 * - "One environment-level atomic operation owner covers setup, retry, remediation, repair, tool test and cache reset;
 *    concurrent requests return Busy rather than queue surprise work." (FR-005)
 * - "Lock admission atomically, release lock before IO; concurrent mutation/test/reset returns Busy with owner."
 * - "Cleanup occurs on every terminal path." (FR-005, FR-006)
 * - "CancellationException is rethrown after cleanup; durable detach and explicit cancel are distinct." (FR-006)
 */
public class SetupOperationCoordinator {

    public data class OperationOwner(
        val operationId: String,
        val kind: String,
        val environmentKey: String,
        val startedAtEpochMs: Long = System.currentTimeMillis(),
        val job: Job? = null,
    )

    private val activeOwners = ConcurrentHashMap<String, OperationOwner>()
    private val admissionLock = Any()
    private val _outcomeFlow = MutableStateFlow<SetupOutcome?>(null)
    public val outcomeFlow: Flow<SetupOutcome?> = _outcomeFlow.asStateFlow()

    public fun isBusy(environmentKey: String): Boolean = activeOwners.containsKey(environmentKey)

    public fun currentOwner(environmentKey: String): OperationOwner? = activeOwners[environmentKey]

    /**
     * Attempts atomic admission for an operation on [environmentKey].
     * Admission decision is held only momentarily; lock is released before invoking [block].
     */
    public suspend fun withAdmission(
        environmentKey: String,
        operationId: String,
        kind: String,
        block: suspend () -> SetupOutcome,
    ): SetupOutcome {
        val job = currentCoroutineContext()[Job]
        val existing = synchronized(admissionLock) {
            val current = activeOwners[environmentKey]
            if (current != null) {
                return@synchronized current
            }
            activeOwners[environmentKey] = OperationOwner(operationId, kind, environmentKey, job = job)
            null
        }

        if (existing != null) {
            return SetupOutcome.Busy(ownerId = existing.operationId)
        }

        return try {
            val outcome = block()
            _outcomeFlow.value = outcome
            outcome
        } catch (ce: CancellationException) {
            _outcomeFlow.value = SetupOutcome.Cancelled
            throw ce
        } catch (t: Throwable) {
            val failed = SetupOutcome.Failed(
                stage = null,
                reason = t.message ?: "Operation failed with unexpected exception",
            )
            _outcomeFlow.value = failed
            failed
        } finally {
            synchronized(admissionLock) {
                activeOwners.remove(environmentKey)
            }
        }
    }

    public fun cancelActiveOperation(environmentKey: String): SetupOutcome {
        val owner = synchronized(admissionLock) {
            activeOwners[environmentKey]
        } ?: return SetupOutcome.Failed(stage = null, reason = "No active operation to cancel.")

        owner.job?.cancel(CancellationException("Operation explicitly cancelled"))
        val cancelled = SetupOutcome.Cancelled
        _outcomeFlow.value = cancelled
        return cancelled
    }
}
