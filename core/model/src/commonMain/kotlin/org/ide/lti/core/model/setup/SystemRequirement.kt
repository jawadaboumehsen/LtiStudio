/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

enum class Need {
    SERVICE,
    BUILD,
    RUNTIME,
}

sealed interface RequirementCheck {
    data class Command(val name: String) : RequirementCheck

    data class Package(val name: String) : RequirementCheck

    data class PythonImport(val modules: List<String>) : RequirementCheck
}

sealed interface RequirementStatus {
    data object Present : RequirementStatus

    data object Missing : RequirementStatus

    data class Unavailable(val release: String) : RequirementStatus

    data class CheckFailed(val reason: String) : RequirementStatus
}

data class SystemRequirement(
    val id: String,
    val check: RequirementCheck,
    val aptPackages: List<String>,
    val neededBy: Need,
) {
    init {
        require(id.isNotBlank()) { "Requirement id must not be blank" }
        require(aptPackages.isNotEmpty()) { "aptPackages must not be empty for requirement '$id'" }
    }
}

data class PrerequisiteReport(
    val environment: SetupEnvironment,
    val results: Map<String, RequirementStatus>,
    val missingPackages: List<String>,
    val installCommand: String?,
    val javaHome: String?,
    val packageListsFresh: Boolean,
    val checkedAt: Long,
)
