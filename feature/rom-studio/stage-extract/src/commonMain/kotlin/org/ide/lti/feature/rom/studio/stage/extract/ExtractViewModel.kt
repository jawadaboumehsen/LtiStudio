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
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveListing
import org.ide.lti.core.domain.pipeline.toolkit.ChecksumResult
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.pipeline.toolkit.PackageManifest
import org.ide.lti.core.domain.pipeline.toolkit.PartitionImage
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.ArchiveWorkbench
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
    /** The partition being unpacked again, and what the last re-extract did. */
    val reextracting: String? = null,
    val reextractMessage: String? = null,
    /** The archive browser: where it is and what that level holds. */
    val browse: ArchiveBrowse = ArchiveBrowse(),
)

/** One place in an archive: [archive] itself, or the member [chain] leads to inside it, and what it holds. */
public data class ArchiveBrowse(
    /** A workspace path (`firmware/downloaded/update.zip`) or a file the user picked (a Windows path). */
    val archive: String? = null,
    val chain: List<String> = emptyList(),
    val listing: ArchiveListing? = null,
    val selected: Set<String> = emptySet(),
    val busy: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
)

/** What the archive browser can do; [ExtractStageEditor] wires these to [ExtractViewModel]. */
public data class ArchiveActions(
    val pickArchive: () -> Unit = {},
    val enter: (String) -> Unit = {},
    val up: () -> Unit = {},
    val toggle: (String) -> Unit = {},
    val copyOut: () -> Unit = {},
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
    private val archives: ArchiveWorkbench? = null,
    private val pickArchive: suspend () -> String? = { DialogUtils.selectFilePath("Pick an archive to look inside") },
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

    /**
     * Unpacks [partition]'s image into `firmware/extracted/<p>` and its sidecars again, the
     * user's own image when one is set, without a whole Extract run.
     */
    public fun reextract(partition: String) {
        val workbench = images ?: return
        viewModelScope.launch {
            _facts.value = _facts.value.copy(reextracting = partition, replaceRefusal = null, reextractMessage = null)
            try {
                workbench.reextract(workspaceId, partition, settings.value.replacementImages[partition])
                _facts.value = _facts.value.copy(reextractMessage = "$partition was extracted again.")
            } catch (e: RefusalException) {
                _facts.value = _facts.value.copy(replaceRefusal = e.refusal)
            } finally {
                _facts.value = _facts.value.copy(reextracting = null)
            }
        }
    }

    /** Goes back to the firmware's own image for [partition] (the imported file stays until freed). */
    public fun useStockImage(partition: String) {
        updateSettings(settings.value.copy(replacementImages = settings.value.replacementImages - partition))
    }

    /** Looks inside a file the user picks (any archive on the PC), at its top level. */
    public fun openArchive() {
        viewModelScope.launch { pickArchive()?.let { browse(it, emptyList()) } }
    }

    /** Goes into [member] of the current level (an archive inside the archive). */
    public fun enter(member: String) {
        val b = _facts.value.browse
        val archive = b.archive ?: return
        viewModelScope.launch { browse(archive, b.chain + member) }
    }

    /** Back to the level holding the current one. */
    public fun up() {
        val b = _facts.value.browse
        val archive = b.archive ?: return
        if (b.chain.isEmpty()) return
        viewModelScope.launch { browse(archive, b.chain.dropLast(1)) }
    }

    public fun toggleMember(member: String) {
        val b = _facts.value.browse
        val selected = if (member in b.selected) b.selected - member else b.selected + member
        _facts.value = _facts.value.copy(browse = b.copy(selected = selected))
    }

    /** Copies the selected members of the current level to `out/toolkit/archive/`. */
    public fun copyOutSelected() {
        val b = _facts.value.browse
        val workbench = archives?.takeIf { b.selected.isNotEmpty() && b.busy == null } ?: return
        val archive = b.archive ?: return
        viewModelScope.launch {
            setBrowse { it.copy(busy = "Copying ${b.selected.size} item(s) out", message = null, refusal = null) }
            try {
                val dest = workbench.extract(workspaceId, archive, b.chain, b.selected.sorted())
                setBrowse { it.copy(message = "Copied ${b.selected.size} item(s) to $dest", selected = emptySet()) }
            } catch (e: RefusalException) {
                setBrowse { it.copy(refusal = e.refusal) }
            } finally {
                setBrowse { it.copy(busy = null) }
            }
        }
    }

    private suspend fun browse(archive: String, chain: List<String>) {
        val workbench = archives ?: return
        val name = (chain.lastOrNull() ?: archive).substringAfterLast('/').substringAfterLast('\\')
        setBrowse { it.copy(busy = "Reading $name", refusal = null, message = null) }
        try {
            val listing = workbench.list(workspaceId, archive, chain)
            if (listing.notArchive && chain.isNotEmpty()) {
                // Not an archive (an image, a payload): stay where we were and say what it is.
                setBrowse { it.copy(message = "$name is not an archive (${listing.format.name}).") }
            } else {
                setBrowse { ArchiveBrowse(archive = archive, chain = chain, listing = listing) }
            }
        } catch (e: RefusalException) {
            setBrowse { it.copy(refusal = e.refusal) }
        } finally {
            setBrowse { it.copy(busy = null) }
        }
    }

    private fun setBrowse(change: (ArchiveBrowse) -> ArchiveBrowse) {
        _facts.value = _facts.value.copy(browse = change(_facts.value.browse))
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

            // The browser opens on the workspace's archive until the user picks another file.
            if (archive != null && _facts.value.browse.archive == null) browse(archive, emptyList())
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
