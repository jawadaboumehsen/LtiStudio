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

sealed interface InstallState {
    data object NotInstalled : InstallState
    data object Outdated : InstallState
    data class Staged(val version: String) : InstallState
    data object Current : InstallState
    data class Failed(val step: String, val stderr: String) : InstallState
}

data class ServerArtifact(
    val bundledVersion: String?,
    val activeVersion: String?,
    val runningVersion: String?,
    val runningActiveRuns: Int,
    val installState: InstallState,
    val javaHome: String?,
)
