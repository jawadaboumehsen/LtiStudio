/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.setup

/**
 * Repository interface for toolchain repository trust persistence.
 */
public interface ToolchainSelectionRepository {
    public suspend fun recordTrust(repoUrl: String): Result<Unit>

    public suspend fun isTrusted(repoUrl: String): Boolean
}
