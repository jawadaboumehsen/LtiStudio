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

import org.ide.lti.core.domain.setup.ports.InstallId

/**
 * Descriptor of a tool group that will revert upon restoring the previous toolchain.
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
 * Outcome of a toolchain restore operation.
 */
public sealed interface RestoreOutcome {
    public data class Succeeded(
        val activeInstallId: InstallId,
        val revertSet: List<RevertingGroup>,
        val desiredUpdated: Boolean,
    ) : RestoreOutcome

    public data class Failed(val reason: String) : RestoreOutcome
}

/**
 * Port for coordinating a toolchain restore operation (T091, US2).
 */
public interface RestoreCoordinatorPort {
    public suspend fun getRevertSet(): List<RevertingGroup>
    public suspend fun restore(distro: String, expectedRevision: Long? = null): RestoreOutcome
}
