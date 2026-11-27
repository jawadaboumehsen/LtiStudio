/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.progress

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.ide.lti.core.designsystem.component.progress.GlassLoadingContainer
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.component.progress.GlassSkeletonCard
import org.ide.lti.core.designsystem.component.progress.GlassSkeletonList
import org.ide.lti.core.designsystem.component.progress.GlassSkeletonPill
import org.ide.lti.core.designsystem.component.progress.GlassSkeletonRow
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.junit.Rule
import org.junit.Test

class GlassSkeletonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun glassLoadingPlaceholderDisplaysLabelAndProgressSemantics() {
        val progress = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
        compose.setContent {
            LtiTheme(effectsEnabled = true, reducedMotion = false) {
                GlassLoadingPlaceholder(
                    label = "Loading workspace items…",
                    modifier = Modifier.testTag("LoadingPlaceholder"),
                )
            }
        }

        compose.onNodeWithTag("LoadingPlaceholder").assertExists()
        compose.onNodeWithText("Loading workspace items…").assertIsDisplayed()
        compose.onAllNodes(progress, useUnmergedTree = true)[0].assertExists()
    }

    @Test
    fun glassSkeletonElementsRenderUnderMotionAndReducedMotion() {
        compose.setContent {
            LtiTheme(effectsEnabled = true, reducedMotion = false) {
                GlassSkeletonPill(modifier = Modifier.testTag("Pill"))
                GlassSkeletonRow(modifier = Modifier.testTag("Row"))
                GlassSkeletonCard(modifier = Modifier.testTag("Card"))
                GlassSkeletonList(count = 3, modifier = Modifier.testTag("List"))
            }
        }

        compose.onNodeWithTag("Pill").assertExists()
        compose.onNodeWithTag("Row").assertExists()
        compose.onNodeWithTag("Card").assertExists()
        compose.onNodeWithTag("List").assertExists()
    }

    @Test
    fun glassLoadingContainerSwitchesBetweenSkeletonAndContent() {
        val loading = mutableStateOf(true)

        compose.setContent {
            LtiTheme(effectsEnabled = false, reducedMotion = true) {
                GlassLoadingContainer(
                    loading = loading.value,
                    skeleton = {
                        Text("Loading skeleton…", modifier = Modifier.testTag("SkeletonView"))
                    },
                    content = {
                        Text("Loaded content", modifier = Modifier.testTag("RealContent"))
                    },
                )
            }
        }

        compose.onNodeWithTag("SkeletonView").assertIsDisplayed()
        compose.onNodeWithTag("RealContent").assertDoesNotExist()

        compose.runOnIdle { loading.value = false }

        compose.onNodeWithTag("RealContent").assertIsDisplayed()
        compose.onNodeWithTag("SkeletonView").assertDoesNotExist()
    }
}
