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

public class ToolchainSelectionRepositoryImpl(private val preferencesDataSource: ToolchainPreferencesDataSource) :
    ToolchainSelectionRepository {
    override suspend fun recordTrust(repoUrl: String): Result<Unit> = preferencesDataSource.recordTrust(repoUrl)

    override suspend fun isTrusted(repoUrl: String): Boolean = preferencesDataSource.isTrusted(repoUrl)
}
