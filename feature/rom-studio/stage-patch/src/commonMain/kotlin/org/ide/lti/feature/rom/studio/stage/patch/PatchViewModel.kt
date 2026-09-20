/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.PluginLockfile
import org.ide.lti.core.model.workspace.PluginLockfileEntry

public class PatchViewModel(
    public val workspaceId: String,
    initialSettings: CustomizationSettings? = null,
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val _settings = MutableStateFlow(
        initialSettings ?: CustomizationSettings(
            lockfile = PluginLockfile(
                schemaVersion = 1,
                entries = listOf(
                    PluginLockfileEntry(
                        publisher = "org.ide.lti",
                        id = "mod-microg-core",
                        version = "0.3.1",
                        contentDigest = "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    ),
                    PluginLockfileEntry(
                        publisher = "org.ide.lti",
                        id = "mod-custom-prop-injector",
                        version = "1.0.0",
                        contentDigest = "sha256:ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
                    ),
                ),
            ),
            enabledPackages = listOf("org.ide.lti:mod-microg-core", "org.ide.lti:mod-custom-prop-injector"),
            settings = mapOf(
                "org.ide.lti:mod-custom-prop-injector" to mapOf(
                    "ro.config.low_ram" to "false",
                    "persist.sys.theme" to "dark",
                ),
            ),
        ),
    )
    public val settings: StateFlow<CustomizationSettings> = _settings.asStateFlow()

    public fun updateSettings(updated: CustomizationSettings) {
        _settings.update { updated }
    }
}
