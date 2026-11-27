/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** A workspace's settings saved under a name, every stage's, to build again or compare. */
@Serializable
data class BuildProfile(val name: String, val savedAt: Instant, val snapshot: ConfigurationSnapshot)

/** A workspace's profiles, kept by the app (not in the workspace). */
interface BuildProfileStore {
    suspend fun load(workspaceId: String): List<BuildProfile>

    /** Reads, changes and saves in one step (two editors saving at once never lose each other's profile). */
    suspend fun update(workspaceId: String, change: (List<BuildProfile>) -> List<BuildProfile>): List<BuildProfile>
}

/**
 * Named settings of a workspace: saved from the current ones, compared with them field by field (as Compare runs
 * does), and put back as the current ones. A profile holds every stage's settings, so it builds what it built.
 */
class BuildProfiles(
    private val store: BuildProfileStore,
    private val settings: WorkspaceSettingsStore,
    private val clock: Clock = Clock.System,
) {
    suspend fun list(workspaceId: String): List<BuildProfile> = store.load(workspaceId).sortedBy { it.name }

    /** The current settings saved as [name] (replacing a profile of that name). */
    suspend fun saveCurrent(workspaceId: String, name: String): List<BuildProfile> {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "A profile needs a name" }
        val current = checkNotNull(settings.current(workspaceId)) { "The workspace has no settings yet" }
        val profile = BuildProfile(clean, clock.now(), current)
        return store.update(workspaceId) { all -> all.filterNot { it.name == clean } + profile }.sortedBy { it.name }
    }

    suspend fun delete(workspaceId: String, name: String): List<BuildProfile> =
        store.update(workspaceId) { all -> all.filterNot { it.name == name } }.sortedBy { it.name }

    /** What using [profile] would change in the current settings: each settings field, then each mod version. */
    suspend fun differences(workspaceId: String, profile: BuildProfile): List<String> {
        val current = settings.current(workspaceId) ?: return listOf("The workspace has no settings to compare")
        return RunComparison.settingsChanges(current, profile.snapshot) +
            RunComparison.modChanges(current, profile.snapshot)
    }

    /** [profile]'s settings made the current ones (every stage's). */
    suspend fun apply(workspaceId: String, profile: BuildProfile): Result<ConfigurationSnapshot> =
        settings.update(workspaceId) { current -> applied(profile.snapshot, current) }

    companion object {
        /** [current] with every stage's settings of [saved]; its own identity, revision and date stay. */
        fun applied(saved: ConfigurationSnapshot, current: ConfigurationSnapshot): ConfigurationSnapshot = current.copy(
            acquisition = saved.acquisition,
            extraction = saved.extraction,
            assembly = saved.assembly,
            debloat = saved.debloat,
            customization = saved.customization,
            build = saved.build,
            release = saved.release,
            publish = saved.publish,
        )
    }
}
