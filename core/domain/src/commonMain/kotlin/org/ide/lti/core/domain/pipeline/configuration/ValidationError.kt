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

public enum class Severity {
    ERROR,
    WARNING,
    INFO,
}

public data class ValidationError(
    val stageId: StageId?,
    val objectId: String?,
    val fieldPath: String,
    val code: String,
    val severity: Severity,
    val message: String,
    val remediation: String? = null,
)

public data class DeferredArtifactCheck(
    val stageId: StageId,
    val description: String,
    val requiredArtifactRelPath: String,
)

public data class ValidationReport(
    val errors: List<ValidationError> = emptyList(),
    val deferredArtifactChecks: List<DeferredArtifactCheck> = emptyList(),
) {
    public val hasBlockingErrors: Boolean
        get() = errors.any { it.severity == Severity.ERROR }

    public fun hasBlockingErrors(): Boolean = hasBlockingErrors
}

public enum class ArtifactFreshness {
    FRESH,
    STALE,
    MISSING,
    UNVERIFIED,
}

public data class ValidationContext(val profileRevision: Int, val targetFacts: Map<String, String> = emptyMap())

public interface SettingsValidator<T> {
    public fun validate(value: T, context: ValidationContext): ValidationReport
}
