package io.ltirom.tooling.client.resilience

import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Background monitor that polls daemon health every [intervalMs] and updates [ConnectionStateHolder].
 * If consecutive missed heartbeats reach [maxMissedHeartbeats], it automatically transitions
 * the state to [ConnectionState.Reconnecting] and attempts zero-latency warm re-attachment.
 * Also sends client heartbeats to the build service to maintain command admission.
 */
public class DaemonHeartbeatMonitor(
    private val supervisor: DaemonSupervisorPort,
    private val stateHolder: ConnectionStateHolder = ConnectionStateHolder(),
    private val intervalMs: Long = 5_000L,
    private val maxMissedHeartbeats: Int = 2,
    private val transport: RemoteTransportPort? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : AutoCloseable {
    private var monitorJob: Job? = null
    private var consecutiveMisses = 0

    @Volatile
    private var isShuttingDown: Boolean = false

    public val isShutdown: Boolean get() = isShuttingDown

    @Volatile
    private var isHandshakeEstablished: Boolean = false

    private var boundConnection: ServerConnectionDescriptor? = null
    private var inFlightTarget: ServerConnectionDescriptor? = null
    private var connectionGeneration: Long = 0L

    private val handshakeMutex = Mutex()
    private var inFlightHandshake: Deferred<Boolean>? = null

    public suspend fun isAdmissionReady(): Boolean {
        if (isShuttingDown || !isHandshakeEstablished) return false
        val current = supervisor.getConnectionInfo()
        return current != null && current == boundConnection
    }

    public suspend fun awaitInitialHeartbeat(
        timeoutMs: Long = DEFAULT_HANDSHAKE_TIMEOUT_MS,
        forceRefresh: Boolean = false,
        targetConnection: ServerConnectionDescriptor? = null,
    ): Boolean {
        if (isShuttingDown) return false

        val currentConn =
            targetConnection ?: (
                supervisor.getConnectionInfo() ?: runCatching {
                    supervisor.ensureStarted()
                }.getOrNull()
                )

        if (currentConn == null) {
            handshakeMutex.withLock {
                resetHandshakeInternal()
            }
            return false
        }

        val deferred =
            handshakeMutex.withLock {
                if (isShuttingDown) return false
                if (!forceRefresh && isHandshakeEstablished && boundConnection == currentConn) {
                    return true
                }
                if (inFlightTarget == currentConn && inFlightHandshake?.isActive == true) {
                    inFlightHandshake!!
                } else {
                    if (boundConnection != null || inFlightTarget != null) {
                        if (boundConnection != currentConn || inFlightTarget != currentConn) {
                            resetHandshakeInternal()
                        }
                    }
                    inFlightTarget = currentConn
                    scope.async {
                        executeHandshake(currentConn, connectionGeneration)
                    }.also { inFlightHandshake = it }
                }
            }

        return try {
            withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: false
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    private suspend fun executeHandshake(target: ServerConnectionDescriptor, generation: Long): Boolean {
        val ack =
            try {
                withTimeoutOrNull(DEFAULT_HANDSHAKE_TIMEOUT_MS) {
                    transport?.sendHeartbeat(target) ?: supervisor.isHealthy(target)
                } ?: false
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                false
            }

        return handshakeMutex.withLock {
            if (inFlightTarget == target) {
                inFlightHandshake = null
                inFlightTarget = null
            }
            val valid =
                generation == connectionGeneration && !isShuttingDown && supervisor.getConnectionInfo() == target
            if (valid) {
                if (ack) {
                    boundConnection = target
                    isHandshakeEstablished = true
                    start()
                } else {
                    boundConnection = null
                    isHandshakeEstablished = false
                }
                ack
            } else {
                false
            }
        }
    }

    public fun start() {
        if (isShuttingDown || monitorJob?.isActive == true) return
        monitorJob =
            scope.launch {
                while (isActive && !isShuttingDown) {
                    delay(intervalMs)
                    checkLiveness()
                }
            }
    }

    public suspend fun checkLiveness() {
        val gen = connectionGeneration
        val currentConn = supervisor.getConnectionInfo()
        if (currentConn == null) {
            handshakeMutex.withLock {
                // Same rules as every other result of a check: one that is already out of date (a connection
                // was established, or the client is shutting down, since it was read) must not undo anything.
                if (gen != connectionGeneration || isShuttingDown || supervisor.getConnectionInfo() != null) return
                stateHolder.transitionTo(ConnectionState.Disconnected("Daemon not started"))
                resetHandshakeInternal()
            }
            return
        }

        val healthy = runCatching { supervisor.isHealthy(currentConn) }.getOrDefault(false)
        val tp = transport
        val heartbeatOk =
            if (healthy && tp != null) {
                try {
                    tp.sendHeartbeat(currentConn)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    false
                }
            } else {
                healthy
            }

        var shouldReconnect = false
        var currentMisses = 0
        var reconnectGen = 0L

        handshakeMutex.withLock {
            val valid = gen == connectionGeneration && !isShuttingDown && supervisor.getConnectionInfo() == currentConn
            if (!valid) {
                // Stale check: connection was replaced or shutdown started while network call was in flight.
                // Discard invalid result immediately for both success and failure.
                return
            }

            if (healthy && heartbeatOk) {
                consecutiveMisses = 0
                boundConnection = currentConn
                isHandshakeEstablished = true
                if (!stateHolder.isConnected) {
                    stateHolder.transitionTo(ConnectionState.Connected(currentConn))
                }
            } else {
                consecutiveMisses++
                isHandshakeEstablished = false
                currentMisses = consecutiveMisses
                if (currentMisses >= maxMissedHeartbeats) {
                    shouldReconnect = true
                    resetHandshakeInternal()
                    reconnectGen = connectionGeneration
                }
            }
        }

        // Perform reconnection outside the mutex to prevent deadlocks with awaitInitialHeartbeat and shutdown
        if (shouldReconnect) {
            stateHolder.transitionTo(ConnectionState.Reconnecting(currentMisses, currentConn))
            val reattached =
                try {
                    supervisor.ensureStarted()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    null
                }
            if (reattached != null) {
                val shouldHandshake =
                    handshakeMutex.withLock {
                        reconnectGen == connectionGeneration &&
                            !isShuttingDown &&
                            supervisor.getConnectionInfo() == reattached
                    }
                if (shouldHandshake) {
                    val ack = awaitInitialHeartbeat(forceRefresh = true, targetConnection = reattached)
                    handshakeMutex.withLock {
                        val valid =
                            reconnectGen == connectionGeneration &&
                                !isShuttingDown &&
                                supervisor.getConnectionInfo() == reattached
                        if (valid) {
                            if (ack) {
                                consecutiveMisses = 0
                                stateHolder.transitionTo(ConnectionState.Connected(reattached))
                            } else {
                                stateHolder.transitionTo(
                                    ConnectionState.Failed(
                                        IllegalStateException(
                                            "Re-attached to daemon but command admission handshake was rejected",
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                handshakeMutex.withLock {
                    val valid =
                        reconnectGen == connectionGeneration &&
                            !isShuttingDown &&
                            supervisor.getConnectionInfo() == currentConn
                    if (valid) {
                        stateHolder.transitionTo(
                            ConnectionState.Failed(IllegalStateException("Failed to re-attach to WSL daemon")),
                        )
                    }
                }
            }
        }
    }

    public fun resetHandshake() {
        scope.launch {
            handshakeMutex.withLock {
                resetHandshakeInternal()
            }
        }
    }

    private fun resetHandshakeInternal(): Deferred<Boolean>? {
        isHandshakeEstablished = false
        boundConnection = null
        inFlightTarget = null
        connectionGeneration++
        val previousInFlight = inFlightHandshake
        inFlightHandshake?.cancel()
        inFlightHandshake = null
        return previousInFlight
    }

    public fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    public suspend fun shutdown() {
        isShuttingDown = true
        val handshakeToJoin =
            handshakeMutex.withLock {
                resetHandshakeInternal()
            }
        val loopToJoin = monitorJob
        monitorJob?.cancel()
        monitorJob = null

        handshakeToJoin?.cancelAndJoin()
        loopToJoin?.join()
    }

    /**
     * [shutdown] without waiting for anything: stops the loop and any handshake at once and refuses new ones. For
     * when the time allowed for an orderly shutdown has run out - [shutdown] itself can be waiting on a check that
     * is stuck inside the lock, and must not be asked again then.
     */
    public fun abort() {
        isShuttingDown = true
        monitorJob?.cancel()
        monitorJob = null
        inFlightHandshake?.cancel()
    }

    public val currentState: ConnectionState get() = stateHolder.currentState

    public fun markDisconnected(reason: String? = null) {
        stateHolder.transitionTo(ConnectionState.Disconnected(reason))
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    public companion object {
        public const val DEFAULT_HANDSHAKE_TIMEOUT_MS: Long = 5_000L
    }
}
