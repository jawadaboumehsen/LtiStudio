package io.ltirom.tooling.client.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class DaemonConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEGRADED,
}

/**
 * Reactive holder for the remote WSL daemon bridge's connection state.
 *
 * Reconstruction note: registered as a Koin singleton (`ToolingModule.kt`) but no
 * surviving caller reads or mutates it, so this is a minimal honest stub matching
 * only the no-arg constructor call site — not a recovered implementation.
 */
public class ConnectionStateHolder {
    private val _state = MutableStateFlow(DaemonConnectionState.DISCONNECTED)
    public val state: StateFlow<DaemonConnectionState> = _state.asStateFlow()

    public fun update(newState: DaemonConnectionState) {
        _state.value = newState
    }
}
