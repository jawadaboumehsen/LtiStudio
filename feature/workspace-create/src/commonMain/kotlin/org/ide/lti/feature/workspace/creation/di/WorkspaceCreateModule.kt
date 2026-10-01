/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.creation.di

import org.ide.lti.feature.workspace.creation.CreateWorkspaceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val workspaceCreateModule: Module = module {
    viewModel {
        CreateWorkspaceViewModel(
            createWorkspaceUseCase = get(),
            targetRepository = get(),
            workspaceRepository = get(),
            workspaceManager = get(),
            readinessPort = getOrNull(),
        )
    }
}
