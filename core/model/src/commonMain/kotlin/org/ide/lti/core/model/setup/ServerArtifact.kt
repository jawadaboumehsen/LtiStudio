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

public sealed interface InstallState {
    public data object NotInstalled : InstallState
    public data object Outdated : InstallState
    public data class Staged(val version: String) : InstallState
    public data object Current : InstallState
    public data class Failed(val step: String, val stderr: String) : InstallState
}

public data class ServerArtifact(
    val bundledVersion: String?,
    val activeVersion: String?,
    val runningVersion: String?,
    val runningActiveRuns: Int,
    val installState: InstallState,
    val javaHome: String?,
)
