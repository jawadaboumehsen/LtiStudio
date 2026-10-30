/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.debloat.AppFacts
import org.ide.lti.core.domain.debloat.AppManifests
import org.ide.lti.core.domain.debloat.AppRisk
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.debloat.DebloatRisk
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RemovalItem
import org.ide.lti.core.domain.pipeline.toolkit.RemovalList
import org.ide.lti.core.domain.pipeline.toolkit.RemovalListMatcher
import org.ide.lti.core.domain.pipeline.toolkit.RemovalMatchResult
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.workbench.RestoreWorkbench
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.DebloatSettings
import java.io.File

/** What the Debloat editor shows from the workspace: the apps in the assembled tree and the last result. */
public data class DebloatFacts(
    val inventory: List<DebloatCandidate> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** What the last run removed; null when Debloat has not run with removals. */
    val lastResult: DebloatDecision? = null,
    /** The backups the last run took of the apps it removed (FR-027, FR-038). */
    val restorePoints: List<RestorePoint> = emptyList(),
    /** What the last imported removal list matched, and which of its entries are not apps of this firmware. */
    val listResult: RemovalMatchResult? = null,
    /** What each app's manifest says (by `partition:path`), read after the inventory; empty until then. */
    val apps: Map<String, AppFacts> = emptyMap(),
    /** Where the app icons are, as Windows sees the folder (`AppFacts.icon` names a file in it). */
    val iconFolder: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
) {
    /** What removing each app would do while the apps in [picked] go too. */
    public fun risks(picked: Set<String>): Map<String, AppRisk> = DebloatRisk.assess(inventory, apps, picked)
}

