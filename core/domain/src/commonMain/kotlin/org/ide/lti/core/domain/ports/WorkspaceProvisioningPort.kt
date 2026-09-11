/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceKeys
import org.ide.lti.core.model.workspace.WorkspaceManifest

enum class KeySource {
    GENERATE,
    COPY_GLOBAL,
}

data class ProvisioningSpec(
    val destinationLinuxPath: String,
    val target: TargetDevice,
    val snapshot: ConfigurationSnapshot,
    val manifest: WorkspaceManifest,
    val keySource: KeySource = KeySource.GENERATE,
    /** `~/LtiRomWorkDir` on the service host; source of the global AVB key for [KeySource.COPY_GLOBAL]. */
    val workDirLinuxPath: String = destinationLinuxPath.substringBeforeLast("/workspaces/"),
)

sealed interface ProvisioningEvent {
    data class Step(val id: String, val label: String) : ProvisioningEvent
    data class Output(val text: String) : ProvisioningEvent
    data class Failed(val step: String, val message: String) : ProvisioningEvent
    data class Completed(val keys: WorkspaceKeys) : ProvisioningEvent
}

class EnvironmentNotReadyException(message: String) : IllegalStateException(message)
class DestinationExistsException(message: String) : IllegalStateException(message)

/**
 * Port interface for remotely provisioning and materializing workspaces in WSL.
 */
interface WorkspaceProvisioningPort {
    fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent>
    suspend fun cancel(id: String)
    suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot)
}
