/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.di

import org.ide.lti.feature.setup.SetupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val setupModule = module {
    viewModel {
        SetupViewModel(
            getRecentWorkspacesUseCase = get(),
            discoverWorkdirWorkspacesUseCase = get(),
            openWorkspaceUseCase = get(),
            openWorkspaceByPathUseCase = get(),
            removeRecentWorkspaceUseCase = get(),
            toolchainService = getOrNull(),
            provisionAvbKeyUseCase = getOrNull(),
            getAvailableTargetsUseCase = getOrNull(),
            getSelectedTargetUseCase = getOrNull(),
            selectTargetUseCase = getOrNull(),
            terminalLauncher = getOrNull(),
            toolchainSelectionRepository = getOrNull(),
            sourceResolver = getOrNull(),
            toolchainInstallationPort = getOrNull(),
            restoreCoordinator = getOrNull(),
        )
    }
}
