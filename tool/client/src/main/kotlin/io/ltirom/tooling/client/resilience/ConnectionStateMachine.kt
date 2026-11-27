package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive states representing the client-server bridge lifecycle.
 */
sealed interface ConnectionState {
    data class Disconnected(val reason: String? = null) : ConnectionState
    data class Connecting(val attempt: Int = 1) : ConnectionState
    data class Connected(val descriptor: ServerConnectionDescriptor) : ConnectionState
    data class Reconnecting(val attempt: Int, val previousDescriptor: ServerConnectionDescriptor) : ConnectionState
    data class Failed(val error: Throwable) : ConnectionState
    data object Disposed : ConnectionState
}

/**
 * Thread-safe holder for reactive connection state updates.
 */
class ConnectionStateHolder(
    initialState: ConnectionState = ConnectionState.Disconnected()
) {
    private val _state = MutableStateFlow<ConnectionState>(initialState)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    val currentState: ConnectionState get() = _state.value

    fun transitionTo(newState: ConnectionState) {
        _state.value = newState
    }

    val isConnected: Boolean
        get() = _state.value is ConnectionState.Connected
}
