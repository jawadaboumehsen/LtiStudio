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
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MachineRowMapperTest {
    @Test
    fun mapWslRowOffersPickerWhenSeveralDistrosAreUsable() {
        val failed =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL",
                description = "Multiple distributions available",
                status = StepStatus.FAILED,
            )
        val a = DistroStatus.Usable(SetupEnvironment("Ubuntu-22.04", 2, "ubuntu", "22.04", "u", "/home/u"))
        val b = DistroStatus.Usable(SetupEnvironment("Ubuntu-24.04", 2, "ubuntu", "24.04", "u", "/home/u"))

        val row = MachineRowMapper.mapWslRow(failed, listOf(a, b), activeDistro = null)

        assertEquals(listOf("Ubuntu-22.04", "Ubuntu-24.04"), row?.choices)
        assertNull(row?.primaryAction)
    }

    @Test
    fun mapWslRowExplainsTheStatusClosestToUsable() {
        val failed =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL",
                description = "needs a user",
                status = StepStatus.FAILED,
            )
        val statuses =
            listOf(
                DistroStatus.Unsupported("docker-desktop", "found  "),
                DistroStatus.NoUsableUser("Ubuntu-24.04", "default user is root"),
            )

        val row = MachineRowMapper.mapWslRow(failed, statuses, activeDistro = null)

        assertEquals(SetupRowAction.OPEN_DISTRO, row?.primaryAction)
        assertEquals("Ubuntu-24.04", row?.distro)
    }

    @Test
    fun mapWslRowDefersToTheGenericRowWhileRunningOrUndetected() {
        val running =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL",
                description = "",
                status = StepStatus.RUNNING,
            )
        assertNull(MachineRowMapper.mapWslRow(running, listOf(DistroStatus.NoDistro), null))
        val failed = running.copy(status = StepStatus.FAILED)
        assertNull(MachineRowMapper.mapWslRow(failed, emptyList(), null))
    }

    @Test
    fun testMapDistroStatus() {
        val wslUnavail = DistroStatus.WslUnavailable("wsl.exe not found")
        val wslUnavailPres = MachineRowMapper.mapDistroStatus(wslUnavail)
        assertTrue(wslUnavailPres.detailText.contains("WSL isn't installed"))
        assertEquals(IdeStatusSeverity.Failed, wslUnavailPres.severity)
        assertFalse(wslUnavailPres.isReady)

        val noDistro = DistroStatus.NoDistro
        val noDistroPres = MachineRowMapper.mapDistroStatus(noDistro)
        assertTrue(noDistroPres.detailText.contains("No Linux distribution"))
        assertEquals(IdeStatusSeverity.Failed, noDistroPres.severity)
        assertFalse(noDistroPres.isReady)

        val startupFailed = DistroStatus.StartupFailed("Ubuntu-24.04", "timeout after 5s")
        val startupFailedPres = MachineRowMapper.mapDistroStatus(startupFailed)
        assertTrue(startupFailedPres.detailText.contains("Ubuntu-24.04 didn't start"))
        assertEquals("Retry", startupFailedPres.primaryActionLabel)
        assertEquals("Show diagnostics", startupFailedPres.secondaryActionLabel)
        assertEquals(IdeStatusSeverity.Failed, startupFailedPres.severity)
        assertFalse(startupFailedPres.isReady)

        val unsupported = DistroStatus.Unsupported("Debian", "found debian 12")
        val unsupportedPres = MachineRowMapper.mapDistroStatus(unsupported)
        assertTrue(unsupportedPres.detailText.contains("Debian isn't a supported Ubuntu LTS"))
        assertEquals(IdeStatusSeverity.Failed, unsupportedPres.severity)
        assertFalse(unsupportedPres.isReady)

        val noUser = DistroStatus.NoUsableUser("Ubuntu-24.04", "default user is root")
        val noUserPres = MachineRowMapper.mapDistroStatus(noUser)
        assertTrue(noUserPres.detailText.contains("Ubuntu-24.04 needs a user"))
        assertEquals("Open Ubuntu-24.04", noUserPres.primaryActionLabel)
        assertEquals(IdeStatusSeverity.Failed, noUserPres.severity)
        assertFalse(noUserPres.isReady)

        val usable =
            DistroStatus.Usable(
                SetupEnvironment("Ubuntu-24.04", 2, "ubuntu", "24.04", "testuser", "/home/testuser"),
            )
        val usablePres = MachineRowMapper.mapDistroStatus(usable)
        assertEquals("Ubuntu-24.04 · testuser", usablePres.detailText)
        assertEquals(IdeStatusSeverity.Ready, usablePres.severity)
        assertTrue(usablePres.isReady)
    }

    @Test
    fun testMapSystemPackagesAllStates() {
        // 1. Checking
        val checking =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "",
                    status = StepStatus.RUNNING,
                ),
                isChecking = true,
            )
        assertEquals("Checking…", checking.detailText)
        assertNull(checking.primaryActionLabel)
        assertTrue(checking.isChecking)

        // 2. Probe failed
        val probeFailed =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "Couldn't check packages: WSL timed out",
                    status = StepStatus.FAILED,
                ),
            )
        assertEquals("Couldn't check packages: WSL timed out", probeFailed.detailText)
        assertEquals("Retry", probeFailed.primaryActionLabel)

        // 3. Missing packages
        val missing =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "3 packages missing: git, cmake, ninja-build",
                    status = StepStatus.FAILED,
                ),
            )
        assertEquals("3 packages missing: git, cmake, ninja-build", missing.detailText)
        assertEquals("Install packages", missing.primaryActionLabel)

        // 4. Unavailable
        val unavailable =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "foo isn't available on Ubuntu 24.04",
                    status = StepStatus.FAILED,
                ),
            )
        assertEquals("foo isn't available on Ubuntu 24.04", unavailable.detailText)
        assertNull(unavailable.primaryActionLabel)

        // 5. Awaiting user action
        val awaitingOp =
            SetupOperation(
                operationId = "test_op",
                executionState = SetupOperationState.AWAITING_USER_ACTION,
            )
        val awaiting =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "",
                    status = StepStatus.FAILED,
                ),
                activeOperation = awaitingOp,
            )
        assertEquals("Waiting for you in the terminal", awaiting.detailText)
        assertEquals("Continue setup", awaiting.primaryActionLabel)

        // 6. Verifying in handoff sheet
        val verifying =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "",
                    status = StepStatus.FAILED,
                ),
                handoffSheetState = TerminalHandoffState.Verifying,
            )
        assertEquals("Checking installation…", verifying.detailText)
        assertNull(verifying.primaryActionLabel)
        assertTrue(verifying.isChecking)

        // 7. Partial still missing
        val partial =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "Still missing: cmake",
                    status = StepStatus.FAILED,
                ),
                handoffSheetState = TerminalHandoffState.StillMissing(listOf("cmake"), "sudo apt-get install cmake"),
            )
        assertEquals("Still missing: cmake", partial.detailText)
        assertEquals("Install packages", partial.primaryActionLabel)

        // 8. OK (All present)
        val ok =
            MachineRowMapper.mapSystemPackages(
                step =
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "",
                    description = "All prerequisite packages installed",
                    status = StepStatus.SUCCESS,
                ),
            )
        assertEquals("All present", ok.detailText)
        assertNull(ok.primaryActionLabel)
        assertTrue(ok.isReady)
    }

    @Test
    fun mapToolchainRow_covers_all_statuses() {
        assertNull(MachineRowMapper.mapToolchainRow(null))

        val successStep = SetupStepDetail(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            title = "Toolchain",
            description = "10 tools ready",
            status = StepStatus.SUCCESS,
        )
        val successRow = MachineRowMapper.mapToolchainRow(successStep)
        assertEquals("10 tools ready", successRow?.detailText)
        assertEquals(IdeStatusSeverity.Ready, successRow?.severity)
        assertTrue(successRow?.isReady == true)
        assertFalse(successRow?.isRunning == true)
        assertNull(successRow?.primaryAction)

        val runningStep = SetupStepDetail(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            title = "Toolchain",
            description = "Building lz4 (2 of 10)",
            status = StepStatus.RUNNING,
        )
        val runningRow = MachineRowMapper.mapToolchainRow(runningStep)
        assertEquals("Building lz4 (2 of 10)", runningRow?.detailText)
        assertEquals(IdeStatusSeverity.Running, runningRow?.severity)
        assertTrue(runningRow?.isRunning == true)
        assertFalse(runningRow?.isReady == true)

        val failedStep = SetupStepDetail(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            title = "Toolchain",
            description = "Tool registration failed",
            error = "erofsfuse: not executable, adb: resolved elsewhere",
            status = StepStatus.FAILED,
        )
        val failedRow = MachineRowMapper.mapToolchainRow(failedStep)
        assertEquals("Tool registration failed", failedRow?.detailText)
        assertEquals(IdeStatusSeverity.Failed, failedRow?.severity)
        assertEquals(SetupRowAction.RETRY, failedRow?.primaryAction)
        assertEquals("Retry", failedRow?.primaryActionLabel)
        assertEquals("erofsfuse: not executable, adb: resolved elsewhere", failedRow?.errorText)

        val warningStep = SetupStepDetail(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            title = "Toolchain",
            description = "Some tools optional",
            error = "optional tool missing",
            status = StepStatus.WARNING,
        )
        val warningRow = MachineRowMapper.mapToolchainRow(warningStep)
        assertEquals("Some tools optional", warningRow?.detailText)
        assertEquals(IdeStatusSeverity.Warning, warningRow?.severity)
        assertEquals(SetupRowAction.RETRY, warningRow?.primaryAction)
        assertEquals("Retry", warningRow?.primaryActionLabel)

        val pendingStep = SetupStepDetail(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            title = "Toolchain",
            description = "",
            status = StepStatus.PENDING,
        )
        val pendingRow = MachineRowMapper.mapToolchainRow(pendingStep)
        assertEquals("Waiting for prerequisites", pendingRow?.detailText)
        assertEquals(IdeStatusSeverity.Neutral, pendingRow?.severity)
    }
}
