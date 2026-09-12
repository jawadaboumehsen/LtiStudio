package org.ide.lti.core.domain.usecase.setup

import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to run the full end-to-end toolchain setup sequence (probe, start daemon,
 * sync repos, build tools), skipping stages already completed.
 */
class RunIncrementalSetupUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke() = toolchainService.runFullSetup()
}
