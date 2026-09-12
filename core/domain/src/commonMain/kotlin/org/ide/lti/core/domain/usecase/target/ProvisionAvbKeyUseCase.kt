package org.ide.lti.core.domain.usecase.target

import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to provision (or verify) the machine-local AVB 2.0 RSA-4096 signing key.
 */
class ProvisionAvbKeyUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke(): Boolean = toolchainService.provisionAvbKey()
}
