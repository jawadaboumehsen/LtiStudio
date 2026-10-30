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
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.ContentWorkbench
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

/** The coverage check of the Build & Sign stage: running, its result, or why it could not run. */
public data class CoverageState(
    val busy: String? = null,
    val result: CoverageResult? = null,
    val refusal: Refusal? = null,
    /** Every build gives files without sidecar lines those of their folder (`fillMissingSidecars`). */
    val fillInEveryBuild: Boolean = false,
)

public class BuildViewModel(
    public val workspaceId: String,
    initialSettings: BuildSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
    private val content: ContentWorkbench? = null,
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

    private val _coverage = MutableStateFlow(CoverageState())

    /** The work tree's permission and label coverage, as last checked (FR-015). */
    public val coverage: StateFlow<CoverageState> = _coverage.asStateFlow()

    public fun checkCoverage() {
        coverageTask("Checking the work tree") { it.coverage(workspaceId, partitions()) }
    }

    /**
     * Makes every build add the missing fs_config and file_contexts lines (FR-016), at the end of the Patches
     * stage. Writing them into work/configs now would not last: the next build assembles work/ again first.
     */
    public fun setFillInEveryBuild(on: Boolean) {
        val store = store ?: return
        viewModelScope.launch {
            store.update(workspaceId) { it.copy(customization = it.customization.copy(fillMissingSidecars = on)) }
                .onSuccess { _coverage.value = _coverage.value.copy(fillInEveryBuild = on) }
        }
    }

    private fun partitions(): List<String> = (device.value ?: targetDevice).dynamicPartitions

    private fun coverageTask(name: String, block: suspend (ContentWorkbench) -> CoverageResult) {
        val workbench = content ?: return
        if (_coverage.value.busy != null) return
        _coverage.value = _coverage.value.copy(busy = name, refusal = null)
        viewModelScope.launch {
            try {
                val fill = store?.current(workspaceId)?.customization?.fillMissingSidecars == true
                _coverage.value = CoverageState(result = block(workbench), fillInEveryBuild = fill)
            } catch (e: RefusalException) {
                _coverage.value = _coverage.value.copy(busy = null, refusal = e.refusal)
            }
        }
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
