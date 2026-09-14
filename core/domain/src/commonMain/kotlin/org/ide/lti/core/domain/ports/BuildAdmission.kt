@file:Suppress("MatchingDeclarationName", "Filename")

package org.ide.lti.core.domain.ports

public sealed interface BuildAdmissionResult {
    public data class Admitted(val envLease: EnvironmentLease, val wsLease: WorkspaceWriteLease) : BuildAdmissionResult

    public data class Busy(val owner: WorkspaceWriteLease) : BuildAdmissionResult

    public data class EnvironmentBusy(val owners: List<EnvironmentLease>) : BuildAdmissionResult

    public data class RevisionMismatch(val expected: Int, val actual: Int) : BuildAdmissionResult
}

/**
 * Acquires build admission enforcing the fixed order: environment -> workspace.
 * If the workspace step is Busy or RevisionMismatch, releases the environment lease before returning.
 */
public suspend fun acquireBuildAdmission(
    env: EnvironmentLeasePort,
    ws: WorkspaceAdmissionPort,
    environmentKey: String,
    workspaceId: String,
    kind: MutationKind,
    operationId: String,
    expectedRevision: Int?,
    currentRevision: Int?,
): BuildAdmissionResult {
    val envAdmission = env.tryAcquire(environmentKey, EnvironmentUse.BUILD_USE, operationId)
    val envLease = when (envAdmission) {
        is EnvironmentAdmission.Busy -> return BuildAdmissionResult.EnvironmentBusy(envAdmission.owners)
        is EnvironmentAdmission.Admitted -> envAdmission.lease
    }

    val wsAdmission = ws.tryAcquire(workspaceId, kind, operationId, expectedRevision, currentRevision)
    return when (wsAdmission) {
        is AdmissionResult.Admitted -> {
            BuildAdmissionResult.Admitted(
                envLease = envLease,
                wsLease = wsAdmission.lease,
            )
        }
        is AdmissionResult.Busy -> {
            env.release(envLease)
            BuildAdmissionResult.Busy(wsAdmission.owner)
        }
        is AdmissionResult.RevisionMismatch -> {
            env.release(envLease)
            BuildAdmissionResult.RevisionMismatch(
                expected = wsAdmission.expected,
                actual = wsAdmission.actual,
            )
        }
    }
}
