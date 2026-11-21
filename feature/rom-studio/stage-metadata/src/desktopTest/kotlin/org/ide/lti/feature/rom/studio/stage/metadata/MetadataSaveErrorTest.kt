/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.metadata

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.ReleaseSettings
import org.junit.Rule
import org.junit.Test

class MetadataSaveErrorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun releaseEditorShowsAutomaticSaveFailure() {
        compose.setContent {
            LtiTheme(appTheme = AppTheme.Dark) {
                MetadataEditor(
                    settings = ReleaseSettings(),
                    target = DefaultTargetCatalog.PQ84P01_DEFAULT,
                    onChange = {},
                    saveError = "The workspace is busy.",
                )
            }
        }
        compose.onNodeWithText("Changes could not be saved. The workspace is busy.").assertExists()
    }
}
