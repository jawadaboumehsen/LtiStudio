/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.macro

enum class MacroStepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED,
}

data class MacroStep(
    val id: String,
    val title: String,
    val description: String,
    val action: suspend () -> Result<Unit>,
    val status: MacroStepStatus = MacroStepStatus.PENDING,
    val durationMillis: Long = 0L,
    val errorMessage: String? = null,
)

data class MacroRecipe(
    val id: String,
    val name: String,
    val description: String,
    val steps: List<MacroStep>,
) {
    val totalSteps: Int get() = steps.size
    val passedSteps: Int get() = steps.count { it.status == MacroStepStatus.PASSED }
    val isComplete: Boolean get() = steps.all { it.status == MacroStepStatus.PASSED || it.status == MacroStepStatus.SKIPPED }
    val hasFailed: Boolean get() = steps.any { it.status == MacroStepStatus.FAILED }
}
