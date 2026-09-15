/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import kotlinx.serialization.Serializable

@Serializable
data class AuthorProject(val path: String, val fingerprint: String, val sdkVersion: String, val generatorEntry: String)

@Serializable
data class TrustApproval(val projectPath: String, val fingerprint: String, val approvedAtEpochMs: Long)

sealed interface TrustCheck {
    data class Approved(val approval: TrustApproval) : TrustCheck
    data class FingerprintChanged(val previous: String, val current: String) : TrustCheck
    data object NotApproved : TrustCheck
}

interface AuthorProjectTrustPort {
    suspend fun fingerprint(projectPath: String): String?
    suspend fun approval(projectPath: String): TrustApproval?
    suspend fun approve(project: AuthorProject): TrustApproval
    suspend fun revoke(projectPath: String)
    suspend fun runTask(project: AuthorProject, task: String, timeoutMs: Long): AuthorTaskResult =
        AuthorTaskResult.Refused(AuthorTaskRefusal.NOT_TRUSTED)
}

class AuthorProjectTrustUseCase(private val port: AuthorProjectTrustPort) {
    suspend fun check(projectPath: String): TrustCheck {
        val currentFingerprint = port.fingerprint(projectPath)
        val stored = port.approval(projectPath)
        return when {
            currentFingerprint == null || stored == null -> TrustCheck.NotApproved
            stored.fingerprint == currentFingerprint -> TrustCheck.Approved(stored)
            else -> TrustCheck.FingerprintChanged(previous = stored.fingerprint, current = currentFingerprint)
        }
    }

    suspend fun approve(project: AuthorProject): TrustApproval = port.approve(project)

    suspend fun revoke(projectPath: String) = port.revoke(projectPath)

    suspend fun runTask(project: AuthorProject, task: String, timeoutMs: Long = 60000L): AuthorTaskResult =
        port.runTask(project, task, timeoutMs)
}

enum class AuthorTaskRefusal {
    NOT_TRUSTED,
    UNKNOWN_TASK,
}

data class AuthorDiagnostic(
    val severity: String,
    val code: String,
    val file: String? = null,
    val line: Int? = null,
    val col: Int? = null,
    val operationId: String? = null,
    val fieldId: String? = null,
    val message: String,
)

sealed interface AuthorTaskResult {
    data class Success(val stdout: String, val stderr: String, val diagnostics: List<AuthorDiagnostic> = emptyList()) :
        AuthorTaskResult

    data class Failed(
        val exitCode: Int,
        val diagnostics: List<AuthorDiagnostic>,
        val stdout: String = "",
        val stderr: String = "",
    ) : AuthorTaskResult

    data class Refused(val reason: AuthorTaskRefusal) : AuthorTaskResult

    /** The task outlived its timeout and its process tree was destroyed. Never a success receipt. */
    data class TimedOut(val afterMs: Long, val stdout: String = "", val stderr: String = "") : AuthorTaskResult
}
