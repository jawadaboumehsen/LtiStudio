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
            errors += TargetValidationError("id", "Target ID cannot be blank.")
        }
        if (target.name.isBlank()) {
            errors += TargetValidationError("name", "Target name cannot be blank.")
        }
        if (target.codename.isBlank()) {
            errors += TargetValidationError("codename", "Target codename cannot be blank.")
        }
        if (target.socPlatform.isBlank()) {
            errors += TargetValidationError("socPlatform", "SoC platform cannot be blank.")
        }
        if (target.filesystemType.isBlank()) {
            errors += TargetValidationError("filesystemType", "Filesystem type cannot be blank.")
        }
        if (target.dynamicPartitions.isEmpty()) {
            errors += TargetValidationError("dynamicPartitions", "At least one dynamic partition is required.")
        }
        if (target.bootPartitions.isEmpty()) {
            errors += TargetValidationError("bootPartitions", "At least one boot partition is required.")
        }

        return errors
    }
}
