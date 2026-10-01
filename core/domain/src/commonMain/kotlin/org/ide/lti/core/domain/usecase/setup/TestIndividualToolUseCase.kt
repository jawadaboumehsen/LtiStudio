/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.setup

import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to test an individual tool binary (runs version/help verification).
 */
class TestIndividualToolUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke(toolId: String): SetupOutcome = toolchainService.testTool(toolId)
}
