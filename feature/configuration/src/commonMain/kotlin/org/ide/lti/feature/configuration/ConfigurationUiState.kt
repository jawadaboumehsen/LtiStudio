/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetValidationError

enum class ConfigurationTab(val title: String) {
    GENERAL("General"),
    OS_STREAMS("OS & Streams"),
    HARDWARE("Hardware & Storage"),
    PARTITIONS("Partitions"),
}

enum class DirtyTargetSelectionDecision {
    SAVE,
    DISCARD,
    STAY,
}

data class ConfigurationUiState(
    val availableTargets: List<TargetDevice> = emptyList(),
    val selectedTargetId: String = "",
    val editingTarget: TargetDevice = TargetDevice.EMPTY,
    val originalTarget: TargetDevice? = null,
    val searchQuery: String = "",
    val activeTab: ConfigurationTab = ConfigurationTab.GENERAL,
    val isDirty: Boolean = false,
    val validationErrors: List<TargetValidationError> = emptyList(),
    val isSyncing: Boolean = false,
    /** Target requested while the current editor contains unsaved changes. */
    val pendingTargetId: String? = null,
    val statusMessage: String? = null,
) {
    val hasValidationErrors: Boolean
        get() = validationErrors.isNotEmpty()

    val validationErrorMessages: List<String>
        get() = validationErrors.map { it.message }
}
