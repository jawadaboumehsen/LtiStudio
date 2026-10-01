/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

public enum class ToolGroupStatus(public val label: String, public val primaryAction: String) {
    UP_TO_DATE("Up to date", "Edit version"),
    CHANGE_PENDING("Change pending", "Review & build"),
    UPDATE_AVAILABLE("Update available", "Review update"),
    UNSUPPORTED("Unsupported", "Edit version"),
    BUILDING("Building", "View progress"),
    NEEDS_RECOVERY("Needs recovery", "Recover"),
}

public data class ToolGroupRowModel(
    val groupId: String,
    val installedLabel: String,
    val selectedLabel: String,
    val status: ToolGroupStatus,
)

public object ToolVersionsPresentationMapper {

    public fun mapGroupRow(
        groupId: String,
        installedVersion: String?,
        desiredVersion: String?,
        isHealthy: Boolean = true,
        isBuilding: Boolean = false,
        isUnsupported: Boolean = false,
        hasUpdateAvailable: Boolean = false,
    ): ToolGroupRowModel {
        val installedLabel = installedVersion ?: "none"
        val selectedLabel = desiredVersion ?: installedLabel

        val status = when {
            !isHealthy -> ToolGroupStatus.NEEDS_RECOVERY
            isBuilding -> ToolGroupStatus.BUILDING
            isUnsupported -> ToolGroupStatus.UNSUPPORTED
            hasUpdateAvailable -> ToolGroupStatus.UPDATE_AVAILABLE
            desiredVersion != null && desiredVersion != installedVersion -> ToolGroupStatus.CHANGE_PENDING
            else -> ToolGroupStatus.UP_TO_DATE
        }

        return ToolGroupRowModel(
            groupId = groupId,
            installedLabel = installedLabel,
            selectedLabel = selectedLabel,
            status = status,
        )
    }
}
