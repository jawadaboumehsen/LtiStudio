package org.ide.lti.core.domain.ports

/**
 * Port for registering product-built tool binaries with LtiRomServer daemon.
 */
public interface ToolPublicationPort {
    /**
     * Scans [binDir] on the remote host, writes daemon manifests for installed binaries matching [toolIds],
     * and refreshes the daemon's dynamic tool registry.
     */
    public suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport

    /**
     * Queries the daemon for all currently resolved and available tools.
     */
    public suspend fun resolvedTools(): ToolRegistryResult
}
