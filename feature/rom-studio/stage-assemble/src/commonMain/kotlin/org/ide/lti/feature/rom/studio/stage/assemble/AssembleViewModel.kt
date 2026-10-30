/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.domain.workbench.BootWorkbench
import org.ide.lti.core.domain.workbench.ImageWorkbench
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AssemblySettings

public class AssembleViewModel(
    public val workspaceId: String,
    initialSettings: AssemblySettings? = null,
    store: WorkspaceSettingsStore? = null,
    boot: BootWorkbench? = null,
    images: ImageWorkbench? = null,
    runner: WorkbenchRunner? = null,
    environment: AppEnvironmentPort? = null,
    pickFile: suspend () -> String? = { DialogUtils.selectFile()?.path },
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: AssemblySettings(),
        read = { it.assembly },
        write = { snapshot, value -> snapshot.copy(assembly = value) },
    )

    public val settings: StateFlow<AssemblySettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    public val bootPane: BootPaneModel = BootPaneModel(
        scope = viewModelScope,
        workspaceId = workspaceId,
        boot = boot,
        runner = runner,
        environment = environment,
        pickFile = pickFile,
        overrides = { settings.value.kernelOverrides },
        setOverrides = { updateSettings(settings.value.copy(kernelOverrides = it)) },
        bootImages = { (device.value ?: targetDevice).bootPartitions },
    )

    public val imagePane: ImagePaneModel = ImagePaneModel(viewModelScope, workspaceId, images, environment) {
        (device.value ?: targetDevice).dynamicPartitions
    }

    public fun updateSettings(updated: AssemblySettings) {
        sync.update(updated)
    }

    public companion object {
        /** The workspace AVB key the Build stage signs with (`BuildSettings.signing.avbKeyRef` default). */
        public const val AVB_KEY_REF: String = "keys/avb.pem"
    }
}
