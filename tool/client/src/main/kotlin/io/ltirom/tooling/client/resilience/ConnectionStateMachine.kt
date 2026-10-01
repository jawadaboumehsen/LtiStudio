package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive states representing the client-server bridge lifecycle.
 */
public sealed interface ConnectionState {
    public data class Disconnected(val reason: String? = null) : ConnectionState
    public data class Connecting(val attempt: Int = 1) : ConnectionState
    public data class Connected(val descriptor: ServerConnectionDescriptor) : ConnectionState
    public data class Reconnecting(val attempt: Int, val previousDescriptor: ServerConnectionDescriptor) : ConnectionState
    public data class Failed(val error: Throwable) : ConnectionState
    public data object Disposed : ConnectionState
}

/**
 * Thread-safe holder for reactive connection state updates.
 */
public class ConnectionStateHolder(
    initialState: ConnectionState = ConnectionState.Disconnected()
) {
    private val _state = MutableStateFlow<ConnectionState>(initialState)
    public val state: StateFlow<ConnectionState> = _state.asStateFlow()

    public val currentState: ConnectionState get() = _state.value

    public fun transitionTo(newState: ConnectionState) {
        _state.value = newState
    }

    public val isConnected: Boolean
        get() = _state.value is ConnectionState.Connected
}
