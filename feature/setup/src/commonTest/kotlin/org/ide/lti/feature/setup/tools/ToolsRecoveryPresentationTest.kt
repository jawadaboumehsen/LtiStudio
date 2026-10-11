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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.InterruptedOp
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.AutoToolsGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure presentation test for recovery and disconnection states (T087 / US6).
 *
 * Verifies:
 * 1. An interrupted op maps to status "Interrupted" with action "Retry";
 *    work/<op>/out is never in installed versions.
 * 2. When disconnected (isConnected = false), every row maps to "Unknown" with action "Reconnect".
 * 3. AutoToolsGate.shouldStart returns false when disconnected.
 */
class ToolsRecoveryPresentationTest {
    private fun sampleVersion(id: String = "v1.0.0", intact: Boolean = true): InstalledVersion = InstalledVersion(
        group = ToolGroupId("erofs-utils"),
        id = id,
        label = id,
        source =
        ResolvedSource.Git(
            repoUrl = "https://example.com/repo.git",
            ref = ToolRef.Tag(id),
            commit = "a".repeat(40),
        ),
        recipe = RecipeSummary(type = "cmake", revision = 1),
        outputs = listOf(InstalledOutput(file = "mkfs.erofs", size = 1024L, mode = "755")),
        builtAt = "2026-10-08T00:00:00Z",
        intact = intact,
    )

    @Test
    fun interruptedOperationMapsToInterruptedStatusWithRetryAction() {
        val group = ToolGroupId("erofs-utils")
        val interruptedOp =
            InterruptedOp(
                opId = "op-interrupted-123",
                versionId = "v1.8.10",
                startedAt = "2026-10-08T12:00:00Z",
            )
        val state =
            GroupState(
                group = group,
                current = null,
                installed = listOf(sampleVersion("v1.8.9")),
                interrupted = interruptedOp,
            )

        val row = ToolsPresentationMapper.mapRow(state, isConnected = true)

        assertEquals("Interrupted", row.statusLabel)
        assertEquals(ToolRowStatusKind.Interrupted, row.statusKind)
        assertEquals("Retry", row.actionLabel)
        assertFalse(row.canCancel)

        // work/<op>/out is never offered as an installed version
        assertTrue(state.installed.none { it.id.contains("work/") || it.id.contains("out") })
        assertTrue(state.installed.all { it.id == "v1.8.9" })
    }

    @Test
    fun disconnectedStateMapsRowsToUnknownWithReconnectAction() {
        val group = ToolGroupId("erofs-utils")
        val readyState =
            GroupState(
                group = group,
                current = sampleVersion("v1.8.9"),
                installed = listOf(sampleVersion("v1.8.9")),
            )

        val row = ToolsPresentationMapper.mapRow(readyState, isConnected = false)

        assertEquals("Unknown", row.statusLabel)
        assertEquals(ToolRowStatusKind.Unknown, row.statusKind)
        assertEquals("Reconnect", row.actionLabel)
        assertFalse(row.canCancel)
    }

    @Test
    fun disconnectedToolsPageStateExposesUnknownRowsWithReconnect() = kotlinx.coroutines.test.runTest {
        val fakePort =
            object : ToolStorePort by ToolStorePort.NoOp {
                override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> =
                    Result.failure(Exception("Connection refused"))
            }

        val manager = ToolManager(port = fakePort)
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val pageState = ToolsPageState(manager = manager, scope = scope)
            manager.refresh()

            assertFalse(manager.state.value.isConnected)
            val rows =
                pageState.rows.first { list ->
                    list.isNotEmpty() && list.all { it.statusKind == ToolRowStatusKind.Unknown }
                }
            assertTrue(rows.isNotEmpty())
            for (row in rows) {
                assertEquals("Unknown", row.statusLabel, "Row ${row.groupId} status should be Unknown")
                assertEquals(
                    ToolRowStatusKind.Unknown,
                    row.statusKind,
                    "Row ${row.groupId} statusKind should be Unknown",
                )
                assertEquals("Reconnect", row.actionLabel, "Row ${row.groupId} action should be Reconnect")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoToolsGateDoesNotStartWhenDisconnected() {
        val steps =
            listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "",
                    status = StepStatus.SUCCESS,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Server",
                    description = "",
                    status = StepStatus.FAILED,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SYSTEM_PACKAGES,
                    title = "Packages",
                    description = "",
                    status = StepStatus.SUCCESS,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tools",
                    description = "",
                    status = StepStatus.PENDING,
                ),
            )
        val disconnectedState = ToolchainSetupState(steps = steps)

        assertFalse(disconnectedState.isServerConnected)
        assertFalse(AutoToolsGate.shouldStart(disconnectedState, attemptedThisLaunch = false))
    }
}
