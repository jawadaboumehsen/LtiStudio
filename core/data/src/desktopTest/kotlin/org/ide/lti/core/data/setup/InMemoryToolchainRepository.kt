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
