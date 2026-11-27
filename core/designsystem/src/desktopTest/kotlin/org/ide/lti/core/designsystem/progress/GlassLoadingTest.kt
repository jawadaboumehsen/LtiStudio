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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GlassLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingButtonShowsProgressAndPreventsDuplicateActions() {
        val loading = mutableStateOf(true)
        var clicks = 0
        val progress = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )
        compose.setContent {
            LtiTheme(effectsEnabled = false, reducedMotion = true) {
                GlassButton(onClick = { clicks++ }, loading = loading.value, modifier = Modifier.testTag("Submit")) {
                    Text("Submit")
                }
            }
        }

        compose.onNodeWithTag("Submit").assertIsNotEnabled()
        compose.onNode(progress, useUnmergedTree = true).assertExists()
        compose.runOnIdle { loading.value = false }
        compose.onNode(progress, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("Submit").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
