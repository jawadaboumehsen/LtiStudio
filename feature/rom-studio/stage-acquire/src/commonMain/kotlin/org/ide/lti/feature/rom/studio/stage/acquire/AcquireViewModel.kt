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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.pipeline.stages.FirmwareAcquisitionStage
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.workspace.FirmwareArchive
import org.ide.lti.core.domain.workspace.FirmwareArchives
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings

public class AcquireViewModel(
    public val workspaceId: String,
    initialSettings: AcquisitionSettings? = null,
    store: WorkspaceSettingsStore? = null,
    private val archives: FirmwareArchives? = null,
    private val facts: WorkspaceFacts? = null,
    private val pickFile: suspend () -> String? = { DialogUtils.selectFile()?.path },
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = targetDevice.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
        read = { it.acquisition },
        write = { snapshot, value -> snapshot.copy(acquisition = value) },
    )

    public val settings: StateFlow<AcquisitionSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    public fun updateSettings(updated: AcquisitionSettings) {
        sync.update(updated)
    }

    private val _integrity = MutableStateFlow(IntegrityFacts())

    /** The archive in the workspace and the SHA-256 the last run measured, for the Integrity pane. */
    public val integrity: StateFlow<IntegrityFacts> = _integrity.asStateFlow()

    private var integrityJob: Job? = null

    /** Re-reads the archive's size and hashes it as it is now; a newer request replaces an unfinished one. */
    public fun refreshIntegrity() {
        val facts = facts ?: return
        integrityJob?.cancel()
        val relPath = FirmwareAcquisitionStage.archiveRelPath(settings.value)
        // Another file's hash is never shown next to this one's expected hash, not even while this one is read.
        if (_integrity.value.archivePath != relPath) _integrity.value = IntegrityFacts()
        integrityJob = viewModelScope.launch {
            val stat = facts.stat(workspaceId, relPath)
            val measured = stat?.let { facts.sha256(workspaceId, relPath) }
            // An archive picked meanwhile is hashed by its own request: this result is for another file.
            if (FirmwareAcquisitionStage.archiveRelPath(settings.value) != relPath) return@launch
            _integrity.value = IntegrityFacts(
                archivePath = relPath.takeIf { stat != null },
                archiveSizeBytes = stat?.sizeBytes,
                measuredSha256 = measured,
            )
        }
    }

    private val _archiveState = MutableStateFlow(ArchiveState())

    /** The archives in the workspace folder, as last probed, and what Probe/Import is doing. */
    public val archiveState: StateFlow<ArchiveState> = _archiveState.asStateFlow()

    /** Lists the archives already in the workspace's `firmware/downloaded/`. */
    public fun probe() {
        viewModelScope.launch { archiveJob { it.list(workspaceId) } }
    }

    /** Asks for an archive on this computer, copies it into the workspace and selects it. */
    public fun importArchive() {
        if (_archiveState.value.busy) return
        viewModelScope.launch {
            val path = pickFile() ?: return@launch
            archiveJob { store ->
                val imported = store.import(workspaceId, path)
                selectArchive(imported)
                _archiveState.update { it.copy(lastDetectedFormat = imported.detectedFormat) }
                store.list(workspaceId)
            }
        }
    }

    /** Uses [archive] for the build: the Acquire stage then checks it exists instead of downloading. */
    public fun selectArchive(archive: FirmwareArchive) {
        updateSettings(settings.value.copy(mode = AcquisitionMode.IMPORT_ARCHIVE, archiveRef = archive.ref))
        _archiveState.update { it.copy(lastDetectedFormat = archive.detectedFormat) }
    }

    private suspend fun archiveJob(block: suspend (FirmwareArchives) -> List<FirmwareArchive>) {
        val store = archives ?: run {
            _archiveState.update { it.copy(error = "The build service is not available") }
            return
        }
        if (_archiveState.value.busy || workspaceId.isBlank()) return
        _archiveState.update { it.copy(busy = true, error = null) }
        val result = try {
            Result.success(block(store))
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Result.failure(e)
        }
        _archiveState.update { state ->
            result.fold(
                onSuccess = {
                    ArchiveState(
                        archives = it,
                        probed = true,
                        lastDetectedFormat = state.lastDetectedFormat,
                    )
                },
                onFailure = { state.copy(busy = false, error = it.message ?: "Could not read the archives") },
            )
        }
    }
}

/** What the Existing archives pane shows. */
public data class ArchiveState(
    val archives: List<FirmwareArchive> = emptyList(),
    val probed: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val lastDetectedFormat: PackageFormat? = null,
)

/** What the Integrity pane shows: the archive in the workspace and the hash its last run measured. */
public data class IntegrityFacts(
    val archivePath: String? = null,
    val archiveSizeBytes: Long? = null,
    val measuredSha256: String? = null,
)
