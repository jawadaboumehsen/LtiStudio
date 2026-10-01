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

import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetValidationError

/**
 * Use case to validate a target device configuration before it is persisted.
 *
 * Rules below were reconstructed (no surviving spec) from [TargetDevice]'s own constraints
 * and the fields [org.ide.lti.core.data.repository.target.TargetRepositoryImpl] treats as
 * required; adjust if the original rules differed.
 */
class ValidateTargetConfigurationUseCase {
    operator fun invoke(target: TargetDevice): List<TargetValidationError> {
        val errors = mutableListOf<TargetValidationError>()

        if (target.id.isBlank()) {
            errors += TargetValidationError.EmptyId
        }
        if (target.name.isBlank()) {
            errors += TargetValidationError.EmptyName
        }
        if (target.codename.isBlank()) {
            errors += TargetValidationError.EmptyCodename
        }
        if (target.socPlatform.isBlank()) {
            errors += TargetValidationError.EmptySocPlatform
        }
        if (target.filesystemType.isBlank()) {
            errors += TargetValidationError.EmptyFilesystemType
        }
        if (target.superPartitionBytes <= 0L) {
            errors += TargetValidationError.InvalidSuperPartitionSize
        }
        if (target.dynamicPartitions.isEmpty()) {
            errors += TargetValidationError.EmptyDynamicPartitions
        }
        if (target.bootPartitions.isEmpty()) {
            errors += TargetValidationError.EmptyBootPartitions
        }

        return errors
    }
}
