package org.ide.lti.feature.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.ide.lti.core.domain.setup.SetupStepDetail
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepProvenance
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolCategory
import org.ide.lti.core.domain.setup.ToolchainSetupState

class SetupPresentationMapperTest {

    @Test
    fun testMapStageState() {
        val detailPending = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.PENDING,
            provenance = StepProvenance.LIVE
        )
        assertEquals(StagePresentationState.UNKNOWN, SetupPresentationMapper.mapStageState(detailPending, false))

        val detailRestored = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.SUCCESS,
            provenance = StepProvenance.RESTORED
        )
        assertEquals(StagePresentationState.RESTORED, SetupPresentationMapper.mapStageState(detailRestored, false))

        val detailChecking = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.RUNNING,
            provenance = StepProvenance.LIVE
        )
        assertEquals(StagePresentationState.CHECKING, SetupPresentationMapper.mapStageState(detailChecking, true))

        val detailReady = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.SUCCESS,
            provenance = StepProvenance.LIVE
        )
        assertEquals(StagePresentationState.READY, SetupPresentationMapper.mapStageState(detailReady, false))

        val detailFailed = SetupStepDetail(
            stage = SetupStepStage.WSL_DETECTION,
            title = "",
            description = "",
            status = StepStatus.FAILED,
            provenance = StepProvenance.LIVE
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
            )
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
            )
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
            )
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
            )
        )
        val mappedRestored = SetupPresentationMapper.mapEnvironmentState(restoredState)
        assertTrue(mappedRestored is EnvironmentState.Restored)
        assertNull(
            mappedRestored.checkedAt,
            "Restored state must have null checkedAt (no live check in this session)"
        )
        assertEquals(1699999900000L, mappedRestored.lastReadyAt)
        assertEquals(
            PrimaryAction.CHECK,
            SetupPresentationMapper.mapAction(mappedRestored),
            "Restored state must offer Check, never live-ready"
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
            )
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateAfterCheck)
        assertEquals(checkTime, presentation.checkedAt)
        assertNull(
            presentation.lastVerifiedAt,
            "lastReadyAt / lastVerifiedAt must remain null when environment needs setup"
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
            publishedToolIds = setOf("lpunpack")
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
            publishedToolIds = setOf("lpunpack"), // mkfs.erofs missing!
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateMissingPub)
        assertFalse(
            presentation.environmentState is EnvironmentState.Ready,
            "Missing publication must prevent Ready state"
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
            "Failed execution probe must prevent Ready state"
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
            toolsMatrix = emptyList(), // Empty inventory!
            publishedToolIds = emptySet(),
        )
        val presentation = SetupPresentationMapper.mapEnvironment(stateEmptyTools)
        assertFalse(
            presentation.environmentState is EnvironmentState.Ready,
            "Empty tool inventory must never become Ready"
        )
    }
}
