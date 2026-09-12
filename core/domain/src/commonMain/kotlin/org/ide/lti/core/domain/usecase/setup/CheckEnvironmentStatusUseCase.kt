package org.ide.lti.core.domain.usecase.setup

import org.ide.lti.core.domain.setup.ToolchainProvisioningService
import org.ide.lti.core.domain.setup.ToolchainSetupState

/**
 * Use case to probe the current toolchain setup status without mutating environment state.
 */
class CheckEnvironmentStatusUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke(): ToolchainSetupState = toolchainService.checkStatus()
}
