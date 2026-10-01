/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

public sealed interface BuildAdmissionResult {
    public data class Admitted(val environmentLease: EnvironmentLease, val workspaceLease: WorkspaceWriteLease) :
        BuildAdmissionResult

    public data class Busy(val owner: WorkspaceWriteLease) : BuildAdmissionResult

    public data class EnvironmentBusy(val owners: List<EnvironmentLease>) : BuildAdmissionResult

    public data class RevisionMismatch(val expected: Int, val actual: Int) : BuildAdmissionResult
}

/**
 * Acquires build admission in the fixed order environment -> workspace; a workspace refusal
 * releases the environment lease before returning so no lease is ever held without its pair.
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
    val envLease = when (val envAdmission = env.tryAcquire(environmentKey, EnvironmentUse.BUILD_USE, operationId)) {
        is EnvironmentAdmission.Busy -> return BuildAdmissionResult.EnvironmentBusy(envAdmission.owners)
        is EnvironmentAdmission.Admitted -> envAdmission.lease
    }
    return when (val wsAdmission = ws.tryAcquire(workspaceId, kind, operationId, expectedRevision, currentRevision)) {
        is AdmissionResult.Admitted -> BuildAdmissionResult.Admitted(envLease, wsAdmission.lease)
        is AdmissionResult.Busy -> {
            env.release(envLease)
            BuildAdmissionResult.Busy(wsAdmission.owner)
        }
        is AdmissionResult.RevisionMismatch -> {
            env.release(envLease)
            BuildAdmissionResult.RevisionMismatch(wsAdmission.expected, wsAdmission.actual)
        }
    }
}
