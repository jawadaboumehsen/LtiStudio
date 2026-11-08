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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.BootWorkbench
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
    /** The stock images Extract unpacked, the size estimate of a partition not built yet. */
    val stockImages: List<WorkspaceFile> = emptyList(),
    val packages: List<WorkspaceFile> = emptyList(),
    /** The package the last build made (named by its measured hash's path), and its member names. */
    val currentPackage: WorkspaceFile? = null,
    val zipMembers: List<String> = emptyList(),
    val zipSha256: String? = null,
    val avbPublicKeySha1: String? = null,
    val platformCertSha1: String? = null,
    /** The ROM version the package name uses (Assemble settings). */
    val romVersion: String = "",
    /** Per partition of the last build: the bytes of its files and of its image before the AVB footer. */
    val contentAndImage: Map<String, Pair<Long, Long>> = emptyMap(),
    /** EROFS compressions the boot kernel reads; null when it cannot be read (no extracted boot image yet). */
    val erofsCompressions: List<String>? = null,
)

/** The coverage check of the Build & Sign stage: running, its result, or why it could not run. */
public data class CoverageState(
    val busy: String? = null,
    val result: CoverageResult? = null,
    val refusal: Refusal? = null,
    /** Every build gives files without sidecar lines those of their folder (`fillMissingSidecars`). */
    val fillInEveryBuild: Boolean = false,
    /** The user's coverage rules (`CustomizationSettings.coverageRules`), tried before "like its folder". */
    val rules: String = "",
    /** Why the coverage settings were not saved, or null. */
    val saveError: String? = null,
)

public class BuildViewModel(
    public val workspaceId: String,
    initialSettings: BuildSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
    private val content: ContentWorkbench? = null,
    private val boot: BootWorkbench? = null,
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
                .onSuccess { _coverage.value = _coverage.value.copy(fillInEveryBuild = on, saveError = null) }
                .onFailure { _coverage.value = _coverage.value.copy(saveError = it.message ?: "It was not saved.") }
        }
    }

    // Debounced and saved in order, so a burst of keys can never leave an older text saved last.
    private val rulesSync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = "",
        read = { it.customization.coverageRules },
        write = { snapshot, value ->
            snapshot.copy(
                customization = snapshot.customization.copy(coverageRules = value),
            )
        },
    )

    init {
        viewModelScope.launch { rulesSync.settings.collect { _coverage.value = _coverage.value.copy(rules = it) } }
        viewModelScope.launch { rulesSync.saveError.collect { _coverage.value = _coverage.value.copy(saveError = it) } }
    }

    /** Saves the user's coverage rules (tried before "like its folder" when lines are added). */
    public fun setCoverageRules(text: String) {
        _coverage.value = _coverage.value.copy(rules = text)
        rulesSync.update(text)
    }

    private fun partitions(): List<String> = (device.value ?: targetDevice).dynamicPartitions

    private fun coverageTask(name: String, block: suspend (ContentWorkbench) -> CoverageResult) {
        val workbench = content ?: return
        if (_coverage.value.busy != null) return
        _coverage.value = _coverage.value.copy(busy = name, refusal = null)
        viewModelScope.launch {
            try {
                val fill = store?.current(workspaceId)?.customization?.fillMissingSidecars == true
                _coverage.value = CoverageState(
                    result = block(workbench),
                    fillInEveryBuild = fill,
                    rules = _coverage.value.rules,
                )
            } catch (e: RefusalException) {
                _coverage.value = _coverage.value.copy(busy = null, refusal = e.refusal)
            } catch (e: CancellationException) {
                _coverage.value = _coverage.value.copy(busy = null)
                throw e
            } catch (e: Exception) {
                // A lost connection must not leave the check busy for good.
                val cause = e.message ?: "The check could not run"
                val refusal = Refusal(cause, name, "Check that the build service is running, then try again.")
                _coverage.value = _coverage.value.copy(busy = null, refusal = refusal)
            }
        }
    }

    public fun refresh() {
        val facts = workspaceFacts ?: return
        viewModelScope.launch {
            suspend fun value(key: String) = facts.lastValue(workspaceId, key)
            val packages = facts.list(workspaceId, "out/package", name = "*.zip")
            // sha256sum prints the file it hashed: that is the package of the last build, whatever its name sorts as.
            val built = value(RuntimeKeys.ZIP_SHA256)?.trim()?.substringAfterLast('/')
            val current = packages.firstOrNull { it.path == built }
            _facts.value = BuildFacts(
                loaded = true,
                images = facts.list(workspaceId, "out/images", name = "*.img"),
                stockImages = facts.list(workspaceId, FirmwareExtractionStage.EXTRACTED, name = "*.img"),
                packages = packages,
                currentPackage = current,
                zipMembers = current?.let { facts.zipMembers(workspaceId, "out/package/${it.path}") }.orEmpty(),
                zipSha256 = value(RuntimeKeys.ZIP_SHA256)?.trim()?.substringBefore(' '),
                avbPublicKeySha1 = value(RuntimeKeys.AVB_PUBKEY_SHA1)?.trim()?.substringBefore(' '),
                platformCertSha1 = value(BuildFlashableZipStage.PLATFORM_CERT_SHA1)?.trim()?.substringBefore(' '),
                romVersion = store?.current(workspaceId)?.assembly?.romVersion.orEmpty(),
                contentAndImage = partitions().mapNotNull { p ->
                    val content = value(BuildFlashableZipStage.contentSize(p))?.trim()?.substringBefore('\t')
                        ?.toLongOrNull()
                    val image = value(RuntimeKeys.imageSize(p))?.trim()?.toLongOrNull()
                    if (content != null && image != null) p to (content to image) else null
                }.toMap(),
                erofsCompressions = try {
                    boot?.kernelFacts(workspaceId)?.erofsCompressions
                } catch (_: RefusalException) {
                    null
                },
            )
            store?.current(workspaceId)?.customization?.let { custom ->
                _coverage.value = _coverage.value.copy(fillInEveryBuild = custom.fillMissingSidecars)
            }
        }
    }
}
