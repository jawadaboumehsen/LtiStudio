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

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolVersionsPresentationMapperTest {

    @Test
    fun `maps to up to date when desired equals installed and healthy`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isHealthy = true,
        )

        assertEquals("v1.2.3", row.installedLabel)
        assertEquals("v1.2.3", row.selectedLabel)
        assertEquals(ToolGroupStatus.UP_TO_DATE, row.status)
        assertEquals("Up to date", row.status.label)
        assertEquals("Edit version", row.status.primaryAction)
    }

    @Test
    fun `maps to change pending when desired differs from installed`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v2.0.0",
            isHealthy = true,
        )

        assertEquals("v1.2.3", row.installedLabel)
        assertEquals("v2.0.0", row.selectedLabel)
        assertEquals(ToolGroupStatus.CHANGE_PENDING, row.status)
        assertEquals("Change pending", row.status.label)
        assertEquals("Review & build", row.status.primaryAction)
    }

    @Test
    fun `maps to needs recovery when toolchain is not healthy`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isHealthy = false,
        )

        assertEquals(ToolGroupStatus.NEEDS_RECOVERY, row.status)
        assertEquals("Needs recovery", row.status.label)
        assertEquals("Recover", row.status.primaryAction)
    }

    @Test
    fun `maps to not installed when installedVersion is null`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = null,
            desiredVersion = "v1.2.3",
            isHealthy = true,
        )

        assertEquals("Not installed", row.installedLabel)
        assertEquals(ToolGroupStatus.NOT_INSTALLED, row.status)
        assertEquals("Not installed", row.status.label)
        assertEquals("Install", row.status.primaryAction)
    }

    @Test
    fun `maps to checking when isChecking is true`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isChecking = true,
        )

        assertEquals(ToolGroupStatus.CHECKING, row.status)
        assertEquals("Checking", row.status.label)
        assertEquals("Please wait", row.status.primaryAction)
    }

    @Test
    fun `maps to waiting for jobs when isWaitingForJobs is true`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isWaitingForJobs = true,
        )

        assertEquals(ToolGroupStatus.WAITING_FOR_RUNNING_JOBS, row.status)
        assertEquals("Waiting for running jobs", row.status.label)
        assertEquals("View jobs", row.status.primaryAction)
    }

    @Test
    fun `maps to service unavailable when isServiceUnavailable is true`() {
        val row = ToolVersionsPresentationMapper.mapGroupRow(
            groupId = "android-tools",
            installedVersion = "v1.2.3",
            desiredVersion = "v1.2.3",
            isServiceUnavailable = true,
        )

        assertEquals(ToolGroupStatus.SERVICE_UNAVAILABLE, row.status)
        assertEquals("Service unavailable", row.status.label)
        assertEquals("Check service", row.status.primaryAction)
    }
}
