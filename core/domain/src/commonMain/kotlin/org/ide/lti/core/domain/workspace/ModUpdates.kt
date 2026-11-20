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
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.plugin.ModUpdateReview
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginWorkspaceUsage
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.plugin.toInstalledPackage

/** An update waiting for review: what changes per version the workspaces use, and the workspaces it would move. */
public data class PendingUpdate(val reviews: List<ModUpdateReview>, val moves: List<PluginWorkspaceUsage.PendingMove>)

/**
 * Reviewing a mod update before the workspaces move onto it: each version they use compared with the newest
 * ([ModUpdateReview]), and, per workspace, the mod lab run with its settings on the new versions (not saved).
 */
public class ModUpdates(
    private val catalog: PluginCatalogPort,
    private val usage: PluginWorkspaceUsage,
    private val preflight: Preflight? = null,
) {
    /** What installing [newest] of [key] would change for the workspaces; null when no workspace would move. */
    public suspend fun pending(installed: List<InstalledRecord>, key: String, newest: String): PendingUpdate? {
        val moves = usage.pendingMoves(installed)
        val records = installed.filter { it.key == key }.associateBy { it.identity.version }
        val newRecord = records[newest]
        if (moves.isEmpty() || newRecord == null) return null
        val used = moves.mapNotNull { it.using[key] }.filter { it != newest }.distinct().sorted()
        val reviews = used.map { from ->
            val publisher = newRecord.identity.publisher
            val id = newRecord.identity.id
            ModUpdateReview.of(
                key = key,
                from = from,
                to = newest,
                old = catalog.templateFor(publisher, id, from),
                new = catalog.templateFor(publisher, id, newest),
                oldPackage = records[from]?.toInstalledPackage(),
                newPackage = newRecord.toInstalledPackage(),
            )
        }
        return PendingUpdate(reviews, moves)
    }

    /**
     * The mod lab for [move]'s workspace on the new versions (file operations; APK/JAR edits are checked by the
     * lab's decode in Patches, or the build), as one line: what was checked, and each refusal.
     */
    public suspend fun check(move: PluginWorkspaceUsage.PendingMove): String {
        val lab = checkNotNull(preflight) { "This app cannot run the mod lab" }.lab(move.workspaceId, move.snapshot)
        val counts = lab.rows.groupingBy { it.status }.eachCount()
        val refused = lab.rows.filter { it.status == LabStatus.REFUSED }
        val summary = listOfNotNull(
            counts[LabStatus.CHECKED]?.let { "$it checked" },
            counts[LabStatus.UNCHECKED]?.let { "$it left to the build" },
            counts[LabStatus.REFUSED]?.let { "$it refused" },
        ).joinToString(" · ").ifEmpty { "nothing to check" }
        val why = (lab.problems + refused.map { "${it.mod} ${it.operationId}: ${it.reason}" }).joinToString("; ")
        return if (why.isEmpty()) summary else "$summary — $why"
    }
}
