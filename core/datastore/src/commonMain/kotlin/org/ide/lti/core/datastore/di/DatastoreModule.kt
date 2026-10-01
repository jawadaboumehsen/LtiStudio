/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore.di

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import org.ide.lti.core.datastore.LtiPreferencesDataSource
import org.ide.lti.core.datastore.RunFileStorage
import org.ide.lti.core.datastore.RunRecordDataSource
import org.ide.lti.core.datastore.SnapshotPreferencesDataSource
import org.ide.lti.core.datastore.StudioPresentationStore
import org.ide.lti.core.datastore.TargetPreferencesDataSource
import org.ide.lti.core.datastore.ToolchainJournalStorage
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.datastore.WorkspacePreferencesDataSource
import org.ide.lti.core.datastore.defaultRunFileStorage
import org.ide.lti.core.datastore.defaultToolchainJournalStorage
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module declaring persistent DataStore dependencies.
 */
val DatastoreModule = module {
    single<Settings> { Settings() }
    single<Json> { ToolchainPreferencesDataSource.defaultJson() }
    single<RunFileStorage> { defaultRunFileStorage() }
    single<ToolchainJournalStorage> { defaultToolchainJournalStorage() }
    single { LtiPreferencesDataSource(settings = get()) }
    single { ToolchainPreferencesDataSource(settings = get(), json = get(), storage = get()) }
    single { WorkspacePreferencesDataSource(settings = get(), json = get()) }
    single { TargetPreferencesDataSource(settings = get(), json = get()) }
    single { SnapshotPreferencesDataSource(settings = get(), json = get()) }
    single { StudioPresentationStore(settings = get(), json = get()) } bind org.ide.lti.core.domain.ports.StudioPresentationStorePort::class
    single { RunRecordDataSource(settings = get(), json = get(), fileStorage = get()) }
}
