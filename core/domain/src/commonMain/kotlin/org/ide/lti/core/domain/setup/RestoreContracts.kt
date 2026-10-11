/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

/**
 * Descriptor of a component group that will revert to its own previous version.
 */
public data class RevertingGroup(
    val groupId: ToolGroupId,
    val currentArtifactId: String,
    val previousArtifactId: String,
    val currentVersion: String,
    val previousVersion: String,
    val affectedTools: List<String> = emptyList(),
)

/**
 * Outcome of a component-version undo operation.
 */
public sealed interface RestoreOutcome {
    public data class Succeeded(val revertSet: List<RevertingGroup>, val desiredUpdated: Boolean) : RestoreOutcome

    public data class Failed(val reason: String) : RestoreOutcome
}
