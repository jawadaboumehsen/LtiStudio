/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import org.ide.lti.core.data.repository.setup.ToolchainSetupRepositoryImpl
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource

/**
 * The real repository and datastore over in-memory settings: durable runs must be journaled (US3), so
 * every provisioner test that confirms a plan or repairs a tool needs a journal to write to.
 */
internal fun inMemoryToolchainRepository(settings: MapSettings = MapSettings()): ToolchainSetupRepositoryImpl =
    ToolchainSetupRepositoryImpl(
        ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined),
    )
