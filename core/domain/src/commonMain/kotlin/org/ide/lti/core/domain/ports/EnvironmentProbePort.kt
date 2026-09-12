package org.ide.lti.core.domain.ports

/**
 * Lifecycle outcome of the remote execution daemon during environment probing.
 */
public sealed interface DaemonProbeStatus {
    public data class Adopted(
        val pid: Long,
        val port: Int = 0,
        val host: String = "127.0.0.1",
    ) : DaemonProbeStatus

    public data class Launched(
        val pid: Long,
        val port: Int = 0,
        val host: String = "127.0.0.1",
    ) : DaemonProbeStatus

    public data class Failed(
        val reason: String,
        val logTail: String? = null,
    ) : DaemonProbeStatus
}

/**
 * Immutable measurement result of an environment verification probe.
 */
public data class EnvironmentProbeResult(
    val distro: String?,
    val home: String?,
    val daemon: DaemonProbeStatus,
    val pingMs: Long?,
    val executables: Set<String>,
    val publishedTools: Set<String>,
    val workDirExists: Boolean,
    val submodules: Set<String>,
    val avbKeyPresent: Boolean,
    val storageAvailableBytes: Long? = null,
    val storageMeasuredAt: Long? = null,
)

/**
 * Port for probing the execution environment (WSL, daemon, published tools, work tree, and keys).
 */
public interface EnvironmentProbePort {
    public suspend fun probe(): EnvironmentProbeResult
}
