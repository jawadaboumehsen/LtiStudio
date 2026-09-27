package org.ide.lti.core.model.setup

public sealed interface DistroStatus {
    public data class WslUnavailable(val detail: String) : DistroStatus
    public data object NoDistro : DistroStatus
    public data class StartupFailed(val distro: String, val detail: String) : DistroStatus
    public data class Unsupported(val distro: String, val reason: String) : DistroStatus
    public data class NoUsableUser(val distro: String, val reason: String) : DistroStatus
    public data class Usable(val environment: SetupEnvironment) : DistroStatus
}
