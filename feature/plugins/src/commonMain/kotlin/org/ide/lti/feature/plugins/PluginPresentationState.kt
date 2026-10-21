/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.plugin.newestPerPlugin

/** One installed package: versions of one plugin are installed side by side, so the version is part of it. */
internal val InstalledRecord.rowKey: String get() = "${identity.key}@${identity.version}"

/**
 * What keeps each installed package (by [rowKey]) from being uninstalled: the workspaces whose builds use that
 * version ([workspaceUsers], by `publisher:id@version`), and the plugins that depend on it. A dependency binds only
 * the newest version of a plugin (the one the lock picks), so an older version nothing uses can be removed.
 */
internal fun packageReferences(
    records: List<InstalledRecord>,
    workspaceUsers: Map<String, List<String>> = emptyMap(),
): Map<String, List<String>> {
    val map = records.associate { record ->
        record.rowKey to workspaceUsers[record.rowKey].orEmpty().map { "Workspace '$it'" }.toMutableList()
    }.toMutableMap()
    val newest = newestPerPlugin(records, { it.identity.key }) { it.identity.version }.associateBy { it.identity.key }
    for (record in newest.values) {
        for (dep in record.dependencies) {
            val target = newest["${dep.publisher}:${dep.id}"] ?: continue
            map.getOrPut(target.rowKey) { mutableListOf() }.add("Package '${record.identity.key}'")
        }
    }
    return map
}

internal fun resolveInstalledSelection(records: List<InstalledRecord>, selectedKey: String?): InstalledRecord? =
    records.firstOrNull { it.rowKey == selectedKey } ?: records.firstOrNull()

internal fun resolveMarketplaceSelection(entries: List<IndexEntry>, selectedId: String?): IndexEntry? =
    entries.firstOrNull { it.id == selectedId } ?: entries.firstOrNull()

internal fun canInstallInspectedPackage(inspection: PackageInspection?, acknowledged: Boolean): Boolean =
    inspection != null && !inspection.report.hasBlockingErrors() && (inspection.hasSignature || acknowledged)

internal fun marketplaceSourceIsVerified(fetchResult: IndexFetchResult?): Boolean =
    fetchResult is IndexFetchResult.Fetched

internal fun authorTaskStatusLabel(selected: Boolean, running: Boolean, result: AuthorTaskResult?): String = when {
    !selected -> "Not run"
    running -> "Running..."
    result is AuthorTaskResult.Success -> "Completed"
    result is AuthorTaskResult.Failed -> "Failed (${result.exitCode})"
    result is AuthorTaskResult.Refused -> "Refused"
    result is AuthorTaskResult.TimedOut -> "Timed out"
    else -> "Not run"
}

internal fun packageTaskSucceeded(selectedTask: String, taskResult: AuthorTaskResult?): Boolean =
    selectedTask == PACKAGE_AUTHOR_TASK && taskResult is AuthorTaskResult.Success

/** The plugin's own name (its manifest's), or one made from its id for packages that have none. */
internal val InstalledRecord.title: String get() = displayName.ifBlank { formatPluginDisplayName(identity.id) }

internal fun formatPluginDisplayName(id: String): String = when (id) {
    "buildprops" -> "Build properties"
    "framework-hooks" -> "Framework hook sample"
    "resource-overlay" -> "Resource overlay"
    "pixel-tuner" -> "Pixel tuner"
    else -> id.split("-", "_", ".").joinToString(" ") { segment ->
        segment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

internal const val PACKAGE_AUTHOR_TASK: String = "packageMod"
