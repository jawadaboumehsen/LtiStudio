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
public sealed interface ToolDomainEvent {
    public val toolId: ToolId
    public val context: ExecutionContext
    public val timestampEpochMs: Long

    /**
     * Emitted immediately before tool execution begins.
     */
    public data class ExecutionStarted(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        public val commandName: String,
        public val arguments: List<String>,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent

    /**
     * Emitted upon successful tool completion.
     */
    public data class ExecutionCompleted(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        public val durationMs: Long,
        public val isSuccess: Boolean = true,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent

    /**
     * Emitted when tool execution fails (e.g. non-zero exit, timeout, safety rejection, exception).
     */
    public data class ExecutionFailed(
        override val toolId: ToolId,
        override val context: ExecutionContext,
        public val durationMs: Long,
        public val errorMessage: String,
        public val throwable: Throwable? = null,
        override val timestampEpochMs: Long = System.currentTimeMillis()
    ) : ToolDomainEvent
}

/**
 * Reactive Domain Event Bus contract.
 * Decouples publishers (execution interceptors, repositories) from consumers (UI ViewModels, telemetry, metrics).
 */
public interface ToolEventBus {
    /**
     * Hot reactive stream of all domain events.
     */
    public val events: SharedFlow<ToolDomainEvent>

    /**
     * Suspends until the event is emitted to all active subscribers.
     */
    public suspend fun publish(event: ToolDomainEvent)

    /**
     * Non-suspending emission, useful in non-coroutine or fast-path call sites.
     */
    public fun tryPublish(event: ToolDomainEvent): Boolean
}

/**
 * Default in-memory implementation of [ToolEventBus] backed by [MutableSharedFlow].
 */
public class DefaultToolEventBus(
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
