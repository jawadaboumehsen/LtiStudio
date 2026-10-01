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

import org.ide.lti.core.domain.setup.ToolchainProvisioningService

/**
 * Use case to provision (or verify) the machine-local AVB 2.0 RSA-4096 signing key.
 */
class ProvisionAvbKeyUseCase(
    private val toolchainService: ToolchainProvisioningService,
) {
    suspend operator fun invoke(): Boolean = toolchainService.provisionAvbKey()
}
