/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.configuration

import org.ide.lti.core.model.run.StageId

enum class Severity {
    ERROR,
    WARNING,
    INFO,
}

data class ValidationError(
    val stageId: StageId?,
    val objectId: String?,
    val fieldPath: String,
    val code: String,
    val severity: Severity,
    val message: String,
    val remediation: String? = null,
)

data class DeferredArtifactCheck(val stageId: StageId, val description: String, val requiredArtifactRelPath: String)

data class ValidationReport(
    val errors: List<ValidationError> = emptyList(),
    val deferredArtifactChecks: List<DeferredArtifactCheck> = emptyList(),
) {
    val hasBlockingErrors: Boolean
        get() = errors.any { it.severity == Severity.ERROR }

    fun hasBlockingErrors(): Boolean = hasBlockingErrors
}

enum class ArtifactFreshness {
    FRESH,
    STALE,
    MISSING,
    UNVERIFIED,
}

data class ValidationContext(val profileRevision: Int, val targetFacts: Map<String, String> = emptyMap())

interface SettingsValidator<T> {
    fun validate(value: T, context: ValidationContext): ValidationReport
}
