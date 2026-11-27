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

import org.ide.lti.core.model.workspace.ConfigurationSnapshot

class UnsupportedConfigurationVersionException(val version: Int, val currentSupportedVersion: Int) :
    IllegalArgumentException(
        "Configuration snapshot schema version $version is newer than " +
            "current supported version $currentSupportedVersion",
    )

class ConfigurationMigrationFailedException(val fromVersion: Int, val toVersion: Int, cause: Throwable) :
    RuntimeException(
        "Failed to migrate configuration snapshot from v$fromVersion to v$toVersion: ${cause.message}",
        cause,
    )

interface ConfigurationMigration {
    val fromVersion: Int
    val toVersion: Int
    fun migrate(snapshot: ConfigurationSnapshot): ConfigurationSnapshot
}

class ConfigurationMigrationRegistry(
    private val migrations: List<ConfigurationMigration> = emptyList(),
    private val targetVersion: Int = ConfigurationSnapshot.CURRENT_SCHEMA_VERSION,
) {
    private val migrationChain: Map<Int, ConfigurationMigration> = migrations.associateBy { it.fromVersion }

    fun migrateToLatest(snapshot: ConfigurationSnapshot): ConfigurationSnapshot {
        validateVersion(snapshot.schemaVersion)

        var active = snapshot
        while (active.schemaVersion < targetVersion) {
            val step = migrationChain[active.schemaVersion]
                ?: throw IllegalStateException(
                    "Missing migration step from version ${active.schemaVersion} towards $targetVersion",
                )
            active = try {
                step.migrate(active)
            } catch (e: Exception) {
                throw ConfigurationMigrationFailedException(step.fromVersion, step.toVersion, e)
            }
        }
        return active
    }

    private fun validateVersion(current: Int) {
        if (current > targetVersion) {
            throw UnsupportedConfigurationVersionException(
                version = current,
                currentSupportedVersion = targetVersion,
            )
        }
    }
}
