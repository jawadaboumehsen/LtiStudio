/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.model.target.TargetConfigurationResult
import org.ide.lti.core.model.target.TargetDevice

/**
 * Master domain orchestration use case for saving, persisting, and selecting
 * a target hardware and ROM build configuration.
 *
 * Coordinates:
 * - Domain validation via [ValidateTargetConfigurationUseCase].
 * - Profile registration/update via [AddTargetUseCase] or [UpdateTargetUseCase].
 * - Active target selection via [SelectTargetUseCase].
 */
class SaveTargetConfigurationUseCase(
    private val validateTargetConfigurationUseCase: ValidateTargetConfigurationUseCase,
    private val addTargetUseCase: AddTargetUseCase,
    private val updateTargetUseCase: UpdateTargetUseCase,
    private val selectTargetUseCase: SelectTargetUseCase,
) {

    /**
     * Executes target configuration validation and persistence.
     *
     * @param target Target device to save.
     * @param isNew True if this is a newly scaffolded target profile.
     */
    suspend operator fun invoke(
        target: TargetDevice,
        isNew: Boolean = false,
    ): Result<TargetConfigurationResult> {
        val validationErrors = validateTargetConfigurationUseCase(target)
        if (validationErrors.isNotEmpty()) {
            val errorSummary = validationErrors.joinToString("; ") { it.message }
            return Result.failure(IllegalArgumentException(errorSummary))
        }

        val persistResult = if (isNew) {
            addTargetUseCase(target)
        } else {
            updateTargetUseCase(target)
        }

        if (persistResult.isFailure) {
            return Result.failure(persistResult.exceptionOrNull() ?: IllegalStateException("Failed to persist target"))
        }

        val selectResult = selectTargetUseCase(target.id)
        if (selectResult.isFailure) {
            return Result.failure(selectResult.exceptionOrNull() ?: IllegalStateException("Failed to select target"))
        }

        return Result.success(
            TargetConfigurationResult(
                target = target,
                workspacePath = null,
                isSynced = false,
            ),
        )
    }
}
