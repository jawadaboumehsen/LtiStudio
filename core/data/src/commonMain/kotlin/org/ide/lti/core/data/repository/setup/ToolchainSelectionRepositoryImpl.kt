/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.setup

import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.ToolSelection

/**
 * Implementation of [ToolchainSelectionRepository] delegating to [ToolchainPreferencesDataSource].
 */
public class ToolchainSelectionRepositoryImpl(private val preferencesDataSource: ToolchainPreferencesDataSource) :
    ToolchainSelectionRepository {
    override suspend fun desiredSelections(distro: String): DistroToolSelections =
        preferencesDataSource.desiredSelections(distro)

    override suspend fun saveSelections(
        distro: String,
        expectedRevision: Long,
        desired: Map<ToolGroupId, ToolSelection>,
    ): Result<Long> = preferencesDataSource.saveSelections(
        distro = distro,
        expectedRevision = expectedRevision,
        desired = desired.mapKeys { it.key.value },
    )

    override suspend fun recordTrust(repoUrl: String): Result<Unit> = preferencesDataSource.recordTrust(repoUrl)

    override suspend fun isTrusted(repoUrl: String): Boolean = preferencesDataSource.isTrusted(repoUrl)
}
