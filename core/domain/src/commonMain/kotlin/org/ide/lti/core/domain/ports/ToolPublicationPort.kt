package org.ide.lti.core.domain.ports

/**
 * Result of publishing local/WSL toolchain binaries to the remote server daemon.
 */
public data class PublishedTools(
    val published: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet()
)

/**
 * Port for registering product-built tool binaries with LtiRomServer daemon.
 */
public interface ToolPublicationPort {
    /**
     * Scans [binDir] on the remote host, writes daemon manifests for installed binaries,
     * and refreshes the daemon's dynamic tool registry.
     */
    public suspend fun publish(binDir: String): PublishedTools

    /**
     * Queries the daemon for all currently resolved and available tools.
     */
    public suspend fun resolvedTools(): Set<String>
}
