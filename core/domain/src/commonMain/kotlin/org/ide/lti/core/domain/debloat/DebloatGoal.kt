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

/** A proposal for a size goal: the apps to pick, what they free, how far short it stays, the ones kept by request. */
public data class GoalPlan(
    val picks: List<DebloatSuggestion>,
    val freedBytes: Long,
    val shortBytes: Long,
    val kept: List<DebloatSuggestion>,
)

/**
 * "Free about N bytes, keep these": only [DebloatSuggestions] (apps nothing in the ROM relies on), minus any whose
 * package or name holds a keep word, picked largest first until the goal is met. Nothing is picked here: the plan
 * is shown, and each pick then goes through Debloat's own checks.
 */
public object DebloatGoal {
    public fun plan(suggestions: List<DebloatSuggestion>, targetBytes: Long, keep: List<String>): GoalPlan {
        val words = keep.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val (kept, open) = suggestions.partition { s ->
            words.any { w -> w in s.packageId.lowercase() || w in s.label.orEmpty().lowercase() }
        }
        val picks = mutableListOf<DebloatSuggestion>()
        var freed = 0L
        for (s in open.sortedByDescending { it.candidate.sizeBytes }) {
            if (freed >= targetBytes) break
            picks += s
            freed += s.candidate.sizeBytes
        }
        return GoalPlan(picks, freed, (targetBytes - freed).coerceAtLeast(0L), kept)
    }
}
