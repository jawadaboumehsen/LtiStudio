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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.domain.debloat.AppFacts
import org.ide.lti.core.domain.debloat.AppManifests
import org.ide.lti.core.domain.debloat.AppMap
import org.ide.lti.core.domain.debloat.AppRisk
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.debloat.DebloatMap
import org.ide.lti.core.domain.debloat.DebloatPresetStore
import org.ide.lti.core.domain.debloat.DebloatRisk
import org.ide.lti.core.domain.debloat.PackageMentions
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.pipeline.toolkit.CommunityOpinion
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalCatalog
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RemovalItem
import org.ide.lti.core.domain.pipeline.toolkit.RemovalList
import org.ide.lti.core.domain.pipeline.toolkit.RemovalListMatcher
import org.ide.lti.core.domain.pipeline.toolkit.RemovalMatchResult
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
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
    /** The permission/sysconfig files of work/ and the packages each names (`PackageMentions`). */
    val mentions: Map<String, PackageMentions.Mentions> = emptyMap(),
    /** The app's saved presets (`DebloatPresetStore`), by name: own lists, community lists and per-phone picks. */
    val presets: List<RemovalList> = emptyList(),
    /** This workspace's phone (its codename), whose picks are kept for its next workspaces; null when unknown. */
    val device: String? = null,
    /** Picks kept for this phone from another workspace, offered while this one has none. */
    val devicePicks: RemovalList? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
) {
    /** What removing each app would do while the apps in [picked] go too. */
    public fun risks(picked: Set<String>): Map<String, AppRisk> = DebloatRisk.assess(inventory, apps, picked)

    /** Every community list's opinion of [packageId], by list name; shown beside the verdict, never part of it. */
    public fun opinions(packageId: String?): List<Pair<String, CommunityOpinion>> =
        packageId?.let { pkg -> presets.mapNotNull { list -> list.opinions[pkg]?.let { list.name to it } } }.orEmpty()

    /** The map of the app [selector] names: what relies on it and what it relies on, as the manifests declare. */
    public fun map(selector: String): AppMap? = DebloatMap.of(selector, apps)
}

