package org.ide.lti.core.domain.ports

/**
 * Port for launching a host interactive terminal attached to a specific WSL environment.
 */
public interface TerminalLauncherPort {
    /**
     * Launches an external interactive terminal session inside the specified [distro].
     */
    public fun launchTerminal(distro: String): Result<Unit>
}
