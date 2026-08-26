/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.GlassLayout
import org.ide.lti.core.designsystem.component.rememberLtiLayoutState
import org.ide.lti.feature.workspace.panels.ai.AIAssistantPanel
import org.ide.lti.feature.workspace.panels.editor.EditorPanel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerPanel
import org.ide.lti.feature.workspace.panels.terminal.TerminalPanel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main workspace screen with complete glass theme integration.
 *
 * Features:
 * - Glass toolbar with navigation controls
 * - Glass file explorer panel (left)
 * - Glass code editor (center)
 * - Glass AI assistant panel (right)
 * - Glass terminal panel (bottom)
 * - Glass status bar
 *
 * All components use the glass morphism effects provided by LtiTheme.
 */
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkspaceViewModel = koinViewModel(),
) {
    val ltiLayoutState = rememberLtiLayoutState()

    GlassLayout(
        state = ltiLayoutState,
        modifier = modifier,
        leftPanel = { ExplorerPanel() },
        mainContent = { EditorPanel() },
        bottomPanel = { TerminalPanel() },
        rightPanel = { AIAssistantPanel() },
    )
}
