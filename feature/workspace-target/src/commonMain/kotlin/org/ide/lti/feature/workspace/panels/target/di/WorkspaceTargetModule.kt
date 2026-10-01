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

import org.ide.lti.core.domain.ports.RemoteFileUploaderPort
import org.ide.lti.feature.workspace.panels.target.WorkspaceConfigurationViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val workspaceTargetModule: Module = module {
    // Explicit: the defaulted file-picker/reader lambdas must not be resolved through Koin.
    viewModel {
        WorkspaceConfigurationViewModel(
            workspaceManager = get(),
            snapshotRepository = get(),
            targetRepository = get(),
            saveConfigurationSnapshotUseCase = get(),
            transport = getOrNull<RemoteFileUploaderPort>(),
            readinessPort = getOrNull(),
        )
    }
}
