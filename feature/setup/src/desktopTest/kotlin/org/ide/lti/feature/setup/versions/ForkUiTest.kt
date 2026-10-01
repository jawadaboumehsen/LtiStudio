/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.versions.components.EditVersionPanel
import org.ide.lti.feature.setup.versions.components.TrustRepositoryDialog
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ForkUiTest {

    @Test
    fun `trust dialog displays required security warning and buttons`() = runDesktopComposeUiTest {
        var confirmed = false
        var dismissed = false
        val forkUrl = "https://github.com/myfork/erofs-tools.git"

        setContent {
            LtiTheme {
                TrustRepositoryDialog(
                    repoUrl = forkUrl,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        onNodeWithText("Trust External Repository?").assertIsDisplayed()
        onNodeWithText(
            "Building this repository runs its code on your machine with your permissions.",
        ).assertIsDisplayed()
        onNodeWithText(forkUrl).assertIsDisplayed()

        onNodeWithText("Trust and proceed").assertIsDisplayed().performClick()
        assertTrue(confirmed, "Confirm callback must be invoked on click")
    }

    @Test
    fun `edit version panel shows error states distinctly with input preserved`() = runDesktopComposeUiTest {
        val draft = ToolGroupDraft(
            group = ToolGroupId("erofs-utils"),
            repoUrl = "https://github.com/myfork/erofs-tools.git",
            ref = ToolRef.Branch("custom-branch"),
            refQuery = "custom-query",
            isAdvanced = true,
        )

        setContent {
            LtiTheme {
                EditVersionPanel(
                    draft = draft,
                    installedVersion = "v1.8.10",
                    availableRefs = RefListing(),
                    isLoadingRefs = false,
                    resolutionState = ResolutionState.Error("Remote repository unreachable"),
                    onRefSelected = {},
                    onRepoUrlChanged = {},
                    onQueryChanged = {},
                    onAdvancedToggled = {},
                    onSave = {},
                    onReviewAndBuild = {},
                    onCancel = {},
                )
            }
        }

        onNodeWithText("Remote repository unreachable").assertIsDisplayed()
        onNodeWithText("https://github.com/myfork/erofs-tools.git").assertIsDisplayed()
        onNodeWithText("custom-query").assertIsDisplayed()
    }

    @Test
    fun `edit version panel shows unsupported layout distinctly`() = runDesktopComposeUiTest {
        val draft = ToolGroupDraft(
            group = ToolGroupId("erofs-utils"),
            repoUrl = "https://github.com/myfork/erofs-tools.git",
            ref = ToolRef.Commit("0123456789abcdef0123456789abcdef01234567"),
            isAdvanced = true,
        )

        val resolved = ResolvedInput.Git(
            group = ToolGroupId("erofs-utils"),
            repoUrl = "https://github.com/myfork/erofs-tools.git",
            ref = ToolRef.Commit("0123456789abcdef0123456789abcdef01234567"),
            commit = "0123456789abcdef0123456789abcdef01234567",
            resolvedAt = 1000L,
        )

        setContent {
            LtiTheme {
                EditVersionPanel(
                    draft = draft,
                    installedVersion = "v1.8.10",
                    availableRefs = RefListing(),
                    isLoadingRefs = false,
                    resolutionState = ResolutionState.Resolved(
                        input = resolved,
                        compatibility = CompatibilityResult.Unsupported(listOf("CMakeLists.txt")),
                    ),
                    onRefSelected = {},
                    onRepoUrlChanged = {},
                    onQueryChanged = {},
                    onAdvancedToggled = {},
                    onSave = {},
                    onReviewAndBuild = {},
                    onCancel = {},
                )
            }
        }

        onNodeWithText("Unsupported layout: missing required files").assertIsDisplayed()
        onNodeWithText("Resolved commit: 0123456").assertIsDisplayed()
    }
}
