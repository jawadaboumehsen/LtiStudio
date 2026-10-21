/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target.di

import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationViewModel
import org.ide.lti.feature.workspace.panels.target.WorkspaceDeviceViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val workspaceTargetModule: Module = module {
    viewModel {
        WorkspaceConfigurationViewModel(
            workspaceManager = get(),
            snapshotRepository = get(),
            workspaceDeviceRepository = get(),
            saveConfigurationSnapshotUseCase = get(),
            readinessPort = getOrNull(),
        )
    }
    viewModel {
        WorkspaceDeviceViewModel(
            workspaceManager = get(),
            workspaceDeviceRepository = get(),
            snapshotRepository = get(),
            observeRunUseCase = getOrNull(),
            targetRepository = getOrNull(),
        )
    }
}
