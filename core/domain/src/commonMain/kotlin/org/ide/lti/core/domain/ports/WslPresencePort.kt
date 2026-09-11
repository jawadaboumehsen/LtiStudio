package org.ide.lti.core.domain.ports

/**
 * Port for querying WSL presence and installed distributions from the host platform.
 */
public interface WslPresencePort {
    /** Returns true if WSL is installed on the host. */
    public suspend fun isWslInstalled(): Boolean

    /** Returns true if at least one WSL distribution is registered. */
    public suspend fun hasDistro(): Boolean

    /** Returns the default WSL distribution name, if any. */
    public suspend fun getDefaultDistro(): String?
}
