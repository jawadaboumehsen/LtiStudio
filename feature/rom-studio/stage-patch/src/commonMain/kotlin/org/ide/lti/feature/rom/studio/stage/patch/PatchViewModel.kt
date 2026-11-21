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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.LockResult
import org.ide.lti.core.domain.plugin.ModLab
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginDependencyResolver
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.incompatibility
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.plugin.newestPerPlugin
import org.ide.lti.core.domain.plugin.toInstalledPackage
import org.ide.lti.core.domain.workbench.ContentWorkbench
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.PluginLockfileEntry

/** The installed plugins and the plans of those the workspace enables; empty until loaded. */
public data class PatchFacts(
    val loaded: Boolean = false,
    val installed: List<InstalledRecord> = emptyList(),
    /** Plan templates by `publisher:id`, for the enabled plugins. */
    val plans: Map<String, PluginPlanTemplate> = emptyMap(),
    /** Why the last enable/disable was refused (a missing dependency, a conflict), or null. */
    val lockError: String? = null,
    /** Plugins made for another device or firmware, by `publisher:id`: "is made for …, not …". */
    val incompatible: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val loadError: String? = null,
    val installedVersions: List<InstalledRecord> = installed,
) {
    val canChangePlugins: Boolean get() = loaded && !loading && loadError == null
}

