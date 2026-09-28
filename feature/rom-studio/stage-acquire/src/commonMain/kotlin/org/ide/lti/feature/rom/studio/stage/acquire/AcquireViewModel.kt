/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings

public class AcquireViewModel(public val workspaceId: String, initialSettings: AcquisitionSettings? = null) :
    ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val _settings = MutableStateFlow(
        initialSettings ?: AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = targetDevice.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
    )
    public val settings: StateFlow<AcquisitionSettings> = _settings.asStateFlow()

    public fun updateSettings(updated: AcquisitionSettings) {
        _settings.update { updated }
    }
}
