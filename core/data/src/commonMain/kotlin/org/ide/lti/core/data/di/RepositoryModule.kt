/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ide.lti.core.data.repository.run.InMemoryRunRepository
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.data.repository.snapshot.SnapshotRepositoryImpl
import org.ide.lti.core.data.repository.target.TargetRepositoryImpl
import org.ide.lti.core.data.repository.user.UserDataRepositoryImpl
import org.ide.lti.core.data.repository.workspace.WorkDirDiscoveryService
import org.ide.lti.core.data.repository.workspace.WorkspaceRepositoryImpl
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.data.utils.NetworkMonitor
import org.ide.lti.core.data.utils.TimeZoneMonitor
import org.ide.lti.core.datastore.di.DatastoreModule
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.user.UserDataRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val DataModule = module {
    includes(platformModule, platformRepositoryModule, DatastoreModule)
    single<PlatformDependentDataModule> { getPlatformDataModule }
    single<NetworkMonitor> { getPlatformDataModule.networkMonitor }
    single<TimeZoneMonitor> { getPlatformDataModule.timeZoneMonitor }

    // User Preferences & Settings DataStore
    singleOf(::UserDataRepositoryImpl) { bind<UserDataRepository>() }
    single {
        val repo = get<UserDataRepository>()
        val initialData = repo.currentUserData
        AppSettingsState(
            initialTheme = initialData.theme,
            initialEffectsEnabled = initialData.effectsEnabled,
            initialReducedMotion = initialData.reducedMotion,
            initialEditorSettings = initialData.editorSettings,
            initialNotificationSettings = initialData.notificationSettings,
            userDataRepository = repo,
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
        )
    }

    // Services & Repositories
    single { WorkDirDiscoveryService() }
    single<WorkspaceRepository> {
        WorkspaceRepositoryImpl(
            preferencesDataSource = get(),
            discoveryService = get(),
            notifier = getOrNull(),
        )
    }
    singleOf(::TargetRepositoryImpl) { bind<TargetRepository>() }
    singleOf(::ToolchainSetupRepositoryImpl) { bind<ToolchainSetupRepository>() }
    single<SnapshotRepository> { SnapshotRepositoryImpl(dataSource = get()) }
    // Replaced by the durable RunRepositoryImpl in US4 (T090).
    single<RunRepository> { InMemoryRunRepository() }
}


internal expect val platformRepositoryModule: org.koin.core.module.Module
