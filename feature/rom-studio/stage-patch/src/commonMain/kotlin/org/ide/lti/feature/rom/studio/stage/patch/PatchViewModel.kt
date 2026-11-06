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
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.LockResult
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
import org.ide.lti.core.model.workspace.CustomizationSettings

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
)

public class PatchViewModel(
    public val workspaceId: String,
    initialSettings: CustomizationSettings? = null,
    private val store: WorkspaceSettingsStore? = null,
    private val plugins: PluginInstallPort? = null,
    private val catalog: PluginCatalogPort? = null,
    private val content: ContentWorkbench? = null,
) : ViewModel() {

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
        // The saved settings load after this view model starts: the plans follow the enabled list as it arrives.
        viewModelScope.launch {
            settings.map { it.enabledPackages }.distinctUntilChanged().collect { enabled ->
                _facts.update { it.copy(plans = plansOf(it.installed, enabled)) }
            }
        }
        // Compatibility is with the device and firmware as they are saved, not as they were at the last Refresh.
        viewModelScope.launch {
            val saves = store?.changes?.filter { it == workspaceId }?.map { } ?: emptyFlow()
            merge(device.map { }, saves).collectLatest {
                val incompatible = incompatibleOf(_facts.value.installed)
                _facts.update { it.copy(incompatible = incompatible) }
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
        viewModelScope.launch {
            // One row per plugin: older installed versions are never locked, so they are not offered either.
            val usable = port.installed().filter { !it.revoked && !it.archived }
            val installed = newestPerPlugin(usable, { it.key }) { it.identity.version }
            // Read before the plans, which are made last from the list as it is now: this read can take a while.
            val incompatible = incompatibleOf(installed)
            _facts.update {
                it.copy(
                    loaded = true,
                    installed = installed,
                    plans = plansOf(installed, settings.value.enabledPackages),
                    incompatible = incompatible,
                )
            }
        }
    }

    /**
     * Enables or disables `publisher:id`. The enabled list is re-locked against the installed plugins, which
     * adds dependencies (before what needs them) and refuses missing or conflicting ones.
     */
    public fun toggle(key: String) {
        val current = settings.value
        val enabled = current.enabledPackages
        _facts.value.incompatible[key]?.takeIf { key !in enabled }?.let { why ->
            _facts.value = _facts.value.copy(lockError = "${nameOf(key, _facts.value.installed)} $why")
            return
        }
        val wanted = if (key in enabled) enabled - key else enabled + key
        val installed = _facts.value.installed
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
                updateSettings(current.copy(enabledPackages = ordered, lockfile = result.lockfile))
                _facts.value = _facts.value.copy(
                    // Disabling a dependency of an enabled plugin does nothing: the lock adds it back.
                    lockError = neededBy(key, installed, ordered).takeIf { key !in wanted && key in ordered },
                    plans = plansOf(installed, ordered),
                )
            }
        }
    }

    /** The workspace's device and firmware against each plugin's plan; nothing while either is unknown. */
    private suspend fun incompatibleOf(installed: List<InstalledRecord>): Map<String, String> {
        val device = runCatching { store?.device(workspaceId)?.codename }.getOrNull()
        val firmware = runCatching { store?.current(workspaceId)?.acquisition?.firmware?.version }.getOrNull()
        return installed.takeIf { device != null }.orEmpty().mapNotNull { r ->
            val plan = catalog?.templateFor(r.identity.publisher, r.identity.id, r.identity.version)
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

    private fun plansOf(installed: List<InstalledRecord>, enabled: List<String>): Map<String, PluginPlanTemplate> =
        installed.filter { it.key in enabled }.mapNotNull { r ->
            catalog?.templateFor(r.identity.publisher, r.identity.id, r.identity.version)?.let { r.key to it }
        }.toMap()
}
