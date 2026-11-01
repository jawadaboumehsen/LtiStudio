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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import org.ide.lti.core.domain.pipeline.toolkit.ApexPatchResult
import org.ide.lti.core.domain.pipeline.toolkit.ApkPatchResult
import org.ide.lti.core.domain.pipeline.toolkit.ArchiveDiff
import org.ide.lti.core.domain.pipeline.toolkit.CodePackageDiff
import org.ide.lti.core.domain.pipeline.toolkit.ConfigEditDiff
import org.ide.lti.core.domain.pipeline.toolkit.ConfigKind
import org.ide.lti.core.domain.pipeline.toolkit.ConfigValidators
import org.ide.lti.core.domain.pipeline.toolkit.DeodexAppResult
import org.ide.lti.core.domain.pipeline.toolkit.FileDiff
import org.ide.lti.core.domain.pipeline.toolkit.OmcInfo
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.TreeDiff
import org.ide.lti.core.domain.pipeline.toolkit.WorkspaceDiff
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.domain.workbench.CompareWorkbench
import org.ide.lti.core.domain.workbench.ContentWorkbench
import org.ide.lti.core.domain.workbench.RiskyFeature
import org.ide.lti.core.domain.workbench.WorkbenchRunner

/** Navigator items of the Patches/Mods stage that open a content tool instead of the plugin pages. */
public object ContentTools {
    public const val DEODEX: String = "deodex"
    public const val PACKAGES: String = "package-editor"
    public const val LIBRARY: String = "library-patch"
    public const val CONFIGS: String = "config-files"
    public const val COMPARE: String = "compare"
    public const val CSC: String = "samsung-csc"
    public val ALL: Set<String> = setOf(DEODEX, PACKAGES, LIBRARY, CONFIGS, COMPARE, CSC)
}

/** What the content tools compare (FR-033). */
public enum class CompareKind { FILES, ARCHIVES, FOLDERS, CODE }

/** The content tools of the Patches/Mods stage as their panes show them (FR-028..FR-033). */
public data class ContentToolsState(
    val busy: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
    val pendingAck: String? = null,
    val deodex: List<DeodexAppResult>? = null,
    val decodedFolder: String? = null,
    val rebuilt: ApkPatchResult? = null,
    val library: ApexPatchResult? = null,
    val configKind: ConfigKind = ConfigKind.BUILD_PROP,
    val configPath: String = "work/system/system/build.prop",
    val configText: String? = null,
    val configErrors: List<String> = emptyList(),
    val configSaved: ConfigEditDiff? = null,
    val fileDiff: FileDiff? = null,
    val archiveDiff: ArchiveDiff? = null,
    val folderDiff: WorkspaceDiff? = null,
    val codeDiff: CodePackageDiff? = null,
    val csc: OmcInfo? = null,
    /** Partitions that have a stock tree to compare the build with, and the last such comparison. */
    val comparable: List<String>? = null,
    val stockDiff: TreeDiff? = null,
    /** The package of [stockDiff] last compared method by method, and that comparison. */
    val stockCode: Pair<String, CodePackageDiff>? = null,
)