public class DebloatViewModel(
    public val workspaceId: String,
    initialSettings: DebloatSettings? = null,
    store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
    private val restore: RestoreWorkbench? = null,
    private val runner: WorkbenchRunner? = null,
    private val pickFile: suspend () -> String? = { DialogUtils.selectFilePath("Pick a removal list (.json)") },
    private val pickFolder: suspend () -> String? = { DialogUtils.selectDirectory("Folder for the removal list") },
) : ViewModel() {

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: DebloatSettings(),
        read = { it.debloat },
        write = { snapshot, value -> snapshot.copy(debloat = value) },
    )

    public val settings: StateFlow<DebloatSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    private val _facts = MutableStateFlow(DebloatFacts())
    public val facts: StateFlow<DebloatFacts> = _facts.asStateFlow()

    public fun updateSettings(updated: DebloatSettings) {
        sync.update(updated)
    }

    /** Picks or un-picks [candidate] for removal; a protected app, or one the device needs, is never picked. */
    public fun toggleRemoval(candidate: DebloatCandidate) {
        if (DebloatInventory.isProtected(candidate)) return
        val selector = selectorOf(candidate)
        val current = settings.value
        val needed = facts.value.risks(current.removeSelectors.toSet())[selector]?.blockers.orEmpty().isNotEmpty()
        if (selector !in current.removeSelectors && needed) return
        val picks = if (selector in current.removeSelectors) {
            current.removeSelectors - selector
        } else {
            current.removeSelectors + selector
        }
        updateSettings(current.copy(enabled = current.enabled || picks.isNotEmpty(), removeSelectors = picks))
    }

    public fun unpick(selector: String) {
        updateSettings(settings.value.copy(removeSelectors = settings.value.removeSelectors - selector))
    }

    /** Reads the apps of the assembled work tree and the last Debloat result. */
    public fun refresh() {
        val facts = workspaceFacts ?: return
        _facts.value = _facts.value.copy(loading = true)
        viewModelScope.launch {
            val listing = facts.list(workspaceId, "work", maxDepth = INVENTORY_DEPTH, name = "*.apk")
                .joinToString("\n") { "${it.sizeBytes}\t${it.path}" }
            val result = facts.read(workspaceId, DebloatStage.RESULT)?.let(DebloatStage::parseResult)
            val inventory = withRemoved(DebloatInventory.parse(listing), result)
            val points = runCatching { restore?.list(workspaceId)?.items.orEmpty() }.getOrDefault(emptyList())
                .filter { it.tool == DebloatStage.TOOL }
            _facts.value = _facts.value.copy(
                inventory = inventory,
                loaded = true,
                loading = false,
                lastResult = result,
                restorePoints = points,
            )
            readApps(inventory)
        }
    }

    /** Reads the manifests of [inventory]'s apps (aapt2 in WSL, a few seconds for a whole ROM). */
    private suspend fun readApps(inventory: List<DebloatCandidate>) {
        val runner = runner ?: return
        try {
            val apks = runner.read(workspaceId) { AppManifests.parse(sh(AppManifests.SCRIPT, abs("work"))) }
            _facts.value = _facts.value.copy(apps = DebloatRisk.byApp(inventory, apks))
            // Names and icons come second: the risks matter more, and badging takes as long again.
            val (labels, icons) = runner.read(workspaceId) {
                AppManifests.parse(sh(AppManifests.LABELS_SCRIPT, abs("work"), abs(ICON_DIR))) to hostPath(ICON_DIR)
            }
            val named = apks.mapValues { (apk, facts) -> labels[apk]?.let(facts::plus) ?: facts }
            _facts.value = _facts.value.copy(apps = DebloatRisk.byApp(inventory, named), iconFolder = icons)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _facts.value = _facts.value.copy(
                refusal = Refusal(
                    "The app manifests could not be read (${e.message ?: e::class.simpleName}).",
                    "work/",
                    "Check that apktool is installed in Setup > Tools; picks are still checked when the build runs.",
                ),
            )
        }
    }

    /**
     * Puts [point]'s backup back into the work tree (with its sidecar lines) and un-picks the app, so later
     * builds keep it too.
     */
    public fun restore(point: RestorePoint) {
        val workbench = restore ?: return
        guarded {
            workbench.restore(workspaceId, point.id)
            unpick(point.id.removePrefix("${DebloatStage.TOOL}:"))
            _facts.value = _facts.value.copy(message = "Restored ${point.itemPath}.")
            refresh()
        }
    }

    public fun restoreAll() {
        val workbench = restore ?: return
        guarded {
            val restored = workbench.restoreAll(workspaceId, DebloatStage.TOOL)
            val selectors = restored.map { it.id.removePrefix("${DebloatStage.TOOL}:") }.toSet()
            updateSettings(settings.value.copy(removeSelectors = settings.value.removeSelectors - selectors))
            _facts.value = _facts.value.copy(message = "Restored ${restored.size} app(s).")
            refresh()
        }
    }

    /** Picks the apps of a removal list file; protected apps and entries this firmware lacks are listed, not picked. */
    public fun importList() {
        viewModelScope.launch {
            val path = pickFile() ?: return@launch
            guarded {
                val list = RemovalList.fromJson(File(path).readText())
                val items = _facts.value.inventory.map { c ->
                    RemovalItem(
                        id = selectorOf(c),
                        name = c.relativePath.substringAfterLast('/'),
                        path = "${c.partition}/${c.relativePath}",
                        isProtected = DebloatInventory.isProtected(c),
                    )
                }
                val match = RemovalListMatcher.match(list, items)
                val picks = (settings.value.removeSelectors + match.effectiveRemovals.map { it.id }).distinct()
                updateSettings(settings.value.copy(enabled = picks.isNotEmpty(), removeSelectors = picks))
                _facts.value = _facts.value.copy(listResult = match, message = "Imported ${list.name}.")
            }
        }
    }

    /** Saves the current picks as a removal list (`partition:path` entries) in a folder the user picks. */
    public fun exportList() {
        viewModelScope.launch {
            val folder = pickFolder() ?: return@launch
            guarded {
                val file = File(folder, "debloat-$workspaceId.json")
                file.writeText(
                    RemovalList.toJson(RemovalList("Debloat of $workspaceId", settings.value.removeSelectors)),
                )
                _facts.value = _facts.value.copy(message = "Saved ${file.path}.")
            }
        }
    }

    public fun dismissMessage() {
        _facts.value = _facts.value.copy(message = null, refusal = null)
    }

    private fun guarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _facts.value = _facts.value.copy(refusal = e.refusal)
            } catch (e: Exception) {
                _facts.value = _facts.value.copy(
                    refusal = Refusal(
                        e.message ?: e::class.simpleName.orEmpty(),
                        "Debloat",
                        "Check the file and try again.",
                    ),
                )
            }
        }
    }

    public companion object {
        /**
         * The assembled tree as Debloat sees it: work/ after the last build lacks the apps that build removed, but
         * the next build re-assembles them first, so they are still in the inventory (and still picked).
         */
        public fun withRemoved(tree: List<DebloatCandidate>, last: DebloatDecision?): List<DebloatCandidate> {
            val present = tree.map(::selectorOf).toSet()
            val removed = last?.removals.orEmpty()
                .map { DebloatCandidate(it.partition, it.relativePath, it.sizeBytes, it.packageId) }
                .filter { selectorOf(it) !in present }
            return (tree + removed).sortedWith(compareBy({ it.partition }, { it.relativePath }))
        }

        /** App icons copied out of the APKs, in the workspace (re-created on each refresh). */
        private const val ICON_DIR = "workbench/debloat/icons"

        public fun selectorOf(candidate: DebloatCandidate): String = "${candidate.partition}:${candidate.relativePath}"

        private const val INVENTORY_DEPTH = 7
    }
}
