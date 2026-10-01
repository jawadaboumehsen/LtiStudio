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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.run.StageId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceOverviewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testRequiredSemanticInventoryIsRendered() {
        val testState = createTestOverviewState()

        composeTestRule.setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
                    WorkspaceOverview(
                        state = testState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("WorkspaceOverview").assertExists()
        composeTestRule.onNodeWithTag("OverviewHeader").assertExists()
        composeTestRule.onNodeWithTag("TargetProfileCard").assertExists()
        composeTestRule.onNodeWithTag("ReadinessMatrix").assertExists()
        composeTestRule.onNodeWithTag("OverviewCtaBar").assertExists()
        composeTestRule.onNodeWithTag("RecentActivitySection").assertExists()
    }

    @Test
    fun testReadinessItemsRenderInStrictOrder() {
        val testState = createTestOverviewState()

        composeTestRule.setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
                    WorkspaceOverview(
                        state = testState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("ReadinessRow_SOURCE").assertExists()
        composeTestRule.onNodeWithTag("ReadinessRow_DEBLOAT").assertExists()
        composeTestRule.onNodeWithTag("ReadinessRow_PATCHES").assertExists()
        composeTestRule.onNodeWithTag("ReadinessRow_SIGNING").assertExists()
        composeTestRule.onNodeWithTag("ReadinessRow_PUBLICATION").assertExists()
    }

    @Test
    fun testActionDispatchWiring() {
        val testState = createTestOverviewState()
        var dispatchedAction: WorkspaceActionUi? = null
        var reloadCalled = false
        var editProfileCalled = false
        var openHistoryCalled = false

        composeTestRule.setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                Box(Modifier.size(GlassDimens.ReferenceWindowWidth, GlassDimens.ReferenceWindowHeight)) {
                    WorkspaceOverview(
                        state = testState,
                        modifier = Modifier.fillMaxSize(),
                        onAction = { dispatchedAction = it },
                        onReload = { reloadCalled = true },
                        onEditProfile = { editProfileCalled = true },
                        onOpenHistory = { openHistoryCalled = true },
                    )
                }
            }
        }

        // Test reload click
        composeTestRule.onNodeWithTag("ReloadStateButton").performClick()
        assertTrue(reloadCalled)

        // Test readiness row action click
        composeTestRule.onNodeWithTag("ReadinessAction_SOURCE").performClick()
        assertEquals(WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION), dispatchedAction)

        // Test CTA button click
        composeTestRule.onNodeWithTag("OverviewCtaButton").performClick()
        assertEquals(WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION), dispatchedAction)
    }

    @Test
    fun testMultiThemeParity() {
        val testState = createTestOverviewState()
        var currentTheme by mutableStateOf<AppTheme>(AppTheme.Light)

        composeTestRule.setContent {
            LtiTheme(appTheme = currentTheme) {
                Box(Modifier.fillMaxSize()) {
                    WorkspaceOverview(
                        state = testState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("WorkspaceOverview").assertIsDisplayed()

        currentTheme = AppTheme.Dark
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("WorkspaceOverview").assertIsDisplayed()

        currentTheme = AppTheme.Blue
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("WorkspaceOverview").assertIsDisplayed()
    }

    private fun createTestOverviewState(): WorkspaceOverviewUiState {
        val items = listOf(
            ReadinessItemUi(
                id = ReadinessId.SOURCE,
                title = "Source firmware",
                contextLabel = "Pipeline step 01",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Needs selection",
                description = "No source package or baseline zip assigned.",
                action = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
                reviewRequired = true,
            ),
            ReadinessItemUi(
                id = ReadinessId.DEBLOAT,
                title = "Debloat",
                contextLabel = "Pipeline step 04",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No removals configured",
                description = "Using stock package list with default keep rules.",
                action = WorkspaceActionUi.OpenStage(StageId.DEBLOAT),
                reviewRequired = false,
            ),
            ReadinessItemUi(
                id = ReadinessId.PATCHES,
                title = "Patches",
                contextLabel = "Pipeline step 05",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "No modules enabled",
                description = "0 of 3 available patch modules selected.",
                action = WorkspaceActionUi.OpenStage(StageId.MODULE_APPLICATION),
                reviewRequired = false,
            ),
            ReadinessItemUi(
                id = ReadinessId.SIGNING,
                title = "Signing key",
                contextLabel = "Security context",
                severity = ReadinessSeverity.Warning,
                statusLabel = "Key required",
                description = "Release and AVB keypair not verified for production.",
                action = WorkspaceActionUi.OpenStage(StageId.BUILD_FLASHABLE_ZIP),
                reviewRequired = true,
            ),
            ReadinessItemUi(
                id = ReadinessId.PUBLICATION,
                title = "Publication",
                contextLabel = "Pipeline step 08",
                severity = ReadinessSeverity.Neutral,
                statusLabel = "Optional until build completes",
                description = "Release target example-org/rom-releases.",
                action = WorkspaceActionUi.OpenStage(StageId.PUBLISH_RELEASE),
                reviewRequired = false,
            ),
        )

        return WorkspaceOverviewUiState(
            workspaceTitle = "Your ROM workspace",
            subtitle = "Configure the next build for PQ84P01.",
            targetSummary = TargetSummaryUi(
                revision = DisplayValue.Available("4"),
                binding = DisplayValue.Available("Bound to this workspace"),
                deviceId = DisplayValue.Available("PQ84P01"),
                androidTarget = DisplayValue.Available("14 (API 34)"),
                partitionSlot = DisplayValue.Available("A / B (Seamless)"),
                buildFlavor = DisplayValue.Available("userdebug"),
            ),
            readinessItems = items,
            reviewCount = 2,
            nextAction = WorkspaceActionUi.OpenStage(StageId.FIRMWARE_ACQUISITION),
            buildState = BuildStateUi.NotStarted,
            recentActivity = emptyList(),
        )
    }
}
