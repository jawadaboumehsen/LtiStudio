/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.di

import org.ide.lti.feature.plugins.PluginManagerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pluginsModule = module {
    viewModel {
        PluginManagerViewModel(
            installPluginUseCase = get(),
            pluginLifecycleUseCase = get(),
            authorProjectTrustUseCase = get(),
            marketplaceIndexPort = get(),
            pluginInstallPort = get(),
            workspaceManager = getOrNull(),
        )
    }
}
