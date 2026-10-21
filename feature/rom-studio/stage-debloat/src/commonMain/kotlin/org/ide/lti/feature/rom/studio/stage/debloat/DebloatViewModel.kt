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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.pipeline.stages.DebloatStage
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceFacts
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.DebloatSettings

/** What the Debloat editor shows from the workspace: the apps in the assembled tree and the last result. */
public data class DebloatFacts(
    val inventory: List<DebloatCandidate> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    /** What the last run removed; null when Debloat has not run with removals. */
    val lastResult: DebloatDecision? = null,
)

public class DebloatViewModel(
    public val workspaceId: String,
    initialSettings: DebloatSettings? = null,
    store: WorkspaceSettingsStore? = null,
    private val workspaceFacts: WorkspaceFacts? = null,
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

    /** Picks or un-picks [candidate] for removal; a protected app is never picked. */
    public fun toggleRemoval(candidate: DebloatCandidate) {
        if (DebloatInventory.isProtected(candidate)) return
        val selector = selectorOf(candidate)
        val current = settings.value
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
            _facts.value = DebloatFacts(inventory, loaded = true, lastResult = result)
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

        public fun selectorOf(candidate: DebloatCandidate): String = "${candidate.partition}:${candidate.relativePath}"

        private const val INVENTORY_DEPTH = 7
    }
}
