/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.di

import org.ide.lti.core.data.repository.workspace.WorkspaceRepository
import org.ide.lti.core.data.repository.workspace.WorkspaceRepositoryImpl
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val DataModule = module {
    includes(platformModule, platformRepositoryModule)
    single<PlatformDependentDataModule> { getPlatformDataModule }
    single<NetworkMonitor> { getPlatformDataModule.networkMonitor }
    single<TimeZoneMonitor> { getPlatformDataModule.timeZoneMonitor }

    // Repositories
    singleOf(::WorkspaceRepositoryImpl) { bind<WorkspaceRepository>() }
}

internal expect val platformRepositoryModule: org.koin.core.module.Module
