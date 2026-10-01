/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.install

import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.PendingSwitchRecord
import org.ide.lti.core.model.setup.SwitchCheckpoint
import kotlin.random.Random

/**
 * Reconciles client-side activation outcomes, in-flight retries, and restarts (008 T082, T085).
 */
public class ActivationReconciler(
    private val installationPort: ToolchainInstallationPort,
    private val repository: ToolchainSetupRepository? = null,
    private val idGenerator: () -> String = ::generateUuid,
) {

    public suspend fun prepareRetryRequest(oldRequest: ActivationRequest): ActivationRequest {
        val currentState = installationPort.state()
        return ActivationRequest(
            requestId = idGenerator(),
            expectedActiveInstallId = currentState.activeInstallId,
            targetInstallId = oldRequest.targetInstallId,
        )
    }

    public suspend fun reconcile(
        request: ActivationRequest,
        onPollDelay: (suspend () -> Unit)? = null,
    ): ActivationOutcome {
        var outcome = installationPort.activate(request)

        if (outcome is ActivationOutcome.Unknown) {
            outcome = reconcileLostResponse(request)
        }

        while (outcome is ActivationOutcome.InProgress) {
            onPollDelay?.invoke()
            val poll = installationPort.activationOutcome(request.requestId)
            if (poll != null) {
                outcome = poll
            } else {
                break
            }
        }

        return outcome
    }

    public suspend fun resumePendingSwitch(
        pending: PendingSwitchRecord,
        onPollDelay: (suspend () -> Unit)? = null,
    ): ActivationOutcome? {
        var outcome = installationPort.activationOutcome(pending.activationRequestId)

        while (outcome is ActivationOutcome.InProgress) {
            onPollDelay?.invoke()
            val poll = installationPort.activationOutcome(pending.activationRequestId)
            if (poll != null) {
                outcome = poll
            } else {
                break
            }
        }

        if (outcome == null) {
            val currentState = installationPort.state()
            if (currentState.activeInstallId?.value == pending.targetInstallId) {
                outcome = ActivationOutcome.Committed(InstallId(pending.targetInstallId))
            }
        }

        return outcome
    }

    public suspend fun recordCheckpoint(
        requestId: String,
        expectedActive: String?,
        target: String,
        revision: Long,
        checkpoint: SwitchCheckpoint,
    ): Result<Unit> {
        val repo = repository ?: return Result.success(Unit)
        val current = repo.currentState
        val record = PendingSwitchRecord(
            activationRequestId = requestId,
            expectedActiveInstallId = expectedActive,
            targetInstallId = target,
            selectionRevision = revision,
            checkpoint = checkpoint,
        )
        return repo.saveToolchainState(current.copy(pendingSwitch = record))
    }

    public suspend fun clearPendingSwitch(): Result<Unit> {
        val repo = repository ?: return Result.success(Unit)
        val current = repo.currentState
        return repo.saveToolchainState(current.copy(pendingSwitch = null))
    }

    private suspend fun reconcileLostResponse(request: ActivationRequest): ActivationOutcome {
        val existing = installationPort.activationOutcome(request.requestId)
        if (existing != null) return existing
        return installationPort.activate(request)
    }

    public companion object {
        public fun generateUuid(): String {
            val bytes = Random.nextBytes(16)
            bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
            return buildString(36) {
                for (i in 0 until 16) {
                    if (i == 4 || i == 6 || i == 8 || i == 10) append('-')
                    append(bytes[i].toInt().and(0xff).toString(16).padStart(2, '0'))
                }
            }
        }
    }
}
