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
