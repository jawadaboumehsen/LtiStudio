/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.di

import org.ide.lti.core.cli.DesktopShellExecutor
import org.ide.lti.core.cli.ShellExecutor
import org.ide.lti.feature.workspace.WorkspaceViewModel
import org.ide.lti.feature.workspace.panels.ai.AIAssistantViewModel
import org.ide.lti.feature.workspace.panels.editor.EditorViewModel
import org.ide.lti.feature.workspace.panels.explorer.ExplorerViewModel
import org.ide.lti.feature.workspace.panels.terminal.TerminalViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val workspaceModule = module {
    // CLI & Terminal execution services
    single<ShellExecutor> { DesktopShellExecutor() }

    // Workspace ViewModels
    viewModelOf(::WorkspaceViewModel)

    // Panel ViewModels
    viewModelOf(::AIAssistantViewModel)
    viewModelOf(::EditorViewModel)
    viewModelOf(::ExplorerViewModel)
    viewModelOf(::TerminalViewModel)
}
