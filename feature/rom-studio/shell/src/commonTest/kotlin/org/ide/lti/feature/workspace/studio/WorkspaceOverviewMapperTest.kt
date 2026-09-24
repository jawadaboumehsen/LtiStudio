/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.ide.lti.core.model.workspace.SigningPolicy
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceOverviewMapperTest {

    private val testWorkspace = Workspace(
        id = "ws-nubia-01",
        name = "Nubia Z60 Ultra ROM",
        path = "/workspaces/nubia-z60",
        targetBinding = TargetBinding(profileId = "NX729J-GL", profileRevision = 3),
    )

    private val testTargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT.copy(
        id = "NX729J",
        name = "REDMAGIC 9 Pro",
        codename = "NX729J",
        revision = 3,
        activeSlotSuffix = "_a",
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "NX729J_V1.0",
                    buildId = "V1.0",
                    androidVersion = "15",
                ),
            ),
        ),
    )

    private fun createDraft(
        sourceConfigured: Boolean = true,
        debloatEnabled: Boolean = false,
        customizationEnabled: Boolean = false,
    ): WorkspacePipelineDraft {
        val baseDraft = WorkspacePipelineDraft.create(
            workspaceId = testWorkspace.id,
            baseSnapshot = if (sourceConfigured) {
                val removals = if (debloatEnabled) {
                    listOf("com.google.android.apps.tachyon")
                } else {
                    emptyList()
                }
                val patches = if (customizationEnabled) {
                    listOf("org.ide.lti.patch.core")
                } else {
                    emptyList()
                }
                ConfigurationSnapshot(
                    id = "snap-01",
                    workspaceId = testWorkspace.id,
                    profileRevision = 3,
                    acquisition = AcquisitionSettings(
                        mode = AcquisitionMode.DOWNLOAD,
                        region = TargetRegion.GLOBAL,
                        firmware = TargetFirmware(
                            version = "NX729J_V1.0",
                            buildId = "V1.0",
                            androidVersion = "15",
                        ),
                    ),
                    extraction = ExtractionSettings(slot = "_a"),
                    assembly = AssemblySettings(buildType = "user"),
                    debloat = DebloatSettings(
                        enabled = debloatEnabled,
                        removeSelectors = removals,
                    ),
                    customization = CustomizationSettings(
                        enabledPackages = patches,
                    ),
                    build = BuildSettings(
                        signing = SigningPolicy(
                            signImages = true,
                            signPackage = true,
                            avbKeyRef = "keys/avb.pem",
                        ),
                    ),
                    release = ReleaseSettings(),
                    publish = PublishSettings(provider = PublishProvider.NONE),
                    createdAt = Instant.fromEpochMilliseconds(1740000000000L),
                    digest = "digest-01",
                )
            } else {
                null
            },
            target = testTargetDevice,
        )
        return if (sourceConfigured) {
            baseDraft
        } else {
            baseDraft.copy(
                acquisition = AcquisitionSettings(
                    mode = AcquisitionMode.DOWNLOAD,
                    region = TargetRegion.GLOBAL,
                    firmware = TargetFirmware(version = "", buildId = "", androidVersion = ""),
                ),
                dirtyPaths = emptySet(),
            )
        }
    }

    @Test
    fun testWorkspaceTitleAndSubtitle_populatedWorkspace() {
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            hasExplicitValidSource = true,
            signingVerified = true,
        )

        assertEquals("Nubia Z60 Ultra ROM", state.workspaceTitle)
        assertTrue(state.subtitle.contains("REDMAGIC 9 Pro"))
    }

    @Test
    fun testWorkspaceTitleAndSubtitle_emptyWorkspaceNoBinding() {
        val state = WorkspaceOverviewMapper.map(
            workspace = null,
            targetDevice = null,
            draft = null,
            hasExplicitValidSource = false,
        )

        assertEquals("", state.workspaceTitle)
        assertEquals("No target profile bound", state.subtitle)
    }

    @Test
    fun testTargetSummary_allAvailableValues() {
        val draft = createDraft()
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = draft,
            hasExplicitValidSource = true,
            signingVerified = true,
        )

        val summary = state.targetSummary
        assertIs<DisplayValue.Available>(summary.revision)
        assertEquals("rev 3", summary.revision.text)

        assertIs<DisplayValue.Available>(summary.binding)
        assertEquals("NX729J-GL", summary.binding.text)

        assertIs<DisplayValue.Available>(summary.deviceId)
        assertEquals("NX729J", summary.deviceId.text)

        assertIs<DisplayValue.Available>(summary.androidTarget)
        assertEquals("Android 15", summary.androidTarget.text)

        assertIs<DisplayValue.Available>(summary.partitionSlot)
        assertEquals("_a", summary.partitionSlot.text)

        assertIs<DisplayValue.Available>(summary.buildFlavor)
        assertEquals("user", summary.buildFlavor.text)
    }

    @Test
    fun testTargetSummary_allUnavailableValues() {
        val state = WorkspaceOverviewMapper.map(
            workspace = null,
            targetDevice = null,
            draft = null,
            hasExplicitValidSource = false,
        )

        val summary = state.targetSummary
        assertIs<DisplayValue.Unavailable>(summary.revision)
        assertEquals("No revision", summary.revision.reason)

        assertIs<DisplayValue.Unavailable>(summary.binding)
        assertEquals("No target bound", summary.binding.reason)

        assertIs<DisplayValue.Unavailable>(summary.deviceId)
        assertEquals("No device ID", summary.deviceId.reason)

        assertIs<DisplayValue.Unavailable>(summary.androidTarget)
        assertEquals("No Android target", summary.androidTarget.reason)

        assertIs<DisplayValue.Unavailable>(summary.partitionSlot)
        assertEquals("No partition slot", summary.partitionSlot.reason)

        assertIs<DisplayValue.Unavailable>(summary.buildFlavor)
        assertEquals("No build flavor", summary.buildFlavor.reason)
    }

    @Test
    fun testReadinessItems_exactFiveItemsInContractOrder() {
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            hasExplicitValidSource = true,
            signingVerified = true,
        )

        assertEquals(5, state.readinessItems.size)
        assertEquals(ReadinessId.SOURCE, state.readinessItems[0].id)
        assertEquals(ReadinessId.DEBLOAT, state.readinessItems[1].id)
        assertEquals(ReadinessId.PATCHES, state.readinessItems[2].id)
        assertEquals(ReadinessId.SIGNING, state.readinessItems[3].id)
        assertEquals(ReadinessId.PUBLICATION, state.readinessItems[4].id)
    }

    @Test
    fun testReadinessItems_reviewCountMatchesItemsRequiringReview() {
        val draft = createDraft()
        // SOURCE blocking -> reviewRequired = true
        // SIGNING warning -> reviewRequired = true
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = draft,
            hasExplicitValidSource = false,
            signingVerified = false,
        )

        assertEquals(state.readinessItems.count { it.reviewRequired }, state.reviewCount)
        assertTrue(state.reviewCount >= 2)
    }

    @Test
    fun testReadinessItems_missingSourceIsBlockingAndSetsNextAction() {
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(sourceConfigured = false),
            hasExplicitValidSource = false,
            signingVerified = false,
        )

        val sourceItem = state.readinessItems[0]
        assertEquals(ReadinessId.SOURCE, sourceItem.id)
        assertEquals(ReadinessSeverity.Blocking, sourceItem.severity)
        assertTrue(sourceItem.reviewRequired)
        assertEquals(
            WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
            state.nextAction,
        )
    }

    @Test
    fun testReadinessItems_missingTargetBindingSetsNextActionWhenSourceReady() {
        val workspaceNoBinding = testWorkspace.copy(targetBinding = null)
        val state = WorkspaceOverviewMapper.map(
            workspace = workspaceNoBinding,
            targetDevice = null,
            draft = createDraft(),
            hasExplicitValidSource = true,
            signingVerified = true,
        )

        assertEquals(
            WorkspaceActionUi.OpenWorkspaceSection(WorkspaceSection.TargetProfile),
            state.nextAction,
        )
    }

    @Test
    fun testReadinessItems_blockingDebloatErrorPrioritizedOverSigningWarning() {
        val validationReport = ValidationReport(
            errors = listOf(
                ValidationError(
                    stageId = StageId.DEBLOAT,
                    objectId = null,
                    fieldPath = "debloat.removeSelectors",
                    code = "INVALID_SELECTOR",
                    severity = Severity.ERROR,
                    message = "Invalid debloat removal selector syntax",
                ),
            ),
        )

        // Warning
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            validationReport = validationReport,
            hasExplicitValidSource = true,
            signingVerified = false,
        )

        val debloatItem = state.readinessItems[1]
        assertEquals(ReadinessSeverity.Blocking, debloatItem.severity)
        assertTrue(debloatItem.reviewRequired)

        val signingItem = state.readinessItems[3]
        assertEquals(ReadinessSeverity.Warning, signingItem.severity)
        assertTrue(signingItem.reviewRequired)

        assertEquals(
            WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
            state.nextAction,
        )
    }

    @Test
    fun testReadinessItems_signingWarningSetsNextActionWhenDebloatAndPatchesAreNeutral() {
        // Warning
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(debloatEnabled = false, customizationEnabled = false),
            hasExplicitValidSource = true,
            signingVerified = false,
        )

        val signingItem = state.readinessItems[3]
        assertEquals(ReadinessSeverity.Warning, signingItem.severity)
        assertTrue(signingItem.reviewRequired)
        assertEquals(
            WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP),
            state.nextAction,
        )
    }

    @Test
    fun testReadinessItems_allReadyOrNeutralEnablesBuildNextAction() {
        val state = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(debloatEnabled = true, customizationEnabled = false),
            hasExplicitValidSource = true,
            signingVerified = true,
        )

        assertEquals(0, state.reviewCount)
        assertEquals(
            WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP),
            state.nextAction,
        )
    }

    @Test
    fun testBuildStateAndActivityMapping() {
        // Not started
        val notStartedState = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            runs = emptyList(),
            hasExplicitValidSource = true,
            signingVerified = true,
        )
        assertEquals(BuildStateUi.NotStarted, notStartedState.buildState)
        assertTrue(notStartedState.recentActivity.isEmpty())

        // Active run
        val activeRun = BuildRun(
            id = "run-001",
            workspaceId = testWorkspace.id,
            snapshotId = "snap-01",
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochMilliseconds(1740000000000L),
            stages = listOf(
                StageOutcome(
                    stageId = StageId.WORK_TREE_ASSEMBLY,
                    state = StageState.RUNNING,
                ),
            ),
        )
        val activeState = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            runs = listOf(activeRun),
            hasExplicitValidSource = true,
            signingVerified = true,
        )
        assertIs<BuildStateUi.InProgress>(activeState.buildState)
        assertEquals("run-001", activeState.buildState.runId)
        assertEquals(1, activeState.recentActivity.size)
        assertEquals("run-001", activeState.recentActivity[0].id)

        // Succeeded run
        val succeededRun = activeRun.copy(
            state = RunState.SUCCEEDED,
            endedAt = Instant.fromEpochMilliseconds(1740000500000L),
        )
        val succeededState = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            runs = listOf(succeededRun),
            hasExplicitValidSource = true,
            signingVerified = true,
        )
        assertIs<BuildStateUi.Succeeded>(succeededState.buildState)
        assertEquals("run-001", succeededState.buildState.runId)

        // Failed run
        val failedRun = activeRun.copy(
            state = RunState.FAILED,
            endedAt = Instant.fromEpochMilliseconds(1740000500000L),
            stages = listOf(
                StageOutcome(
                    stageId = StageId.BUILD_FLASHABLE_ZIP,
                    state = StageState.FAILED,
                    message = "Signing key rejected",
                ),
            ),
        )
        val failedState = WorkspaceOverviewMapper.map(
            workspace = testWorkspace,
            targetDevice = testTargetDevice,
            draft = createDraft(),
            runs = listOf(failedRun),
            hasExplicitValidSource = true,
            signingVerified = true,
        )
        assertIs<BuildStateUi.Failed>(failedState.buildState)
        assertEquals("Signing key rejected", failedState.buildState.errorMessage)
    }
}
