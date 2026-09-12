package org.ide.lti.core.domain.usecase.setup

import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to reset the persisted setup cache and restart probing cleanly.
 */
class ResetToolchainSetupUseCase(
    private val toolchainService: ToolchainProvisioningService,
    private val repository: ToolchainSetupRepository? = null,
) {
    suspend operator fun invoke() {
        toolchainService.resetSetupCache()
        repository?.clearToolchainState()
    }
}
