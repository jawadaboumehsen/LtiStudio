package org.ide.lti.core.model.setup

public sealed interface InstallState {
    public data object NotInstalled : InstallState
    public data object Outdated : InstallState
    public data class Staged(val version: String) : InstallState
    public data object Current : InstallState
    public data class Failed(val step: String, val stderr: String) : InstallState
}

public data class ServerArtifact(
    val bundledVersion: String?,
    val activeVersion: String?,
    val runningVersion: String?,
    val runningActiveRuns: Int,
    val installState: InstallState,
    val javaHome: String?,
)
