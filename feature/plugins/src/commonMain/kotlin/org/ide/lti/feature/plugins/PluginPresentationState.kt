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

internal fun resolveInstalledSelection(records: List<InstalledRecord>, selectedKey: String?): InstalledRecord? =
    records.firstOrNull { it.identity.key == selectedKey } ?: records.firstOrNull()

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
