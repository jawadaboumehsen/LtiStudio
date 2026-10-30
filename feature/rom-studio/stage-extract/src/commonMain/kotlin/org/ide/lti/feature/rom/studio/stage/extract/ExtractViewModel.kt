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
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.toolkit.ChecksumResult
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
import org.ide.lti.core.domain.pipeline.toolkit.PartitionImage
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.ImageWorkbench
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
    val detectedFormat: PackageFormat? = null,
    val checksumResult: ChecksumResult? = null,
    val partitionImages: List<PartitionImage> = emptyList(),
    val refusal: Refusal? = null,
    /** The partition whose own image is being imported, and why the last import was refused. */
    val replacing: String? = null,
    val replaceRefusal: Refusal? = null,
)

public class ExtractViewModel(
    public val workspaceId: String,
    initialSettings: ExtractionSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
    private val images: ImageWorkbench? = null,
    private val pickFile: suspend () -> String? = {
        DialogUtils.selectFilePath(
            "Pick an EROFS, ext4 or F2FS partition image",
        )
    },
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

    /** Uses an image the user picks instead of the firmware's for [partition]. */
    public fun replacePartition(partition: String) {
        val workbench = images ?: return
        viewModelScope.launch {
            val path = pickFile() ?: return@launch
            _facts.value = _facts.value.copy(replacing = partition, replaceRefusal = null)
            try {
                val sha256 = workbench.importReplacement(workspaceId, partition, path)
                updateSettings(
                    settings.value.copy(replacementImages = settings.value.replacementImages + (partition to sha256)),
                )
            } catch (e: RefusalException) {
                _facts.value = _facts.value.copy(replaceRefusal = e.refusal)
            } finally {
                _facts.value = _facts.value.copy(replacing = null)
            }
        }
    }

    /** Goes back to the firmware's own image for [partition] (the imported file stays until freed). */
    public fun useStockImage(partition: String) {
        updateSettings(settings.value.copy(replacementImages = settings.value.replacementImages - partition))
    }

    /** Reads the archive Acquire selected (or the newest downloaded one) and `firmware/extracted/`. */
    public fun refreshFacts() {
        val facts = workspaceFacts ?: return
        viewModelScope.launch {
            val archive = store?.current(workspaceId)?.acquisition?.archiveRef
                ?: facts.list(workspaceId, "firmware/downloaded", name = "*.zip").lastOrNull()
                    ?.let { "firmware/downloaded/${it.path}" }

            val manifestRaw = facts.lastValue(workspaceId, RuntimeKeys.PACKAGE_MANIFEST)
            val manifest = manifestRaw?.let { runCatching { PackageManifest.decode(it) }.getOrNull() }

            val checksumRaw = facts.lastValue(workspaceId, RuntimeKeys.PACKAGE_CHECKSUM_RESULT)
            val checksum = checksumRaw?.let { runCatching { ChecksumResult.decode(it) }.getOrNull() }

            val imagesRaw = facts.lastValue(workspaceId, RuntimeKeys.EXTRACT_IMAGES)
            val images = imagesRaw?.let { runCatching { PartitionImage.decodeList(it) }.getOrNull() }.orEmpty()

            _facts.value = _facts.value.copy(
                loaded = true,
                archive = archive,
                members = archive?.let { facts.zipMembers(workspaceId, it) }.orEmpty(),
                extracted = facts.list(workspaceId, "firmware/extracted", name = "*.img"),
                detectedFormat = manifest?.format,
                checksumResult = checksum,
                partitionImages = images,
                refusal = manifest?.refusal,
            )
        }
    }
}
