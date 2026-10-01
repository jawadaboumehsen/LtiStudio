/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.migration

import kotlinx.datetime.Clock
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigurationMigrationTest {

    private fun sampleSnapshot(schemaVersion: Int): ConfigurationSnapshot {
        val target = DefaultTargetCatalog.PQ84P01_DEFAULT
        return ConfigurationSnapshot.create(
            id = "snap-test",
            workspaceId = "ws-test",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = target.availableRegions.first(),
                firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
            ),
            createdAt = Clock.System.now(),
            schemaVersion = schemaVersion,
        )
    }

    @Test
    fun latestSnapshotRequiresNoMigration() {
        val registry = ConfigurationMigrationRegistry()
        val snapshot = sampleSnapshot(ConfigurationSnapshot.CURRENT_SCHEMA_VERSION)
        val migrated = registry.migrateToLatest(snapshot)
        assertEquals(ConfigurationSnapshot.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
    }

    @Test
    fun unsupportedForwardVersionThrows() {
        val registry = ConfigurationMigrationRegistry()
        val forwardSnapshot = sampleSnapshot(ConfigurationSnapshot.CURRENT_SCHEMA_VERSION + 1)
        assertFailsWith<UnsupportedConfigurationVersionException> {
            registry.migrateToLatest(forwardSnapshot)
        }
    }

    @Test
    fun stepMigrationChainExecutesSequentially() {
        val testMigration = object : ConfigurationMigration {
            override val fromVersion: Int = 1
            override val toVersion: Int = 2
            override fun migrate(snapshot: ConfigurationSnapshot): ConfigurationSnapshot =
                snapshot.copy(schemaVersion = 2)
        }
        val registry = ConfigurationMigrationRegistry(
            migrations = listOf(testMigration),
            targetVersion = 2,
        )
        val v1 = sampleSnapshot(1)
        val result = registry.migrateToLatest(v1)
        assertEquals(2, result.schemaVersion)
    }
}
