package org.ide.lti.core.model.setup

public sealed interface DistroStatus {
    public data class WslUnavailable(val detail: String) : DistroStatus
    public data object NoDistro : DistroStatus
    public data class StartupFailed(val distro: String, val detail: String) : DistroStatus
    public data class Unsupported(val distro: String, val reason: String) : DistroStatus
    public data class NoUsableUser(val distro: String, val reason: String) : DistroStatus
    public data class Usable(val environment: SetupEnvironment) : DistroStatus
}

/** The status nearest to usable, so a stray non-Ubuntu distro never hides the real problem. */
public fun List<DistroStatus>.closestToUsable(): DistroStatus? = minByOrNull {
    when (it) {
        is DistroStatus.Usable -> 0
        is DistroStatus.NoUsableUser -> 1
        is DistroStatus.StartupFailed -> 2
        is DistroStatus.Unsupported -> 3
        is DistroStatus.NoDistro -> 4
        is DistroStatus.WslUnavailable -> 5
    }
}

/** The distro this status is about; null when there is none (WSL missing or no distro installed). */
public val DistroStatus.distroName: String?
    get() = when (this) {
        is DistroStatus.Usable -> environment.distro
        is DistroStatus.Unsupported -> distro
        is DistroStatus.StartupFailed -> distro
        is DistroStatus.NoUsableUser -> distro
        is DistroStatus.NoDistro, is DistroStatus.WslUnavailable -> null
    }