public class ContentToolsViewModel(
    public val workspaceId: String,
    private val content: ContentWorkbench? = null,
    private val comparing: CompareWorkbench? = null,
    private val runner: WorkbenchRunner? = null,
    private val environment: AppEnvironmentPort? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(ContentToolsState())
    public val state: StateFlow<ContentToolsState> = _state.asStateFlow()

    private var job: Job? = null
    private var afterAck: (() -> Unit)? = null

    public fun deodex() {
        task("Deodexing") { tools ->
            val results = tools.deodex(workspaceId)
            _state.update { it.copy(deodex = results) }
        }
    }

    public fun decode(path: String) {
        task("Decoding ${path.substringAfterLast('/')}") { tools ->
            val folder = tools.decode(workspaceId, path)
            _state.update { it.copy(decodedFolder = folder, message = "Decoded to $folder; edit it, then rebuild.") }
            environment?.openFolder(folder)
        }
    }

    public fun rebuild(path: String) {
        withAck(RiskyFeature.PACKAGE_REBUILD) {
            task("Rebuilding ${path.substringAfterLast('/')}") { tools ->
                val result = tools.rebuild(workspaceId, path, PLATFORM_KEY_REF)
                _state.update { it.copy(rebuilt = result, message = "Rebuilt $path; the original is a restore point.") }
            }
        }
    }

    public fun patchLibrary(apex: String, library: String, find: String, replace: String, dest: String) {
        withAck(RiskyFeature.LIBRARY_PATCH) {
            task("Patching ${library.substringAfterLast('/')}") { tools ->
                val result = tools.patchApexLibrary(workspaceId, apex, library, find, replace, dest)
                _state.update { it.copy(library = result, message = result.message) }
            }
        }
    }

    public fun loadConfig(kind: ConfigKind, path: String) {
        // While a task runs nothing changes: a switch now would pair this read's text with another path.
        if (_state.value.busy != null) return
        _state.update { it.copy(configKind = kind, configPath = path, configText = null, configErrors = emptyList()) }
        task("Reading ${path.substringAfterLast('/')}") { tools ->
            val text = tools.readConfig(workspaceId, path)
            _state.update { if (it.configPath == path) it.copy(configText = text, configSaved = null) else it }
        }
    }

    /** Edits the loaded text; the syntax is checked as it is typed (FR-031). */
    public fun editConfig(text: String) {
        _state.update { it.copy(configText = text, configErrors = errorsOf(it.configKind, text)) }
    }

    public fun saveConfig() {
        val current = _state.value
        val text = current.configText ?: return
        task("Saving ${current.configPath.substringAfterLast('/')}") { tools ->
            val diff = tools.saveConfig(workspaceId, current.configKind, current.configPath, text)
            _state.update {
                it.copy(
                    configSaved = diff,
                    message = "Saved; ${diff.differences.size} line(s) changed, old text kept as a restore point.",
                )
            }
        }
    }

    public fun compare(kind: CompareKind, a: String, b: String) {
        compareTask("Comparing") { tools ->
            when (kind) {
                CompareKind.FILES -> tools.compareFiles(
                    workspaceId,
                    a,
                    b,
                ).let { d -> _state.update { it.copy(fileDiff = d) } }

                CompareKind.ARCHIVES -> tools.compareArchives(
                    workspaceId,
                    a,
                    b,
                ).let { d -> _state.update { it.copy(archiveDiff = d) } }

                CompareKind.FOLDERS -> tools.compareFolders(
                    workspaceId,
                    a,
                    b,
                ).let { d -> _state.update { it.copy(folderDiff = d) } }

                CompareKind.CODE -> {
                    // Two packages are decoded first; two folders are already decoded.
                    val packages = listOf(a, b).all { it.endsWith(".jar") || it.endsWith(".apk") }
                    val d = if (packages) {
                        tools.compareCodeFiles(
                            workspaceId,
                            a,
                            b,
                        )
                    } else {
                        tools.compareCode(workspaceId, a, b)
                    }
                    _state.update { it.copy(codeDiff = d) }
                }
            }
        }
    }

    public fun loadComparable() {
        compareTask("Looking for partitions to compare") { tools ->
            val partitions = tools.comparablePartitions(workspaceId)
            _state.update { it.copy(comparable = partitions) }
        }
    }

    /** What the build changed in [partition] compared with the stock firmware. */
    public fun compareWithStock(partition: String) {
        compareTask("Comparing $partition with stock") { tools ->
            val diff = tools.compareWithStock(workspaceId, partition)
            _state.update { it.copy(stockDiff = diff) }
        }
    }

    /** The code of a package the build changed ([path] in [partition]) against the stock one, method by method. */
    public fun compareStockCode(partition: String, path: String) {
        compareTask("Comparing the code of ${path.substringAfterLast('/')}") { tools ->
            val diff = tools.compareCodeFiles(
                workspaceId,
                "${FirmwareExtractionStage.EXTRACTED}/$partition/$path",
                "work/$partition/$path",
            )
            _state.update { it.copy(stockCode = path to diff) }
        }
    }

    public fun readCsc() {
        task("Reading Samsung CSC") { tools ->
            val csc = tools.samsungCsc(workspaceId)
            _state.update { it.copy(csc = csc) }
        }
    }

    public fun cancel() {
        job?.cancel()
    }

    public fun dismiss() {
        _state.update { it.copy(message = null, refusal = null) }
    }

    public fun acknowledge(feature: String) {
        val next = afterAck
        afterAck = null
        _state.update { it.copy(pendingAck = null) }
        viewModelScope.launch {
            runner?.acknowledge(workspaceId, feature)
            next?.invoke()
        }
    }

    public fun dismissAck() {
        afterAck = null
        _state.update { it.copy(pendingAck = null) }
    }

    private fun withAck(feature: String, action: () -> Unit) {
        viewModelScope.launch {
            if (runner == null || runner.isAcknowledged(workspaceId, feature)) {
                action()
            } else {
                afterAck = action
                _state.update { it.copy(pendingAck = feature) }
            }
        }
    }

    private fun task(name: String, block: suspend (ContentWorkbench) -> Unit) = launchTask(content, name, block)

    private fun compareTask(name: String, block: suspend (CompareWorkbench) -> Unit) = launchTask(
        comparing,
        name,
        block,
    )

    private fun <T : Any> launchTask(workbench: T?, name: String, block: suspend (T) -> Unit) {
        val tools = workbench ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = name, message = null, refusal = null) }
        job = viewModelScope.launch {
            try {
                block(tools)
            } catch (e: CancellationException) {
                _state.update { it.copy(message = "$name cancelled.") }
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(refusal = e.refusal) }
            } catch (e: IllegalArgumentException) {
                _state.update {
                    it.copy(
                        refusal = Refusal(e.message ?: "Invalid input.", name, "Correct it and try again."),
                    )
                }
            } catch (e: Exception) {
                // A transport or state failure is shown too, never lost in the background job.
                _state.update {
                    it.copy(
                        refusal = Refusal(
                            e.message ?: e::class.simpleName.orEmpty(),
                            name,
                            "Check the details and try again.",
                        ),
                    )
                }
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    public companion object {
        /** The workspace platform key the Build stage signs the package with (`keys/platform.{x509.pem,pk8}`). */
        public const val PLATFORM_KEY_REF: String = "keys/platform"

        public fun errorsOf(kind: ConfigKind, text: String): List<String> = when (kind) {
            ConfigKind.FSTAB -> ConfigValidators.validateFstab(text)
            ConfigKind.BUILD_PROP -> ConfigValidators.validateBuildProp(text)
            ConfigKind.FS_CONFIG -> ConfigValidators.validateFsConfig(text)
            ConfigKind.FILE_CONTEXTS -> ConfigValidators.validateFileContexts(text)
        }
    }
}
