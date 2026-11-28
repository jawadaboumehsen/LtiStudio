/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.workspace.FirmwareArchives
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.run.StageId

/** Classification of an OS-dragged file or directory dropped onto LtiRom Studio. */
enum class DropArtifactCategory(val displayLabel: String) {
    OTA_ZIP("OTA / Flashable ZIP"),
    PAYLOAD_BIN("A/B payload.bin"),
    ODIN_TAR("Odin .tar / .tar.md5"),
    PARTITION_IMAGE("Partition Image (.img)"),
    MOD_BUNDLE("Mod / Plugin Bundle"),
    APK_PACKAGE("Android Package (.apk)"),
    FOLDER("Directory"),
    UNSUPPORTED("Unsupported file"),
}

/** Classified representation of a single dropped file path before dispatch. */
data class ClassifiedDropFile(
    val path: String,
    val name: String,
    val extension: String,
    val category: DropArtifactCategory,
)

/** Actionable outcome reported after ingesting a dropped file. */
sealed interface DropRouteOutcome {
    data class ImportedFirmware(val name: String, val archiveRef: String, val workspaceId: String) : DropRouteOutcome

    data class OpenedPartitionImage(val path: String, val name: String) : DropRouteOutcome

    data class PreparedModBundle(val path: String, val name: String) : DropRouteOutcome

    data class QueuedApkInspection(val path: String, val name: String) : DropRouteOutcome

    data class IgnoredUnsupported(val name: String, val reason: String) : DropRouteOutcome

    data class Failed(val name: String, val message: String) : DropRouteOutcome
}

/** Live drag-and-drop overlay state observed by Studio, Partition Explorer, and desktop shells. */
data class DropOverlayState(
    val isDraggingOver: Boolean = false,
    val hoverFiles: List<ClassifiedDropFile> = emptyList(),
    val isIngesting: Boolean = false,
    val lastOutcomes: List<DropRouteOutcome> = emptyList(),
    val lastMessage: String? = null,
    val lastError: String? = null,
)

/** Classifies dropped files into LtiRom artifacts without performing filesystem I/O. */
object DropFileClassifier {
    private val imageExtensions = setOf("img", "erofs", "ext4", "f2fs")
    private val modExtensions = setOf("ltimod", "ltiplugin")
    private val modZipHints = setOf("mod", "module", "magisk", "kernelsu", "ksu", "patch")

    fun classify(path: String, isDirectory: Boolean = false, preferredStage: StageId? = null): ClassifiedDropFile {
        val normalized = path.replace('\\', '/').trimEnd('/')
        val name = normalized.substringAfterLast('/')
        val lower = name.lowercase()
        val ext = lower.substringAfterLast('.', missingDelimiterValue = "")

        val category = when {
            isDirectory -> DropArtifactCategory.FOLDER

            lower == "payload.bin" || lower.endsWith(".payload.bin") -> DropArtifactCategory.PAYLOAD_BIN

            lower.endsWith(".tar.md5") || lower.endsWith(".tar.lz4") || ext == "tar" ->
                DropArtifactCategory.ODIN_TAR

            ext in imageExtensions -> DropArtifactCategory.PARTITION_IMAGE

            ext in modExtensions -> DropArtifactCategory.MOD_BUNDLE

            ext == "apk" -> DropArtifactCategory.APK_PACKAGE

            ext == "zip" -> {
                val isModHint = preferredStage == StageId.MODULE_APPLICATION ||
                    modZipHints.any { hint -> lower.contains(hint) }
                if (isModHint) DropArtifactCategory.MOD_BUNDLE else DropArtifactCategory.OTA_ZIP
            }

            else -> DropArtifactCategory.UNSUPPORTED
        }

        return ClassifiedDropFile(
            path = path,
            name = name.ifBlank { path },
            extension = ext,
            category = category,
        )
    }
}

/**
 * Orchestrates OS file drag-and-drop ingest across workspaces (importing `.zip` OTAs, `payload.bin`,
 * `.tar.md5`), Partition Explorer (`.img`), Mod Manager (`.ltimod`/mod bundles), and APK diff/cert
 * checks while tracking active operations in [BackgroundSessionManager].
 */
