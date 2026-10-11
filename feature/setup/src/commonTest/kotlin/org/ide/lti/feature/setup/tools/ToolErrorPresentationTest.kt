/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.GroupStatus
import org.ide.lti.core.domain.tools.OperationKind
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * SC-008 table test verifying every ToolError code maps to the authoritative message
 * and next action from data-model.md, and that "Not installed" shows when nothing was ever installed (T076).
 */
class ToolErrorPresentationTest {
    @Test
    fun everyToolErrorCodeMapsToExpectedMessageAndNextAction() {
        data class TestCase(val error: ToolError, val expectedMessage: String, val expectedNextAction: String?)

        val table =
            listOf(
                TestCase(
                    error = ToolError.MissingPackages(listOf("cmake", "clang")),
                    expectedMessage = "Needs packages: cmake, clang",
                    expectedNextAction = "Show command",
                ),
                TestCase(
                    error = ToolError.Network("github.com"),
                    expectedMessage = "Can't reach github.com after 3 tries",
                    expectedNextAction = "Retry",
                ),
                TestCase(
                    error = ToolError.RefNotFound("v2.0.0"),
                    expectedMessage = "Version v2.0.0 not found",
                    expectedNextAction = "Choose another",
                ),
                TestCase(
                    error = ToolError.UnsupportedLayout(listOf("CMakeLists.txt")),
                    expectedMessage = "This repository isn't compatible: missing CMakeLists.txt",
                    expectedNextAction = "Choose another",
                ),
                TestCase(
                    error = ToolError.PatchFailed("break.patch"),
                    expectedMessage = "Vendor patches don't apply to this version",
                    expectedNextAction = "Use Studio's version",
                ),
                TestCase(
                    error = ToolError.BuildFailed("cmake"),
                    expectedMessage = "Build failed at cmake",
                    expectedNextAction = "View log · Retry",
                ),
                TestCase(
                    error = ToolError.VerifyFailed("adb"),
                    expectedMessage = "adb didn't run after building",
                    expectedNextAction = "View log · Retry",
                ),
                TestCase(
                    error = ToolError.ChecksumMismatch("sha256 bad"),
                    expectedMessage = "Download checksum doesn't match",
                    expectedNextAction = "Retry",
                ),
                TestCase(
                    error = ToolError.DiskFull("insufficient"),
                    expectedMessage = "Not enough disk space",
                    expectedNextAction = "Check again",
                ),
                TestCase(
                    error = ToolError.Busy("running"),
                    expectedMessage = "Another change is running for this group",
                    expectedNextAction = "(wait)",
                ),
                TestCase(
                    error = ToolError.InUse("locked"),
                    expectedMessage = "Can't remove the version in use, or one a running job uses",
                    expectedNextAction = "—",
                ),
                TestCase(
                    error = ToolError.NotFound("unknown"),
                    expectedMessage = "Unknown group or version",
                    expectedNextAction = "—",
                ),
                TestCase(
                    error = ToolError.ConnectionLost("closed"),
                    expectedMessage = "Lost connection to the build service",
                    expectedNextAction = "Reconnect",
                ),
                TestCase(
                    error = ToolError.AdmissionRequired("not admitted"),
                    expectedMessage = "Build service hasn't accepted this app yet",
                    expectedNextAction = "Reconnect",
                ),
                TestCase(
                    error = ToolError.Internal("unexpected"),
                    expectedMessage = "Unexpected result",
                    expectedNextAction = "View log",
                ),
            )

        for (case in table) {
            val pres = ToolsPresentationMapper.mapError(case.error)
            assertEquals(
                expected = case.expectedMessage,
                actual = pres.message,
                message = "Message mismatch for ${case.error::class.simpleName}",
            )
            assertEquals(
                expected = case.expectedNextAction,
                actual = pres.nextAction,
                message = "Next action mismatch for ${case.error::class.simpleName}",
            )
        }
    }

    @Test
    fun notInstalledShowsWhenNothingWasEverInstalledAndNeverPreviousVersionKept() {
        val neverInstalledState =
            GroupState(
                group = ToolGroupId("apktool"),
                current = null,
                installed = emptyList(),
                selection = null,
            )
        assertEquals(GroupStatus.NotInstalled, neverInstalledState.status)

        val row = ToolsPresentationMapper.mapRow(neverInstalledState)
        assertEquals("Not installed", row.statusLabel)
        assertEquals(ToolRowStatusKind.NotInstalled, row.statusKind)
        assertEquals("—", row.inUseLabel)
        assertFalse(row.inUseLabel.contains("previous version kept", ignoreCase = true))

        // Even with a failed operation, a group that was never installed shows "—" as inUseLabel,
        // never claiming a previous version was kept
        val failedOpState =
            GroupState(
                group = ToolGroupId("apktool"),
                current = null,
                installed = emptyList(),
                selection = null,
                operation =
                ToolOperation(
                    opId = "op-fail",
                    group = ToolGroupId("apktool"),
                    kind = OperationKind.Install,
                    phase = OperationPhase.Building,
                    result = OperationResult.Failed(ToolError.BuildFailed("compile error"), "Failed"),
                ),
            )
        val failedRow = ToolsPresentationMapper.mapRow(failedOpState)
        assertEquals("—", failedRow.inUseLabel)
        assertNotEquals("previous version kept", failedRow.inUseLabel)
    }
}
