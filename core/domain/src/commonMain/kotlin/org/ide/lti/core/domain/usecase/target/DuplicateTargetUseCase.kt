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
import org.ide.lti.core.model.target.TargetStatus

/**
 * Use case to clone an existing [TargetDevice] profile with unique identification,
 * sanitized codenames, and non-default status.
 *
 * Adheres to Single Responsibility Principle.
 */
class DuplicateTargetUseCase {

    /**
     * Creates a deep duplicate of [target] with updated IDs and copy descriptors.
     *
     * @param target Source target device to clone.
     * @param existingIds Set of already used target IDs to prevent collision.
     * @param suffix Unique descriptor suffix (default: "COPY").
     */
    operator fun invoke(
        target: TargetDevice,
        existingIds: Set<String>,
        suffix: String = "COPY",
    ): TargetDevice {
        val sanitizedSuffix = suffix.trim().uppercase()
        val baseId = "${target.id}_$sanitizedSuffix"
        val baseCodename = "${target.codename.lowercase()}_${sanitizedSuffix.lowercase()}"
        val duplicatedName = "${target.name} (Copy)"

        val (duplicatedId, duplicatedCodename) = if (baseId !in existingIds) {
            baseId to baseCodename
        } else {
            var counter = 2
            while ("${baseId}_$counter" in existingIds) {
                counter++
            }
            "${baseId}_$counter" to "${baseCodename}_$counter"
        }

        return target.copy(
            id = duplicatedId,
            name = duplicatedName,
            codename = duplicatedCodename,
            status = TargetStatus.EXPERIMENTAL,
            isDefault = false,
            description = "Cloned from ${target.name} (${target.codename})",
        )
    }
}