class WorkspaceDropCoordinator(
    private val workspaceManager: WorkspaceManager? = null,
    private val firmwareArchives: FirmwareArchives? = null,
    private val sessions: BackgroundSessionManager? = null,
) {
    private val _state = MutableStateFlow(DropOverlayState())
    val state: StateFlow<DropOverlayState> = _state.asStateFlow()

    private val _partitionOpenRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val partitionOpenRequests: SharedFlow<String> = _partitionOpenRequests.asSharedFlow()

    private val _modBundleRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** A dropped mod bundle waiting for Plugins > Import to inspect it (that page may open after the drop). */
    private val _pendingModBundle = MutableStateFlow<String?>(null)
    val pendingModBundle: StateFlow<String?> = _pendingModBundle.asStateFlow()

    /** The waiting mod bundle, handed over once. */
    fun takePendingModBundle(): String? = _pendingModBundle.getAndUpdate { null }
    val modBundleRequests: SharedFlow<String> = _modBundleRequests.asSharedFlow()

    private val _apkAuditRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val apkAuditRequests: SharedFlow<String> = _apkAuditRequests.asSharedFlow()

    fun onDragEntered(paths: List<String>, preferredStage: StageId? = null) {
        val classified = paths.map { DropFileClassifier.classify(it, preferredStage = preferredStage) }
        _state.update {
            it.copy(
                isDraggingOver = true,
                hoverFiles = classified,
            )
        }
    }

    fun onDragExited() {
        _state.update {
            it.copy(
                isDraggingOver = false,
                hoverFiles = emptyList(),
            )
        }
    }

    fun clearBanner() {
        _state.update { it.copy(lastMessage = null, lastError = null, lastOutcomes = emptyList()) }
    }

    suspend fun handleDroppedPaths(
        paths: List<String>,
        directoryFlags: Map<String, Boolean> = emptyMap(),
        activeWorkspaceId: String? = null,
        preferredStage: StageId? = null,
    ): List<DropRouteOutcome> {
        val items = paths.map { path ->
            DropFileClassifier.classify(
                path = path,
                isDirectory = directoryFlags[path] == true,
                preferredStage = preferredStage,
            )
        }
        _state.update {
            it.copy(
                isDraggingOver = false,
                hoverFiles = emptyList(),
                isIngesting = true,
                lastError = null,
            )
        }

        val workspaceId = activeWorkspaceId ?: workspaceManager?.currentWorkspace?.value?.id
        val outcomes = items.map { item -> dispatchItem(item, workspaceId) }

        val summary = buildSummaryMessage(outcomes)
        val firstFailure = outcomes.filterIsInstance<DropRouteOutcome.Failed>().firstOrNull()?.message
        _state.update {
            it.copy(
                isIngesting = false,
                lastOutcomes = outcomes,
                lastMessage = summary,
                lastError = firstFailure,
            )
        }
        return outcomes
    }

    private suspend fun dispatchItem(item: ClassifiedDropFile, workspaceId: String?): DropRouteOutcome =
        when (item.category) {
            DropArtifactCategory.OTA_ZIP,
            DropArtifactCategory.PAYLOAD_BIN,
            DropArtifactCategory.ODIN_TAR,
            -> importFirmwareItem(item, workspaceId)

            DropArtifactCategory.PARTITION_IMAGE -> openPartitionImageItem(item)

            DropArtifactCategory.MOD_BUNDLE -> routeModBundleItem(item)

            DropArtifactCategory.APK_PACKAGE -> routeApkItem(item)

            DropArtifactCategory.FOLDER -> {
                val reason = "Drop an OTA .zip, payload.bin, .tar.md5, .img, or mod bundle file directly."
                sessions?.recordAlert(
                    title = "Folder dropped: ${item.name}",
                    message = reason,
                )
                DropRouteOutcome.IgnoredUnsupported(item.name, reason)
            }

            DropArtifactCategory.UNSUPPORTED -> {
                val reason = "Unsupported extension '${item.extension.ifBlank { "none" }}'."
                DropRouteOutcome.IgnoredUnsupported(item.name, reason)
            }
        }

    private suspend fun importFirmwareItem(item: ClassifiedDropFile, workspaceId: String?): DropRouteOutcome {
        val importer = firmwareArchives
        return when {
            workspaceId.isNullOrBlank() -> DropRouteOutcome.Failed(
                name = item.name,
                message = "Open or create a workspace before dropping ${item.name}.",
            )
            importer == null -> DropRouteOutcome.Failed(
                name = item.name,
                message = "Firmware importer service is unavailable in this session.",
            )
            else -> runImportWithSession(importer, item, workspaceId)
        }
    }

    private suspend fun runImportWithSession(
        importer: FirmwareArchives,
        item: ClassifiedDropFile,
        workspaceId: String,
    ): DropRouteOutcome {
        val jump = BackgroundSessionJumpTarget.WorkspaceStage(workspaceId, StageId.FIRMWARE_ACQUISITION)
        val sessionId = sessions?.startSession(
            kind = BackgroundSessionKind.FIRMWARE_IMPORT,
            title = "Import ${item.name}",
            detail = "Staging ${item.category.displayLabel} into workspace…",
            workspaceId = workspaceId,
            jumpTarget = jump,
        )
        return runCatchingCancellable { importer.import(workspaceId, item.path) }.fold(
            onSuccess = { archive ->
                if (sessionId != null) {
                    sessions.completeSession(
                        id = sessionId,
                        detail = "Imported into ${archive.ref}",
                        jumpTarget = jump,
                    )
                }
                DropRouteOutcome.ImportedFirmware(
                    name = archive.name,
                    archiveRef = archive.ref,
                    workspaceId = workspaceId,
                )
            },
            onFailure = { error ->
                val message = error.message ?: "Failed to import ${item.name}"
                if (sessionId != null) {
                    sessions.failSession(sessionId, message)
                }
                DropRouteOutcome.Failed(item.name, message)
            },
        )
    }

    private fun openPartitionImageItem(item: ClassifiedDropFile): DropRouteOutcome {
        _partitionOpenRequests.tryEmit(item.path)
        val jump = BackgroundSessionJumpTarget.PartitionExplorer(item.path)
        sessions?.recordAlert(
            title = "Partition image loaded",
            message = "${item.name} queued in Partition Explorer.",
            notifyOs = false,
            jumpTarget = jump,
        )
        sessions?.requestJump(jump)
        return DropRouteOutcome.OpenedPartitionImage(item.path, item.name)
    }

    private fun routeModBundleItem(item: ClassifiedDropFile): DropRouteOutcome {
        _pendingModBundle.value = item.path
        _modBundleRequests.tryEmit(item.path)
        val wsId = workspaceManager?.currentWorkspace?.value?.id
        val jump = if (wsId != null) {
            BackgroundSessionJumpTarget.WorkspaceStage(wsId, StageId.MODULE_APPLICATION)
        } else {
            BackgroundSessionJumpTarget.PluginManager
        }
        sessions?.recordAlert(
            title = "Mod bundle ready",
            message = "${item.name} opened in Plugins > Import for review.",
            notifyOs = false,
            jumpTarget = jump,
        )
        sessions?.requestJump(jump)
        return DropRouteOutcome.PreparedModBundle(item.path, item.name)
    }

    private fun routeApkItem(item: ClassifiedDropFile): DropRouteOutcome {
        _apkAuditRequests.tryEmit(item.path)
        val jump = BackgroundSessionJumpTarget.DiffInspector
        sessions?.recordAlert(
            title = "APK package queued",
            message = "${item.name} ready for certificate & structural inspection.",
            notifyOs = false,
            jumpTarget = jump,
        )
        sessions?.requestJump(jump)
        return DropRouteOutcome.QueuedApkInspection(item.path, item.name)
    }

    private fun buildSummaryMessage(outcomes: List<DropRouteOutcome>): String? {
        if (outcomes.isEmpty()) return null
        val parts = outcomes.map { outcome ->
            when (outcome) {
                is DropRouteOutcome.ImportedFirmware -> "Imported ${outcome.name} -> ${outcome.archiveRef}"
                is DropRouteOutcome.OpenedPartitionImage -> "Opened ${outcome.name} in Partition Explorer"
                is DropRouteOutcome.PreparedModBundle -> "Queued mod bundle ${outcome.name}"
                is DropRouteOutcome.QueuedApkInspection -> "Queued APK ${outcome.name} for inspection"
                is DropRouteOutcome.IgnoredUnsupported -> "Ignored ${outcome.name}: ${outcome.reason}"
                is DropRouteOutcome.Failed -> "Failed ${outcome.name}: ${outcome.message}"
            }
        }
        return parts.joinToString(" · ")
    }
}
