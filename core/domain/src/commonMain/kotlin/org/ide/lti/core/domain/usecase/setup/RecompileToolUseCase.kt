package org.ide.lti.core.domain.usecase.setup

import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to recompile or re-acquire an individual tool binary.
 */
class RecompileToolUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke(toolId: String): Boolean = toolchainService.recompileTool(toolId)
}
