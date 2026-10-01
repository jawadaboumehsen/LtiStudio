/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.repository.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.target.TargetDevice

/**
 * Repository for target hardware device profiles and their active build configuration.
 */
public interface TargetRepository {
    public fun getAvailableTargets(): Flow<List<TargetDevice>>

    public fun getSelectedTarget(): Flow<TargetDevice>

    public suspend fun selectTarget(targetId: String): Result<TargetDevice>

    public suspend fun addTarget(target: TargetDevice): Result<TargetDevice>

    public suspend fun updateTarget(target: TargetDevice): Result<TargetDevice>

    public suspend fun deleteTarget(targetId: String): Result<Unit>
}
