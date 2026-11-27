package io.ltirom.tooling.core.events

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Domain event hierarchy representing lifecycle transitions and notifications
 * emitted during tool execution across the entire system.
 *
 * Adheres to:
 * - Single Responsibility Principle: represents immutable domain events.
 * - Open/Closed Principle: new events can be added without altering existing listeners.
 */
sealed interface ToolDomainEvent {
    val toolId: ToolId
    val context: ExecutionContext
    val timestampEpochMs: Long

    /**
     * Emitted immediately before tool execution begins.
     */
    data class ExecutionStarted(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        val commandName: String,
        val arguments: List<String>,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent

    /**
     * Emitted upon successful tool completion.
     */
    data class ExecutionCompleted(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        val durationMs: Long,
        val isSuccess: Boolean = true,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent

    /**
     * Emitted when tool execution fails (e.g. non-zero exit, timeout, safety rejection, exception).
     */
    data class ExecutionFailed(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        val durationMs: Long,
        val errorMessage: String,
        val throwable: Throwable? = null,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent
}

/**
 * Reactive Domain Event Bus contract.
 * Decouples publishers (execution interceptors, repositories) from consumers (UI ViewModels, telemetry, metrics).
 */
interface ToolEventBus {
    /**
     * Hot reactive stream of all domain events.
     */
    val events: SharedFlow<ToolDomainEvent>

    /**
     * Suspends until the event is emitted to all active subscribers.
     */
    suspend fun publish(event: ToolDomainEvent)

    /**
     * Non-suspending emission, useful in non-coroutine or fast-path call sites.
     */
    fun tryPublish(event: ToolDomainEvent): Boolean
}

/**
 * Default in-memory implementation of [ToolEventBus] backed by [MutableSharedFlow].
 */
class DefaultToolEventBus(
    replay: Int = 10,
    extraBufferCapacity: Int = 64
) : ToolEventBus {

    private val _events = MutableSharedFlow<ToolDomainEvent>(
        replay = replay,
        extraBufferCapacity = extraBufferCapacity
    )

    override val events: SharedFlow<ToolDomainEvent> = _events.asSharedFlow()

    override suspend fun publish(event: ToolDomainEvent) {
        _events.emit(event)
    }

    override fun tryPublish(event: ToolDomainEvent): Boolean {
        return _events.tryEmit(event)
    }
}
