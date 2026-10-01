package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import kotlinx.coroutines.*

/**
 * Background monitor that polls daemon health every [intervalMs] and updates [ConnectionStateHolder].
 * If consecutive missed heartbeats reach [maxMissedHeartbeats], it automatically transitions
 * the state to [ConnectionState.Reconnecting] and attempts zero-latency warm re-attachment.
 */
public class DaemonHeartbeatMonitor(
    private val supervisor: DaemonSupervisorPort,
    private val stateHolder: ConnectionStateHolder,
    private val intervalMs: Long = 5_000L,
    private val maxMissedHeartbeats: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : AutoCloseable {

    private var monitorJob: Job? = null
    private var consecutiveMisses = 0

    public fun start() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                checkLiveness()
            }
        }
    }

    public suspend fun checkLiveness() {
        val currentConn = supervisor.getConnectionInfo()
        if (currentConn == null) {
            stateHolder.transitionTo(ConnectionState.Disconnected("Daemon not started"))
            return
        }

        val healthy = runCatching { supervisor.isHealthy(currentConn) }.getOrDefault(false)
        if (healthy) {
            consecutiveMisses = 0
            if (!stateHolder.isConnected) {
                stateHolder.transitionTo(ConnectionState.Connected(currentConn))
            }
        } else {
            consecutiveMisses++
            if (consecutiveMisses >= maxMissedHeartbeats) {
                stateHolder.transitionTo(ConnectionState.Reconnecting(consecutiveMisses, currentConn))
                // Attempt proactive warm re-attachment
                val reattached = runCatching { supervisor.ensureStarted() }.getOrNull()
                if (reattached != null) {
                    consecutiveMisses = 0
                    stateHolder.transitionTo(ConnectionState.Connected(reattached))
                } else {
                    stateHolder.transitionTo(ConnectionState.Failed(IllegalStateException("Failed to re-attach to WSL daemon")))
                }
            }
        }
    }

    public fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    override fun close() {
        stop()
        scope.cancel()
    }
}
