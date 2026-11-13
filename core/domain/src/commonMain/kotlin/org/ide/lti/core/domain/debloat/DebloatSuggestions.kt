/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

/** An app the ROM itself says can go, the space it frees and why. */
public data class DebloatSuggestion(
    val candidate: DebloatCandidate,
    val packageId: String,
    val label: String?,
    val reasons: List<String>,
)

/**
 * Debloat suggestions computed from the ROM, never from a list: apps no other app relies on (no library, permission
 * or overlay of theirs is used: `DebloatMap`), with nothing `DebloatRisk` warns about (not persistent, no system user
 * id, not the last home screen or keyboard), that do not start at boot and are not overlays themselves; the largest
 * first. What the apps do at run time (intents, bound services) is in no file, so "nothing relies on it" means
 * nothing the ROM declares.
 */
public object DebloatSuggestions {
    public fun of(
        inventory: List<DebloatCandidate>,
        apps: Map<String, AppFacts>,
        picked: Set<String>,
        limit: Int = LIMIT,
    ): List<DebloatSuggestion> {
        val risks = DebloatRisk.assess(inventory, apps, picked)
        return inventory.mapNotNull { c ->
            val selector = "${c.partition}:${c.relativePath}"
            val app = apps[selector]
            val pkg = app?.packageId
            val risk = risks[selector]
            val quiet = risk != null && risk.blockers.isEmpty() && risk.warnings.isEmpty()
            when {
                selector in picked || DebloatInventory.isProtected(c) || app == null || pkg == null -> null

                app.unreadable || app.bootReceiver || app.overlayTargets.isNotEmpty() || !quiet -> null

                DebloatMap.of(selector, apps)?.reliedOnBy?.isNotEmpty() != false -> null
                // Another app looks for it by name when it runs (`<queries>`): likely a part it calls.
                apps.values.any { it.packageId != pkg && pkg in it.queries } -> null

                else -> DebloatSuggestion(
                    c,
                    pkg,
                    app.label,
                    listOf(
                        "nothing in the ROM relies on it or looks for it",
                        "not part of the system (not persistent, no system user id)",
                        "does not start at boot",
                    ),
                )
            }
        }.sortedByDescending { it.candidate.sizeBytes }.take(limit)
    }

    private const val LIMIT = 25
}
