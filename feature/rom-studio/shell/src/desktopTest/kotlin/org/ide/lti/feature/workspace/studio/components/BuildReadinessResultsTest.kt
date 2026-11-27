/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.pipeline.PipelineStageSequence
import org.ide.lti.core.domain.pipeline.StagePreview
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.stages.VintfCheck
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.workspace.studio.PreflightUiState
import org.ide.lti.feature.workspace.studio.ReadinessStageLink
import org.ide.lti.feature.workspace.studio.readinessStageLink
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildReadinessResultsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun android10ShowsWhichVendorSkuBlocksTheBuildAndWhereToReviewIt() {
        val output = """
            @@@ android 10
            @@@ first-api 29
            @@@ mapping
            29.0.cil
            @@@ vendor-sepolicy
            29.0
            @@@ kernel ok 4.14.180
            @@@ result carrier|- 0
            @@@ result retail|- 65
            ERROR: Required HAL android.hardware.example@1.0::IExample/default is missing.
            @@@ done 2
        """.trimIndent()
        val findings = VintfCheck.refusals(output).map {
            PreflightFinding(it.substringBefore(':'), it.substringAfter(": "))
        }
        val stages = listOf(
            StagePreview(StageId.WORK_TREE_ASSEMBLY, null),
            StagePreview(StageId.BUILD_FLASHABLE_ZIP, "Build settings changed"),
        )
        val opened = mutableListOf<Pair<StageId, String>>()

        show(
            PreflightUiState(
                findings = findings,
                vintf = VintfCheck.assess(output),
                next = NextBuildFacts(null, null, stages, null),
            ),
            onOpen = { stage, objectId -> opened += stage to objectId },
        )

        compose.onNodeWithText("Blocked by 1 finding").assertExists()
        compose.onNodeWithText("IExample/default", substring = true).assertExists()
        compose.onNodeWithText("Vendor SKU carrier · ODM SKU default · Compatible").assertExists()
        compose.onNodeWithText("Vendor SKU retail · ODM SKU default · Incompatible").assertExists()
        compose.onNodeWithText("Assemble · Reuse cached output").assertExists()
        compose.onNodeWithText("Build & sign · Run — Build settings changed").assertExists()
        compose.onNodeWithText("Review firmware baseline").performClick()
        compose.runOnIdle { assertEquals(listOf(StageId.FIRMWARE_ACQUISITION to "firmware-baseline"), opened) }
    }

    @Test
    fun android14LinksMissingOdmAndApexEvidenceToTheirEditors() {
        val assessment = VintfCheck.assess(
            """
            @@@ android 14
            @@@ first-api 34
            @@@ mapping
            34.0.cil
            @@@ vendor-sepolicy
            34.0
            @@@ kernel ok 5.15.148
            @@@ missing partition|The selected /odm partition has no readable VINTF input directory.
            @@@ missing apex|Could not read VINTF fragments from APEX system/system/apex/com.android.test.apex.
            @@@ result global|wifi 0
            @@@ result global|cellular 0
            @@@ done 2
            """.trimIndent(),
        )
        val opened = mutableListOf<Pair<StageId, String>>()
        show(
            PreflightUiState(findings = emptyList(), vintf = assessment),
            onOpen = { stage, id ->
                opened += stage to id
            },
        )

        compose.onNodeWithText("Checks incomplete").assertExists()
        compose.onNodeWithText("Android 14 · Vendor first API level 34").assertExists()
        compose.onNodeWithText("Vendor SKU global · ODM SKU cellular · Compatible · Provisional").assertExists()
        compose.onNodeWithText("system/system/apex/com.android.test.apex", substring = true).assertExists()
        compose.onNodeWithText("Review extracted partitions").performClick()
        compose.onNodeWithText("Inspect firmware files").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    StageId.FIRMWARE_EXTRACTION to "dynamic-partitions",
                    StageId.WORK_TREE_ASSEMBLY to "image-browser",
                ),
                opened,
            )
        }
    }

    @Test
    fun missingCheckerLinksToSetup() {
        var setupOpened = false
        show(
            PreflightUiState(findings = emptyList(), vintf = VintfCheck.assess("@@@ tool-missing")),
            onOpenSetup = { setupOpened = true },
        )
        compose.onNodeWithText("VINTF · Not run").assertExists()
        compose.onNodeWithText("Open Setup for vintf-tools").performClick()
        compose.runOnIdle { assertTrue(setupOpened) }
    }

    @Test
    fun lockedAssemblyEvidenceExplainsAndOpensTheChecksumPrerequisite() {
        val report = ValidationReport(
            errors = listOf(
                ValidationError(
                    StageId.FIRMWARE_ACQUISITION,
                    "integrity",
                    "firmware.sha256",
                    "CHECKSUM_CONFLICT",
                    Severity.ERROR,
                    "Choose checksum or unavailable",
                ),
            ),
        )
        val assessment = VintfCheck.assess(
            "@@@ missing assembly|The assembled /system partition is missing. Run Assemble.",
        )
        val opened = mutableListOf<Pair<StageId, String>>()
        show(
            PreflightUiState(findings = emptyList(), vintf = assessment),
            onOpen = { stage, id -> opened += stage to id },
            resolveLink = { readinessStageLink(it, PipelineStageSequence.ORDER, report) },
        )

        compose.onAllNodesWithText("Assemble is locked until Acquire firmware is configured.")[0].assertExists()
        compose.onAllNodesWithText("Fix Acquire firmware first")[0].performClick()
        compose.runOnIdle { assertEquals(listOf(StageId.FIRMWARE_ACQUISITION to "integrity"), opened) }
    }

    @Test
    fun placeholdersGiveWayToEachResultAsItArrives() {
        val state = mutableStateOf(PreflightUiState(checking = true, readingNext = true))
        compose.setContent {
            LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) {
                Column {
                    BuildReadinessResults(state.value, { _, _ -> }, {}, onRetry = {}, onApply = {})
                }
            }
        }

        compose.onNodeWithTag("PreflightPlanLoading").assertExists()
        compose.onNodeWithTag("PreflightChecksLoading").assertExists()
        compose.onNodeWithTag("PreflightVintfStatus").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                readingNext = false,
                next = NextBuildFacts(null, null, listOf(StagePreview(StageId.WORK_TREE_ASSEMBLY, null)), null),
            )
        }
        compose.onNodeWithTag("PreflightPlanLoading").assertDoesNotExist()
        compose.onNodeWithText("Assemble · Reuse cached output").assertExists()
        compose.onNodeWithTag("PreflightChecksLoading").assertExists()

        compose.runOnIdle {
            state.value = state.value.copy(
                checking = false,
                findings = emptyList(),
                vintf = VintfAssessment(VintfStatus.COMPATIBLE),
            )
        }
        compose.onNodeWithTag("PreflightChecksLoading").assertDoesNotExist()
        compose.onNodeWithText("VINTF · Compatible").assertExists()
    }

    private fun show(
        state: PreflightUiState,
        onOpen: (StageId, String) -> Unit = { _, _ -> },
        onOpenSetup: () -> Unit = {},
        resolveLink: (PreflightFix.Open) -> ReadinessStageLink = { ReadinessStageLink(it) },
    ) {
        compose.setContent {
            LtiTheme(appTheme = AppTheme.Dark, effectsEnabled = false) {
                Column {
                    BuildReadinessResults(state, onOpen, onOpenSetup, onRetry = {}, onApply = {}, resolveLink)
                }
            }
        }
    }
}
