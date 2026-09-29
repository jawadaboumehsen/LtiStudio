/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.TargetDevice

/**
 * Use case to observe the currently selected target device profile.
 */
class GetSelectedTargetUseCase(
    private val repository: TargetRepository,
) {
    operator fun invoke(): Flow<TargetDevice> = repository.getSelectedTarget()
}
