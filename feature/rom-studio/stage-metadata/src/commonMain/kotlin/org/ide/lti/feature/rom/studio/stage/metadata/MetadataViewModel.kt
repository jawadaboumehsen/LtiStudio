/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.metadata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ReleaseSettings

public class MetadataViewModel(
    public val workspaceId: String,
    initialSettings: ReleaseSettings? = null,
    store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: ReleaseSettings(),
        read = { it.release },
        write = { snapshot, value -> snapshot.copy(release = value) },
    )

    public val settings: StateFlow<ReleaseSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    public fun updateSettings(updated: ReleaseSettings) {
        sync.update(updated)
    }

    private val _manifest = MutableStateFlow<String?>(null)

    /** The manifest the last build wrote (`out/manifest.json`), or null before Release metadata has run. */
    public val manifest: StateFlow<String?> = _manifest.asStateFlow()

    public fun refreshManifest() {
        val facts = workspaceFacts ?: return
        viewModelScope.launch { _manifest.value = facts.read(workspaceId, "out/manifest.json") }
    }
}
