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

public enum class Need {
    SERVICE,
    BUILD,
    RUNTIME,
}

public sealed interface RequirementCheck {
    public data class Command(val name: String) : RequirementCheck
    public data class Package(val name: String) : RequirementCheck
    public data class JavaMajor(val min: Int) : RequirementCheck
    public data class PythonImport(val modules: List<String>) : RequirementCheck
}

public sealed interface RequirementStatus {
    public data object Present : RequirementStatus
    public data object Missing : RequirementStatus
    public data class Unavailable(val release: String) : RequirementStatus
    public data class CheckFailed(val reason: String) : RequirementStatus
}

public data class SystemRequirement(
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

public data class PrerequisiteReport(
    val environment: SetupEnvironment,
    val results: Map<String, RequirementStatus>,
    val missingPackages: List<String>,
    val installCommand: String?,
    val javaHome: String?,
    val packageListsFresh: Boolean,
    val checkedAt: Long,
)