public class DebloatViewModel(
    public val workspaceId: String,
    initialSettings: DebloatSettings? = null,
    store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
    private val restore: RestoreWorkbench? = null,
    private val runner: WorkbenchRunner? = null,
    private val presets: DebloatPresetStore? = null,
    /** The workspace's device, whose codename keys the picks kept for that phone. */
    private val devices: WorkspaceDeviceRepository? = null,
    private val pickFile: suspend () -> String? = {
        DialogUtils.selectFilePath("Pick a removal list (.json, uad_lists.json, or text with one package per line)")
    },
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
        val before = settings.value.removeSelectors
        sync.update(updated)
        val device = _facts.value.device
        val store = presets
        if (device == null || store == null || updated.removeSelectors == before) return
        // The picks of this phone, kept for its next workspaces (matched there by package name).
        guarded {
            val kept = portableList("Picks for $device").copy(device = device)
            val saved = store.update { all -> all.filter { it.device != device } + kept }
            _facts.value = _facts.value.copy(presets = saved)
        }
    }

    /** Picks the apps another workspace of this phone picked ([DebloatFacts.devicePicks]). */
    public fun applyDevicePicks() {
        _facts.value.devicePicks?.let {
            pick(it, "Applied the picks kept for ${it.device}:")
            _facts.value = _facts.value.copy(devicePicks = null)
        }
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

    private var refreshJob: Job? = null

    /** Reads the apps of the assembled work tree and the last Debloat result. */
    public fun refresh() {
        val facts = workspaceFacts ?: return
        _facts.value = _facts.value.copy(loading = true)
        // A newer refresh replaces this one whole: its risks must never land on the newer inventory.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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
                // Read again for this inventory below; until then (or if that fails) none are shown.
                apps = emptyMap(),
                mentions = emptyMap(),
                iconFolder = null,
            )
            readApps(inventory)
        }
        guarded {
            val saved = presets?.load().orEmpty()
            val device = devices?.get(workspaceId)?.codename
            val offered = saved.firstOrNull { it.device != null && it.device == device && it.entries.isNotEmpty() }
                ?.takeIf { settings.value.removeSelectors.isEmpty() }
            _facts.value = _facts.value.copy(presets = saved, device = device, devicePicks = offered)
        }
    }

    /** Reads the manifests of [inventory]'s apps (aapt2 in WSL, a few seconds for a whole ROM). */
    private suspend fun readApps(inventory: List<DebloatCandidate>) {
        val runner = runner ?: return
        try {
            // Apps the last build removed are no longer in work/; their backups (same layout) still have manifests.
            val manifests = runner.read(workspaceId) {
                val backups = DebloatStage.BACKUP_DIR.takeIf { exists(it) }
                    ?.let { AppManifests.parse(sh(AppManifests.SCRIPT, abs(it))) }.orEmpty()
                backups + AppManifests.parse(sh(AppManifests.SCRIPT, abs("work")))
            }
            // Signers take under a second (only each APK's end is read); without them the apps still show.
            val signers = try {
                runner.read(workspaceId) { AppManifests.parse(sh(AppManifests.SIGNERS_SCRIPT, abs("work"))) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("SwallowedException") e: Exception) {
                emptyMap()
            }
            val apks = manifests.mapValues { (apk, facts) -> signers[apk]?.let(facts::plus) ?: facts }
            val mentions = runner.read(workspaceId) { PackageMentions.parse(sh(PackageMentions.SCRIPT, abs("work"))) }
            _facts.value = _facts.value.copy(apps = DebloatRisk.byApp(inventory, apks), mentions = mentions)
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
                refusal = if (RefusalCatalog.BUILD_RUNNING in e.message.orEmpty()) {
                    Refusal(
                        "A build is running in this workspace, so app names, signers and risks cannot be read now.",
                        "work/",
                        "Press Refresh when the build has finished; your picks are kept.",
                    )
                } else {
                    Refusal(
                        "The app manifests could not be read (${e.message ?: e::class.simpleName}).",
                        "work/",
                        "Check that aapt2 is installed in Setup > Tools; picks are still checked when the build runs.",
                    )
                },
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
                val file = File(path)
                val list = RemovalList.parse(file.nameWithoutExtension, file.readText())
                if (list.opinions.isEmpty()) {
                    pick(list, "Imported")
                } else {
                    keepCommunityList(list)
                }
            }
        }
    }

    /**
     * Adds the apps of [list] to the picks, matched by `partition:path` or by package name (once the manifests are
     * read), so a list from another firmware still finds apps whose folder moved.
     */
    private fun pick(list: RemovalList, verb: String) {
        val items = _facts.value.inventory.map { c ->
            RemovalItem(
                id = selectorOf(c),
                name = c.relativePath.substringAfterLast('/'),
                path = "${c.partition}/${c.relativePath}",
                packageId = packageOf(selectorOf(c)),
                isProtected = DebloatInventory.isProtected(c),
            )
        }
        val listed = RemovalListMatcher.match(list, items)
        // As a pick by hand: an app the device needs once the others go (DebloatRisk blockers) is not picked.
        val all = (settings.value.removeSelectors + listed.effectiveRemovals.map { it.id }).toSet()
        val risks = _facts.value.risks(all)
        val (needed, removable) = listed.effectiveRemovals.partition { risks[it.id]?.blockers.orEmpty().isNotEmpty() }
        val match = listed.copy(blockedProtected = listed.blockedProtected + needed, effectiveRemovals = removable)
        val picks = (settings.value.removeSelectors + match.effectiveRemovals.map { it.id }).distinct()
        updateSettings(settings.value.copy(enabled = picks.isNotEmpty(), removeSelectors = picks))
        _facts.value = _facts.value.copy(listResult = match, message = "$verb ${list.name}.")
    }

    /**
     * [selector]'s package name: from its manifest, else from the last Debloat result (an app that build removed is
     * no longer in work/, so its manifest cannot be read); null when neither knows it.
     */
    private fun packageOf(selector: String): String? = _facts.value.apps[selector]?.packageId
        ?: _facts.value.inventory.firstOrNull { selectorOf(it) == selector }?.packageId

    /** The picks as list entries: each app's package name where it is known, else `partition:path`. */
    private fun portableList(name: String): RemovalList =
        RemovalList(name, settings.value.removeSelectors.map { packageOf(it) ?: it })

    /**
     * Keeps a community list (a package map with a rating per package) in the app: its opinion shows beside each
     * app's own verdict and picks nothing; applying it, like a preset, picks what it recommends.
     */
    private suspend fun keepCommunityList(list: RemovalList) {
        val store = presets ?: return
        val saved = store.update { all ->
            (all.filter { it.name != list.name } + list).sortedBy { it.name.lowercase() }
        }
        val here = _facts.value.apps.values.mapNotNull { it.packageId }.toSet()
        _facts.value = _facts.value.copy(
            presets = saved,
            message = "Kept the community list ${list.name}: ${list.opinions.size} packages rated, " +
                "${list.opinions.keys.count { it in here }} of them in this ROM. Its opinion shows beside each app; " +
                "nothing was picked.",
        )
    }

    /** Saves the current picks as a removal list in a folder the user picks. */
    public fun exportList() {
        viewModelScope.launch {
            val folder = pickFolder() ?: return@launch
            guarded {
                val file = File(folder, "debloat-$workspaceId.json")
                file.writeText(RemovalList.toJson(portableList("Debloat of $workspaceId")))
                _facts.value = _facts.value.copy(message = "Saved ${file.path}.")
            }
        }
    }

    /** Keeps the current picks in the app as preset [name] (replacing one of that name). */
    public fun savePreset(name: String) {
        val store = presets ?: return
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return
        guarded {
            val preset = portableList(trimmed)
            val saved = store.update { all ->
                (all.filter { it.name != trimmed } + preset).sortedBy { it.name.lowercase() }
            }
            _facts.value = _facts.value.copy(presets = saved, message = "Saved the preset $trimmed.")
        }
    }

    /** Adds preset [name]'s apps to the picks, as an imported list would. */
    public fun applyPreset(name: String) {
        _facts.value.presets.firstOrNull { it.name == name }?.let { pick(it, "Applied the preset") }
    }

    public fun deletePreset(name: String) {
        val store = presets ?: return
        guarded {
            val saved = store.update { all -> all.filter { it.name != name } }
            _facts.value = _facts.value.copy(presets = saved, message = "Deleted the preset $name.")
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
