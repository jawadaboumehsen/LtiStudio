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

import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.LabRow
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.plugin.ModLab
import org.ide.lti.core.domain.plugin.ModUpdateReview
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginWorkspaceUsage
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.plugin.toInstalledPackage

/** An update waiting for review: what changes per version the workspaces use, and the workspaces it would move. */
data class PendingUpdate(val reviews: List<ModUpdateReview>, val moves: List<PluginWorkspaceUsage.PendingMove>)

/**
 * Reviewing a mod update before the workspaces move onto it: each version they use compared with the newest
 * ([ModUpdateReview]), and, per workspace, the mod lab run with its settings on the new versions (not saved).
 */
class ModUpdates(
    private val catalog: PluginCatalogPort,
    private val usage: PluginWorkspaceUsage,
    private val preflight: Preflight? = null,
) {
    /** What installing [newest] of [key] would change for the workspaces; null when no workspace would move. */
    suspend fun pending(installed: List<InstalledRecord>, key: String, newest: String): PendingUpdate? {
        val moves = usage.pendingMoves(installed).filter { move ->
            move.using[key] != null && move.using[key] != newest &&
                move.snapshot.customization.lockfile.entries.any {
                    "${it.publisher}:${it.id}" == key && it.version == newest
                }
        }
        if (moves.isEmpty()) return null
        val records = installed.associateBy { it.key to it.identity.version }
        // Re-locking can also upgrade dependencies and other enabled mods. Review every change
        // that applying these moves will save, rather than only the package just imported.
        val changes = moves.flatMap { move ->
            move.snapshot.customization.lockfile.entries.mapNotNull { entry ->
                val changedKey = "${entry.publisher}:${entry.id}"
                val from = move.using[changedKey]
                Triple(changedKey, from ?: NOT_ENABLED, entry.version).takeIf { from != entry.version }
            }
        }.distinct().sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
        val reviews = changes.map { (changedKey, from, to) ->
            val (publisher, id) = changedKey.split(':', limit = 2)
            if (from == NOT_ENABLED) {
                return@map ModUpdateReview(
                    changedKey,
                    from,
                    to,
                    mapOf(ModUpdateReview.DEPENDENCIES to listOf("newly enabled: a new version needs it")),
                )
            }
            ModUpdateReview.of(
                key = changedKey,
                from = from,
                to = to,
                old = catalog.templateFor(publisher, id, from),
                new = catalog.templateFor(publisher, id, to),
                oldPackage = records[changedKey to from]?.toInstalledPackage(),
                newPackage = records[changedKey to to]?.toInstalledPackage(),
            )
        }
        return PendingUpdate(reviews, moves)
    }

    /**
     * Compare both locks against the same prepared tree, with APK/JAR decoding enabled.
     * Report existing failures separately from candidate regressions.
     */
    suspend fun check(move: PluginWorkspaceUsage.PendingMove): String {
        val checker = checkNotNull(preflight) { "This app cannot run the mod lab" }
        val before = checker.lab(move.workspaceId, move.before, decode = true)
        val lab = checker.lab(move.workspaceId, move.snapshot, decode = true)
        return summarizeUpdate(before, lab)
    }

    /** Revalidate the candidate locks against the latest installed inventory before applying any workspace. */
    suspend fun apply(update: PendingUpdate, installed: List<InstalledRecord>): List<String> {
        val latest = usage.pendingMoves(installed).associateBy { it.workspaceId }
        check(update.moves.all { latest[it.workspaceId] == it }) {
            "Installed versions or workspace settings changed. Review the update again."
        }
        return update.moves.map { move ->
            usage.applyReviewed(move).fold(
                onSuccess = { "${move.name}: updated" },
                onFailure = { "${move.name}: ${it.message ?: "could not save"}" },
            )
        }
    }
}

private const val NOT_ENABLED = "not enabled"

internal fun summarizeUpdate(before: ModLab, after: ModLab): String {
    fun refused(row: LabRow) = row.status == LabStatus.REFUSED
    val oldRefusals = before.rows.filter(::refused).associateBy { it.mod to it.operationId }
    val newRefusals = after.rows.filter(::refused)
    val regressions = newRefusals.filter { row -> oldRefusals[row.mod to row.operationId]?.reason != row.reason }
    val issues = buildList {
        if (before.problems.isNotEmpty()) add("Current versions need evidence: ${before.problems.joinToString()}")
        if (after.problems.isNotEmpty()) add("Candidate versions need evidence: ${after.problems.joinToString()}")
        regressions.forEach {
            val result = if (before.problems.isEmpty()) "New failure" else "Candidate failure"
            add("$result · ${it.mod} ${it.operationId}: ${it.reason}")
        }
        newRefusals.filterNot { it in regressions }.forEach {
            add("Existing failure · ${it.mod} ${it.operationId}: ${it.reason}")
        }
        val unchecked = after.rows.count { it.status == LabStatus.UNCHECKED }
        if (unchecked > 0) add("$unchecked operations still need evidence.")
    }
    val checked = after.rows.count { it.status == LabStatus.CHECKED }
    val summary = if (checked == 0 && issues.isEmpty()) {
        "No applicable operations checked"
    } else {
        "$checked candidate operations checked"
    }
    return (listOf(summary) + issues).joinToString("\n")
}
