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
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupPresentationMapperTest {
    @Test
    fun testMapStageState() {
        val detailPending =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "",
                description = "",
                status = StepStatus.PENDING,
                provenance = StepProvenance.LIVE,
            )
        assertEquals(StagePresentationState.UNKNOWN, SetupPresentationMapper.mapStageState(detailPending, false))

        val detailRestored =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.RESTORED,
            )
        assertEquals(StagePresentationState.RESTORED, SetupPresentationMapper.mapStageState(detailRestored, false))

        val detailChecking =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "",
                description = "",
                status = StepStatus.RUNNING,
                provenance = StepProvenance.LIVE,
            )
        assertEquals(StagePresentationState.CHECKING, SetupPresentationMapper.mapStageState(detailChecking, true))

        val detailReady =
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "",
                description = "",
                status = StepStatus.SUCCESS,
                provenance = StepProvenance.LIVE,
            )
        assertEquals(StagePresentationState.READY, SetupPresentationMapper.mapStageState(detailReady, false))

        val detailFailed =
            SetupStepDetail(
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
        val needsSetupState =
            ToolchainSetupState(
                checkedAt = checkedAtMs,
                steps =
                listOf(
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
        val readyTools =
            listOf(
                coreTool("lpunpack", "LP Unpack"),
                coreTool("mkfs.erofs", "Make EROFS", ToolCategory.FILESYSTEM_AND_IMAGES),
            )
        val readyState =
            ToolchainSetupState(
                checkedAt = checkedAtMs,
                lastReadyAt = checkedAtMs,
                steps =
                listOf(
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
        val failedState =
            ToolchainSetupState(
                checkedAt = checkedAtMs,
                steps =
                listOf(
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
        val unavailableState =
            ToolchainSetupState(
                steps =
                listOf(
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
        val restoredState =
            ToolchainSetupState(
                lastVerifiedTimestamp = 1699999900000L,
                checkedAt = null,
                steps =
                listOf(
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
        val stateAfterCheck =
            ToolchainSetupState(
                checkedAt = checkTime,
                lastReadyAt = null,
                steps =
                listOf(
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
        val fullyReadyState =
            ToolchainSetupState(
                checkedAt = readyTime,
                lastReadyAt = readyTime,
                steps =
                listOf(
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

    /** The live stages exactly as the check reports them, with the Toolchain stage as given. */
    private fun checkedState(toolchain: SetupStepDetail, tools: List<ToolComponentItem>, published: Set<String>) =
        ToolchainSetupState(
            checkedAt = 1700000300000L,
            steps = listOf(
                step(SetupStepStage.WSL_DETECTION),
                step(SetupStepStage.SERVER_CONNECTIVITY),
                step(SetupStepStage.SYSTEM_DIAGNOSTICS),
                step(SetupStepStage.REPO_SYNCHRONIZATION),
                toolchain,
            ),
            toolsMatrix = tools,
            publishedToolIds = published,
        )

    @Test
    fun testToolchainProblemReportedByTheCheckPreventsReady() {
        // Missing publication, a failed required-tool test and an empty inventory all fail the Toolchain stage in
        // the check; that stage is what both this screen and Projects read.
        val reported = listOf(
            "Missing published tools: mkfs.erofs",
            "Tool lpunpack verification probe failed with exit code 1",
            "adb not built",
        )
        for (problem in reported) {
            val state = checkedState(
                toolchain = step(
                    SetupStepStage.TOOLCHAIN_COMPILATION,
                    status = StepStatus.FAILED,
                    description = problem,
                ),
                tools = listOf(coreTool("lpunpack", "LP Unpack")),
                published = setOf("lpunpack"),
            )

            val presentation = SetupPresentationMapper.mapEnvironment(state)

            assertFalse(presentation.environmentState is EnvironmentState.Ready, problem)
            assertFalse(state.canLaunchWorkspace, "Projects must agree: $problem")
        }
    }

    @Test
    fun testEnvironmentAndProjectsShareOneReadyUntestedToolsIncluded() {
        // All stages green but no tool individually tested yet (a full setup does not probe each one): Projects
        // admits the workspace, so this screen must not keep offering Set up.
        val state = checkedState(
            toolchain = step(SetupStepStage.TOOLCHAIN_COMPILATION),
            tools = listOf(coreTool("lpunpack", "LP Unpack", lastTested = null)),
            published = setOf("lpunpack"),
        )

        val presentation = SetupPresentationMapper.mapEnvironment(state)

        assertTrue(state.canLaunchWorkspace)
        assertTrue(presentation.environmentState is EnvironmentState.Ready, "${presentation.environmentState}")
        assertEquals(PrimaryAction.OPEN_WORKSPACES, presentation.primaryAction)
    }

    @Test
    fun testInterruptedActiveOperationMapsToInterruptedAndReconnect() {
        val state = ToolchainSetupState()
        val interruptedOp =
            SetupOperation(
                operationId = "interrupted-op-123",
                executionState = SetupOperationState.INTERRUPTED,
                failure = "Daemon connection lost mid-flight",
            )
        val presentation =
            SetupPresentationMapper.mapEnvironment(
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
        val liveReady =
            ToolchainSetupState(
                steps =
                ToolchainSetupState.defaultSteps().map {
                    it.copy(status = StepStatus.SUCCESS, provenance = StepProvenance.LIVE)
                },
                checkedAt = 1_000L,
                lastReadyAt = 1_000L,
            )
        val blocked =
            SetupPresentationMapper.mapEnvironment(
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
    fun testMapProjectsHeaderStatus() {
        val checking = ToolchainSetupState(isChecking = true)
        val checkingPres = SetupPresentationMapper.mapProjectsHeaderStatus(checking)
        assertEquals("Checking this machine…", checkingPres.statusText)
        assertNull(checkingPres.actionLabel)
        assertFalse(checkingPres.isNeedsSetup)

        val ready =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_PACKAGES,
                        title = "Packages",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SERVER_CONNECTIVITY,
                        title = "Server",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                        title = "Doctor",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.REPO_SYNCHRONIZATION,
                        title = "Repos",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                        title = "Tools",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
                lastReadyAt = 1700000000000L,
                checkedAt = 1700000000000L,
            )
        val readyPres = SetupPresentationMapper.mapProjectsHeaderStatus(ready)
        assertEquals("Machine ready", readyPres.statusText)
        assertNull(readyPres.actionLabel)
        assertFalse(readyPres.isNeedsSetup)

        val needsSetup =
            ToolchainSetupState(
                steps =
                listOf(
                    SetupStepDetail(
                        stage = SetupStepStage.WSL_DETECTION,
                        title = "WSL",
                        description = "",
                        status = StepStatus.SUCCESS,
                        provenance = StepProvenance.LIVE,
                    ),
                    SetupStepDetail(
                        stage = SetupStepStage.SYSTEM_PACKAGES,
                        title = "Packages",
                        description = "3 packages missing",
                        status = StepStatus.FAILED,
                        provenance = StepProvenance.LIVE,
                    ),
                ),
            )
        val needsSetupPres = SetupPresentationMapper.mapProjectsHeaderStatus(needsSetup)
        assertEquals("This machine needs setup", needsSetupPres.statusText)
        assertEquals("Set up this machine", needsSetupPres.actionLabel)
        assertTrue(needsSetupPres.isNeedsSetup)
    }

    @Test
    fun testRecoveryMapping_idleState_returnsEmpty() {
        val idleState = ToolchainSetupState()
        val pres = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = idleState,
            activeOperation = null,
            attemptRecord = null,
            hasLogs = false,
        )

        assertTrue(pres.isEmpty, "Idle state must map to isEmpty=true")
        assertNull(pres.operation)
        assertNull(pres.service)
        assertNull(pres.blockedReason)
        assertFalse(pres.hasProblems)
        assertFalse(pres.hasEvidence)
    }

    @Test
    fun testRecoveryMapping_interruptedAttempt_showsOperationDetails() {
        val state = ToolchainSetupState(pendingAttemptId = "attempt-99")
        val record = SetupAttemptRecord(
            attemptId = "attempt-99",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            status = AttemptStatus.INTERRUPTED,
            terminalReason = "WSL connection dropped",
            createdAtEpochMs = 1700000000000L,
        )

        val pres = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = state,
            attemptRecord = record,
        )

        assertFalse(pres.isEmpty)
        val op = pres.operation
        assertNotNull(op)
        assertEquals("Full setup", op.kind)
        assertEquals("attempt-99", op.attemptId)
        assertEquals("Interrupted", op.state)
        assertEquals("WSL connection dropped", op.failure)
        assertNotNull(op.issuedAt)
    }

    @Test
    fun testRecoveryMapping_serviceStopped_showsBuildServiceRowState() {
        val serverStep = step(
            stage = SetupStepStage.SERVER_CONNECTIVITY,
            status = StepStatus.FAILED,
            description = "Build service didn't start: port collision",
            error = "port collision",
        )
        val state = ToolchainSetupState(
            steps = listOf(serverStep),
        )

        val pres = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = state,
        )

        assertFalse(pres.isEmpty)
        val srv = pres.service
        assertNotNull(srv)
        assertEquals("Won't start", srv.chipText)
        assertEquals("Build service didn't start: port collision", srv.detailText)
        assertEquals(IdeStatusSeverity.Failed, srv.severity)
    }

    @Test
    fun testRecoveryMapping_recoveryBlockReason_showsBlockedReason() {
        val state = ToolchainSetupState(
            recoveryBlockReason = "ambiguous receipt on attempt-12",
        )

        val pres = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = state,
        )

        assertFalse(pres.isEmpty)
        assertEquals("ambiguous receipt on attempt-12", pres.blockedReason)
    }

    @Test
    fun testRecoveryMapping_evidenceAndProblemsTabs() {
        val failedStep = step(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            status = StepStatus.FAILED,
            description = "llvm build failed",
            error = "clang error 1",
        )
        val state = ToolchainSetupState(
            steps = listOf(failedStep),
        )
        val childIntent = ChildIntentRecord(
            childIndex = 0,
            stage = "TOOLCHAIN_COMPILATION",
            actionId = "build-llvm",
            request = PersistedExecutionRequest(toolId = "llvm"),
            idempotencyKey = "idem-llvm",
            workspaceLock = "lock-1",
        )
        val record = SetupAttemptRecord(
            attemptId = "attempt-1",
            environmentKey = "Ubuntu",
            planId = "plan-1",
            planRevisionHash = "rev-1",
            planKind = "FULL_SETUP",
            orderedIntents = listOf(childIntent),
        )

        val pres = SetupPresentationMapper.mapRecovery(
            toolchainSetupState = state,
            attemptRecord = record,
        )

        assertTrue(pres.hasEvidence)
        assertEquals(1, pres.evidence.intents.size)
        assertTrue(pres.hasProblems)
        assertEquals(1, pres.problems.size)
        assertEquals("Toolchain Binaries", pres.problems.first().title)
        assertEquals("clang error 1", pres.problems.first().detail)
    }
}
