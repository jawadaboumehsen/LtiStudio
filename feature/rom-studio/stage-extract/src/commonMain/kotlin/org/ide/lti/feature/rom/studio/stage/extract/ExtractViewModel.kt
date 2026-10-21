/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceFile
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ExtractionSettings

/** What the workspace holds for Extract: the source archive's members and the images the last run produced. */
public data class ExtractFacts(
    val loaded: Boolean = false,
    val archive: String? = null,
    val members: List<String> = emptyList(),
    val extracted: List<WorkspaceFile> = emptyList(),
)

public class ExtractViewModel(
    public val workspaceId: String,
    initialSettings: ExtractionSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: ExtractionSettings(),
        read = { it.extraction },
        write = { snapshot, value -> snapshot.copy(extraction = value) },
    )

    public val settings: StateFlow<ExtractionSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    public fun updateSettings(updated: ExtractionSettings) {
        sync.update(updated)
    }

    private val _facts = MutableStateFlow(ExtractFacts())
    public val facts: StateFlow<ExtractFacts> = _facts.asStateFlow()

    /** Reads the archive Acquire selected (or the newest downloaded one) and `firmware/extracted/`. */
    public fun refreshFacts() {
        val facts = workspaceFacts ?: return
        viewModelScope.launch {
            val archive = store?.current(workspaceId)?.acquisition?.archiveRef
                ?: facts.list(workspaceId, "firmware/downloaded", name = "*.zip").lastOrNull()
                    ?.let { "firmware/downloaded/${it.path}" }
            _facts.value = ExtractFacts(
                loaded = true,
                archive = archive,
                members = archive?.let { facts.zipMembers(workspaceId, it) }.orEmpty(),
                extracted = facts.list(workspaceId, "firmware/extracted", name = "*.img"),
            )
        }
    }
}
