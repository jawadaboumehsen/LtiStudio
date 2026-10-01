/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.compose.runtime.Immutable
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.ChildTerminalProof

/**
 * Presentation model for the operation card in the Recovery view.
 */
@Immutable
data class RecoveryOperationPresentation(
    val kind: String,
    val attemptId: String,
    val state: String,
    val failure: String? = null,
    val issuedAt: String? = null,
)

/**
 * An individual problem item surfaced in the Recovery view's Problems tab.
 */
@Immutable
data class RecoveryProblemItem(val title: String, val detail: String, val isStep: Boolean = true)

/**
 * Journaled evidence surfaced in the Recovery view's Evidence tab.
 */
@Immutable
data class RecoveryEvidencePresentation(
    val intents: List<ChildIntentRecord> = emptyList(),
    val terminalProofs: List<ChildTerminalProof> = emptyList(),
    val completedActionIds: List<String> = emptyList(),
) {
    val hasEvidence: Boolean
        get() = intents.isNotEmpty() || terminalProofs.isNotEmpty() || completedActionIds.isNotEmpty()
}

/**
 * Pure presentation state for the Recovery view (contracts/ui-states.md §8).
 */
@Immutable
data class RecoveryPresentation(
    val isEmpty: Boolean,
    val operation: RecoveryOperationPresentation? = null,
    val service: BuildServicePresentation? = null,
    val blockedReason: String? = null,
    val problems: List<RecoveryProblemItem> = emptyList(),
    val evidence: RecoveryEvidencePresentation = RecoveryEvidencePresentation(),
) {
    val hasProblems: Boolean get() = problems.isNotEmpty()
    val hasEvidence: Boolean get() = evidence.hasEvidence
}