public class PatchViewModel(
    public val workspaceId: String,
    initialSettings: CustomizationSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val plugins: PluginInstallPort? = null,
    private val catalog: PluginCatalogPort? = null,
    private val content: ContentWorkbench? = null,
    private val preflight: Preflight? = null,
) : ViewModel() {

    private val _lab = MutableStateFlow(LabState())
    private var labJob: Job? = null
    private var refreshJob: Job? = null

    /** The mod lab's last answer: each enabled mod's operations checked against the assembled tree. */
    public val lab: StateFlow<LabState> = _lab.asStateFlow()

    /**
     * Checks every operation of the enabled mods together, as the build would apply them; nothing is written. With
     * [decode], APK/JAR edits are applied to their decoded packages too.
     */
    public fun checkTogether(decode: Boolean = false) {
        val checks = preflight ?: return
        val checkedSettings = settings.value
        labJob?.cancel()
        labJob = viewModelScope.launch {
            _lab.value = LabState(checking = true)
            val result = try {
                val saved = store?.current(workspaceId)
                if (saved == null || saved.customization == checkedSettings) {
                    checkSavedWorkspace(checks, saved, decode)
                } else {
                    LabState(error = "The patch settings haven’t been saved yet. Try again after they save.")
                }
            } catch (e: RefusalException) {
                LabState(error = "${e.refusal.cause} ${e.refusal.nextStep}")
            } catch (e: IllegalStateException) {
                LabState(error = e.message ?: "The check failed")
            }
            ensureActive()
            if (settings.value == checkedSettings) _lab.value = result
        }
    }

    private suspend fun checkSavedWorkspace(
        checks: Preflight,
        saved: ConfigurationSnapshot?,
        decode: Boolean,
    ): LabState {
        val checkedDevice = store?.device(workspaceId)
        val result = checks.lab(workspaceId, with = saved, decode = decode)
        val current = store?.current(workspaceId)
        return if (current?.acquisition?.firmware == saved?.acquisition?.firmware &&
            store?.device(workspaceId) == checkedDevice
        ) {
            LabState(result = result)
        } else {
            LabState()
        }
    }

    private val _scriptCheck = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The last check of each script by name: `ok`, or the shell's syntax error. */
    public val scriptCheck: StateFlow<Map<String, String>> = _scriptCheck.asStateFlow()

    /** Runs `sh -n` on [name]'s content as the build does before running any script. */
    public fun checkScript(name: String) {
        val workbench = content ?: return
        val script = settings.value.scripts.firstOrNull { it.name == name } ?: return
        viewModelScope.launch {
            val result = try {
                workbench.checkScript(workspaceId, script.content) ?: "ok"
            } catch (e: RefusalException) {
                "${e.refusal.cause} ${e.refusal.nextStep}"
            }
            // A check of content edited since says nothing about what is on screen now.
            val unchanged = settings.value.scripts.firstOrNull { it.name == name }?.content == script.content
            if (unchanged) _scriptCheck.update { it + (name to result) }
        }
    }

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: CustomizationSettings(),
        read = { it.customization },
        write = { snapshot, value -> snapshot.copy(customization = value) },
    )

    public val settings: StateFlow<CustomizationSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    private val _facts = MutableStateFlow(PatchFacts())
    public val facts: StateFlow<PatchFacts> = _facts.asStateFlow()

    init {
        // The saved settings load after this view model starts.
        viewModelScope.launch {
            settings.collect {
                resetLab()
            }
        }
        viewModelScope.launch {
            // A plugin's plan can change while its ID stays enabled.
            settings.map { it.enabledPackages to it.lockfile }.distinctUntilChanged().collect {
                _facts.update { facts -> facts.copy(plans = plansOf(settings.value)) }
            }
        }
        // Compatibility is with the device and firmware as they are saved, not as they were at the last Refresh.
        viewModelScope.launch {
            val saves = store?.changes?.filter { it == workspaceId }?.map { } ?: emptyFlow()
            merge(device.map { }, saves).collectLatest {
                resetLab()
                val installed = _facts.value.installed
                val incompatible = incompatibleOf(installed)
                _facts.update { if (it.installed == installed) it.copy(incompatible = incompatible) else it }
            }
        }
    }

    public fun updateSettings(updated: CustomizationSettings) {
        val before = settings.value.scripts.associate { it.name to it.content }
        val after = updated.scripts.associate { it.name to it.content }
        _scriptCheck.update { checks -> checks.filterKeys { before[it] == after[it] } }
        sync.update(updated)
    }

    public fun refresh() {
        val port = plugins ?: return
        refreshJob?.cancel()
        _facts.update { it.copy(loading = true, loadError = null) }
        refreshJob = viewModelScope.launch {
            runCatchingCancellable {
                val usable = port.installed().filter { !it.revoked && !it.archived }
                val installed = newestPerPlugin(usable, { it.key }) { it.identity.version }
                val incompatible = incompatibleOf(installed)
                ensureActive()
                val plans = plansOf(settings.value)
                if (_facts.value.plans != plans) resetLab()
                _facts.update {
                    it.copy(
                        loaded = true,
                        loading = false,
                        installed = installed,
                        installedVersions = usable,
                        plans = plans,
                        incompatible = incompatible,
                    )
                }
            }.onFailure { failure ->
                _facts.update {
                    it.copy(loading = false, loadError = failure.message ?: "Could not read installed plugins")
                }
            }
        }
    }

    /**
     * Enables or disables `publisher:id`. The enabled list is re-locked against the installed plugins, which
     * adds dependencies (before what needs them) and refuses missing or conflicting ones.
     */
    public fun toggle(key: String) {
        if (!_facts.value.canChangePlugins) return
        val current = settings.value
        val enabled = current.enabledPackages.map { id -> current.lockedEntry(id)?.packageKey ?: id }
        val packageKey = current.lockedEntry(key)?.packageKey ?: key
        _facts.value.incompatible[packageKey]?.takeIf { packageKey !in enabled }?.let { why ->
            _facts.value = _facts.value.copy(lockError = "${nameOf(packageKey, _facts.value.installed)} $why")
            return
        }
        val wanted = if (packageKey in enabled) enabled - packageKey else enabled + packageKey
        val installed = candidatesFor(current)
        val packages = installed.map { it.toInstalledPackage() }
        // The list's own order stays (independent mods that touch the same files apply as chosen), unless the
        // dependencies no longer allow it.
        val locked = PluginDependencyResolver.lock(wanted, packages, requestedOrder = wanted)
            .takeIf { it is LockResult.Locked } ?: PluginDependencyResolver.lock(wanted, packages, emptyList())
        when (val result = locked) {
            is LockResult.Rejected -> _facts.value = _facts.value.copy(
                lockError = result.report.errors.joinToString("; ") { it.message },
            )

            is LockResult.Locked -> {
                val ordered = result.lockfile.entries.map { "${it.publisher}:${it.id}" }
                val updated = current.copy(enabledPackages = ordered, lockfile = result.lockfile)
                updateSettings(updated)
                _facts.value = _facts.value.copy(
                    // Disabling a dependency of an enabled plugin does nothing: the lock adds it back.
                    lockError = if (packageKey !in wanted && packageKey in ordered) {
                        neededBy(packageKey, installed, ordered)
                    } else {
                        null
                    },
                    plans = plansOf(updated),
                )
            }
        }
    }

    /** The workspace's device and firmware against each plugin's plan; nothing while either is unknown. */
    private suspend fun incompatibleOf(installed: List<InstalledRecord>): Map<String, String> {
        val device = runCatchingCancellable { store?.device(workspaceId)?.codename }.getOrNull()
        val firmware = runCatchingCancellable {
            store?.current(
                workspaceId,
            )?.acquisition?.firmware?.version
        }.getOrNull()
        val current = settings.value
        val pins = current.enabledPackages.mapNotNull(current::lockedEntry).associateBy { it.packageKey }
        return installed.takeIf { device != null }.orEmpty().mapNotNull { r ->
            val version = pins[r.key]?.version ?: r.identity.version
            val plan = catalog?.templateFor(r.identity.publisher, r.identity.id, version)
            plan?.let { incompatibility(it, device.orEmpty(), firmware.orEmpty()) }?.let { r.key to it }
        }.toMap()
    }

    private fun nameOf(key: String, installed: List<InstalledRecord>): String =
        installed.firstOrNull { it.key == key }?.displayName?.takeIf { it.isNotBlank() } ?: key

    /** "Keybox imitation is needed by Play Integrity fixes; disable that first." */
    private fun neededBy(key: String, installed: List<InstalledRecord>, enabled: List<String>): String {
        fun name(k: String) = nameOf(k, installed)
        val users = installed.filter { record ->
            record.key in enabled && record.dependencies.any { "${it.publisher}:${it.id}" == key }
        }
        val who = users.joinToString { name(it.key) }.ifBlank { "another enabled plugin" }
        return "${name(key)} is needed by $who; disable that first."
    }

    private fun candidatesFor(current: CustomizationSettings): List<InstalledRecord> {
        val pins = current.enabledPackages.mapNotNull(current::lockedEntry).associateBy { it.packageKey }
        val unpinned = _facts.value.installed.filter { it.key !in pins }
        val pinned = _facts.value.installedVersions.filter { record ->
            val pin = pins[record.key] ?: return@filter false
            record.matches(pin)
        }
        return unpinned + pinned
    }

    private fun plansOf(settings: CustomizationSettings): Map<String, PluginPlanTemplate> {
        val source = catalog ?: return emptyMap()
        return settings.enabledPackages.mapNotNull { key ->
            val entry = settings.lockedEntry(key) ?: return@mapNotNull null
            val digest = source.contentDigestFor(entry.publisher, entry.id, entry.version).orEmpty()
            if (entry.contentDigest.isNotEmpty() && digest.isNotEmpty() && digest != entry.contentDigest) {
                return@mapNotNull null
            }
            source.templateFor(entry.publisher, entry.id, entry.version)?.let { key to it }
        }.toMap()
    }

    private fun resetLab() {
        labJob?.cancel()
        _lab.value = LabState()
    }
}

internal fun CustomizationSettings.lockedEntry(key: String): PluginLockfileEntry? =
    lockfile.entries.firstOrNull { it.packageKey == key || it.id == key }

internal val PluginLockfileEntry.packageKey: String get() = "$publisher:$id"

internal fun InstalledRecord.matches(pin: PluginLockfileEntry): Boolean = key == pin.packageKey &&
    identity.version == pin.version && (pin.contentDigest.isEmpty() || identity.contentDigest == pin.contentDigest)

/** The mod lab on screen: checking, its [result], or why it could not run. */
public data class LabState(val checking: Boolean = false, val result: ModLab? = null, val error: String? = null)
