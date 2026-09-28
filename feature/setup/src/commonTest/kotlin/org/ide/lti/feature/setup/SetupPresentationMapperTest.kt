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
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupPresentationMapperTest {

    @Test
    fun testMapStageState() {
        val detailPending = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.PENDING,
            provenance = StepProvenance.LIVE,
        )
        assertEquals(StagePresentationState.UNKNOWN, SetupPresentationMapper.mapStageState(detailPending, false))

        val detailRestored = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.SUCCESS,
            provenance = StepProvenance.RESTORED,
        )
        assertEquals(StagePresentationState.RESTORED, SetupPresentationMapper.mapStageState(detailRestored, false))

        val detailChecking = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.RUNNING,
            provenance = StepProvenance.LIVE,
        )
        assertEquals(StagePresentationState.CHECKING, SetupPresentationMapper.mapStageState(detailChecking, true))

        val detailReady = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.SUCCESS,
            provenance = StepProvenance.LIVE,
        )
        assertEquals(StagePresentationState.READY, SetupPresentationMapper.mapStageState(detailReady, false))

        val detailFailed = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.FAILED,
            provenance = StepProvenance.LIVE,
        )
        assertEquals(StagePresentationState.FAILED, SetupPresentationMapper.mapStageState(detailFailed, false))
    }

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

    private fun coreTool(
        id: String,
        name: String = id,
        category: ToolCategory = ToolCategory.DYNAMIC_PARTITIONS,
        status: StepStatus = StepStatus.SUCCESS,
        lastTested: String? = "2026-09-13",
    ) = ToolComponentItem(
        id = id,
        name = name,
        category = category,
        binaryName = id,
        status = status,
        lastTested = lastTested,
        isCore = true,
    )

    @Test
    fun testEveryFr001TransitionMappedExhaustively() {
        // 1. Unknown: never checked
        val unknownState = ToolchainSetupState()
        val mappedUnknown = SetupPresentationMapper.mapEnvironmentState(unknownState)
        assertEquals(EnvironmentState.Unknown, mappedUnknown)
        assertEquals(PrimaryAction.CHECK, SetupPresentationMapper.mapAction(mappedUnknown))

        // 2. Checking: check actively running
        val checkingState = ToolchainSetupState(isChecking = true)
        val mappedChecking = SetupPresentationMapper.mapEnvironmentState(checkingState)
        assertEquals(EnvironmentState.Checking, mappedChecking)
        assertEquals(PrimaryAction.CHECKING, SetupPresentationMapper.mapAction(mappedChecking))

        // 3. CheckedNeedsSetup: live check completed, tools/repos missing
        val checkedAtMs = 1700000050000L
        val needsSetupState = ToolchainSetupState(
            checkedAt = checkedAtMs,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION, status = StepStatus.PENDING),
                step(SetupStepStage.TOOLCHAIN_COMPILATION, status = StepStatus.PENDING),
            ),
        )
        val mappedNeedsSetup = SetupPresentationMapper.mapEnvironmentState(needsSetupState)
        assertTrue(mappedNeedsSetup is EnvironmentState.CheckedNeedsSetup)
        assertEquals(checkedAtMs, mappedNeedsSetup.checkedAt)
        assertEquals(PrimaryAction.SET_UP, SetupPresentationMapper.mapAction(mappedNeedsSetup))

        // 4. Ready: all live evidence ready, verified and published
        val readyTools = listOf(
            coreTool("lpunpack", "LP Unpack"),
            coreTool("mkfs.erofs", "Make EROFS", ToolCategory.FILESYSTEM_AND_IMAGES),
        )
        val readyState = ToolchainSetupState(
            checkedAt = checkedAtMs,
            lastReadyAt = checkedAtMs,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                step(SetupStepStage.TOOLCHAIN_COMPILATION),
            ),
            toolsMatrix = readyTools,
            publishedToolIds = setOf("lpunpack", "mkfs.erofs"),
        )
        val mappedReady = SetupPresentationMapper.mapEnvironmentState(readyState)
        assertTrue(mappedReady is EnvironmentState.Ready)
        assertEquals(checkedAtMs, mappedReady.checkedAt)
        assertEquals(checkedAtMs, mappedReady.lastReadyAt)
        assertEquals(PrimaryAction.OPEN_WORKSPACES, SetupPresentationMapper.mapAction(mappedReady))

        // 5. Failed: stage error with preserved evidence
        val failedState = ToolchainSetupState(
            checkedAt = checkedAtMs,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    description = "Build error in erofs-utils",
                    error = "make failed exit code 2",
                    status = StepStatus.FAILED,
                ),
            ),
        )
        val mappedFailed = SetupPresentationMapper.mapEnvironmentState(failedState)
        assertTrue(mappedFailed is EnvironmentState.Failed)
        assertEquals(SetupStepStage.TOOLCHAIN_COMPILATION, mappedFailed.stage)
        assertEquals("make failed exit code 2", mappedFailed.error)
        assertEquals(PrimaryAction.RETRY_STAGE, SetupPresentationMapper.mapAction(mappedFailed))

        // 6. Unavailable: server bridge down
        val unavailableState = ToolchainSetupState(
            steps = listOf(
                step(
                    SetupStepStage.SERVER_CONNECTIVITY,
                    description = "Daemon unreachable",
                    error = "Connection refused",
                    status = StepStatus.FAILED,
                ),
            ),
        )
        val mappedUnavailable = SetupPresentationMapper.mapEnvironmentState(unavailableState)
        assertTrue(mappedUnavailable is EnvironmentState.Unavailable)
        assertEquals(PrimaryAction.RETRY_CONNECTION, SetupPresentationMapper.mapAction(mappedUnavailable))

        // 7. Restored: persisted evidence restored from previous session, not live-verified
        val restoredState = ToolchainSetupState(
            lastVerifiedTimestamp = 1699999900000L,
            checkedAt = null,
            steps = listOf(
                step(
                    SetupStepStage.WSL_DETECTION,
                    status = StepStatus.PENDING,
                    provenance = StepProvenance.RESTORED,
                    verifiedAt = 1699999900000L,
                ),
            ),
        )
        val mappedRestored = SetupPresentationMapper.mapEnvironmentState(restoredState)
        assertTrue(mappedRestored is EnvironmentState.Restored)
        assertNull(
            mappedRestored.checkedAt,
            "Restored state must have null checkedAt (no live check in this session)",
        )
        assertEquals(1699999900000L, mappedRestored.lastReadyAt)
        assertEquals(
            PrimaryAction.CHECK,
            SetupPresentationMapper.mapAction(mappedRestored),
            "Restored state must offer Check, never live-ready",
        )
    }

    @Test
    fun testCheckedAtVsLastReadyAtSeparation() {
        val checkTime = 1700000100000L

        // Fresh check on incomplete system updates checkedAt, while lastReadyAt remains null
        val stateAfterCheck = ToolchainSetupState(
            checkedAt = checkTime,
            lastReadyAt = null,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.REPO_SYNCHRONIZATION, status = StepStatus.PENDING),
            ),
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateAfterCheck)
        assertEquals(checkTime, presentation.checkedAt)
        assertNull(
            presentation.lastVerifiedAt,
            "lastReadyAt / lastVerifiedAt must remain null when environment needs setup",
        )
        assertEquals(PrimaryAction.SET_UP, presentation.primaryAction)

        // Fully ready live evidence updates both checkedAt and lastReadyAt
        val readyTime = 1700000200000L
        val fullyReadyState = ToolchainSetupState(
            checkedAt = readyTime,
            lastReadyAt = readyTime,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                step(SetupStepStage.TOOLCHAIN_COMPILATION),
            ),
            toolsMatrix = listOf(coreTool("lpunpack", "LP Unpack")),
            publishedToolIds = setOf("lpunpack"),
        )
        val readyPresentation = SetupPresentationMapper.mapEnvironment(fullyReadyState)
        assertEquals(readyTime, readyPresentation.checkedAt)
        assertEquals(readyTime, readyPresentation.lastVerifiedAt)
        assertTrue(readyPresentation.lastVerifiedAt!! <= readyPresentation.checkedAt!!)
        assertEquals(PrimaryAction.OPEN_WORKSPACES, readyPresentation.primaryAction)
    }

    @Test
    fun testNullServiceProducesUnavailableStateNotWarningFallback() {
        val state = ToolchainSetupState()
        // When service is not bound in DI
        val presentation = SetupPresentationMapper.mapEnvironment(state, isToolchainBound = false)
        assertTrue(presentation.environmentState is EnvironmentState.Unavailable)
        assertEquals(PrimaryAction.RETRY_CONNECTION, presentation.primaryAction)
        assertFalse(presentation.primaryAction == PrimaryAction.OPEN_WORKSPACES)
    }

    @Test
    fun testMissingPublicationPreventsReady() {
        val checkTime = 1700000300000L
        val tools = listOf(
            coreTool("lpunpack", "LP Unpack"),
            coreTool("mkfs.erofs", "Make EROFS", ToolCategory.FILESYSTEM_AND_IMAGES),
        )
        // Only lpunpack is published; mkfs.erofs publication is missing!
        val stateMissingPub = ToolchainSetupState(
            checkedAt = checkTime,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                step(SetupStepStage.TOOLCHAIN_COMPILATION),
            ),
            toolsMatrix = tools,
            // mkfs.erofs missing!
            publishedToolIds = setOf("lpunpack"),
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateMissingPub)
        assertFalse(
            presentation.environmentState is EnvironmentState.Ready,
            "Missing publication must prevent Ready state",
        )
        assertTrue(presentation.environmentState is EnvironmentState.CheckedNeedsSetup)
        assertEquals(PrimaryAction.SET_UP, presentation.primaryAction)
    }

    @Test
    fun testFailedProbePreventsReady() {
        val checkTime = 1700000400000L
        val tools = listOf(coreTool("lpunpack", "LP Unpack", status = StepStatus.FAILED))
        val stateFailedProbe = ToolchainSetupState(
            checkedAt = checkTime,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                step(SetupStepStage.TOOLCHAIN_COMPILATION),
            ),
            toolsMatrix = tools,
            publishedToolIds = setOf("lpunpack"),
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateFailedProbe)
        assertFalse(
            presentation.environmentState is EnvironmentState.Ready,
            "Failed execution probe must prevent Ready state",
        )
    }

    @Test
    fun testEmptyInventoryPreventsReady() {
        val checkTime = 1700000500000L
        val stateEmptyTools = ToolchainSetupState(
            checkedAt = checkTime,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                step(SetupStepStage.TOOLCHAIN_COMPILATION),
            ),
            // Empty inventory!
            toolsMatrix = emptyList(),
            publishedToolIds = emptySet(),
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateEmptyTools)
        assertFalse(
            presentation.environmentState is EnvironmentState.Ready,
            "Empty tool inventory must never become Ready",
        )
    }

    @Test
    fun testInterruptedActiveOperationMapsToInterruptedAndReconnect() {
        val state = ToolchainSetupState()
        val interruptedOp = SetupOperation(
            operationId = "interrupted-op-123",
            executionState = SetupOperationState.INTERRUPTED,
            failure = "Daemon connection lost mid-flight",
        )
        val presentation = SetupPresentationMapper.mapEnvironment(
            state = state,
            isToolchainBound = true,
            activeOperation = interruptedOp,
        )
        val envState = presentation.environmentState
        assertTrue(envState is EnvironmentState.Interrupted)
        assertEquals(
            "Daemon connection lost mid-flight",
            envState.reason,
        )
        assertEquals(PrimaryAction.RECONNECT, presentation.primaryAction)
    }

    @Test
    fun testJournalTruthOutranksEvidenceBlockedThenPending() {
        val liveReady = ToolchainSetupState(
            steps = ToolchainSetupState.defaultSteps().map {
                it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
            },
            checkedAt = 1_000L,
            lastReadyAt = 1_000L,
        )
        val blocked = SetupPresentationMapper.mapEnvironment(
            state = liveReady.copy(pendingAttemptId = "attempt-9", recoveryBlockReason = "ambiguous receipt"),
        )
        val blockedState = blocked.environmentState
        assertTrue(
            blockedState is EnvironmentState.Interrupted,
            "A recovery block is never presented as ready: $blockedState",
        )
        assertEquals("ambiguous receipt", blockedState.reason)
        assertEquals(PrimaryAction.RECONNECT, blocked.primaryAction)

        val pending = SetupPresentationMapper.mapEnvironment(state = liveReady.copy(pendingAttemptId = "attempt-9"))
        val pendingState = pending.environmentState
        assertTrue(
            pendingState is EnvironmentState.Reconnecting,
            "An unreconciled attempt is reconnecting, not ready: $pendingState",
        )
        assertEquals("attempt-9", pendingState.operationId)
        assertEquals(PrimaryAction.RECONNECT, pending.primaryAction)
    }

    @Test
    fun testMapStatusBarRunningOperation() {
        val op = SetupOperation(
            operationId = "op-1",
            executionState = SetupOperationState.RUNNING,
        )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Running environment setup…", statusBar.leadingStatusText)
        assertNull(statusBar.failureText)
    }

    @Test
    fun testMapStatusBarReconnectingOperation() {
        val op = SetupOperation(
            operationId = "op-2",
            executionState = SetupOperationState.RECONNECTING,
        )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Reconnecting to background setup…", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarAwaitingUserAction() {
        val op = SetupOperation(
            operationId = "op-3",
            executionState = SetupOperationState.AWAITING_USER_ACTION,
        )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Terminal authorization required", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarSucceededAndCancelledOperations() {
        val opSucceeded = SetupOperation(
            operationId = "op-4",
            executionState = SetupOperationState.SUCCEEDED,
        )
        val statusBarSucceeded = SetupPresentationMapper.mapStatusBar(opSucceeded, ToolchainSetupState())
        assertEquals("Environment setup completed", statusBarSucceeded.leadingStatusText)

        val opCancelled = SetupOperation(
            operationId = "op-5",
            executionState = SetupOperationState.CANCELLED,
        )
        val statusBarCancelled = SetupPresentationMapper.mapStatusBar(opCancelled, ToolchainSetupState())
        assertEquals("Environment setup cancelled", statusBarCancelled.leadingStatusText)
    }

    @Test
    fun testMapStatusBarCheckingWithoutActiveOperation() {
        val toolchain = ToolchainSetupState(isChecking = true)
        val statusBar = SetupPresentationMapper.mapStatusBar(null, toolchain)
        assertEquals("Verifying machine environment…", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarPendingAttemptWithoutActiveOperation() {
        val toolchain = ToolchainSetupState(pendingAttemptId = "attempt-old")
        val statusBar = SetupPresentationMapper.mapStatusBar(null, toolchain)
        assertEquals("A previous setup didn't finish • Review in Recovery", statusBar.leadingStatusText)
    }

    @Test
    fun testMapStatusBarFailureExposed() {
        val op = SetupOperation(
            operationId = "op-6",
            executionState = SetupOperationState.FAILED,
            failure = "WSL command failed with exit 1",
        )
        val statusBar = SetupPresentationMapper.mapStatusBar(op, ToolchainSetupState())
        assertEquals("Environment setup failed", statusBar.leadingStatusText)
        assertEquals("WSL command failed with exit 1", statusBar.failureText)
    }

    @Test
    fun testMapEnvironmentSummaryHealthyServerWithFailedTools() {
        val toolchain = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Build Service",
                    description = "",
                    status = StepStatus.SUCCESS,
                    provenance = StepProvenance.LIVE,
                ),
                SetupStepDetail(
                    stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                    title = "Tools",
                    description = "",
                    status = StepStatus.FAILED,
                    provenance = StepProvenance.LIVE,
                ),
            ),
        )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val buildServiceItem = summary.items.first { it.label == "Build service" }
        assertEquals("Ready", buildServiceItem.value)
        assertEquals(IdeStatusSeverity.Ready, buildServiceItem.severity)

        val toolReadinessItem = summary.items.first { it.label == "Tool readiness" }
        assertEquals("Needs attention", toolReadinessItem.value)
        assertEquals(IdeStatusSeverity.Failed, toolReadinessItem.severity)
    }

    @Test
    fun testMapEnvironmentSummaryWslFailure() {
        val toolchain = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.WSL_DETECTION,
                    title = "WSL",
                    description = "",
                    status = StepStatus.FAILED,
                    provenance = StepProvenance.LIVE,
                ),
            ),
        )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val runtimeItem = summary.items.first { it.label == "Runtime" }
        assertEquals("Unavailable", runtimeItem.value)
        assertEquals(IdeStatusSeverity.Failed, runtimeItem.severity)
    }

    @Test
    fun testMapEnvironmentSummaryRunningState() {
        val toolchain = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(
                    stage = SetupStepStage.SERVER_CONNECTIVITY,
                    title = "Build Service",
                    description = "",
                    status = StepStatus.RUNNING,
                    provenance = StepProvenance.LIVE,
                ),
            ),
        )
        val summary = SetupPresentationMapper.mapEnvironmentSummary(toolchain)
        val buildServiceItem = summary.items.first { it.label == "Build service" }
        assertEquals("Checking…", buildServiceItem.value)
        assertEquals(IdeStatusSeverity.Running, buildServiceItem.severity)
    }

    @Test
    fun testMapDistroStatus() {
        val wslUnavail = DistroStatus.WslUnavailable("wsl.exe not found")
        val wslUnavailPres = SetupPresentationMapper.mapDistroStatus(wslUnavail)
        assertTrue(wslUnavailPres.detailText.contains("WSL isn't installed"))
        assertEquals(IdeStatusSeverity.Failed, wslUnavailPres.severity)
        assertFalse(wslUnavailPres.isReady)

        val noDistro = DistroStatus.NoDistro
        val noDistroPres = SetupPresentationMapper.mapDistroStatus(noDistro)
        assertTrue(noDistroPres.detailText.contains("No Linux distribution"))
        assertEquals(IdeStatusSeverity.Failed, noDistroPres.severity)
        assertFalse(noDistroPres.isReady)

        val startupFailed = DistroStatus.StartupFailed("Ubuntu-24.04", "timeout after 5s")
        val startupFailedPres = SetupPresentationMapper.mapDistroStatus(startupFailed)
        assertTrue(startupFailedPres.detailText.contains("Ubuntu-24.04 didn't start"))
        assertEquals("Retry", startupFailedPres.primaryActionLabel)
        assertEquals("Show diagnostics", startupFailedPres.secondaryActionLabel)
        assertEquals(IdeStatusSeverity.Failed, startupFailedPres.severity)
        assertFalse(startupFailedPres.isReady)

        val unsupported = DistroStatus.Unsupported("Debian", "found debian 12")
        val unsupportedPres = SetupPresentationMapper.mapDistroStatus(unsupported)
        assertTrue(unsupportedPres.detailText.contains("Debian isn't a supported Ubuntu LTS"))
        assertEquals(IdeStatusSeverity.Failed, unsupportedPres.severity)
        assertFalse(unsupportedPres.isReady)

        val noUser = DistroStatus.NoUsableUser("Ubuntu-24.04", "default user is root")
        val noUserPres = SetupPresentationMapper.mapDistroStatus(noUser)
        assertTrue(noUserPres.detailText.contains("Ubuntu-24.04 needs a user"))
        assertEquals("Open Ubuntu-24.04", noUserPres.primaryActionLabel)
        assertEquals(IdeStatusSeverity.Failed, noUserPres.severity)
        assertFalse(noUserPres.isReady)

        val usable = DistroStatus.Usable(
            SetupEnvironment("Ubuntu-24.04", 2, "ubuntu", "24.04", "testuser", "/home/testuser")
        )
        val usablePres = SetupPresentationMapper.mapDistroStatus(usable)
        assertEquals("Ubuntu-24.04 · testuser", usablePres.detailText)
        assertEquals(IdeStatusSeverity.Ready, usablePres.severity)
        assertTrue(usablePres.isReady)
    }

    @Test
    fun testMapProjectsHeaderStatus() {
        val checking = ToolchainSetupState(isChecking = true)
        val checkingPres = SetupPresentationMapper.mapProjectsHeaderStatus(checking)
        assertEquals("Checking this machine…", checkingPres.statusText)
        assertNull(checkingPres.actionLabel)
        assertFalse(checkingPres.isNeedsSetup)

        val ready = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "Packages", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.SERVER_CONNECTIVITY, title = "Server", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.SYSTEM_DIAGNOSTICS, title = "Doctor", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.REPO_SYNCHRONIZATION, title = "Repos", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.TOOLCHAIN_COMPILATION, title = "Tools", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
            ),
            lastReadyAt = 1700000000000L,
            checkedAt = 1700000000000L,
        )
        val readyPres = SetupPresentationMapper.mapProjectsHeaderStatus(ready)
        assertEquals("Machine ready", readyPres.statusText)
        assertNull(readyPres.actionLabel)
        assertFalse(readyPres.isNeedsSetup)

        val needsSetup = ToolchainSetupState(
            steps = listOf(
                SetupStepDetail(stage = SetupStepStage.WSL_DETECTION, title = "WSL", description = "", status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE),
                SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "Packages", description = "3 packages missing", status = StepStatus.FAILED, provenance = StepProvenance.LIVE),
            ),
        )
        val needsSetupPres = SetupPresentationMapper.mapProjectsHeaderStatus(needsSetup)
        assertEquals("This machine needs setup", needsSetupPres.statusText)
        assertEquals("Set up this machine", needsSetupPres.actionLabel)
        assertTrue(needsSetupPres.isNeedsSetup)
    }

    @Test
    fun testMapSystemPackagesAllStates() {
        // 1. Checking
        val checking = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "", status = StepStatus.RUNNING),
            isChecking = true,
        )
        assertEquals("Checking…", checking.detailText)
        assertNull(checking.primaryActionLabel)
        assertTrue(checking.isChecking)

        // 2. Probe failed
        val probeFailed = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "Couldn't check packages: WSL timed out", status = StepStatus.FAILED),
        )
        assertEquals("Couldn't check packages: WSL timed out", probeFailed.detailText)
        assertEquals("Retry", probeFailed.primaryActionLabel)

        // 3. Missing packages
        val missing = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "3 packages missing: git, cmake, ninja-build", status = StepStatus.FAILED),
        )
        assertEquals("3 packages missing: git, cmake, ninja-build", missing.detailText)
        assertEquals("Install packages", missing.primaryActionLabel)

        // 4. Unavailable
        val unavailable = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "foo isn't available on Ubuntu 24.04", status = StepStatus.FAILED),
        )
        assertEquals("foo isn't available on Ubuntu 24.04", unavailable.detailText)
        assertNull(unavailable.primaryActionLabel)

        // 5. Awaiting user action
        val awaitingOp = SetupOperation(
            operationId = "test_op",
            executionState = SetupOperationState.AWAITING_USER_ACTION,
        )
        val awaiting = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "", status = StepStatus.FAILED),
            activeOperation = awaitingOp,
        )
        assertEquals("Waiting for you in the terminal", awaiting.detailText)
        assertEquals("Continue setup", awaiting.primaryActionLabel)

        // 6. Verifying in handoff sheet
        val verifying = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "", status = StepStatus.FAILED),
            handoffSheetState = TerminalHandoffState.Verifying,
        )
        assertEquals("Checking installation…", verifying.detailText)
        assertNull(verifying.primaryActionLabel)
        assertTrue(verifying.isChecking)

        // 7. Partial still missing
        val partial = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "Still missing: cmake", status = StepStatus.FAILED),
            handoffSheetState = TerminalHandoffState.StillMissing(listOf("cmake"), "sudo apt-get install cmake"),
        )
        assertEquals("Still missing: cmake", partial.detailText)
        assertEquals("Install packages", partial.primaryActionLabel)

        // 8. OK (All present)
        val ok = SetupPresentationMapper.mapSystemPackages(
            step = SetupStepDetail(stage = SetupStepStage.SYSTEM_PACKAGES, title = "", description = "All prerequisite packages installed", status = StepStatus.SUCCESS),
        )
        assertEquals("All present", ok.detailText)
        assertNull(ok.primaryActionLabel)
        assertTrue(ok.isReady)
    }
}
