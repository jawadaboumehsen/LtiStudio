/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.run.di

import org.ide.lti.feature.workspace.panels.run.BuildRunViewModel
import org.ide.lti.feature.workspace.panels.run.RunHistoryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

public val workspaceRunModule: Module = module {
    viewModelOf(::RunHistoryViewModel)
    viewModel {
        BuildRunViewModel(
            workspaceManager = get(),
            startPipelineRunUseCase = get(),
            observeRunUseCase = get(),
            cancelRunUseCase = getOrNull(),
            reattachRunUseCase = getOrNull(),
            runRepository = getOrNull(),
            readinessPort = getOrNull(),
        )
    }
}
