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
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetupUiStateFreshnessTest {

    private val readyTools = listOf(
        ToolComponentItem(
            id = "lpunpack",
            name = "LP Unpack",
            category = ToolCategory.DYNAMIC_PARTITIONS,
            binaryName = "lpunpack",
            status = StepStatus.SUCCESS,
            lastTested = "2026-09-13",
        ),
    )

    @Test
    fun testCopyToolchainSetupStateImmediatelyUpdatesComputedPresentations() {
        val initialState = SetupUiState(
            toolchainSetupState = ToolchainSetupState(),
            isToolchainBound = true,
        )

        // Initially not checking, no operations
        assertEquals(EnvironmentState.Unknown, initialState.environmentPresentation.environmentState)
        assertEquals(null, initialState.statusBarPresentation.leadingStatusText)

        // Copy state with isChecking = true
        val checkingState = initialState.copy(
            toolchainSetupState = initialState.toolchainSetupState.copy(isChecking = true),
        )
        // Computed getter must reflect fresh state immediately
        assertEquals("Verifying machine environment…", checkingState.statusBarPresentation.leadingStatusText)

        // Copy state with active operation
        val runningOp = SetupOperation(
            operationId = "fresh-op",
            executionState = SetupOperationState.RUNNING,
        )
        val runningState = checkingState.copy(activeOperation = runningOp)
        assertEquals("Running environment setup…", runningState.statusBarPresentation.leadingStatusText)

        // Copy state with ready steps
        val readySteps = ToolchainSetupState.defaultSteps().map {
            it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
        }
        val readyState = runningState.copy(
            toolchainSetupState = runningState.toolchainSetupState.copy(
                isChecking = false,
                steps = readySteps,
                toolsMatrix = readyTools,
                publishedToolIds = setOf("lpunpack"),
                checkedAt = 1000L,
                lastReadyAt = 1000L,
            ),
            activeOperation = null,
        )
        assertTrue(readyState.environmentPresentation.environmentState is EnvironmentState.Ready)
        val buildServiceItem = readyState.environmentSummary.items.first { it.label == "Build service" }
        assertEquals(IdeStatusSeverity.Ready, buildServiceItem.severity)
    }

    @Test
    fun testIsToolchainBoundAffectsEnvironmentPresentation() {
        val readySteps = ToolchainSetupState.defaultSteps().map {
            it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
        }
        val readyToolchain = ToolchainSetupState(
            steps = readySteps,
            toolsMatrix = readyTools,
            publishedToolIds = setOf("lpunpack"),
            checkedAt = 1000L,
            lastReadyAt = 1000L,
        )
        val boundState = SetupUiState(
            toolchainSetupState = readyToolchain,
            isToolchainBound = true,
        )
        assertTrue(boundState.environmentPresentation.environmentState is EnvironmentState.Ready)

        val unboundState = SetupUiState(
            toolchainSetupState = readyToolchain,
            isToolchainBound = false,
        )
        assertTrue(unboundState.environmentPresentation.environmentState is EnvironmentState.Unavailable)
    }
}
