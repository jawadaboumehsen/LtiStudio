/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.usecase.publish.PublishPreflight
import org.ide.lti.core.domain.usecase.publish.ReleaseFiles
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.PublishSettings
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertSame

class PublishConfirmationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editingDestinationDismissesConfirmationAndRequiresNewReview() {
        val first = review("/tmp/first")
        val second = review("/tmp/second")
        val settings = mutableStateOf(first.settings)
        val state = mutableStateOf(PublishUiState(review = first))
        var submitted: PublishReview? = null

        compose.setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                PublishEditor(
                    settings = settings.value,
                    target = DefaultTargetCatalog.PQ84P01_DEFAULT,
                    onChange = {},
                    selectedObjectId = "publish-review-and-recovery",
                    state = state.value,
                    onPublish = { submitted = it },
                )
            }
        }
        compose.onNodeWithTag("PublishButton").performClick()
        compose.onNodeWithText("Publish this release?").assertExists()

        compose.runOnIdle { settings.value = second.settings }
        compose.onNodeWithText("Publish this release?").assertDoesNotExist()
        compose.onNodeWithTag("PublishButton").assertIsNotEnabled()
        compose.runOnIdle { assertSame(null, submitted) }

        compose.runOnIdle { state.value = PublishUiState(review = second) }
        compose.onNodeWithTag("PublishButton").performClick()
        compose.onNodeWithText("/tmp/second", substring = true).assertExists()
        compose.onNode(hasText("Publish") and hasClickAction()).performClick()
        compose.runOnIdle { assertSame(second, submitted) }
    }

    private fun review(destination: String): PublishReview = PublishReview(
        PublishSettings(localExportFolder = destination),
        PublishPreflight(ReleaseFiles("release.zip", "hash", 3), emptyList(), destination, null),
    )
}
