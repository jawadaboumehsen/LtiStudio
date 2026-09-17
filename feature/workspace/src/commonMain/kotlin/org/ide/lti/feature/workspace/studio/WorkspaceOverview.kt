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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/**
 * Workspace Overview pane shown when no stage editor is active.
 *
 * Receives a fully resolved [WorkspaceOverviewUiState] produced by [WorkspaceOverviewMapper]
 * and owned by [StudioViewModel]. The composable is intentionally stateless and reads only
 * from the provided state object.
 */
@Composable
public fun WorkspaceOverview(state: WorkspaceOverviewUiState, modifier: Modifier = Modifier) {
    val colors = GlassTheme.ideShellColors
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium)
            .testTag("WorkspaceOverview"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.workspaceTitle.ifBlank { "Workspace Overview" },
            fontFamily = ideFontFamily(),
            color = colors.textMain,
        )
    }
}
