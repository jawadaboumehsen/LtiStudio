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
    NOT_INSTALLED("Not installed", "Install"),
    CHECKING("Checking", "Please wait"),
    UP_TO_DATE("Up to date", "Edit version"),
    CHANGE_PENDING("Change pending", "Review & build"),
    UPDATE_AVAILABLE("Update available", "Review update"),
    UNSUPPORTED("Unsupported", "Edit version"),
    BUILDING("Building", "View progress"),
    WAITING_FOR_RUNNING_JOBS("Waiting for running jobs", "View jobs"),
    RECOVERY_REQUIRED("Recovery required", "Recover"),
    SERVICE_UNAVAILABLE("Service unavailable", "Check service"),
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
        isChecking: Boolean = false,
        isWaitingForJobs: Boolean = false,
        isServiceUnavailable: Boolean = false,
        isInstallationDataInvalid: Boolean = false,
    ): ToolGroupRowModel {
        val installedLabel = when {
            isInstallationDataInvalid -> "Installation data unavailable"
            installedVersion != null -> installedVersion
            else -> "Not installed"
        }
        val selectedLabel = desiredVersion ?: installedLabel

        val status = when {
            isServiceUnavailable -> ToolGroupStatus.SERVICE_UNAVAILABLE
            isChecking -> ToolGroupStatus.CHECKING
            isInstallationDataInvalid -> ToolGroupStatus.NEEDS_RECOVERY
            installedVersion == null -> ToolGroupStatus.NOT_INSTALLED
            !isHealthy -> ToolGroupStatus.NEEDS_RECOVERY
            isWaitingForJobs -> ToolGroupStatus.WAITING_FOR_RUNNING_JOBS
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
