/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceFile
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings

/** What the last builds produced, for the Build & Sign panes; empty before Build & Sign has run. */
public data class BuildFacts(
    val loaded: Boolean = false,
    val images: List<WorkspaceFile> = emptyList(),
    val packages: List<WorkspaceFile> = emptyList(),
    /** Member names of the newest package. */
    val zipMembers: List<String> = emptyList(),
    val zipSha256: String? = null,
    val avbPublicKeySha1: String? = null,
    val platformCertSha1: String? = null,
    /** The ROM version the package name uses (Assemble settings). */
    val romVersion: String = "",
)

public class BuildViewModel(
    public val workspaceId: String,
    initialSettings: BuildSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: BuildSettings(),
        read = { it.build },
        write = { snapshot, value -> snapshot.copy(build = value) },
    )

    public val settings: StateFlow<BuildSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    private val _facts = MutableStateFlow(BuildFacts())
    public val facts: StateFlow<BuildFacts> = _facts.asStateFlow()

    public fun updateSettings(updated: BuildSettings) {
        sync.update(updated)
    }

    public fun refresh() {
        val facts = workspaceFacts ?: return
        viewModelScope.launch {
            suspend fun value(key: String) = facts.lastValue(workspaceId, key)
            val packages = facts.list(workspaceId, "out/package", name = "*.zip")
            _facts.value = BuildFacts(
                loaded = true,
                images = facts.list(workspaceId, "out/images", name = "*.img"),
                packages = packages,
                zipMembers = packages.lastOrNull()?.let { facts.zipMembers(workspaceId, "out/package/${it.path}") }
                    .orEmpty(),
                zipSha256 = value(RuntimeKeys.ZIP_SHA256)?.trim()?.substringBefore(' '),
                avbPublicKeySha1 = value(RuntimeKeys.AVB_PUBKEY_SHA1)?.trim()?.substringBefore(' '),
                platformCertSha1 = value(BuildFlashableZipStage.PLATFORM_CERT_SHA1)?.trim()?.substringBefore(' '),
                romVersion = store?.current(workspaceId)?.assembly?.romVersion.orEmpty(),
            )
        }
    }
}
