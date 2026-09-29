/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SetupBuildServicePresentationTest {

    private fun step(
        stage: SetupStepStage,
        title: String = stage.displayName,
        status: StepStatus = StepStatus.SUCCESS,
        provenance: StepProvenance = StepProvenance.LIVE,
        description: String = "",
        error: String? = null,
        verifiedAt: Long? = null,
    ) = SetupStepDetail(
        stage = stage,
        title = title,
        description = description,
        status = status,
        provenance = provenance,
        error = error,
        verifiedAtEpochMs = verifiedAt,
    )

    @Test
    fun testBuildServiceState_needsJava() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Needs Java 21",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Needs Java", presentation.chipText)
        assertEquals("Needs Java 21 (install via System packages)", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
        assertFalse(presentation.detailText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_needsJavaFromSystemPackages() {
        val serverStep = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.PENDING,
            description = "Pending verification.",
        )
        val sysPkgStep = step(
            stage = SetupStepStage.SYSTEM_PACKAGES,
            status = StepStatus.FAILED,
            description = "openjdk-21-jdk missing",
        )
        val presentation = SetupPresentationMapper.mapBuildService(
            step = serverStep,
            systemPackagesStep = sysPkgStep,
        )

        assertEquals("Needs Java", presentation.chipText)
        assertEquals("Needs Java 21 (install via System packages)", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
    }

    @Test
    fun testBuildServiceState_notBundled() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Build service missing from this app",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Not bundled", presentation.chipText)
        assertEquals("Build service missing from this app — reinstall LtiRom Studio", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
        assertFalse(presentation.detailText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_installing() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.RUNNING,
            description = "Installing build service 1.0.0…",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Installing", presentation.chipText)
        assertEquals("Installing build service 1.0.0…", presentation.detailText)
        assertEquals(IdeStatusSeverity.Running, presentation.severity)
        assertTrue(presentation.isRunning)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_installFailed() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Installing build service failed",
            error = "Extraction failed",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Install failed", presentation.chipText)
        assertEquals("Installing build service failed", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
        assertEquals(SetupRowAction.RETRY, presentation.primaryAction)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_updateWaiting() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.WARNING,
            description = "Update to 1.2.0 waits for the current build to finish",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Update waiting", presentation.chipText)
        assertEquals("Update to 1.2.0 waits for the current build to finish", presentation.detailText)
        assertEquals(IdeStatusSeverity.Warning, presentation.severity)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_wontStart() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Build service didn't start: port collision",
            error = "port collision",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Won't start", presentation.chipText)
        assertEquals("Build service didn't start: port collision", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
        assertEquals(SetupRowAction.RETRY, presentation.primaryAction)
        assertEquals(SetupRowAction.SHOW_LOG, presentation.secondaryAction)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_unreachable() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Can't reach the build service",
            error = "Connection refused",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Unreachable", presentation.chipText)
        assertEquals("Can't reach the build service", presentation.detailText)
        assertEquals(IdeStatusSeverity.Failed, presentation.severity)
        assertEquals(SetupRowAction.RETRY, presentation.primaryAction)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_connectedWithPing() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.SUCCESS,
            description = "1.0.0 · connected",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st, daemonPingMs = 28L)

        assertEquals("28ms", presentation.chipText)
        assertEquals("1.0.0 · connected", presentation.detailText)
        assertEquals(IdeStatusSeverity.Ready, presentation.severity)
        assertTrue(presentation.isReady)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_connectedWithoutPing() {
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.SUCCESS,
            description = "1.0.0 · connected",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st, daemonPingMs = null)

        assertEquals("Connected", presentation.chipText)
        assertEquals(IdeStatusSeverity.Ready, presentation.severity)
        assertTrue(presentation.isReady)
        assertFalse(presentation.chipText.contains("Disconnected", ignoreCase = true))
    }

    @Test
    fun testBuildServiceState_neverMapsToDisconnected() {
        val allTestSteps = listOf(
            null,
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.PENDING, description = "Pending"),
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.RUNNING, description = "Checking…"),
            step(
                SetupStepStage.SERVER_CONNECTIVITY,
                status = StepStatus.RUNNING,
                description = "Installing build service",
            ),
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.SUCCESS, description = "Connected"),
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.FAILED, description = "Needs Java 21"),
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.FAILED, description = "Not bundled"),
            step(
                SetupStepStage.SERVER_CONNECTIVITY,
                status = StepStatus.FAILED,
                description = "Installing build service failed",
            ),
            step(
                SetupStepStage.SERVER_CONNECTIVITY,
                status = StepStatus.FAILED,
                description = "Build service didn't start",
            ),
            step(
                SetupStepStage.SERVER_CONNECTIVITY,
                status = StepStatus.FAILED,
                description = "Can't reach the build service",
            ),
            step(SetupStepStage.SERVER_CONNECTIVITY, status = StepStatus.WARNING, description = "Update waiting"),
        )

        allTestSteps.forEach { st ->
            val pres = SetupPresentationMapper.mapBuildService(st)
            assertNotEquals("Disconnected", pres.chipText, "Chip text must never be 'Disconnected' for step: $st")
            val containsDisc = pres.detailText.contains("Disconnected", ignoreCase = true)
            assertFalse(containsDisc, "Detail text must never be 'Disconnected' for step: $st")
        }
    }

    @Test
    fun testFormatElapsedTime() {
        assertEquals("00:00", SetupPresentationMapper.formatElapsedTime(0L))
        assertEquals("00:00", SetupPresentationMapper.formatElapsedTime(-500L))
        assertEquals("00:05", SetupPresentationMapper.formatElapsedTime(5_000L))
        assertEquals("00:59", SetupPresentationMapper.formatElapsedTime(59_000L))
        assertEquals("01:00", SetupPresentationMapper.formatElapsedTime(60_000L))
        assertEquals("01:05", SetupPresentationMapper.formatElapsedTime(65_000L))
        assertEquals("10:30", SetupPresentationMapper.formatElapsedTime(630_000L))
        assertEquals("61:05", SetupPresentationMapper.formatElapsedTime(3_665_000L))
    }

    @Test
    fun testFormatProgressDetail() {
        val baseDescription = "Building gcc (2 of 5)"
        val startedAt = 100_000L
        val nowEpochMs = 165_000L // 65 seconds elapsed -> 01:05

        val result = SetupPresentationMapper.formatProgressDetail(
            description = baseDescription,
            startedAt = startedAt,
            nowEpochMs = nowEpochMs,
        )
        assertEquals("Building gcc (2 of 5) · 01:05", result)

        // Null startedAt returns unchanged
        assertEquals(baseDescription, SetupPresentationMapper.formatProgressDetail(baseDescription, null, nowEpochMs))

        // Null nowEpochMs returns unchanged
        assertEquals(baseDescription, SetupPresentationMapper.formatProgressDetail(baseDescription, startedAt, null))

        // nowEpochMs < startedAt returns unchanged
        assertEquals(baseDescription, SetupPresentationMapper.formatProgressDetail(baseDescription, startedAt, 50_000L))
    }

    @Test
    fun testIdeStatusSeverityRunningIsNotWarning() {
        assertNotEquals(
            IdeStatusSeverity.Warning,
            IdeStatusSeverity.Running,
            "IdeStatusSeverity.Running must not be Warning",
        )
        val runningStep = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.RUNNING,
            description = "Installing build service…",
        )
        val pres = SetupPresentationMapper.mapBuildService(runningStep)
        assertEquals(IdeStatusSeverity.Running, pres.severity)
        assertNotEquals(IdeStatusSeverity.Warning, pres.severity)
    }

    @Test
    fun `the provisioner's deferred-update step shows the Update waiting chip`() {
        // Exactly what WslToolchainProvisioner writes when the old service is still running jobs.
        val st = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.WARNING,
            description = "Update waiting: build service 1.0.0 is finishing 2 active run(s); 2.0.0 starts after.",
        )
        val presentation = SetupPresentationMapper.mapBuildService(step = st)

        assertEquals("Update waiting", presentation.chipText)
        assertEquals(IdeStatusSeverity.Warning, presentation.severity)
    }

    @Test
    fun `a connected build service stays ready while later steps of the check are still running`() {
        // isChecking is true for the whole check; the Server Bridge row finished third and must not spin until
        // the toolchain step (the last one) is done.
        val connected = step(
            SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.SUCCESS,
            description = "Connected · 30ms · pid 1383",
        )

        val presentation = SetupPresentationMapper.mapBuildService(step = connected, isChecking = true)

        assertFalse(presentation.isRunning, "$presentation")
        assertEquals(IdeStatusSeverity.Ready, presentation.severity)
    }
}
